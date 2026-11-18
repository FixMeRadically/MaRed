"""Only changed JAR bytes are retained. No world/config/server-folder snapshots.
Journal is HMAC-bound; writes use before/after CAS and can resume an interrupted apply/restore.
"""
from pathlib import Path
import hashlib,json,shutil,uuid,zipfile
from .common import safe,sha,atomic
from .bridge67 import read,write

class Changes104:
 def __init__(self,server,root,key):self.server=safe(server);self.root=safe(root);self.key=key
 def inventory(self):
  mods=safe(self.server/'mods');mods.mkdir(exist_ok=True);out={}
  for p in mods.iterdir():
   if p.suffix.lower()!='.jar':continue
   if not p.is_file() or not __import__('re').fullmatch('[A-Za-z0-9][A-Za-z0-9._+ -]{0,119}\\.jar',p.name):raise ValueError('SERVER104_MOD_FILENAME')
   if p.name.lower() in {k.lower() for k in out}:raise ValueError('SERVER104_MOD_CASE_COLLISION')
   out[p.name]=sha(p,64*1024*1024)
   if len(out)>256:raise ValueError('SERVER104_MOD_LIMIT')
  return out
 def stage(self,updates,remove=()):
  if (self.root/'change104').exists():raise ValueError('SERVER104_CHANGE_PENDING')
  before=self.inventory();after=dict(before);changes={}
  for name in remove:
   if name not in before:raise ValueError('SERVER104_MOD_NOT_OWNED')
   after.pop(name)
  for name,source in updates.items():
   if Path(name).name!=name or not name.endswith('.jar') or not __import__('re').fullmatch('[A-Za-z0-9][A-Za-z0-9._+ -]{0,119}\\.jar',name):raise ValueError('SERVER104_MOD_FILENAME')
   source=safe(source)
   with zipfile.ZipFile(source) as z:
    if not any(p in z.namelist() for p in ('META-INF/neoforge.mods.toml','META-INF/mods.toml')):raise ValueError('SERVER104_MOD_METADATA')
   after[name]=sha(source,64*1024*1024)
  if len(after)>256 or len({n.lower() for n in after})!=len(after):raise ValueError('SERVER104_MOD_LIMIT')
  names=sorted(n for n in set(before)|set(after) if before.get(n)!=after.get(n))
  if not names:return None
  folder=self.root/'changes'/str(uuid.uuid4());folder.mkdir(parents=True);(folder/'before').mkdir();(folder/'after').mkdir();total=0
  for name in names:
   old=before.get(name,'');new=after.get(name,'');changes[name]={'old':old,'new':new}
   for digest,source,dest in [(old,self.server/'mods'/name,folder/'before'/name),(new,updates.get(name),folder/'after'/name)]:
    if digest:
     total+=safe(source).stat().st_size
     if total>512*1024*1024:raise ValueError('SERVER104_CHANGE_LIMIT')
     shutil.copyfile(safe(source),dest)
     if sha(dest,64*1024*1024)!=digest:raise ValueError('SERVER104_CHANGE_MOVED')
  plan={'before':before,'after':after,'changes':changes};data=json.dumps(plan,sort_keys=True,separators=(',',':')).encode();atomic(folder/'plan.json',data,new=True);write(self.root/'change104',self.key,{'schema':'104','id':folder.name,'plan':hashlib.sha256(data).hexdigest(),'phase':'PREPARED'});return folder.name
 def load(self):
  if not (self.root/'change104').exists():return None
  d=read(self.root/'change104',self.key)
  if set(d)!={'schema','id','plan','phase'} or d['schema']!='104' or str(uuid.UUID(d['id']))!=d['id'] or d['phase'] not in {'PREPARED','APPLYING','TRIAL','RESTORING','COMMITTED','RESTORED'}:raise ValueError('SERVER104_CHANGE_RECORD')
  folder=safe(self.root/'changes'/d['id']);data=(folder/'plan.json').read_bytes()
  if len(data)>262144 or hashlib.sha256(data).hexdigest()!=d['plan']:raise ValueError('SERVER104_CHANGE_PLAN')
  plan=json.loads(data);return d,plan,folder
 def phase(self,d,value):d=dict(d,phase=value);write(self.root/'change104',self.key,d);return d
 def apply(self):return self.write_set(False)
 def restore(self):return self.write_set(True)
 def write_set(self,restore):
  loaded=self.load()
  if not loaded:return
  d,plan,folder=loaded;current=self.inventory();before=plan['before'];after=plan['after'];goal=before if restore else after
  if d['phase'] in {'COMMITTED','RESTORED'}:raise ValueError('SERVER104_CHANGE_TERMINAL')
  # Unknown edits are never overwritten, even if they affect a file outside this transaction.
  for name in set(current)|set(before)|set(after):
   value=current.get(name,'')
   if value not in {before.get(name,''),after.get(name,'')}:raise ValueError('SERVER104_FOREIGN_MOD_CHANGE')
   if name not in plan['changes'] and value!=before.get(name,''):raise ValueError('SERVER104_FOREIGN_MOD_CHANGE')
  d=self.phase(d,'RESTORING' if restore else 'APPLYING')
  for name,change in plan['changes'].items():
   dest=safe(self.server/'mods'/name);expected=goal.get(name,'');now=sha(dest,64*1024*1024) if dest.exists() else ''
   if now==expected:continue
   if now not in {change['old'],change['new']}:raise ValueError('SERVER104_FOREIGN_MOD_CHANGE')
   if expected:
    source=safe(folder/('before' if restore else 'after')/name)
    if sha(source,64*1024*1024)!=expected:raise ValueError('SERVER104_CHANGE_BYTES')
    atomic(dest,source.read_bytes())
   else:dest.unlink()
  if self.inventory()!=goal:raise ValueError('SERVER104_CHANGE_INCOMPLETE')
  self.phase(d,'RESTORED' if restore else 'TRIAL')
 def clear_terminal(self):
  loaded=self.load()
  if not loaded:return False
  d,plan,_=loaded
  if d['phase'] not in {'COMMITTED','RESTORED'}:return False
  if self.inventory()!=plan['after' if d['phase']=='COMMITTED' else 'before']:raise ValueError('SERVER104_CHANGE_CONFIRM')
  (self.root/'change104').unlink();return True
 def finish(self,success):
  value=self.load()
  if not value:return
  d,plan,folder=value
  expected='TRIAL' if success else 'RESTORED'
  if d['phase']!=expected or self.inventory()!=plan['after' if success else 'before']:raise ValueError('SERVER104_CHANGE_CONFIRM')
  self.phase(d,'COMMITTED' if success else 'RESTORED');atomic(folder/'result104',(b'COMMITTED\n' if success else b'RESTORED\n'),new=not (folder/'result104').exists());(self.root/'change104').unlink()
