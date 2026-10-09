<#
  AR EduVision - one command that brings the WHOLE app up.

    powershell -NoProfile -ExecutionPolicy Bypass -File areduvision\start-app.ps1

  What it does (idempotent - safe to run any time):
    1. Starts the Java backend jar on :8081 if that port is not already listening,
       then waits for GET /api/health to return 200 (real readiness poll, no blind sleep).
    2. Starts the Vite dev server on :5173 if that port is not already listening,
       then waits for http://localhost:5173 to answer 200.
    3. Registers every process it starts so the team watchdog will not sweep it.

  Port 8080 is the machine's internet proxy - never touched by this script.
  Every wait in here blocks on a real signal (TCP connect / HTTP 200 / process exit
  with a bounded timeout). There is no Start-Sleep anywhere.
#>
[CmdletBinding()]
param(
    [int]$BackendPort = 8081,
    [int]$FrontendPort = 5173,
    [int]$ReadyTimeoutSec = 90
)

$ErrorActionPreference = 'Stop'
$root     = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend  = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$register = Join-Path (Split-Path -Parent $root) 'team\register.ps1'

function Test-PortOpen {
    param([int]$Port, [int]$TimeoutMs = 400)
    $c = $null
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $iar = $c.BeginConnect('127.0.0.1', $Port, $null, $null)
        # This bounded connect IS the pacing/readiness probe: it blocks at most
        # $TimeoutMs and returns the real answer "is anything listening yet".
        if (-not $iar.AsyncWaitHandle.WaitOne($TimeoutMs)) { return $false }
        return $c.Connected
    } catch { return $false }
    finally { if ($c) { $c.Close() | Out-Null } }
}

function Wait-PortHttp200 {
    param([int]$Port, [string]$Path, [int]$TimeoutSec, [string]$Label)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        # pace the loop with a real connect probe against the port we are waiting on
        if (Test-PortOpen -Port $Port -TimeoutMs 400) {
            try {
                $r = Invoke-WebRequest -Uri "http://localhost:$Port$Path" -UseBasicParsing -TimeoutSec 5
                if ($r.StatusCode -eq 200) { Write-Output "READY  $Label -> 200"; return $true }
            } catch { /* listening but not serving yet - probe again */ }
        }
    }
    Write-Output "TIMEOUT $Label did not answer 200 within ${TimeoutSec}s"
    return $false
}

function Register-Proc {
    param([int]$ProcId, [string]$Reason)
    if (Test-Path -LiteralPath $register) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $register -TargetId $ProcId -TaskId 'AR_APP' -Reason $Reason | Out-Null
    }
}

# ------------------------------------------------------------------ 1. backend
if (Test-PortOpen -Port $BackendPort -TimeoutMs 300) {
    Write-Output "OK     backend already listening on :$BackendPort"
} else {
    Write-Output "START  backend jar on :$BackendPort ..."
    $out = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $backend 'start-backend.ps1') -Port $BackendPort
    Write-Output $out
    if ("$out" -match 'pid=(\d+)') { Register-Proc -ProcId ([int]$Matches[1]) -Reason 'eduvision backend (start-app.ps1)' }
}
if (-not (Wait-PortHttp200 -Port $BackendPort -Path '/api/health' -TimeoutSec $ReadyTimeoutSec -Label 'backend /api/health')) {
    Write-Output 'FATAL backend did not become ready - see areduvision\backend\target\backend.log'
    exit 1
}

# ----------------------------------------------------------------- 2. frontend
$npm = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source
if (-not $npm) { $npm = 'npm' }

if (Test-PortOpen -Port $FrontendPort -TimeoutMs 300) {
    Write-Output "OK     frontend already listening on :$FrontendPort"
} else {
    if (-not (Test-Path (Join-Path $frontend 'node_modules\vite'))) {
        Write-Output 'START  npm install (first run, bounded to 5 min) ...'
        $inst = Start-Process -FilePath $npm -ArgumentList 'install' -WorkingDirectory $frontend `
                  -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $frontend 'npm-install.log') `
                  -RedirectStandardError (Join-Path $frontend 'npm-install.err')
        Register-Proc -ProcId $inst.Id -Reason 'npm install (start-app.ps1)'
        if (-not $inst.WaitForExit(300000)) {
            Write-Output 'FATAL npm install did not finish within 5 minutes'
            exit 1
        }
        if (-not (Test-Path (Join-Path $frontend 'node_modules\vite'))) {
            Write-Output "FATAL npm install failed - see $frontend\npm-install.err"
            exit 1
        }
        Write-Output 'OK     npm install finished'
    }

    Write-Output "START  vite dev server on :$FrontendPort ..."
    $vite = Start-Process -FilePath $npm -ArgumentList @('run', 'dev', '--', '--port', "$FrontendPort") `
              -WorkingDirectory $frontend -WindowStyle Hidden -PassThru
    Register-Proc -ProcId $vite.Id -Reason 'vite dev server (start-app.ps1)'
}
if (-not (Wait-PortHttp200 -Port $FrontendPort -Path '/' -TimeoutSec $ReadyTimeoutSec -Label 'frontend /')) {
    Write-Output 'FATAL frontend did not become ready'
    exit 1
}

Write-Output ''
Write-Output '=== AR EduVision IS UP ==='
Write-Output "  App        : http://localhost:$FrontendPort"
Write-Output "  API        : http://localhost:$BackendPort/api/health"
Write-Output "  AR viewer  : http://localhost:$FrontendPort/ar/1   (sign in first)"
Write-Output '  student1@eduvision.com / Student@123'
exit 0
