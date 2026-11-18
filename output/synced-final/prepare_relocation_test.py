from pathlib import Path
from zipfile import ZipFile
from lxml import etree as E
import shutil
B=Path('output/synced-final');F=B/'Связанные_работы';T=B/'test-relocated'
T.mkdir(exist_ok=True)
for p in F.iterdir():
 if p.suffix in ['.docx','.xlsx','.ps1']:shutil.copy2(p,T/p.name)
shutil.copytree(F/'Связи',T/'Связи',dirs_exist_ok=True)
for name,old,new in [('02_Семейное_дерево.docx','Тронин Георгий Владимирович','ПРОВЕРКА СВЯЗИ'),('01_План_квартиры.docx','тамбур','ПРОВЕРКА ТАБЛИЦЫ')]:
 p=T/name
 with ZipFile(p) as z:items=[(i,z.read(i.filename)) for i in z.infolist()]
 with ZipFile(p,'w') as z:
  for i,data in items:
   if i.filename=='word/document.xml':data=data.replace(old.encode(),new.encode())
   z.writestr(i,data)
p=T/'Копия 04_Оценки.xlsx'
with ZipFile(p) as z:items=[(i,z.read(i.filename)) for i in z.infolist()]
with ZipFile(p,'w') as z:
 for i,data in items:
  if i.filename=='xl/worksheets/sheet1.xml':
   root=E.fromstring(data)
   for c in root:
    pass
   node=root.xpath('//*[local-name()="c" and @r="C12"]/*[local-name()="v"]')[0]
   print('Test C12:',node.text,'-> 3');node.text='3'
   data=E.tostring(root,encoding='utf8',xml_declaration=True,standalone=True)
  z.writestr(i,data)
for p in F.glob('*Отчёт.docx'):
 with ZipFile(p) as z:
  r=E.fromstring(z.read('word/_rels/document.xml.rels'))
  print(p.name,[(e.get('Id'),e.get('Target')) for e in r if e.get('TargetMode')=='External'])
