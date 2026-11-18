from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from lxml import etree as E
B=Path(__file__).parent/'Связанные_работы'
ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
changes={
 '02_Отчёт.docx':{
  'Подписан блок Тронина Кирилла Валерьевича. ФИО родственников не предоставлены, поэтому вместо них оставлены поля. Перед сдачей их нужно заполнить своими сведениями.':
  'В дереве указаны Тронин Кирилл Валерьевич, его родители, бабушки и дедушки. ФИО родственников заполнены в исходном файле семейного дерева. Изображение в отчёте связано с этим файлом.',
 },
 '02_Семейное_дерево.docx':{
  'Схематические портреты. Поля ФИО родственников заполните своими данными.':
  'Схематические портреты. Указаны три поколения семьи.',
 }
}
for name,replacements in changes.items():
 p=B/name
 with ZipFile(p) as z:items=[(i,z.read(i.filename)) for i in z.infolist()]
 with ZipFile(p,'w',ZIP_DEFLATED) as z:
  for i,data in items:
   if i.filename=='word/document.xml':
    root=E.fromstring(data)
    for para in root.xpath('//w:p',namespaces=ns):
     nodes=para.xpath('./w:r/w:t',namespaces=ns)
     text=''.join(n.text or '' for n in nodes)
     if text in replacements:
      nodes[0].text=replacements[text]
      for n in nodes[1:]:n.text=''
    data=E.tostring(root,encoding='utf-8',xml_declaration=True,standalone=True)
   z.writestr(i,data)
