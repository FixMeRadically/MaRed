from pathlib import Path
from pdf2image import convert_from_path
B=Path('output/synced-final/qa')
for p in B.glob('*.pdf'):
 pages=convert_from_path(str(p),dpi=110,poppler_path=r'C:\Users\fixme\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\poppler\Library\bin')
 for i,im in enumerate(pages):im.save(B/(p.stem+f'-{i+1}.png'))
