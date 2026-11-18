"""Bounded read-only snapshot of a running CLIENT's files. Never copies local worlds."""
from pathlib import Path
import hashlib,os,shutil
from .common import safe,sha,inventory

EXCLUDED={'saves','logs','crash-reports','screenshots','backups','__pycache__','.genesis-dev62','first58-temp'}

def live_client_snapshot(source,target,cancel=lambda:False,excluded=()):
 source=safe(source);target=safe(target)
 if target.exists() or source==target or source in target.parents or target in source.parents:raise ValueError('NEW disjoint client snapshot required')
 before_mods=inventory(source)
 target.mkdir();count=total=nodes=0;observed={}
 for directory,dirs,names in os.walk(source,followlinks=False):
  if cancel():raise InterruptedError('Pilot cancelled')
  nodes+=len(dirs)+len(names)+1
  if nodes>400000:raise ValueError('Profile copy budget')
  directory=safe(directory);relative=directory.relative_to(source)
  if len(relative.parts)>32:raise ValueError('Profile depth limit')
  dirs[:]=[n for n in dirs if n not in EXCLUDED and (relative.parts or n not in excluded)]
  for n in dirs:safe(directory/n);(target/relative/n).mkdir()
  for n in names:
   if n in {'session.lock','first58.marker','controller.lock','client-session61.json'}:continue
   p=safe(directory/n)
   if not p.is_file():raise ValueError('Special profile file')
   count+=1;size=p.stat().st_size;total+=size
   if count>200000 or total>20*1024**3:raise ValueError('Profile copy budget')
   before=sha(p,20*1024**3);dest=target/relative/n
   with p.open('rb') as src,dest.open('xb') as out:shutil.copyfileobj(src,out,1024*1024)
   if sha(p,20*1024**3)!=before or sha(dest,20*1024**3)!=before:raise ValueError('Client files changed during snapshot')
   observed[str(relative/n)]=before
 # Verify important mutable runtime inputs once more before accepting the snapshot.
 for name,digest in observed.items():
  if cancel():raise InterruptedError('Pilot cancelled')
  if name.startswith(('mods'+os.sep,'config'+os.sep)) and sha(source/name,20*1024**3)!=digest:raise ValueError('Client files changed during snapshot')
 if inventory(source)!=before_mods or inventory(target)!=before_mods:raise ValueError('Client files changed during snapshot')
 return {'files':count,'bytes':total,'localWorldsCopied':False}
