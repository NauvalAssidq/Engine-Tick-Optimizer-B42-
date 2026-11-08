@echo off
title Engine Tick Optimizer - Uninstaller
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\uninstall.ps1" -RestoreBackup
echo.
pause
