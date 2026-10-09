@echo off
REM ---------------------------------------------------------------------------
REM AR EduVision - run the backend
REM
REM   Usage:  run.cmd              (foreground, logs to console)
REM           run.cmd background    (start hidden, log to target\backend.log)
REM
REM   application.yml sets server.port: 8080 - the documented default.
REM   BUT on THIS machine 127.0.0.1:8080 is permanently held by
REM   team\oclimit\local-switch-proxy.py - the HTTP CONNECT proxy that carries
REM   this box's internet. That process is critical team infra, NEVER kill it.
REM   So run.cmd auto-falls back to 8081 when 8080 is taken and prints the URL
REM   it actually bound to. Force a port with:  set EDUVISION_PORT=xxxx
REM
REM   Health check:  Invoke-WebRequest http://localhost:PORT/api/health
REM ---------------------------------------------------------------------------
setlocal

set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "BACKEND_DIR=%~dp0"
for %%I in ("%BACKEND_DIR%..") do set "PROJECT_DIR=%%~fI"
set "MVN=%PROJECT_DIR%\.tools\apache-maven-3.9.16\bin\mvn.cmd"
set "JAR=%BACKEND_DIR%target\eduvision-backend-1.0.0.jar"
set "LOG=%BACKEND_DIR%target\backend.log"
set "USEPORT=%EDUVISION_PORT%"
if "%USEPORT%"=="" set "USEPORT=8080"

if not exist "%MVN%" (
  echo [ERROR] Local Maven not found at: %MVN%
  echo         Run tools\setup-maven.cmd once, or see README.md
  exit /b 1
)

REM ---- pick a free port -----------------------------------------------------
call :PORT_BUSY "%USEPORT%"
if not "%PORT_BUSY%"=="1" goto port_ok
if not "%EDUVISION_PORT%"=="" (
  echo [ERROR] Requested port %USEPORT% is already in use.
  exit /b 1
)
call :PORT_BUSY 8081
if "%PORT_BUSY%"=="1" (
  echo [ERROR] Both 8080 and 8081 are in use. Set EDUVISION_PORT=FreePort and retry.
  exit /b 1
)
set "USEPORT=8081"
echo [WARN] Port 8080 is in use by team\oclimit\local-switch-proxy.py
echo [WARN] Falling back to port 8081 - override with:  set EDUVISION_PORT=xxxx

:port_ok
echo [INFO] Backend port: %USEPORT%   URL: http://localhost:%USEPORT%/api/health
echo.

if not exist "%JAR%" (
  echo [INFO] Jar not found - building first via build.cmd ...
  call "%BACKEND_DIR%build.cmd"
  if errorlevel 1 (
    echo [ERROR] Build failed.
    exit /b 1
  )
)

if /I "%~1"=="background" goto background

echo === AR EduVision backend starting - foreground ===
java -jar "%JAR%" "--server.port=%USEPORT%"
exit /b %ERRORLEVEL%

:background
if not exist "%BACKEND_DIR%target" mkdir "%BACKEND_DIR%target"
echo === AR EduVision backend starting - background ===
powershell -NoProfile -ExecutionPolicy Bypass -File "%BACKEND_DIR%start-backend.ps1" -Port %USEPORT%
if errorlevel 1 (
  echo [ERROR] start-backend.ps1 failed.
  exit /b 1
)
echo Waiting for startup - log: %LOG%
ping -n 31 127.0.0.1 >nul
curl -s "http://localhost:%USEPORT%/api/health"
echo.
exit /b 0

REM ---- helper: PORT_BUSY=1 when the given port has a listener ----------------
:PORT_BUSY
set "PORT_BUSY=0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Get-NetTCPConnection -LocalPort %~1 -State Listen -ErrorAction SilentlyContinue) { exit 1 } else { exit 0 }"
if errorlevel 1 set "PORT_BUSY=1"
exit /b 0
