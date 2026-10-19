@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-test-server.ps1" -Skyblock %*
exit /b %ERRORLEVEL%
