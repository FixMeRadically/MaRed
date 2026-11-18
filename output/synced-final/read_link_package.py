from zipfile import ZipFile
from pathlib import Path
from lxml import etree as E
f=Path('output/synced-final/qa/test-linked.docx')
with ZipFile(f) as z:
 print(z.read('word/_rels/document.xml.rels').decode())
 r=E.fromstring(z.read('word/document.xml'))
 for e in r.xpath('//*[local-name()="OLEObject" or local-name()="instrText"]'):print(E.tostring(e).decode()[:1800])
