from pathlib import Path
from zipfile import ZipFile
p=Path('output/synced-final/test-relocated/Копия 04_Оценки.xlsx')
with ZipFile(p) as z:items=[(i,z.read(i.filename)) for i in z.infolist()]
with ZipFile(p,'w') as z:
 for i,data in items:
  if i.filename.endswith('.xml'):data=data.replace(b"encoding='utf8'",b"encoding='UTF-8'")
  z.writestr(i,data)
print('Test workbook XML encoding normalized')
