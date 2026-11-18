from docx import Document
from pathlib import Path
p=Path('output/gost-work/готовые_работы/01_План_квартиры.docx')
d=Document(p)
for s in d.sections:
 print(s.footer._element.xml[:10000])
