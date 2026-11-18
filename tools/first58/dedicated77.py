"""Explicit one-branch recovery from an authenticated, cleanly stopped generation.
No PID discovery, owner replay, world rollback, old cohort reuse or live-file install.
"""
from pathlib import Path
import contextlib,hashlib,json,os,re,time,uuid
from . import dedicated74 as base,dedicated76 as previous
from .dedicated75 import History75
from .bridge67 import read,write
from .common import safe,sha,inventory,json_write
from .install70 import policy,files

CHECKPOINT_FIELDS={'schema','run','generation','inventory','profile','session','receipt','command','release','label','pair','at'}

def failure(error):
 code=error.args[0] if len(error.args)==1 else ''
 return code if isinstance(code,str) and re.fullmatch('[A-Z0-9_]{1,100}',code) else 'DEDICATED77_INVALID_SOURCE'

@contextlib.contextmanager
def owner_lease(root):
 # Same byte and same file as the existing Windows owner, held throughout validation/copy.
 path=safe(root/'controller.lock')
 with path.open('a+b') as f:
  if path.stat().st_size==0:f.write(b'0');f.flush()
  f.seek(0)
  try:
   if os.name=='nt':
    import msvcrt
    msvcrt.locking(f.fileno(),msvcrt.LK_NBLCK,1)
   else:
    import fcntl
    fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB,1)
  except OSError:raise ValueError('DEDICATED77_OWNER_ACTIVE') from None
  try:yield
  finally:
   f.seek(0)
   if os.name=='nt':msvcrt.locking(f.fileno(),msvcrt.LK_UNLCK,1)
   else:fcntl.lockf(f,fcntl.LOCK_UN,1)

def generations(root):
 folder=safe(root/'generations');items=list(folder.iterdir())
 if len(items)>33:raise ValueError('DEDICATED77_GENERATION_LIMIT')
 out=[]
 for p in items:
  safe(p)
  if not p.is_dir() or not re.fullmatch(r'server-(?:0|[1-9][0-9]?)',p.name):raise ValueError('DEDICATED77_GENERATION_PATH')
  n=int(p.name[7:])
  if n>32:raise ValueError('DEDICATED77_GENERATION_LIMIT')
  out.append(n)
 if not out:raise ValueError('DEDICATED77_NO_GENERATIONS')
 return sorted(out)

def receipt(root,s,key,g):
 c=read(root/f'server-{g}.session',key);d=read(root/f'server-{g}.status',key);cmd=read(root/f'server-{g}.command',key)
 if c!=dict(schema='74',run=s['run'],generation=str(g),inventory=c.get('inventory'),minecraft=s['minecraft'],neoforge=s['neoforge'],auth=s['auth'],bind=s['bind'],port=s['port']):raise ValueError('DEDICATED77_SESSION')
 if set(d)!={'schema','run','generation','inventory','sequence','request','phase','round','at'} or set(cmd)!={'schema','run','generation','inventory','sequence','request','action','round','label'}:raise ValueError('DEDICATED77_RECEIPT')
 if d['phase']!='STOPPED_SAVED' or cmd['action'] not in {'SHUTDOWN','SAVE_STOP'} or any(d[k]!=c[k] for k in ('schema','run','generation','inventory')) or any(cmd[k]!=d[k] for k in ('schema','run','generation','inventory','sequence','request','round')):raise ValueError('DEDICATED77_RECEIPT')
 if not re.fullmatch(r'[a-f0-9]{64}',c['inventory']) or not re.fullmatch(r'[1-9][0-9]{0,17}',d['sequence']) or str(uuid.UUID(d['request']))!=d['request']:raise ValueError('DEDICATED77_RECEIPT')
 return c,d,cmd

def checkpoint(root,s,key,g,release='none',label='none'):
 c,d,cmd=receipt(root,s,key,g);profile=safe(root/f'generations/server-{g}')
 if inventory(profile)!=c['inventory']:raise ValueError('DEDICATED77_INVENTORY_CHANGED')
 if (release=='none')!=(label=='none'):raise ValueError('DEDICATED77_RELEASE_BINDING')
 pair='none'
 if release!='none':
  base.label(label);uuid.UUID(release);pair=base.digest(base.tree(root/'releases'/label,'unused'))
 value=dict(schema='77',run=s['run'],generation=str(g),inventory=c['inventory'],profile=base.digest(base.tree(profile,s['level'])),session=sha(root/f'server-{g}.session'),receipt=sha(root/f'server-{g}.status'),command=sha(root/f'server-{g}.command'),release=release,label=label,pair=pair,at=str(int(time.time()*1000)))
 write(root/f'saved77-{g}',key,value);return value

