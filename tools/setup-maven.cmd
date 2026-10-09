@echo off
REM ---------------------------------------------------------------------------
REM AR EduVision - one-time local Maven setup (no admin, no PATH changes).
REM Downloads Apache Maven 3.9.16 binary zip into ..\.tools and unzips it.
REM Safe to re-run: the zip is cached.
REM ---------------------------------------------------------------------------
setlocal

set "TOOLS=%~dp0..\.tools"
set "VER=3.9.16"
set "ZIP=%TOOLS%\apache-maven-%VER%-bin.zip"
set "MVN=%TOOLS%\apache-maven-%VER%\bin\mvn.cmd"

if exist "%MVN%" (
  echo [OK] Maven already present: %MVN%
  exit /b 0
)

if not exist "%TOOLS%" mkdir "%TOOLS%"

if not exist "%ZIP%" (
  echo [INFO] Downloading Apache Maven %VER% ...
  curl -fL -o "%ZIP%" "https://archive.apache.org/dist/maven/maven-3/%VER%/binaries/apache-maven-%VER%-bin.zip"
  if errorlevel 1 (
    echo [INFO] archive.apache.org failed, trying dlcdn.apache.org ...
    curl -fL -o "%ZIP%" "https://dlcdn.apache.org/maven/maven-3/%VER%/binaries/apache-maven-%VER%-bin.zip"
    if errorlevel 1 (
      echo [ERROR] Maven download failed.
      exit /b 1
    )
  )
)

echo [INFO] Unzipping to %TOOLS% ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%ZIP%' -DestinationPath '%TOOLS%' -Force"

if exist "%MVN%" (
  echo [OK] Maven ready: %MVN%
  exit /b 0
)

echo [ERROR] mvn.cmd not found after extraction.
exit /b 1