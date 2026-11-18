from pathlib import Path
from zipfile import ZipFile
from lxml import etree as E
from docx import Document
from PIL import Image
B=Path('output/gost-work/исправленные_работы')
for f in [B/'04_Оценки.xlsx',B/'05_Справочник.xlsx']:
 with ZipFile(f) as z:
  print(f.name,z.read('xl/workbook.xml').decode()[:4500])
d=Document(B/'05_Отчёт.docx')
for s in d.inline_shapes:
 rel=s._inline.xpath('.//a:blip')[0].get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}embed')
 part=d.part.related_parts[rel]
 print(str(part.partname),len(part.blob),s.width,s.height)
