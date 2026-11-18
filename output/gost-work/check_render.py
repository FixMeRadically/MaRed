import pypdfium2 as p
from pathlib import Path
from pypdf import PdfReader
from docx import Document
B=Path('output/gost-work')
f=B/'готовые_работы/01_Отчёт.pdf'
d=p.PdfDocument(f)
d[6].render(scale=1.3).to_pil().save(B/'qa/isolated7.png')
r=PdfReader(f)
for i in [0,5,6,7,8]:
 pg=r.pages[i];print(i+1,pg.mediabox,pg.cropbox);print(pg.get_contents().get_data()[-2000:-1000])
d=Document(B/'готовые_работы/01_План_квартиры.docx')
for s in d.sections:
 for t in s.footer.tables:
  print(t._tbl.tblPr.xml[:700], [c.width.twips for c in t.columns])
