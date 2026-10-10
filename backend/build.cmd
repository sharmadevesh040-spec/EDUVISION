@echo off
REM ---------------------------------------------------------------------------
REM AR EduVision - backend Maven build
REM   Produces target\eduvision-backend-1.0.0.jar
REM Uses the project-local Maven (no system install, no admin, PATH untouched).
REM ---------------------------------------------------------------------------
setlocal

set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.2"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "BACKEND_DIR=%~dp0"
for %%I in ("%BACKEND_DIR%..") do set "PROJECT_DIR=%%~fI"
set "MVN=%PROJECT_DIR%\.tools\apache-maven-3.9.16\bin\mvn.cmd"

if not exist "%MVN%" (
  echo [ERROR] Local Maven not found at: %MVN%
  echo         Run tools\setup-maven.cmd once, or see README.md
  exit /b 1
)

echo === Building AR EduVision backend (Maven 3.9.16 / Java 25) ===
echo JAVA_HOME=%JAVA_HOME%
echo MVN=%MVN%
echo.

pushd "%BACKEND_DIR%"
call "%MVN%" clean package %*
set "RC=%ERRORLEVEL%"
popd

echo.
if "%RC%"=="0" (
  echo === BUILD SUCCESS === jar: %BACKEND_DIR%target\eduvision-backend-1.0.0.jar
) else (
  echo === BUILD FAILED (exit %RC%) ===
)
exit /b %RC%