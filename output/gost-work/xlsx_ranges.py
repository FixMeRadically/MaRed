from zipfile import ZipFile
from pathlib import Path
from lxml import etree as E
for f in Path('output/gost-work/исправленные_работы').glob('*.xlsx'):
 with ZipFile(f) as z:
  for n in ['xl/worksheets/sheet1.xml','xl/worksheets/sheet3.xml']:
   if n in z.namelist():
    r=E.fromstring(z.read(n));print(f.name,n)
    for e in r:
     if E.QName(e).localname in ['dimension','pageSetup','pageMargins','mergeCells','drawing']:print(E.tostring(e).decode()[:1200])
