"""Idle metadata renewal and bounded return admission for the actual folder owner.
No game/world copy. No timer or metadata receipt substitutes for held live runtime proof.
"""
from pathlib import Path
import json,re,time,uuid
from .bridge67 import read,write
from .common import safe,inventory
from . import publication83,publication85,dedicated74 as base
from .install70 import policy

class FolderServices120:
 def __init__(self,rollout):
  self.f=rollout;self.o=rollout.o;self.root=rollout.root;self.key=rollout.key;self.next=0;self.state='WAITING_BASELINE';self.code='';self.expiry=0;self.clock=False;self.wall=time.time();self.mono=time.monotonic();self.revision=None;self.request='';self.returning=None;self.uncertain=False
  self.expiry_binding=None;self.expiry_check_at=0
  write(self.root/'owner85',self.key,dict(schema='85',run=self.o.run));write(self.root/'owner120',self.key,dict(schema='120',run=self.o.run))
  cfg=self.root/'renew85.cfg'
  if cfg.exists():
   d=read(cfg,self.key)
   if set(d)!={'schema','run','revision','enabled','ttl','margin'} or d['schema']!='85':raise ValueError('RENEWAL120_CONFIG')
   d.update(run=self.o.run,revision=str(uuid.uuid4()));write(cfg,self.key,d);publication85.config(self.root)
  prior=self.tx()
  self.uncertain=bool(prior and prior['phase']=='AUTHORING')
  # Old runtime admission is never replayed after owner death.
  for name in ('return120.local','return120.intent'):(self.root/name).unlink(missing_ok=True)
 def call(self,*args):return publication83.command(self.o.app,self.root,'com.fixmer.genesis.technology.server.remote.Renewal85',*args,checkpoint=self.f.checkpoint)
 def guard(self):
  o=self.o;f=self.f
  if f.pending or f.fence or o.busy or o.phase!='RUNNING' or (self.root/'review104').exists() or o.changes.load():raise ValueError('RENEWAL120_UPDATE_BUSY')
  native=o.job.status() if o.job else None;r=o.receipt() if native and not native['rootExited'] else None
  if not native or native['rootExited'] or native['logFailed'] or not r or r['phase'] not in {'READY','COMMAND_ACCEPTED'} or inventory(o.server)!=o.expected:raise ValueError('RENEWAL120_RUNTIME_REQUIRED')
  pointer=read(self.root/'publication75',self.key)
  if pointer['run']!=o.run:raise ValueError('RENEWAL120_OWNER_CHANGED')
  return pointer
 def snapshot(self,pointer):
  label=base.label(pointer['label']);pair=self.root/'releases'/label
  info=self.call('inspect',self.root/'publisher73',pair)
  if info['release']!=pointer['release']:raise ValueError('RENEWAL120_SOURCE_CHANGED')
  historical=self.f.tool('verify-history',pair,self.root/'publisher73/public.der');p=policy(self.o.server/'mods',pair/'server.zip',historical)
  if p['before']!=p['after']:raise ValueError('RENEWAL120_RUNTIME_CHANGED')
  return info
 def tx(self):
  p=self.root/'renew120'
  if not p.exists():return None
  d=read(p,self.key)
  if set(d)!={'schema','run','phase','source','release','label','generation','inventory','ttl'} or d['schema']!='120' or d['phase'] not in {'AUTHORING','DONE','ABORTED'}:raise ValueError('RENEWAL120_JOURNAL')
  for k in ('source','label'):base.label(d[k])
  return d
 def complete(self,d):
  p=self.guard()
  if d['generation']!=str(self.o.generation) or d['inventory']!=self.o.expected:raise ValueError('RENEWAL120_RUNTIME_CHANGED')
  target=self.root/'releases'/d['label']
  if not target.exists():
   write(self.root/'renew120',self.key,dict(d,phase='ABORTED'));self.uncertain=False;self.state='ABORTED';return
  self.call('same',self.root/'publisher73',self.root/'releases'/d['source'],target)
  info=self.call('inspect',self.root/'publisher73',target)
  if int(info['expires'])<=int(time.time()*1000):raise ValueError('RENEWAL120_TARGET_EXPIRED')
  if p not in [dict(schema='75',run=self.o.run,label=d['source'],release=d['release']),dict(schema='75',run=self.o.run,label=d['label'],release=info['release'])]:raise ValueError('RENEWAL120_POINTER_CONFLICT')
  self.snapshot(p);self.guard()
  write(self.root/'publication75',self.key,dict(schema='75',run=self.o.run,label=d['label'],release=info['release']))
  self.f.baseline_binding=(d['label'],info['release'],self.o.expected);write(self.root/'renew120',self.key,dict(d,phase='DONE'));self.uncertain=False;self.state='RENEWED';self.code='';self.expiry=int(info['expires'])
 def renew(self,cfg,p):
  self.snapshot(p)
  if len(list((self.root/'releases').iterdir()))>=120:raise ValueError('RENEWAL85_RETENTION_REQUIRED')
  source=self.root/'releases'/p['label'];head=self.call('head',self.root/'publisher73',source,self.root/'releases')['current']
  seed='none'
  if not head:
   # A known failed committed head may be superseded ONLY with the exact already-running old code.
   if not self.returning:raise ValueError('RENEWAL85_UNAPPLIED_RELEASE')
   seed=self.returning['failedLabel']
   if not self.call('head',self.root/'publisher73',self.root/'releases'/seed,self.root/'releases')['current']:raise ValueError('RENEWAL85_UNAPPLIED_RELEASE')
  d=dict(schema='120',run=self.o.run,phase='AUTHORING',source=p['label'],release=p['release'],label='renew-'+uuid.uuid4().hex,generation=str(self.o.generation),inventory=self.o.expected,ttl=cfg['ttl'])
  write(self.root/'renew120',self.key,d);self.uncertain=True;self.state='RENEWING'
  if seed=='none':self.call('renew',self.root/'publisher73',source,self.root/'releases',d['label'],cfg['ttl'])
  else:self.call('renew-return',self.root,source,self.root/'releases'/seed,self.root/'releases',d['label'],cfg['ttl'])
  self.complete(d)
 def tick(self):
  if time.monotonic()<self.next:return
  self.next=time.monotonic()+5
  try:
   cfg=publication85.config(self.root)
   if cfg['revision']!=self.revision:self.revision=cfg['revision'];self.wall=time.time();self.mono=time.monotonic();self.clock=False
   if abs((time.time()-self.wall)-(time.monotonic()-self.mono))>120:self.clock=True
   if self.clock:raise ValueError('RENEWAL85_CLOCK_CONFIRM_REQUIRED')
   p=self.root/'renew85.request';action=None
   if p.exists():
    d=read(p,self.key)
    if set(d)!={'schema','run','request','action'} or d['schema']!='85' or d['run']!=self.o.run or d['action'] not in {'RENEW','REPAIR','ABORT'}:raise ValueError('RENEWAL85_REQUEST')
    uuid.UUID(d['request'])
    if d['request']!=self.request:action=d['action']
   if self.f.pending or self.o.busy:self.state='WAITING_UPDATE';return
   if not (self.root/'publication75').exists():self.state='WAITING_BASELINE';return
   tx=self.tx()
   if tx and tx['phase']=='AUTHORING':
    selected=self.guard()
    if action:self.request=d['request']
    if tx['run']!=self.o.run:
     if action not in {'REPAIR','ABORT'}:raise ValueError('RENEWAL120_OWNER_REVIEW_REQUIRED')
     if selected['label'] not in {tx['source'],tx['label']}:raise ValueError('RENEWAL120_POINTER_CONFLICT')
     self.snapshot(selected);tx=dict(tx,run=self.o.run,generation=str(self.o.generation),inventory=self.o.expected);write(self.root/'renew120',self.key,tx)
    if action=='ABORT':
     if selected['label']!=tx['source']:raise ValueError('RENEWAL120_POINTER_CONFLICT')
     write(self.root/'renew120',self.key,dict(tx,phase='ABORTED'));self.uncertain=False;self.state='ABORTED';return
    self.complete(tx);return
   if action=='RENEW':
    selected=self.guard();self.request=d['request']
    self.renew(cfg,selected)
    return
   if action:self.request=d['request']
   selected=read(self.root/'publication75',self.key)
   binding=(selected,self.o.generation,self.o.expected,cfg['revision'])
   if binding!=self.expiry_binding or time.monotonic()>=self.expiry_check_at:
    selected=self.guard()
    self.expiry=int(self.snapshot(selected)['expires'])
    self.expiry_binding=(selected,self.o.generation,self.o.expected,cfg['revision'])
    self.expiry_check_at=time.monotonic()+60
   # Cached expiry schedules work only. Every renewal rechecks the live runtime and archives.
   if cfg['enabled']=='true' and self.expiry<=int(time.time()*1000)+int(cfg['margin'])*1000:self.renew(cfg,self.guard())
   else:self.state='WATCHING' if cfg['enabled']=='true' else 'DISABLED';self.code=''
  except Exception as e:
   code=str(e);self.code=code if re.fullmatch('[A-Z0-9_]{1,100}',code) else 'RENEWAL120_FAILED';self.state='WAITING_UPDATE' if self.code=='RENEWAL120_UPDATE_BUSY' else 'SUSPENDED';self.next=time.monotonic()+30
 def restored(self,p,fence):
  if not p or not fence or not p.get('prior'):return
  d=dict(schema='120',run=self.o.run,round=fence['round'],label=p['prior']['label'],failedLabel=p['label'],generation=str(self.o.generation),inventory=p['old'],created=str(int(time.time()*1000)))
  write(self.root/'return120.intent',self.key,d);self.returning=d
 def withdraw(self):
  self.returning=None
  for name in ('return120.local','return120.intent'):(self.root/name).unlink(missing_ok=True)
 def heartbeat(self):
  now=int(time.time()*1000)
  cert_expiry=int(self.f.https.active['expires']) if self.f.https.active else 0
  certificate='EXPIRED' if cert_expiry and cert_expiry<=now else 'EXPIRING' if cert_expiry and cert_expiry-now<=7*86400000 else 'VALID' if cert_expiry else 'NOT_CONFIGURED'
  write(self.root/'publication85.status',self.key,dict(schema='85',run=self.o.run,at=str(now),state=self.state,code=self.code,expires=str(self.expiry),certificate=certificate,certificateExpires=str(cert_expiry)))
  d=self.returning
  if d and now-int(d['created'])<=1800000:
   try:
    self.guard()
    if self.o.expected!=d['inventory'] or self.o.generation!=int(d['generation']):raise ValueError('RETURN120_RUNTIME_CHANGED')
    write(self.root/'return120.local',self.key,dict(schema='120',run=self.o.run,round=d['round'],generation=d['generation'],inventory=d['inventory'],at=str(now),serial=str(self.f.serial)))
    return
   except Exception:pass
  (self.root/'return120.local').unlink(missing_ok=True)
