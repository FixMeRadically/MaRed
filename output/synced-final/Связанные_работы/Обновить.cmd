@echo off
chcp 65001 >nul
powershell.exe -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0Обновить_работы.ps1"
if errorlevel 1 echo Ошибка обновления. Проверьте сообщение выше.
pause
