"""Owner-local baseline selection: only exact running SERVER code and unchanged CLIENT renewal."""
from .dedicated82 import Owner82
from . import publication83 as pub
from . import dedicated74 as base
from .bridge67 import read,write
from .common import inventory
from .install70 import policy
import time

class Owner83(Owner82):
 def __init__(self,*args,**kwargs):
  super().__init__(*args,**kwargs);write(self.root/'owner83',self.key,dict(schema='83',run=self.s['run']));self.publication_error83=''
 def report(self,phase=None,reason=''):
  return super().report(phase,reason or getattr(self,'publication_error83',''))
 def checkpoint83(self):
  self.health()
  if (self.root/'request74').is_file() and read(self.root/'request74',self.key).get('action')=='SHUTDOWN':
   from .dedicated75 import Owner75
   Owner75.receive(self);self.stop_requested=True
  if self.stop_requested:raise InterruptedError('PUBLICATION83_STOP_PENDING')
  if time.monotonic()>=getattr(self,'heartbeat83',0):self.report();self.heartbeat83=time.monotonic()+.5
 def publish_baseline(self,label):
  label=base.label(label)
  if self.pending or self.external or self.busy75 or self.current<1:raise ValueError('PUBLICATION83_UPDATE_BUSY')
  self.busy75=True
  try:
   if (self.root/'control73/active73').exists() and self.tool('inspect',self.root)['phase'] not in {'CANCELLED','CLOSED'}:raise ValueError('PUBLICATION83_UPDATE_BUSY')
   if self.current not in self.jobs:raise ValueError('PUBLICATION83_SERVER_NOT_READY')
   native=self.jobs[self.current].status();status=self.status(self.current)
   if not self.ready_generation1 or native['rootExited'] or native['logFailed'] or not status or status['phase'] not in {'READY','COMMAND_ACCEPTED'}:raise ValueError('PUBLICATION83_SERVER_NOT_READY')
   profile=self.root/f'generations/server-{self.current}';session=read(self.root/f'server-{self.current}.session',self.key)
   if inventory(profile)!=session['inventory']:raise ValueError('PUBLICATION83_RUNTIME_CHANGED')
   pair=self.root/'releases'/label;info=pub.verify_pair(self.project,self.root,pair,self.root/'publisher73/public.der',self.checkpoint83)
   if info['minecraft']!=self.s['minecraft'] or info['neoforge']!=self.s['neoforge'] or info['game'].rsplit(':',1)[1]!=str(self.s['port']):raise ValueError('PUBLICATION83_ADDRESS')
   if self.s['auth']=='false':
    import urllib.parse
    if info['game'].rsplit(':',1)[0] not in {'localhost','127.0.0.1','[::1]'} or urllib.parse.urlsplit(info['endpoint']).hostname not in {'localhost','127.0.0.1','::1'}:raise ValueError('PUBLICATION83_OFFLINE_LOOPBACK')
   plan=policy(profile/'mods',pair/'server.zip',info)
   if any(plan['delta'][k] for k in ('added','changed','removed')):raise ValueError('PUBLICATION83_SERVER_CODE_DIFFERS')
   if self.previous_info:
    if info['release']!=self.previous_info['release'] and not self.successor(info):raise ValueError('DEDICATED75_RELEASE_CHAIN')
    active=self.root/'releases'/self.active_label76
    if pub.call(self.project,self.root,'client-seal',pair,self.root/'publisher73/public.der',checkpoint=self.checkpoint83)!=pub.call(self.project,self.root,'client-seal',active,self.root/'publisher73/public.der',checkpoint=self.checkpoint83):raise ValueError('PUBLICATION83_CLIENT_CODE_DIFFERS')
    if any(info[k]!=self.previous_info[k] for k in ('game','endpoint','distribution','signer')):raise ValueError('PUBLICATION83_IDENTITY')
   else:
    first=pub.call(self.project,self.root,'baseline',self.root/'publisher73',self.root/'releases',checkpoint=self.checkpoint83)
    if first['path']!=str(pair):raise ValueError('PUBLICATION83_FIRST_REQUIRED')
   self.health();native=self.jobs[self.current].status();receipt=self.status(self.current)
   if self.stop_requested or native['rootExited'] or native['logFailed'] or not receipt or receipt['phase'] not in {'READY','COMMAND_ACCEPTED'}:raise ValueError('PUBLICATION83_SERVER_NOT_READY')
   if inventory(profile)!=session['inventory']:raise ValueError('PUBLICATION83_RUNTIME_CHANGED')
   if (self.root/'request74').is_file() and read(self.root/'request74',self.key).get('action')=='SHUTDOWN':raise ValueError('PUBLICATION83_STOP_PENDING')
   # No mod/world files are written. A partial pointer write never fabricates a READY heartbeat.
   write(self.root/'baseline83',self.key,dict(schema='83',run=self.s['run'],generation=str(self.current),label=label,release=info['release'],inventory=session['inventory']))
   write(self.root/'publication75',self.key,dict(schema='75',run=self.s['run'],label=label,release=info['release']))
   self.previous_info=info;self.active_label76=label;self.publication_error83=''
   self.history.append('BASELINE_PUBLISHED',self.current,self.current,info['release'],'none',session['inventory'])
   self.report('ACTIVE_UPDATED','Публикация выбрана. Файлы и мир не менялись; HTTPS настраивается отдельно.')
  finally:
   self.busy75=False
   self.report(reason=self.reason)
 def receive(self):
  request=super().receive()
  if request and request['action']=='PUBLISH_BASELINE':
   try:self.publish_baseline(request['label'])
   except InterruptedError:raise
   except Exception as e:
    code=str(e) if len(e.args)==1 and isinstance(e.args[0],str) and __import__('re').fullmatch('[A-Z0-9_]{1,100}',str(e)) else 'PUBLICATION83_REJECTED'
    self.publication_error83='Публикация не переключена или результат не подтверждён: '+code;self.report()
   return None
  if request:self.publication_error83=''
  return request
