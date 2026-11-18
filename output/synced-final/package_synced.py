from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
import json,hashlib
B=Path('output/synced-final');F=B/'Связанные_работы';S=B/'input/ФИНАЛ'
original=[p.name for p in S.iterdir() if p.suffix in ['.docx','.xlsx'] and not p.name.startswith('~$')]
assert len(original)==9
files=[F/name for name in original]
files += [F/'Обновить.cmd',F/'Обновить_работы.ps1',F/'Как обновлять.txt']
files += sorted((F/'Связи').glob('*.png'))
assert len(files)==17
archive=B/'ФИНАЛ_связанные_работы.zip'
with ZipFile(archive,'w',ZIP_DEFLATED) as z:
 for p in files:
  assert p.is_file(),p
  if p.suffix in ['.docx','.xlsx']:
   with ZipFile(p) as inner:assert inner.testzip() is None,p
  z.write(p,Path('ФИНАЛ')/p.relative_to(F))
with ZipFile(archive) as z:assert z.testzip() is None
print(archive.resolve());print('Files:',len(files));print('Archive bytes:',archive.stat().st_size)
