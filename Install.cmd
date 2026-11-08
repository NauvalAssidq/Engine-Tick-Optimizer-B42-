@echo off
title Engine Tick Optimizer - Installer
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\install.ps1"
echo.
pause
