from docx import Document
from pathlib import Path
for name in ['01_План_квартиры.docx','02_Семейное_дерево.docx']:
 d=Document(Path('output/gost-work/source')/name)
 print(name)
 print(d._element.xml[:7000])
