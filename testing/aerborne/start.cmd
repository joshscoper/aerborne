@echo off
rem Opens the Aerborne test website and test server in their own windows.
setlocal
start "Aerborne test website" powershell.exe -NoExit -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-website.ps1"
start "Aerborne test server" powershell.exe -NoExit -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-server.ps1" %*
