"""Authenticated metadata transaction and local renewal configuration. Never owns a game or writes world/mods."""
from pathlib import Path
import base64,json,re,time,uuid
from . import dedicated74 as base
from .bridge67 import read,write,sign,verify
from .common import safe,atomic
TERMINAL={'COMMITTED','ABORTED'}
POINTERS=('baseline83','publication75')

def config(root):
 root=safe(root);s,key=base.settings(root);p=root/'renew85.cfg'
 d=read(p,key) if p.exists() else dict(schema='85',run=s['run'],revision='default',enabled='true',ttl='86400',margin='900')
 if set(d)!={'schema','run','revision','enabled','ttl','margin'} or d['schema']!='85' or d['run']!=s['run'] or d['enabled'] not in {'true','false'} or d['revision']!='default' and str(uuid.UUID(d['revision']))!=d['revision']:raise ValueError('RENEWAL85_CONFIG')
 if not d['ttl'].isdigit() or not 3600<=int(d['ttl'])<=604800 or not d['margin'].isdigit() or not 60<=int(d['margin'])<=int(d['ttl'])//2:raise ValueError('RENEWAL85_CONFIG')
 return d

def save(root,enabled,ttl,margin):
 root=safe(root);s,key=base.settings(root)
 if not isinstance(enabled,bool) or not isinstance(ttl,int) or not 3600<=ttl<=604800 or not isinstance(margin,int) or not 60<=margin<=ttl//2:raise ValueError('RENEWAL85_CONFIG')
 d=dict(schema='85',run=s['run'],revision=str(uuid.uuid4()),enabled=str(enabled).lower(),ttl=str(ttl),margin=str(margin));write(root/'renew85.cfg',key,d);return d

def inherit(old,new):
 d=config(old);s,key=base.settings(new);d=dict(d,run=s['run'],revision=str(uuid.uuid4()));p=safe(new/'renew85.cfg')
 if p.exists():config(new);return
 write(p,key,d) # Options only. Never copy a pending transaction/request/runtime receipt.

def request(root,action):
 root=safe(root);s,key=base.settings(root)
 if action not in {'RENEW','REPAIR','ABORT'}:raise ValueError('RENEWAL85_ACTION')
 if not (root/'owner85').is_file() or read(root/'owner85',key)!=dict(schema='85',run=s['run']):raise ValueError('RENEWAL85_NEW_OWNER_REQUIRED')
 rid=str(uuid.uuid4());write(root/'renew85.request',key,dict(schema='85',run=s['run'],request=rid,action=action));return dict(queued=rid)

