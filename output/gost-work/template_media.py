from zipfile import ZipFile
from pathlib import Path
from lxml import etree
p=Path(r'C:\Users\fixme\Downloads\Telegram Desktop\Титульный лист отчёта.docx')
with ZipFile(p) as z:
 for n in z.namelist():
  if n.startswith('word/media/'):
   q=Path('output/gost-work/qa')/Path(n).name;q.write_bytes(z.read(n));print(q)
 print(z.read('word/document.xml').decode()[:16000])
