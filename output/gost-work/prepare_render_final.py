from pathlib import Path
s=Path('output/gost-work/render_compact.py').read_text(encoding='utf8').replace("B/'исправленные_работы'","B/'финальные_работы'").replace("B/'qa-compact'/p.stem","B/'qa-final'/p.stem")
Path('output/gost-work/render_final.py').write_text(s,encoding='utf8')
Path('output/gost-work/qa-final').mkdir(exist_ok=True)
