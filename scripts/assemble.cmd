@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0assemble.ps1" %*
