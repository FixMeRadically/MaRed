from pathlib import Path
p=Path('output/synced-final/Связанные_работы/Обновить.cmd')
p.write_text('@echo off\nchcp 65001 >nul\npowershell.exe -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0Обновить_работы.ps1"\nif errorlevel 1 echo Ошибка обновления. Проверьте сообщение выше.\npause\n',encoding='utf8')
