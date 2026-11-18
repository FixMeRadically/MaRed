from pathlib import Path
from zipfile import ZipFile
from docx import Document
from lxml import etree as E
import difflib,hashlib
B=Path('output/synced-final');B.mkdir(exist_ok=True)
with ZipFile(r'C:\Users\fixme\Downloads\ФИНАЛ.zip') as z:z.extractall(B/'input')
S=B/'input/ФИНАЛ'
for f in S.iterdir():
 old=Path('output/gost-work/финальные_работы')/f.name.replace('Копия ','')
 if f.suffix=='.docx':
  d=Document(f);lines=[p.text for p in d.paragraphs]
  for t in d.tables:
   for r in t.rows:lines.append(' | '.join(c.text for c in r.cells))
  # Include text stored inside drawing text boxes.
  nested=[e.text for e in d._element.xpath('.//w:txbxContent//w:t') if e.text]
  lines+=nested
  (B/(f.stem+'.txt')).write_text('\n'.join(lines),encoding='utf8')
  print('\nFILE',f.name,'sections',len(d.sections),'shapes',len(d.inline_shapes))
  if old.exists():
   od=Document(old);before=[p.text for p in od.paragraphs]
   before+=[e.text for e in od._element.xpath('.//w:txbxContent//w:t') if e.text]
   print('\n'.join(difflib.unified_diff(before,lines,n=1))[:8500])
  print('Nested:',nested)
 else:
  print('\nFILE',f.name)
  with ZipFile(f) as z:
   r=E.fromstring(z.read('xl/sharedStrings.xml'))
   strings=[''.join(e.itertext()) for e in r]
   (B/(f.stem+'.txt')).write_text('\n'.join(strings),encoding='utf8')
   if old.exists():
    with ZipFile(old) as oz:
     rr=E.fromstring(oz.read('xl/sharedStrings.xml'));prior=[''.join(e.itertext()) for e in rr]
    print('\n'.join(difflib.unified_diff(prior,strings,n=1))[:9000])
