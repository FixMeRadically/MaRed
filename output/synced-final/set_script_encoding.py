from pathlib import Path
p=Path('output/synced-final/Связанные_работы/Обновить_работы.ps1')
s=p.read_text(encoding='utf-8-sig').lstrip('\ufeff');p.write_text(s,encoding='utf-8-sig')
