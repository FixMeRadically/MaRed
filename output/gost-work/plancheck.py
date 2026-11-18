from pathlib import Path
from docx import Document
from docx.oxml.ns import qn
import pypdfium2 as p
B=Path('output/gost-work')
d=Document(B/'готовые_работы/01_План_квартиры.docx')
for s in d.sections:
 for t in s.footer.tables:print(t._tbl.tblPr.find(qn('w:tblpPr')).attrib)
f=p.PdfDocument(B/'готовые_работы/01_План_квартиры.pdf'); f[1].render(scale=1.3).to_pil().save(B/'qa/plan2.png')
