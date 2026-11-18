from pathlib import Path
from zipfile import ZipFile
from docx import Document
base=Path('output/gost-work')
z=ZipFile(r'C:\Users\fixme\Downloads\Практические_работы_1-5_WPS.zip')
for i in z.infolist():
 print(repr(i.filename), i.file_size)
z.extractall(base/'source')
paths=[Path(r'C:\Users\fixme\Downloads\Оформление реферата и любого текста отчетов.docx'),Path(r'C:\Users\fixme\Downloads\Telegram Desktop\Титульный лист отчёта.docx')]+list((base/'source').rglob('*.docx'))
for j,p in enumerate(paths):
 d=Document(p)
 lines=[f'FILE {p}']
 for s in d.sections: lines.append(f'SECTION size={s.page_width.cm},{s.page_height.cm} margins={s.top_margin.cm},{s.bottom_margin.cm},{s.left_margin.cm},{s.right_margin.cm}')
 for i,para in enumerate(d.paragraphs):
  if para.text: lines.append(f'{i} [{para.style.name}] {para.text}')
 for t in d.tables:
  lines.append('TABLE')
  for r in t.rows: lines.append(' | '.join(c.text for c in r.cells))
 (base/'qa'/f'inspect-{j}.txt').write_text('\n'.join(lines),encoding='utf8')
 print('\n'.join(lines)[:22000])
