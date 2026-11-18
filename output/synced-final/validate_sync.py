from pathlib import Path
from zipfile import ZipFile
from docx import Document
from lxml import etree as E
import hashlib,json
B=Path('output/synced-final');F=B/'Связанные_работы';T=B/'test-relocated';S=B/'input/ФИНАЛ'
assert 'ПРОВЕРКА ТАБЛИЦЫ' in '\n'.join(c.text for t in Document(T/'01_Отчёт.docx').tables for r in t.rows for c in r.cells)
text='\n'.join(p.text for p in Document(T/'04_Отчёт.docx').paragraphs)
assert 'средний балл - 4,14' in text,text
assert hashlib.sha256((T/'Связи/02_Древо.png').read_bytes()).hexdigest()!=hashlib.sha256((F/'Связи/02_Древо.png').read_bytes()).hexdigest()
assert hashlib.sha256((T/'Связи/04_Оценки.png').read_bytes()).hexdigest()!=hashlib.sha256((F/'Связи/04_Оценки.png').read_bytes()).hexdigest()
for name in ['01_План_квартиры.docx','03_Реферат.docx','Копия 04_Оценки.xlsx','Копия 05_Справочник.xlsx']:
 assert (F/name).read_bytes()==(S/name).read_bytes(),name
# All entered family names must be retained, including source spelling.
names=['Тронин Георгий Владимирович','Тронина Ирина Генадьевна','Пупкой Александр Сергеевич','Пупкова Мария Григорьевна','Тронин Валерий Георгиевич','Тронина Алёна Александровна']
d=Document(F/'02_Семейное_дерево.docx');tree=' '.join(e.text or '' for e in d._element.xpath('.//w:t'))
for name in names:assert name in tree,name
assert 'не предоставлены' not in '\n'.join(p.text for p in Document(F/'02_Отчёт.docx').paragraphs)
links=[]
for f in F.glob('*.docx'):
 if f.name.startswith('~$'):continue
 with ZipFile(f) as z:
  rels=E.fromstring(z.read('word/_rels/document.xml.rels'))
  for e in rels:
   if e.get('TargetMode')=='External' and e.get('Type','').endswith('/image'):
    target=e.get('Target');assert target.startswith('Связи/'),target
    assert (F/target).is_file(),target
    links.append((f.name,target))
assert len(links)==5,links
(B/'qa-linked/validation.json').write_text(json.dumps({'linked_pictures':links,'relocation_test':'passed','changed_tree_cache':'passed','changed_specification':'passed','changed_grades_and_mean':'passed','original_sources_preserved':'passed'},ensure_ascii=False,indent=2),encoding='utf8')
print('Validated 5 relative picture links, specification sync, moved-folder refresh, changed tree and recalculated grades.')
