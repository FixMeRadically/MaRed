from pathlib import Path
from zipfile import ZipFile
from lxml import etree as E
B=Path('output/gost-work/исправленные_работы')
for f in B.glob('*'):
 if f.suffix not in ['.docx','.xlsx']:continue
 print('\nFILE',f.name)
 with ZipFile(f) as z:
  for n in z.namelist():
   if not n.endswith('.xml') or (f.suffix=='.docx' and not n.startswith('word/')):continue
   root=E.fromstring(z.read(n))
   for e in root.iter():
    if E.QName(e).localname in ['t'] and e.text and any(c in e.text for c in ';→←⇒«»“”()'):
     print(n,repr(e.text))