def proof(root,s,key,g):
 path=root/f'saved77-{g}'
 if not path.is_file():raise ValueError('DEDICATED77_NO_CLEAN_CHECKPOINT')
 p=read(path,key)
 if set(p)!=CHECKPOINT_FIELDS or p['schema']!='77' or p['run']!=s['run'] or p['generation']!=str(g):raise ValueError('DEDICATED77_CHECKPOINT_BINDING')
 c,_,_=receipt(root,s,key,g)
 for field,name in [('session','session'),('receipt','status'),('command','command')]:
  if p[field]!=sha(root/f'server-{g}.{name}'):raise ValueError('DEDICATED77_RECEIPT_CHANGED')
 if p['inventory']!=c['inventory'] or inventory(root/f'generations/server-{g}')!=p['inventory']:raise ValueError('DEDICATED77_INVENTORY_CHANGED')
 if (p['release']=='none')!=(p['label']=='none'):raise ValueError('DEDICATED77_RELEASE_BINDING')
 for k in ('inventory','profile','session','receipt','command'):
  if not re.fullmatch('[a-f0-9]{64}',p[k]):raise ValueError('DEDICATED77_CHECKPOINT_BINDING')
 if not re.fullmatch('[1-9][0-9]{0,17}',p['at']):raise ValueError('DEDICATED77_CHECKPOINT_BINDING')
 if p['release']!='none':
  base.label(p['label']);uuid.UUID(p['release'])
  if not re.fullmatch('[a-f0-9]{64}',p['pair']):raise ValueError('DEDICATED77_RELEASE_BINDING')
 return p

def selection(root,s,key,g):
 if (root/f'saved77-{g}').exists():return proof(root,s,key,g),'SEALED77'
 # Legacy76 may be imported only after its WHOLE owner reports normal shutdown.
 # This is a snapshot of stopped files NOW, not a retroactive version77 checkpoint.
 if not (root/'owner76').is_file() or not (root/'view75').is_file():raise ValueError('DEDICATED77_NO_CLEAN_CHECKPOINT')
 if read(root/'owner76',key)!=dict(schema='76',run=s['run']):raise ValueError('DEDICATED77_NO_CLEAN_CHECKPOINT')
 view=read(root/'view75',key)
 if view.get('schema')!='75' or view.get('run')!=s['run'] or view.get('phase')!='STOPPED_SAVED' or view.get('generation')!=str(g) or g!=max(generations(root)):raise ValueError('DEDICATED77_NO_CLEAN_CHECKPOINT')
 c,d,cmd=receipt(root,s,key,g);profile=root/f'generations/server-{g}'
 if inventory(profile)!=c['inventory']:raise ValueError('DEDICATED77_INVENTORY_CHANGED')
 release=label='none';history=History75(root,key,s['run'])
 if g>0:
  rows=[x for x in history.rows if x['phase']=='SERVER_READY' and x['target']==str(g)]
  if not rows:raise ValueError('DEDICATED77_NO_CLEAN_CHECKPOINT')
  release=rows[-1]['release'];labels=[]
  folders=list((root/'releases').iterdir())
  if len(folders)>128:raise ValueError('DEDICATED77_RELEASE_LIMIT')
  # Match an authenticated release UUID via the signed pair. Signature/inventory checks run again before copying.
  from .bootstrap_wire76 import decode
  for folder in folders:
   try:
    base.label(folder.name);file=safe(folder/'pair74')
    if file.stat().st_size>16384:continue
    raw=file.read_bytes()
    body,sep,sig=raw.rpartition(b'sig=')
    if sep and decode(body).get('release')==release:labels.append(folder.name)
   except Exception:continue
  if len(labels)!=1:raise ValueError('DEDICATED77_RELEASE_BINDING')
  label=labels[0]
 with base.stopped_world(profile,s['level']):seal=base.digest(base.tree(profile,s['level']))
 p=dict(schema='77',run=s['run'],generation=str(g),inventory=c['inventory'],profile=seal,session=sha(root/f'server-{g}.session'),receipt=sha(root/f'server-{g}.status'),command=sha(root/f'server-{g}.command'),release=release,label=label,pair=base.digest(base.tree(root/'releases'/label,'unused')) if label!='none' else 'none',at=d['at'])
 return p,'LEGACY76'

