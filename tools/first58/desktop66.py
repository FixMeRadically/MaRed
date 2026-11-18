"""Bounded read-only snapshot of a running CLIENT's files. Never copies local worlds."""
from pathlib import Path
import hashlib,os,shutil
from .common import safe,sha,inventory
from .preflight87 import EXCLUDED,walk,check

class _Reader87:
 def __init__(self,stream,cancel,limit):self.stream=stream;self.cancel=cancel;self.name=stream.name;self.left=limit;self.digest=hashlib.sha256()
 def read(self,size=-1):
  check(self.cancel);data=self.stream.read(size)
  if len(data)>self.left:raise ValueError('Client files changed during snapshot')
  self.left-=len(data);self.digest.update(data);return data

def _stamp(st):
 return st.st_dev,st.st_ino,st.st_size,st.st_mtime_ns,st.st_ctime_ns

def live_client_snapshot(source,target,cancel=lambda:False,excluded=(),*,closed=False,charge=None):
 skip_mods='mods' in excluded
 source=safe(source);target=safe(target)
 if target.exists() or source==target or source in target.parents or target in source.parents:raise ValueError('NEW disjoint client snapshot required')
 before_mods=None if skip_mods else inventory(source)
 target.mkdir();count=total=0;observed={}
 for relative,p,st in walk(source,cancel,excluded):
  if charge is not None:charge(st.st_size if st is not None else 0)
  dest=target/relative
  if p is None:
   if relative.parts:dest.mkdir()
   continue
  count+=1;total+=st.st_size
  with p.open('rb') as src,dest.open('xb') as out:
   handle_stat=os.fstat(src.fileno()) if closed else None
   opened=_stamp(handle_stat) if closed else None
   if closed and handle_stat.st_size!=st.st_size:raise ValueError('Client files changed during snapshot')
   reader=_Reader87(src,cancel,st.st_size)
   shutil.copyfileobj(reader,out,1024*1024)
   if closed and _stamp(os.fstat(src.fileno()))!=opened:raise ValueError('Client files changed during snapshot')
  if reader.left:raise ValueError('Client files changed during snapshot')
  before=reader.digest.hexdigest()
  name=str(relative)
  if name.startswith(('mods'+os.sep,'config'+os.sep)):
   observed[name]=before # These files are rechecked once at the end of the copy.
  elif closed:
   if _stamp(p.stat())!=_stamp(st):raise ValueError('Client files changed during snapshot')
  elif sha(p,20*1024**3)!=before:raise ValueError('Client files changed during snapshot')
  if sha(dest,20*1024**3)!=before:raise ValueError('Client files changed during snapshot')
 for name,digest in observed.items():
  check(cancel)
  if sha(source/name,20*1024**3)!=digest:raise ValueError('Client files changed during snapshot')
 if not skip_mods and (inventory(source)!=before_mods or inventory(target)!=before_mods):raise ValueError('Client files changed during snapshot')
 return {'files':count,'bytes':total,'localWorldsCopied':False}
