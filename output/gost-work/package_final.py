from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from docx import Document
import hashlib,json
B=Path('output/gost-work'); O=B/'готовые_работы'
checks=[]
for src in (B/'source').glob('*.docx'):
 out=O/src.name
 a=ZipFile(src);b=ZipFile(out)
 original=[n for n in a.namelist() if n.startswith('word/media/') or n.startswith('word/embeddings/')]
 # Some Word saves rename media; verify binary payloads rather than part names.
 hashes={hashlib.sha256(b.read(n)).hexdigest() for n in b.namelist()}
 missing=[n for n in original if hashlib.sha256(a.read(n)).hexdigest() not in hashes]
 checks.append({'file':src.name,'source_media_count':len(original),'changed_or_reencoded_media':missing})
 d=Document(out)
 if src.name in ['01_Отчёт.docx','02_Отчёт.docx','03_Реферат.docx','04_Отчёт.docx','05_Отчёт.docx']:
  text='\n'.join(p.text for p in d.paragraphs)
  assert 'Тронин Кирилл Валерьевич' in text and 'Черных Татьяна Александровна' in text and 'ПЕ-62б' in text
  assert 'TOC ' in d._element.xml
for p in (B/'source').glob('*.xlsx'): assert p.read_bytes()==(O/p.name).read_bytes()
(B/'qa/final-checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf8')
zip_path=B/'Практические_работы_1-5_оформление.zip'
with ZipFile(zip_path,'w',ZIP_DEFLATED) as z:
 for p in sorted(O.iterdir()):
  if p.suffix in ['.docx','.pdf','.xlsx'] and not p.name.startswith('~$'):z.write(p,p.name)
with ZipFile(zip_path) as z:assert z.testzip() is None;print(len(z.namelist()),'files')
print(zip_path.resolve());print(json.dumps(checks,ensure_ascii=False))