class Journal85:
 def __init__(self,root):self.root=safe(root);self.s,self.key=base.settings(root);self.path=self.root/'publication85.tx'
 def load(self):
  if not self.path.exists():return None
  d=read(self.path,self.key)
  if set(d)!={'schema','run','id','phase','generation','inventory','label','release','before','target','at'} or d['schema']!='85' or d['run']!=self.s['run'] or d['phase'] not in TERMINAL|{'PREPARED','ABORTING'} or str(uuid.UUID(d['id']))!=d['id'] or not d['generation'].isdigit() or not 1<=int(d['generation'])<=32 or not re.fullmatch('[a-f0-9]{64}',d['inventory']):raise ValueError('RENEWAL85_JOURNAL')
  base.label(d['label'])
  if str(uuid.UUID(d['release']))!=d['release'] or uuid.UUID(d['release']).int==0:raise ValueError('RENEWAL85_JOURNAL')
  old=json.loads(d['before']);target=json.loads(d['target'])
  if set(old)!=set(POINTERS) or set(target)!=set(POINTERS):raise ValueError('RENEWAL85_JOURNAL')
  for n in POINTERS:
   a=self.unpack(old[n]);b=self.unpack(target[n]);self.packet(n,a,optional=True);self.packet(n,b)
   t=verify(self.key,b)
   if t['label']!=d['label'] or t['release']!=d['release'] or n=='baseline83' and (t['generation']!=d['generation'] or t['inventory']!=d['inventory']):raise ValueError('RENEWAL85_JOURNAL')
  return d
 def packet(self,name,raw,optional=False):
  if raw is None:
   if optional:return
   raise ValueError('RENEWAL85_JOURNAL')
  d=verify(self.key,raw);fields={'schema','run','label','release'}|({'generation','inventory'} if name=='baseline83' else set())
  if set(d)!=fields or d['run']!=self.s['run'] or d['schema']!=('83' if name=='baseline83' else '75'):raise ValueError('RENEWAL85_POINTER')
  base.label(d['label'])
  if str(uuid.UUID(d['release']))!=d['release'] or uuid.UUID(d['release']).int==0:raise ValueError('RENEWAL85_POINTER')
 @staticmethod
 def pack(raw):return None if raw is None else base64.b64encode(raw).decode('ascii')
 @staticmethod
 def unpack(value):
  if value is None:return None
  b=base64.b64decode(value,validate=True)
  if len(b)>16384 or base64.b64encode(b).decode('ascii')!=value:raise ValueError('RENEWAL85_JOURNAL')
  return b
 def raw(self,name):
  p=safe(self.root/name)
  if not p.exists():return None
  if not p.is_file() or p.stat().st_size>16384:raise ValueError('RENEWAL85_POINTER')
  with p.open('rb') as stream:b=stream.read(16385)
  if len(b)>16384:raise ValueError('RENEWAL85_POINTER')
  self.packet(name,b);return b
 def match(self,d):
  for n in POINTERS:
   current=self.raw(n);allowed=(self.unpack(json.loads(d['before'])[n]),self.unpack(json.loads(d['target'])[n]))
   if current not in allowed:raise ValueError('RENEWAL85_POINTER_CONFLICT')
 def begin(self,generation,inventory,label,release):
  current=self.load()
  if current and current['phase'] not in TERMINAL:
   if any(current[k]!=str(v) for k,v in dict(generation=generation,inventory=inventory,label=label,release=release).items()):raise ValueError('RENEWAL85_REPAIR_REQUIRED')
   self.match(current);return current
  before={n:self.pack(self.raw(n)) for n in POINTERS};target={'publication75':sign(self.key,dict(schema='75',run=self.s['run'],label=label,release=release)),'baseline83':sign(self.key,dict(schema='83',run=self.s['run'],generation=str(generation),inventory=inventory,label=label,release=release))}
  d=dict(schema='85',run=self.s['run'],id=str(uuid.uuid4()),phase='PREPARED',generation=str(generation),inventory=inventory,label=label,release=release,before=json.dumps(before,separators=(',',':')),target=json.dumps({k:self.pack(v) for k,v in target.items()},separators=(',',':')),at=str(int(time.time()*1000)));write(self.path,self.key,d);return self.load()
 def apply(self,d,abort=False):
  if self.load()!=d:raise ValueError('RENEWAL85_JOURNAL_CHANGED')
  if d['phase'] in TERMINAL:return d
  if d['phase']=='ABORTING' and not abort:raise ValueError('RENEWAL85_ABORT_PENDING')
  self.match(d)
  if abort and d['phase']!='ABORTING':d=dict(d,phase='ABORTING');write(self.path,self.key,d)
  desired=json.loads(d['before' if abort else 'target'])
  for n in POINTERS:
   self.match(d);raw=self.unpack(desired[n]);p=safe(self.root/n)
   if self.raw(n)==raw:continue
   if raw is None:p.unlink(missing_ok=True)
   else:atomic(p,raw)
  self.match(d)
  if any(self.raw(n)!=self.unpack(desired[n]) for n in POINTERS):raise ValueError('RENEWAL85_UNCONFIRMED')
  d=dict(d,phase='ABORTED' if abort else 'COMMITTED');write(self.path,self.key,d);return d

def status(root):
 root=safe(root);s,key=base.settings(root);d=dict(config=config(root),state='NEW_OWNER_REQUIRED',code='',transaction='NONE',expires='0',certificate='UNKNOWN',certificateExpires='0')
 if (root/'publication85.status').exists():
  p=read(root/'publication85.status',key)
  if set(p)!={'schema','run','at','state','code','expires','certificate','certificateExpires'} or p['schema']!='85' or p['run']!=s['run']:raise ValueError('RENEWAL85_STATUS')
  d.update({k:p[k] for k in ('state','code','expires','certificate','certificateExpires')})
  if not -30000<=int(time.time()*1000)-int(p['at'])<=10000:d['state']='OWNER_UNAVAILABLE'
 if (root/'view75').is_file():
  v=read(root/'view75',key)
  if v.get('schema')!='75' or v.get('run')!=s['run']:raise ValueError('RENEWAL85_OWNER_VIEW')
  if v.get('phase') in {'STOPPED_SAVED','STOP_NOT_CONFIRMED','FAILED'}:d['state']='STOPPED'
  elif not -30000<=int(time.time()*1000)-int(v.get('updated','0'))<=10000:d['state']='OWNER_UNAVAILABLE'
 if (root/'owner120').exists():
  p=root/'renew120'
  if p.exists():
   tx=read(p,key)
   if tx.get('schema')!='120' or tx.get('phase') not in {'AUTHORING','DONE','ABORTED'}:raise ValueError('RENEWAL120_JOURNAL')
   d['transaction']=tx['phase']
  return d
 tx=Journal85(root).load()
 if tx:d['transaction']=tx['phase']
 return d
