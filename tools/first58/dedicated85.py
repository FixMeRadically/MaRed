"""Renew immutable baseline while idle; recover authenticated metadata writes only against the held live Job."""
import json,re,time,uuid
from pathlib import Path
from .dedicated83 import Owner83
from . import dedicated74 as base,publication83 as pub,publication85 as state
from .bridge67 import read,write
from .common import inventory,safe
from .install70 import policy
from .renew85 import Manager85

class Owner85(Owner83):
 https_factory=Manager85
 def __init__(self,*args,**kwargs):
  super().__init__(*args,**kwargs);write(self.root/'owner85',self.key,dict(schema='85',run=self.s['run']));self.journal85=state.Journal85(self.root);self.state85='WAITING_BASELINE';self.code85='';self.expiry85=0;self.next85=0;self.config85=None;self.clock85=False;self.wall85=time.time();self.mono85=time.monotonic();self.request85='';self.auto85=False;self.inspect85=None;self.inspect_label85='';self.paused85=False
 def report(self,phase=None,reason=''):
  super().report(phase,reason)
  if not hasattr(self,'state85'):return
  try:
   try:tx=self.journal85.load();uncertain=bool(tx and tx['phase'] not in state.TERMINAL)
   except Exception:uncertain=True
   local=self.root/'bootstrap76.local'
   if uncertain and local.is_file():
    d=read(local,self.key);self.serial76+=1;d.update(serial=str(self.serial76),at=str(int(time.time()*1000)),phase='UNAVAILABLE');write(local,self.key,d)
   transport=getattr(self,'https78',None);active=transport.active if transport else None;expiry=int(active['expires']) if active else 0;certificate='EXPIRED' if expiry and expiry<=int(time.time()*1000) else 'EXPIRING' if expiry and expiry-int(time.time()*1000)<=7*86400000 else 'VALID' if expiry else 'NOT_CONFIGURED'
   write(self.root/'publication85.status',self.key,dict(schema='85',run=self.s['run'],at=str(int(time.time()*1000)),state='STOPPED' if self.stop_requested or self.phase in {'FAILED','STOPPED_SAVED','STOP_NOT_CONFIRMED'} else self.state85,code=self.code85,expires=str(self.expiry85),certificate=certificate,certificateExpires=str(expiry)))
  except Exception:pass # Diagnostics alone do not terminate the world.
 def guard85(self,operator=True):
  if self.stop_requested:raise InterruptedError('RENEWAL85_STOP_PENDING')
  if self.pending or self.external or self.busy75 or self.current<1:raise ValueError('RENEWAL85_UPDATE_BUSY')
  if (self.root/'control73/active73').exists() and self.tool('inspect',self.root)['phase'] not in {'CANCELLED','CLOSED'}:raise ValueError('RENEWAL85_UPDATE_BUSY')
  self.health();job=self.jobs.get(self.current);native=job.status() if job else None;receipt=self.status(self.current) if job else None
  if not self.ready_generation1 or not native or native['rootExited'] or native['logFailed'] or not receipt or receipt['phase'] not in {'READY','COMMAND_ACCEPTED'}:raise ValueError('RENEWAL85_SERVER_NOT_READY')
  session=read(self.root/f'server-{self.current}.session',self.key)
  if inventory(self.root/f'generations/server-{self.current}')!=session['inventory']:raise ValueError('RENEWAL85_RUNTIME_CHANGED')
  p=self.root/'request74'
  if operator and p.is_file() and p.read_bytes()!=self.last_request:raise ValueError('RENEWAL85_OPERATOR_PENDING')
  return session
 def checkpoint85(self):
  self.checkpoint83()
  p=self.root/'request74'
  if p.is_file() and p.read_bytes()!=self.last_request:raise ValueError('RENEWAL85_OPERATOR_PENDING')
 def core85(self,*args):return pub.command(self.project,self.root,'com.fixmer.genesis.technology.server.remote.Renewal85',*args,checkpoint=self.checkpoint85)
 def switch_baseline83(self,label,info,session):
  # Called only after all original83 runtime, source/target, CLIENT and release-chain checks.
  tx=self.journal85.begin(self.current,session['inventory'],label,info['release']);self.journal85.apply(tx)
 def repair85(self,abort=False):
  tx=self.journal85.load()
  if not tx or tx['phase'] in state.TERMINAL:return False
  session=self.guard85()
  if tx['generation']!=str(self.current) or tx['inventory']!=session['inventory']:raise ValueError('RENEWAL85_GENERATION_CHANGED')
  self.journal85.match(tx)
  if abort or tx['phase']=='ABORTING':
   target=self.root/'releases'/tx['label'];info=pub.command(self.project,self.root,'com.fixmer.genesis.technology.server.remote.DedicatedTools74','verify-history',target,self.root/'publisher73/public.der',checkpoint=self.checkpoint85)
   if info['release']!=tx['release'] or any(policy(self.root/f'generations/server-{self.current}/mods',target/'server.zip',info)['delta'][k] for k in ('added','changed','removed')):raise ValueError('RENEWAL85_RUNTIME_CHANGED')
   before=json.loads(tx['before']);raw=self.journal85.unpack(before['publication75']);old=state.verify(self.key,raw) if raw else None;previous=None
   if old:
    source=self.root/'releases'/base.label(old['label']);previous=pub.command(self.project,self.root,'com.fixmer.genesis.technology.server.remote.DedicatedTools74','verify-history',source,self.root/'publisher73/public.der',checkpoint=self.checkpoint85)
    if previous['release']!=old['release']:raise ValueError('RENEWAL85_SOURCE_CHANGED')
    self.core85('same',self.root/'publisher73',source,target)
   self.guard85();self.journal85.apply(tx,abort=True);self.previous_info=previous;self.active_label76=old['label'] if old else None;self.inspect_label85='';self.state85='RESTORED';self.code85='';self.publication_error83='';self.paused85=True
   self.history.append('PUBLICATION_RESTORED',self.current,self.current,previous['release'] if previous else 'none',inventory_hash=session['inventory'],reason=tx['id']);return True
  # Full83 validation again, including FRESH target offer. Never treats the journal as readiness.
  if int(self.core85('inspect',self.root/'publisher73',self.root/'releases'/tx['label'])['expires'])<=int(time.time()*1000):raise ValueError('RENEWAL85_TARGET_EXPIRED')
  self.publish_baseline(tx['label']);self.state85='REPAIRED';self.code85='';return True
 def pending85(self):
  p=self.root/'renew85.pending'
  if not p.exists():return None
  d=read(p,self.key)
  if set(d)!={'schema','run','phase','source','release','label','generation','inventory','ttl'} or d['schema']!='85' or d['run']!=self.s['run'] or d['phase'] not in {'AUTHORING','DONE','ABORTED'}:raise ValueError('RENEWAL85_PENDING')
  base.label(d['source']);base.label(d['label'])
  if not d['ttl'].isdigit() or not 3600<=int(d['ttl'])<=604800:raise ValueError('RENEWAL85_PENDING')
  return d
 def resume85(self,p):
  if p['phase']!='AUTHORING':return False
  session=self.guard85()
  tx=self.journal85.load()
  if p['generation']==str(self.current) and p['inventory']==session['inventory'] and tx and tx['phase']=='COMMITTED' and tx['label']==p['label'] and tx['generation']==str(self.current) and tx['inventory']==session['inventory'] and self.active_label76==p['label'] and self.previous_info and self.previous_info['release']==tx['release']:
   self.journal85.match(tx)
   for name in state.POINTERS:
    if self.journal85.raw(name)!=self.journal85.unpack(json.loads(tx['target'])[name]):raise ValueError('RENEWAL85_POINTER_CONFLICT')
   self.core85('same',self.root/'publisher73',self.root/'releases'/p['source'],self.root/'releases'/p['label']);write(self.root/'renew85.pending',self.key,dict(p,phase='DONE'));self.state85='RENEWED';self.paused85=False;self.inspect_label85='';return True
  if p['generation']!=str(self.current) or p['inventory']!=session['inventory'] or not self.previous_info or p['release']!=self.previous_info['release'] or p['source']!=self.active_label76:raise ValueError('RENEWAL85_SOURCE_CHANGED')
  folder=self.root/'releases'/p['label']
  if not folder.exists():write(self.root/'renew85.pending',self.key,dict(p,phase='ABORTED'));return False
  self.core85('same',self.root/'publisher73',self.root/'releases'/p['source'],folder)
  if int(self.core85('inspect',self.root/'publisher73',folder)['expires'])<=int(time.time()*1000):raise ValueError('RENEWAL85_TARGET_EXPIRED')
  self.guard85();self.publish_baseline(p['label']);write(self.root/'renew85.pending',self.key,dict(p,phase='DONE'));self.state85='RENEWED';self.code85='';self.paused85=False;self.inspect_label85='';return True
 def renew85(self,cfg):
  session=self.guard85()
  if not self.previous_info or not self.active_label76:raise ValueError('RENEWAL85_BASELINE_REQUIRED')
  if len(self.history.rows)>=480 or len(list((self.root/'releases').iterdir()))>=120:raise ValueError('RENEWAL85_RETENTION_REQUIRED')
  # Unpublished Script/next-code releases take priority: do not move their expected baseline.
  owned=self.root/'releases'/self.active_label76;head=self.core85('inspect',self.root/'publisher73',owned)
  pointer=self.root/'publication75'
  if not pointer.is_file() or read(pointer,self.key)!=dict(schema='75',run=self.s['run'],label=self.active_label76,release=self.previous_info['release']):raise ValueError('RENEWAL85_SOURCE_NOT_SELECTED')
  # A higher signed release may already be awaiting the operator. Never silently overtake it.
  from .renew85 import head_is_current
  ignored='none';prior=self.journal85.load()
  if prior and prior['phase']=='ABORTED' and prior['generation']==str(self.current) and prior['inventory']==session['inventory']:
   self.journal85.match(prior)
   if all(self.journal85.raw(n)==self.journal85.unpack(json.loads(prior['before'])[n]) for n in state.POINTERS):ignored=prior['label']
  if not head_is_current(self.project,self.root,owned,self.checkpoint85,ignored):raise ValueError('RENEWAL85_UNAPPLIED_RELEASE')
  label='renew-'+uuid.uuid4().hex;p=dict(schema='85',run=self.s['run'],phase='AUTHORING',source=self.active_label76,release=self.previous_info['release'],label=label,generation=str(self.current),inventory=session['inventory'],ttl=cfg['ttl']);write(self.root/'renew85.pending',self.key,p)
  self.state85='RENEWING';self.busy75=True
  try:self.core85('renew',self.root/'publisher73',owned,self.root/'releases',label,cfg['ttl'],ignored)
  finally:self.busy75=False
  self.resume85(p)
 def abandon85(self,p):
  session=self.guard85()
  if p['generation']!=str(self.current) or p['inventory']!=session['inventory'] or not self.previous_info or p['release']!=self.previous_info['release'] or p['source']!=self.active_label76:raise ValueError('RENEWAL85_SOURCE_CHANGED')
  pointer=read(self.root/'publication75',self.key)
  if pointer!=dict(schema='75',run=self.s['run'],label=p['source'],release=p['release']):raise ValueError('RENEWAL85_POINTER_CONFLICT')
  folder=self.root/'releases'/p['label']
  if folder.exists():
   self.core85('same',self.root/'publisher73',self.root/'releases'/p['source'],folder);info=self.core85('inspect',self.root/'publisher73',folder)
   self.guard85();tx=self.journal85.begin(self.current,session['inventory'],p['label'],info['release']);self.journal85.apply(tx,abort=True)
  write(self.root/'renew85.pending',self.key,dict(p,phase='ABORTED'));self.paused85=True;self.state85='RESTORED';self.code85=''
 def action85(self):
  p=self.root/'renew85.request'
  if not p.exists():return None
  d=read(p,self.key)
  if set(d)!={'schema','run','request','action'} or d['schema']!='85' or d['run']!=self.s['run'] or d['action'] not in {'RENEW','REPAIR','ABORT'} or str(uuid.UUID(d['request']))!=d['request']:raise ValueError('RENEWAL85_REQUEST')
  if d['request']==self.request85:return None
  self.request85=d['request'];return d['action']
 def maintenance85(self):
  if time.monotonic()<self.next85:return
  self.next85=time.monotonic()+5
  try:
   cfg=state.config(self.root)
   if cfg['revision']!=self.config85:self.config85=cfg['revision'];self.clock85=False;self.wall85=time.time();self.mono85=time.monotonic();self.paused85=False
   if abs((time.time()-self.wall85)-(time.monotonic()-self.mono85))>120:self.clock85=True
   prior_request=self.request85;action=self.action85()
   if self.clock85:self.state85='CLOCK_CHANGED';self.code85='RENEWAL85_CLOCK_CONFIRM_REQUIRED';return
   tx=self.journal85.load()
   if tx and tx['phase'] not in state.TERMINAL:
    if action=='ABORT':self.repair85(True)
    else:self.repair85()
    return
   p=self.pending85()
   if action=='ABORT':
    if p and p['phase']=='AUTHORING':self.abandon85(p)
    else:self.state85='NO_TRANSACTION'
    return
   if p and p['phase']=='AUTHORING':self.resume85(p);return
   if not self.previous_info or not self.active_label76:self.state85='WAITING_BASELINE';self.expiry85=0;return
   if self.pending or self.external or self.busy75:
    if action:self.request85=prior_request
    self.state85='WAITING_UPDATE';return
   if self.inspect_label85!=self.active_label76:
    self.inspect85=self.core85('inspect',self.root/'publisher73',self.root/'releases'/self.active_label76);self.inspect_label85=self.active_label76
   self.expiry85=int(self.inspect85['expires'])
   if action=='REPAIR':self.state85='NO_TRANSACTION';return
   if self.paused85 and action!='RENEW':self.state85='PAUSED_AFTER_RESTORE';return
   if action=='RENEW' or cfg['enabled']=='true' and self.expiry85<=int(time.time()*1000)+int(cfg['margin'])*1000:self.renew85(cfg)
   else:self.state85='WATCHING' if cfg['enabled']=='true' else 'DISABLED';self.code85=''
  except InterruptedError:raise
  except Exception as e:
   c=str(e);self.code85=c if re.fullmatch('[A-Z0-9_]{1,100}',c) else 'RENEWAL85_FAILED';self.state85='REPAIR_REQUIRED' if self.journal85.path.exists() and self.journal85.load()['phase'] not in state.TERMINAL else 'SUSPENDED';self.next85=time.monotonic()+30
 def receive(self):
  request=super().receive()
  if request and request['action']=='SHUTDOWN':return request
  try:
   tx=self.journal85.load()
   if request and tx and tx['phase'] not in state.TERMINAL:self.state85='REPAIR_REQUIRED';self.code85='RENEWAL85_REPAIR_REQUIRED';request=None
   if not request:self.maintenance85()
  except InterruptedError:raise
  except Exception:self.state85='REPAIR_REQUIRED';self.code85='RENEWAL85_JOURNAL'
  return request
