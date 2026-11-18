from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from docx import Document
from pypdf import PdfReader
import json,re
B=Path('output/gost-work');O=B/'исправленные_работы'
counts={}
for f in O.glob('*.docx'):
 d=Document(f)
 if f.name in ['01_Отчёт.docx','02_Отчёт.docx','03_Реферат.docx','04_Отчёт.docx','05_Отчёт.docx']:
  text='\n'.join(p.text for p in d.paragraphs)
  assert 'Проверила: доцент кафедры ИСТ\nЧерных Татьяна Александровна' in text
  assert 'Тронин Кирилл Валерьевич' in text and 'ПЕ-62б' in text
  original=Document(B/'source'/f.name)
  topic=original.paragraphs[8].text
  assert topic in text
  # Every original prose paragraph remains verbatim, excluding cover, TOC,
  # numbered headings, figure captions and formatted bibliographical references.
  for p in original.paragraphs[15:]:
   s=p.text.strip()
   if not s or p.style.name.startswith('Heading') or s=='Содержание' or re.match(r'^Рисунок|.*\.{3,}',s):continue
   assert (s.rstrip('.') if s.startswith('Источник:') else s) in text,(f.name,s)
  counts[f.name]=len(PdfReader(O/(f.stem+'.pdf')).pages)
for f in O.glob('*.xlsx'):assert f.read_bytes()==(B/'source'/f.name).read_bytes()
archive=B/'Практические_работы_1-5_исправленные.zip'
with ZipFile(archive,'w',ZIP_DEFLATED) as z:
 for p in sorted(O.iterdir()):
  if p.suffix in ['.docx','.pdf','.xlsx'] and not p.name.startswith('~$'):z.write(p,p.name)
with ZipFile(archive) as z:assert z.testzip() is None;assert len(z.namelist())==18
(B/'qa-compact/final-checks.json').write_text(json.dumps(counts,ensure_ascii=False,indent=2),encoding='utf8')
print(archive.resolve());print(json.dumps(counts,ensure_ascii=False))
