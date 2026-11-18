"""Private file bridge for the in-game server panel. No standalone configuration window."""
from pathlib import Path
import os,subprocess,time,uuid
from .common import safe,json_read,json_write
from .diagnostics63 import failure_data
from .session61 import consume,for_mode
from .capture import read_client
from .wizard_model import ClientLease,client_details,plan,prepare,workspace_parent,java,check_stopped
from .runtime import core_classpath
from .desktop66 import live_client_snapshot
from .setup import doctor

PHASES={'VALIDATING','SNAPSHOT','PREPARING','READY','CHECKED','ARMING','ARMED','STARTING','CANCELLED','COMPLETE','FAILED'}
REQUEST_FIELDS={'schema','request','pid','profile','account','neoforge','server','jar','side','authMode','port','operation'}

def load_request(runtime):
 r=json_read(runtime/'request66.json',16384)
 if not isinstance(r,dict) or set(r)!=REQUEST_FIELDS or r['schema']!=66:raise ValueError('Panel request format')
 if str(uuid.UUID(r['request']))!=r['request']:raise ValueError('Panel request identity')
 if type(r['pid']) is not int or not 1<=r['pid']<=0xffffffff:raise ValueError('Panel process identity')
 if r['side'] not in ('BOTH','SERVER','CLIENT') or r['authMode'] not in ('ONLINE','LOCAL_OFFLINE') or r['operation'] not in ('CHECK','PREPARE'):raise ValueError('Panel operation')
 if any(not isinstance(r[k],str) or len(r[k])>4096 or any(ord(c)<32 for c in r[k]) for k in ('profile','server','jar','account','neoforge')):raise ValueError('Panel path format')
 if type(r['port']) is not int or not 1024<=r['port']<=65535:raise ValueError('Loopback port')
 return r

def command(runtime,request):
 p=runtime/'command66.json'
 if not p.exists():return None
 d=json_read(p,4096)
 if not isinstance(d,dict) or set(d)!={'schema','request','action'} or d['schema']!=66 or d['request']!=request or d['action'] not in ('CANCEL','ARM','COMMIT'):raise ValueError('Panel command binding')
 return d['action']

class Worker:
 def __init__(self,runtime,lease_factory=ClientLease):
  self.runtime=safe(runtime);self.r=load_request(self.runtime);self.request=self.r['request'];self.root=None;self.lease_factory=lease_factory;self.phase='VALIDATING';self.delta={}
 def state(self,phase):
  if phase not in PHASES:raise ValueError('Panel phase')
  self.phase=phase
  value={'schema':66,'request':self.request,'phase':phase,'reportDirectory':str(self.root or self.runtime),'delta':self.delta}
  if hasattr(self,'failure_reason'):value['failureReason']=self.failure_reason
  json_write(self.runtime/'state66.json',value)
 def cancelled(self):return command(self.runtime,self.request)=='CANCEL'
 def require_open(self):
  if self.cancelled():raise InterruptedError('Pilot cancelled')
  if self.lease.closed():raise InterruptedError('Client closed before preparation completed')
 def wait_action(self,allowed,seconds=900):
  end=time.monotonic()+seconds
  while time.monotonic()<end:
   self.require_open();action=command(self.runtime,self.request)
   if action in allowed:return action
   time.sleep(.1)
  raise TimeoutError('Panel confirmation expired')
 def prepare(self):
  r=self.r;self.state('VALIDATING')
  session=consume(self.runtime,self.runtime/'client-session61.json',r['pid'],r['profile'],r['account'],allow_offline=True)
  argv,profile=read_client(r['pid'],r['profile'],session.get('cwd'))
  argv,account=for_mode(argv,session,r['authMode'])
  client=client_details(argv,profile,account,r['neoforge'],auth_mode=r['authMode'],dev_mods=session.get('devMods',''))
  spec,self.delta=plan(r['server'],client,port=r['port'])
  check_stopped(r['server'],self.runtime)
  replacement=safe(r['jar']) if r['jar'] else None;replacements={}
  if replacement:
   from .wizard_model import replacement_inventory,changes
   for role in (('server','client') if r['side']=='BOTH' else (r['side'].lower(),)):
    before,after=replacement_inventory(spec['next'+role.title()+'Mods'],replacement)
    self.delta[role]=changes(before,after);replacements[role]=replacement
  self.require_open()
  if r['operation']=='CHECK':self.state('CHECKED');return
  # Private sibling of server-tests, never inside a live source or the run directory.
  parent=workspace_parent();staging=safe(parent.parent/'client-preparation66')
  staging.mkdir(parents=True,exist_ok=True);java(self.runtime,argv[0],'private',staging)
  stage=staging/str(uuid.uuid4());stage.mkdir();java(self.runtime,argv[0],'private',stage)
  # Reject containment before any file copy. No local saves or locked worlds enter the copy.
  source=safe(profile)
  if source==staging or source in staging.parents or staging in source.parents:raise ValueError('NEW disjoint client snapshot required')
  self.state('SNAPSHOT');snapshot=stage/'client'
  live_client_snapshot(source,snapshot,self.cancelled)
  spec['clientTemplate']=str(snapshot)
  if safe(spec['nextClientMods'])==source/'mods':spec['nextClientMods']=str(snapshot/'mods')
  self.require_open();self.state('PREPARING')
  self.root=prepare(self.runtime,spec,parent,replacements=replacements)
  self.require_open();self.state('READY')
 def launch(self):
  self.wait_action({'ARM'});self.state('ARMING')
  # Current signatures and the native descendant selftest must pass BEFORE source-client shutdown.
  from first_launch import ownership
  with ownership(self.root):doctor(self.runtime,self.root)
  self.require_open()
  from .ui import run_cover
  from .controller import Pilot
  def operation(notify,cancel,ask):
   notify('COVER') # visible before the game is asked to close
   self.state('ARMED')
   end=time.monotonic()+30
   while command(self.runtime,self.request)!='COMMIT':
    if cancel() or self.cancelled():raise InterruptedError('Pilot cancelled')
    if time.monotonic()>=end:raise TimeoutError('Panel commit expired')
    time.sleep(.05)
   # Retained wait HANDLE, not a PID lookup or process adoption. Never kill the source process.
   end=time.monotonic()+120
   while not self.lease.closed():
    if time.monotonic()>=end:raise TimeoutError('Source client did not exit normally')
    time.sleep(.1)
   self.state('STARTING')
   with ownership(self.root):
    if (self.root/'started.v58').exists():raise ValueError('This pilot root has already been run; do not replay an unknown update')
    Pilot(self.runtime,self.root,notify,cancel,ask).run()
  run_cover(operation,self.root/'generations/client-0')
  self.state('COMPLETE')
 def run(self):
  self.lease=self.lease_factory(self.r['pid'])
  try:
   self.prepare()
   if self.r['operation']=='PREPARE':self.launch()
  except InterruptedError:self.state('CANCELLED')
  except Exception as error:
   # Use only the established safe exception catalogue, never argv/env or arbitrary messages.
   data=failure_data(error,{'phase':self.phase,'action':'run'})
   self.failure_reason=data['reason']
   json_write((self.root or self.runtime)/'panel-report66.json',{'schema':66,'request':self.request,**data})
   self.state('FAILED')
   raise
  finally:self.lease.close()
