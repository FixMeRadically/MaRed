"""Run real signing/verification using a supplied controller core (no game process)."""
from pathlib import Path
import argparse
import json
import subprocess
import sys
import tempfile
import zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.setup import java

def main():
 p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);a=p.parse_args()
 with tempfile.TemporaryDirectory() as folder:
  root=Path(folder);sources=[]
  for i in range(4):
   mods=root/('mods-'+str(i));mods.mkdir()
   with zipfile.ZipFile(mods/'sample.jar','w') as z:z.writestr('sample.txt',str(i))
   sources.append(mods)
  made=java(a.project,a.java,'author',root/'packages',*sources,'1.21.1','21.1.264')
  for role in ('server','client'):
   for gen in (0,1):
    archive=root/'packages'/f'{role}-{gen}.zip'
    verified=java(a.project,a.java,'verify',archive,root/'packages/public.der',made['distribution'],role.upper(),'1.21.1','21.1.264','1')
    assert verified==made[f'{role}-{gen}']
  bad=root/'tampered.zip'
  with zipfile.ZipFile(root/'packages/server-1.zip') as src,zipfile.ZipFile(bad,'w') as dest:
   for entry in src.infolist():
    data=src.read(entry.filename)
    if entry.filename.startswith('files/'):data+=b'tampered'
    dest.writestr(entry,data)
  try:java(a.project,a.java,'verify',bad,root/'packages/public.der',made['distribution'],'SERVER','1.21.1','21.1.264','1')
  except ValueError:pass
  else:raise AssertionError('Tampered archive accepted')
  print('Portable real-core signing/verification: 4 valid archives and tamper rejection passed')

if __name__=='__main__':main()
