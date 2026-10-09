<#
  AR EduVision - start the backend jar as a detached background process.

  Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File start-backend.ps1 -Port 8081

  Writes target\backend.log, target\backend.err and target\backend.pid.
#>
param(
    [int]$Port = 8081
)

$ErrorActionPreference = 'Stop'

$backendDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$targetDir  = Join-Path $backendDir 'target'
$jar        = Join-Path $targetDir 'eduvision-backend-1.0.0.jar'

if (-not (Test-Path -LiteralPath $targetDir)) {
    New-Item -ItemType Directory -Path $targetDir | Out-Null
}
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Jar not found: $jar  --  run build.cmd first."
}

$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
$javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) {
    throw "java.exe not found under $env:JAVA_HOME"
}

$log = Join-Path $targetDir 'backend.log'
$err = Join-Path $targetDir 'backend.err'
$pidFile = Join-Path $targetDir 'backend.pid'

# relative jar path keeps it space-free for Start-Process argument quoting
$p = Start-Process -FilePath $javaExe `
        -ArgumentList @('-jar', 'target\eduvision-backend-1.0.0.jar', "--server.port=$Port") `
        -WorkingDirectory $backendDir `
        -RedirectStandardOutput $log `
        -RedirectStandardError $err `
        -WindowStyle Hidden `
        -PassThru

[System.IO.File]::WriteAllText($pidFile, "$($p.Id)")
Write-Output "STARTED pid=$($p.Id) port=$Port log=$log"