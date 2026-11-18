"""Only changed JAR bytes are retained. No world/config/server-folder snapshots.
Journal is HMAC-bound; writes use before/after CAS and can resume an interrupted apply/restore.
"""
from pathlib import Path
import hashlib,json,shutil,uuid,zipfile
from .common import safe,sha,atomic,valid_mod_name
from .bridge67 import read,write

class Changes104:
 def __init__(self,server,root,key):self.server=safe(server);self.root=safe(root);self.key=key
 def inventory(self):
  mods=safe(self.server/'mods');mods.mkdir(exist_ok=True);out={}
  for p in mods.iterdir():
   if p.suffix.lower()!='.jar':continue
   if not p.is_file() or p.stat().st_size==0 or not valid_mod_name(p.name):raise ValueError('SERVER104_MOD_FILENAME')
   if p.name.lower() in {k.lower() for k in out}:raise ValueError('SERVER104_MOD_CASE_COLLISION')
   out[p.name]=sha(p,64*1024*1024)
   if len(out)>256:raise ValueError('SERVER104_MOD_LIMIT')
  return out
 def review(self,updates,remove=()):
  if (self.root/'change104').exists():raise ValueError('SERVER104_CHANGE_PENDING')
  before=self.inventory();after=dict(before);changes={}
  for name in remove:
   if name not in before:raise ValueError('SERVER104_MOD_NOT_OWNED')
   after.pop(name)
  for name,source in updates.items():
   if not valid_mod_name(name):raise ValueError('SERVER104_MOD_FILENAME')
   source=safe(source)
   with zipfile.ZipFile(source) as z:
    if not any(p in z.namelist() for p in ('META-INF/neoforge.mods.toml','META-INF/mods.toml')):raise ValueError('SERVER104_MOD_METADATA')
   after[name]=sha(source,64*1024*1024)
  if len(after)>256:raise ValueError('SERVER104_MOD_LIMIT')
  # A case-only rename cannot be applied and rolled back as two independent files on Windows.
  names_by_case={}
  for name in (*before,*after):
   if names_by_case.setdefault(name.lower(),name)!=name:raise ValueError('SERVER104_MOD_CASE_COLLISION')
  names=sorted(n for n in set(before)|set(after) if before.get(n)!=after.get(n))
  if not names:return None
  return dict(profile=str(self.server),before=before,after=after,changes={n:dict(old=before.get(n,''),new=after.get(n,'')) for n in names})
 def approve(self,review):
  if review is None:return None
  from .approval120 import issue
  token=str(uuid.uuid4());digest=hashlib.sha256(json.dumps(review,sort_keys=True,separators=(',',':')).encode()).hexdigest();issue(self.root,'FOLDER',token,digest);return dict(transaction=token,artifact=digest)
 def stage(self,updates,remove=(),approval=None):
  reviewed=self.review(updates,remove)
  if reviewed is None:return None
  digest=hashlib.sha256(json.dumps(reviewed,sort_keys=True,separators=(',',':')).encode()).hexdigest()
  if not approval or set(approval)!={'transaction','artifact'} or approval['artifact']!=digest:raise ValueError('APPROVAL120_EXPLICIT_REVIEW_REQUIRED')
  from .approval120 import verify
  verify(self.root,'FOLDER',approval['transaction'],digest)
  before=reviewed['before'];after=reviewed['after'];changes={};names=sorted(reviewed['changes'])
  folder=self.root/'changes'/approval['transaction']
  folder.mkdir(parents=True)
  try:
   (folder/'before').mkdir();(folder/'after').mkdir();total=0
   for name in names:
    old=before.get(name,'');new=after.get(name,'');changes[name]={'old':old,'new':new}
    for digest,source,dest in [(old,self.server/'mods'/name,folder/'before'/name),(new,updates.get(name),folder/'after'/name)]:
     if digest:
      total+=safe(source).stat().st_size
      if total>512*1024*1024:raise ValueError('SERVER104_CHANGE_LIMIT')
      shutil.copyfile(safe(source),dest)
      if sha(dest,64*1024*1024)!=digest:raise ValueError('SERVER104_CHANGE_MOVED')
   plan={'profile':str(self.server),'before':before,'after':after,'changes':changes}
   if plan!=reviewed:raise ValueError('APPROVAL120_REVIEW_CHANGED')
   data=json.dumps(plan,sort_keys=True,separators=(',',':')).encode();atomic(folder/'plan.json',data,new=True)
  except BaseException as error:
   try:shutil.rmtree(folder)
   except OSError as cleanup:error.add_note('Could not remove unpublished change staging: '+type(cleanup).__name__)
   raise
  write(self.root/'change104',self.key,{'schema':'104','id':folder.name,'plan':hashlib.sha256(data).hexdigest(),'phase':'PREPARED'});return folder.name
 def load(self):
  if not (self.root/'change104').exists():return None
  d=read(self.root/'change104',self.key)
  if set(d)!={'schema','id','plan','phase'} or d['schema']!='104' or str(uuid.UUID(d['id']))!=d['id'] or d['phase'] not in {'PREPARED','APPLYING','TRIAL','RESTORING','COMMITTED','RESTORED'}:raise ValueError('SERVER104_CHANGE_RECORD')
  folder=safe(self.root/'changes'/d['id'])
  with safe(folder/'plan.json').open('rb') as source:data=source.read(262145)
  if len(data)>262144 or hashlib.sha256(data).hexdigest()!=d['plan']:raise ValueError('SERVER104_CHANGE_PLAN')
  plan=json.loads(data);return d,plan,folder
 def phase(self,d,value):d=dict(d,phase=value);write(self.root/'change104',self.key,d);return d
 def apply(self):return self.write_set(False)
 def restore(self):return self.write_set(True)
 def write_set(self,restore):
  loaded=self.load()
  if not loaded:return
  d,plan,folder=loaded
  if not restore:
   from .approval120 import verify
   verify(self.root,'FOLDER',d['id'],d['plan'])
  current=self.inventory();before=plan['before'];after=plan['after'];goal=before if restore else after
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
    with source.open('rb') as stream:data=stream.read(64*1024*1024+1)
    if len(data)>64*1024*1024 or hashlib.sha256(data).hexdigest()!=expected:raise ValueError('SERVER104_CHANGE_BYTES')
    atomic(dest,data)
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
