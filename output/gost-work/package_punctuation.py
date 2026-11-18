from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from lxml import etree as E
from pypdf import PdfReader
import json
B=Path('output/gost-work');O=B/'финальные_работы'
for f in O.glob('*'):
 if f.suffix not in ['.docx','.xlsx']:continue
 with ZipFile(f) as z:
  for n in z.namelist():
   if not n.endswith('.xml'):continue
   root=E.fromstring(z.read(n))
   for e in root.iter():
    if E.QName(e).localname=='t' and e.text:
     assert not any(c in e.text for c in ';→⇒«»“”„()'),(f.name,n,e.text)
 if f.suffix=='.xlsx':
  with ZipFile(B/'исправленные_работы'/f.name) as a,ZipFile(f) as b:
   # All workbook content except the visible shared strings is byte-for-byte intact.
   assert a.namelist()==b.namelist()
   assert all(a.read(n)==b.read(n) for n in a.namelist() if n!='xl/sharedStrings.xml')
counts={p.name:len(PdfReader(p).pages) for p in O.glob('*.pdf')}
assert counts['01_Отчёт.pdf']==counts['02_Отчёт.pdf']==counts['04_Отчёт.pdf']==counts['05_Отчёт.pdf']==3
assert counts['03_Реферат.pdf']==5
archive=B/'Практические_работы_1-5_финальные.zip'
with ZipFile(archive,'w',ZIP_DEFLATED) as z:
 for p in sorted(O.iterdir()):
  if p.suffix in ['.docx','.xlsx','.pdf'] and not p.name.startswith('~$'):z.write(p,p.name)
with ZipFile(archive) as z:assert z.testzip() is None;assert len(z.namelist())==18
(B/'qa-final/checks.json').write_text(json.dumps(counts,ensure_ascii=False,indent=2),encoding='utf8')
print(archive.resolve());print('Punctuation, pagination and formula preservation verified. 18 files packaged.')
