from pathlib import Path
from zipfile import ZipFile
p=Path(r'C:\Users\fixme\Downloads\ФИНАЛ.zip')
with ZipFile(p) as z:
 for i in z.infolist(): print(repr(i.filename),i.file_size)
