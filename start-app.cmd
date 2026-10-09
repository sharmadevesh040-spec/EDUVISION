@echo off
rem AR EduVision - double-click to start backend + frontend, then open the app.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-app.ps1" %*
set RC=%ERRORLEVEL%
if "%RC%"=="0" start "" http://localhost:5173
exit /b %RC%
