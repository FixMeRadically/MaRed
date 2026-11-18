from pathlib import Path
s=Path('output/gost-work/render_pdf.py').read_text(encoding='utf8').replace("B/'готовые_работы'","B/'исправленные_работы'").replace("B/'qa'/p.stem","B/'qa-compact'/p.stem")
Path('output/gost-work/render_compact.py').write_text(s,encoding='utf8')