def ticket(root,s,key,g,all_generations,p):
 h=History75(root,key,s['run']);raw=dict(run=s['run'],generation=g,generations=all_generations,checkpoint=p,settings=sha(root/'settings.json'),history=h.head)
 return hashlib.sha256(json.dumps(raw,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def _catalog(root,s,key):
 nums=generations(root);rows=[];forked=(root/'recovered77').exists()
 if forked:
  bound=read(root/'recovered77',key)
  if bound.get('run')!=s['run'] or bound.get('schema')!='77':raise ValueError('DEDICATED77_FORK_RECORD')
 for g in reversed(nums):
  row=dict(generation=g,latest=g==max(nums),eligible=False,reason='DEDICATED77_ALREADY_RECOVERED' if forked else '',ticket='',release='none',savedAt='',kind='NONE')
  try:
   p,kind=selection(root,s,key,g);row.update(release=p['release'],savedAt=p['at'],kind=kind)
   if not forked:row.update(eligible=True,reason='CLEAN_CHECKPOINT' if kind=='SEALED77' else 'LEGACY_STOPPED_IMPORT',ticket=ticket(root,s,key,g,nums,p))
  except Exception as bad:
   if not forked:row['reason']=failure(bad)
  rows.append(row)
 return dict(schema=77,root=str(root),run=s['run'],latest=max(nums),world=s['level'],auth=s['auth'],port=s['port'],rows=rows,successor=bound.get('targetRoot','') if forked else '')

def catalog(root):
 root=safe(root);s,key=base.settings(root)
 with owner_lease(root):return _catalog(root,s,key)

@contextlib.contextmanager
def publication_lease79(root):
 # Conflicts with Java FileChannel publisher.lock, so recovery cannot copy a stale counter.
 folder=safe(root/'publisher73')
 with safe(folder/'publisher.lock').open('a+b') as f:
  f.seek(0)
  try:
   if os.name=='nt':
    import msvcrt
    msvcrt.locking(f.fileno(),msvcrt.LK_NBLCK,1)
   else:
    import fcntl
    fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB,1)
  except OSError:raise ValueError('PUBLICATION79_PUBLISHER_BUSY') from None
  try:
   if os.fstat(f.fileno()).st_size==0:f.write(b'0');f.flush()
   yield
  finally:
   f.seek(0)
   if os.name=='nt':msvcrt.locking(f.fileno(),msvcrt.LK_UNLCK,1)
   else:fcntl.lockf(f,fcntl.LOCK_UN,1)

def _recover(project,root,g,expected,target,java,allow_older=False):
 root=safe(root);target=safe(target);project=safe(project)
 if root==target or root in target.parents or target in root.parents or target.exists():raise ValueError('DEDICATED77_NEW_DISJOINT_ROOT')
 if not re.fullmatch('[a-f0-9]{64}',expected):raise ValueError('DEDICATED77_TICKET')
 with owner_lease(root),publication_lease79(root):
  s,key=base.settings(root);view=_catalog(root,s,key);row=next((r for r in view['rows'] if r['generation']==g),None)
  if not row or not row['eligible']:raise ValueError(row['reason'] if row else 'DEDICATED77_GENERATION')
  if row['ticket']!=expected:raise ValueError('DEDICATED77_SELECTION_CHANGED')
  if g!=view['latest'] and not allow_older:raise ValueError('DEDICATED77_OLDER_WORLD_REQUIRES_CHOICE')
  p,kind=selection(root,s,key,g);source=root/f'generations/server-{g}';info=None
  with base.stopped_world(source,s['level']):
   if base.digest(base.tree(source,s['level']))!=p['profile']:raise ValueError('DEDICATED77_PROFILE_CHANGED')
   if p['release']!='none':
    folder=root/'releases'/p['label']
    if base.digest(base.tree(folder,'unused'))!=p['pair']:raise ValueError('DEDICATED77_PAIR_CHANGED')
    info=base.core(project,java,'verify-history',folder,root/'publisher73/public.der')
    if policy(source/'mods',folder/'server.zip',info)['after']!=files(source/'mods'):raise ValueError('DEDICATED77_RELEASE_INVENTORY')
    if info['release']!=p['release'] or info['minecraft']!=s['minecraft'] or info['neoforge']!=s['neoforge']:raise ValueError('DEDICATED77_RELEASE_BINDING')
   initialize(project,source,target,java,publisher=root/'publisher73',recovering=True,source_locked=True)
   ns,nk=base.settings(target);profile=target/'generations/server-0'
   if base.digest(base.tree(source,s['level']))!=p['profile'] or base.digest(base.tree(profile,s['level']))!=p['profile']:raise ValueError('DEDICATED77_COPY_CHANGED')
   if g:
    profile.rename(target/f'generations/server-{g}');(target/'server-0.session').unlink()
   ns['initialGeneration']=g;json_write(target/'settings.json',ns);write(target/'settings74',nk,{'schema':'74','sha':sha(target/'settings.json',65536)});base.session(target,ns,nk,g,ns['initialInventory'])
   if info:
    base.copy_tree(folder,target/'releases'/p['label'],'unused')
    if base.core(project,java,'verify-history',target/'releases'/p['label'],target/'publisher73/public.der')!=info:raise ValueError('DEDICATED77_COPY_CHANGED')
    write(target/'publication75',nk,dict(schema='75',run=ns['run'],label=p['label'],release=p['release']))
   marker=dict(schema='77',run=ns['run'],sourceRun=s['run'],generation=str(g),release=p['release'],label=p['label'],ticket=expected,inventory=ns['initialInventory'],profile=p['profile'],kind=kind)
   write(target/'recovery77',nk,marker)
   from .https78 import inherit
   inherit(root,target)
   from .publication85 import inherit as inherit85
   inherit85(root,target)
   History75(target,nk,ns['run']).append('RECOVERED_COPY',g,g,p['release'],inventory_hash=ns['initialInventory'])
   # One explicit branch per old root. A partial new root is never runnable without this receipt.
   write(root/'recovered77',key,dict(schema='77',run=s['run'],targetRun=ns['run'],targetRoot=str(target),generation=str(g),ticket=expected))
   write(target/'recovery77.ready',nk,dict(schema='77',run=ns['run'],ticket=expected))
   json_write(target/'recovery77-report.json',dict(schema=77,phase='READY_TO_START',generation=g,root=str(target),sourceProfileModified=False,oldCohortCopied=False))
   (target/'import77.pending').unlink()
   return dict(schema=77,root=str(target),generation=g,release=p['release'],sourceModified=False,oldCohortCopied=False)

def recover(project,root,g,expected,target,java,allow_older=False):
 target=safe(target);existed=target.exists()
 try:return _recover(project,root,g,expected,target,java,allow_older)
 except Exception as error:
  if not existed and target.is_dir():
   try:json_write(target/'recovery77-report.json',dict(schema=77,phase='INCOMPLETE',code=failure(error),root=str(target),automaticStart=False,sourceProfileModified=False))
   except Exception:pass
  raise

def initialize(project,source,root,java,publisher=None,recovering=False,source_locked=False):
 root=previous.initialize(project,source,root,java,publisher=publisher,recovering=recovering,source_locked=source_locked);s,key=base.settings(root)
 write(root/'owner77',key,dict(schema='77',run=s['run']));return root

class Owner77(previous.Owner76):
 def __init__(self,*args,**kwargs):
  super().__init__(*args,**kwargs)
  if (self.root/'import77.pending').exists():raise ValueError('DEDICATED77_INCOMPLETE_RECOVERY')
  if read(self.root/'owner77',self.key)!=dict(schema='77',run=self.s['run']):raise ValueError('DEDICATED77_NEW_OWNER_REQUIRED')
  if (self.root/'recovery77').exists():
   p=read(self.root/'recovery77',self.key)
   if p.get('run')!=self.s['run'] or p.get('schema')!='77' or p.get('inventory')!=self.s['initialInventory'] or p.get('generation')!=str(self.s.get('initialGeneration',0)) or read(self.root/'recovery77.ready',self.key)!=dict(schema='77',run=self.s['run'],ticket=p['ticket']):raise ValueError('DEDICATED77_INCOMPLETE_RECOVERY')
   if base.digest(base.tree(self.root/f"generations/server-{p['generation']}",self.s['level']))!=p['profile']:raise ValueError('DEDICATED77_PROFILE_CHANGED')
   if p['release']!='none':
    self.previous_info=self.tool('verify-history',self.root/'releases'/base.label(p['label']),self.root/'publisher73/public.der')
    if self.previous_info['release']!=p['release']:raise ValueError('DEDICATED77_RELEASE_BINDING')
    self.active_label76=p['label']
 def run(self):
  if (self.root/'started74').exists():raise ValueError('DEDICATED77_USE_RECOVERY')
  return super().run()
 def confirmed_stop(self,g):
  job=self.jobs[g];status=job.status()
  if not status['rootExited'] or status['exitCode']!=0 or not job.empty():raise ValueError('DEDICATED77_EMPTY_JOB_REQUIRED')
  release=self.previous_info['release'] if self.previous_info else 'none';label=self.active_label76 or 'none'
  if self.previous_info:
   folder=self.root/'releases'/base.label(label);info=self.tool('verify-history',folder,self.root/'publisher73/public.der')
   if info!=self.previous_info or policy(self.root/f'generations/server-{g}/mods',folder/'server.zip',info)['after']!=files(self.root/f'generations/server-{g}/mods'):raise ValueError('DEDICATED77_RELEASE_INVENTORY')
  # This hook is invoked while the empty Job handle is still retained.
  with base.stopped_world(self.root/f'generations/server-{g}',self.s['level']):checkpoint(self.root,self.s,self.key,g,release,label)
