"""Persistent dedicated owner: one OS owner, monotonic generations, authenticated bounded history."""
from pathlib import Path
import contextlib,hashlib,json,os,re,threading,time,uuid,zipfile,shutil
from . import dedicated74 as old
from .common import safe,atomic,inventory,json_write,sha
from .bridge67 import read,write,verify
from .install70 import policy,install

@contextlib.contextmanager
def rpc_lock(root):
 path=safe(root/'submit75.lock')
 with path.open('a+b') as f:
  if path.stat().st_size==0:f.write(b'0');f.flush()
  f.seek(0)
  if os.name=='nt':
   import msvcrt
   msvcrt.locking(f.fileno(),msvcrt.LK_NBLCK,1)
  else:
   import fcntl
   fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB,1)
  try:yield
  finally:
   f.seek(0)
   if os.name=='nt':msvcrt.locking(f.fileno(),msvcrt.LK_UNLCK,1)
   else:fcntl.lockf(f,fcntl.LOCK_UN,1)

def submit(root,action,name='r1',request=None):
 root=safe(root);s,key=old.settings(root)
 if not (root/'owner75').is_file() or read(root/'owner75',key)!={'schema':'75','run':s['run']}:raise ValueError('DEDICATED75_OWNER_REQUIRED')
 action=action.upper()
 if action not in old.ACTIONS|{'STAGE'}:raise ValueError('DEDICATED75_ACTION')
 nonce=str(uuid.UUID(request)) if request else str(uuid.uuid4())
 if nonce==str(uuid.UUID(int=0)):raise ValueError('DEDICATED75_NONCE')
 state=read(root/'view75',key) if (root/'view75').exists() else {}
 if state.get('run')!=s['run'] or state.get('schema')!='75' or state.get('phase') in {'STOPPED_SAVED','STOP_NOT_CONFIRMED','FAILED'}:raise ValueError('DEDICATED75_OWNER_UNAVAILABLE')
 if int(time.time()*1000)-int(state['updated']) < -30000 or action!='SHUTDOWN' and (int(time.time()*1000)-int(state['updated'])>10000 or state['busy']=='true'):raise ValueError('DEDICATED75_BUSY_OR_STALE')
 d=dict(schema='75',run=s['run'],generation=state['generation'],request=nonce,action=action,label=old.label(name))
 with rpc_lock(root):
  previous=read(root/'request74',key) if (root/'request74').exists() else None
  if previous:
   if previous['request']==nonce:
    if previous!=d:raise ValueError('DEDICATED75_NONCE_CONFLICT')
    return nonce
   ack=read(root/'request74.ack',key) if (root/'request74.ack').exists() else {}
   if ack.get('request')!=previous['request'] or ack.get('run')!=s['run']:raise ValueError('DEDICATED75_REQUEST_PENDING')
  write(root/'request74',key,d)
 return nonce

def independent_owner():
 # A server owner inherited into a client's Job would die when that client restarts.
 # Never weaken its parent Job or silently claim independence.
 import ctypes as C
 from ctypes import wintypes as W
 if os.name!='nt' or C.sizeof(C.c_void_p)!=8:raise ValueError('DEDICATED75_WINDOWS_X64_ONLY')
 kernel=C.WinDLL('kernel32',use_last_error=True);kernel.GetCurrentProcess.restype=W.HANDLE;kernel.GetCurrentProcess.argtypes=[];kernel.IsProcessInJob.restype=W.BOOL;kernel.IsProcessInJob.argtypes=[W.HANDLE,W.HANDLE,C.POINTER(W.BOOL)];member=W.BOOL()
 if not kernel.IsProcessInJob(kernel.GetCurrentProcess(),None,C.byref(member)):raise ValueError('DEDICATED75_OWNER_SCOPE_UNCONFIRMED')
 if member.value:raise ValueError('DEDICATED75_START_FROM_TERMINAL')

def initialize(project,source,root,java):
 # Reject old bootstrap before creating a private, incomplete new root.
 bootstrap=[]
 for jar in safe(source/'mods').glob('*.jar'):
  if jar.stat().st_size>64*1024*1024:raise ValueError('DEDICATED75_MOD_LIMIT')
  with zipfile.ZipFile(jar) as z:
   if 'com/fixmer/mared/Mared.class' in z.namelist():
    if 'com/fixmer/mared/technology/server/remote/ServerAdmin75.class' not in z.namelist():raise ValueError('DEDICATED75_INSTALL_NEW_BOOTSTRAP')
    bootstrap.append(jar)
 if len(bootstrap)!=1:raise ValueError('DEDICATED75_BOOTSTRAP')
 root=old.initialize(project,source,root,java);s,key=old.settings(root);write(root/'owner75',key,{'schema':'75','run':s['run']});return root

class History75:
 def __init__(self,root,key,run):
  self.root=safe(root/'history75');self.root.mkdir(exist_ok=True);self.key=key;self.run=run;self.rows=[];self.head='0'*64
  files=sorted(self.root.iterdir())
  if len(files)>512:raise ValueError('DEDICATED75_HISTORY_LIMIT')
  for index,file in enumerate(files,1):
   if file.name!=f'{index:04}.entry':raise ValueError('DEDICATED75_HISTORY_GAP')
   if file.stat().st_size>16384:raise ValueError('DEDICATED75_HISTORY_SIZE')
   raw=safe(file).read_bytes();d=verify(key,raw)
   if set(d)!={'schema','run','sequence','at','phase','source','target','release','round','inventory','previous','reason'} or d['schema']!='75' or d['run']!=run or d['sequence']!=str(index) or d['previous']!=self.head:raise ValueError('DEDICATED75_HISTORY_BINDING')
   self.rows.append(d);self.head=hashlib.sha256(raw).hexdigest()
 def append(self,phase,source,target,release='none',round_id='none',inventory_hash='none',reason=''):
  if len(self.rows)>=512:raise ValueError('DEDICATED75_HISTORY_LIMIT')
  d=dict(schema='75',run=self.run,sequence=str(len(self.rows)+1),at=str(int(time.time()*1000)),phase=phase,source=str(source),target=str(target),release=release,round=round_id,inventory=inventory_hash,previous=self.head,reason=reason)
  path=self.root/f'{len(self.rows)+1:04}.entry'
  if path.exists():raise ValueError('DEDICATED75_HISTORY_EXISTS')
  from .bridge67 import sign
  data=sign(self.key,d);atomic(path,data,new=True);self.rows.append(d);self.head=hashlib.sha256(data).hexdigest();return d
 def public(self,limit=8):return [{k:r[k] for k in ('sequence','at','phase','source','target','release','reason')} for r in (self.rows[-limit:] if limit else self.rows)]

class Owner75(old.Owner74):
 def __init__(self,*args,**kwargs):
  super().__init__(*args,**kwargs)
  if read(self.root/'owner75',self.key)!={'schema':'75','run':self.s['run']}:raise ValueError('DEDICATED75_OWNER_REQUIRED')
  self.history=History75(self.root,self.key,self.s['run']);self.previous_info=None;self.completed_round=None;self.last_event=None;self.current_inventory=self.s['initialInventory'];self.busy75=False
 def event(self,phase):
  p=self.pending;self.history.append(phase,p['sourceGen'] if p else self.current,p['targetGen'] if p else self.current,p['info']['release'] if p else 'none',self.pump_round if self.external else 'none',self.current_inventory,self.failure_code)
 def report(self,phase=None,reason=''):
  super().report(phase,reason)
  if not hasattr(self,'history'):return
  delta=self.pending['approved']['delta'] if self.pending else {};compact={k:v[:12] for k,v in delta.items() if isinstance(v,list)};compact.update({k+'Count':len(v) for k,v in delta.items() if isinstance(v,list)})
  view=dict(schema='75',run=self.s['run'],generation=str(self.current),phase=self.phase,updated=str(int(time.time()*1000)),ready=str(self.ready_generation1).lower(),control=json.dumps(self.last_pump or {},separators=(',',':')),delta=json.dumps(compact,separators=(',',':')),history=json.dumps(self.history.public(),separators=(',',':')),failureCode=self.failure_code,failureStage=self.failure_stage,reason=reason,busy=str(self.busy75).lower())
  write(self.root/'view75',self.key,view)
 def wait(self,predicate,seconds,code,check_control=True):
  end=time.monotonic()+seconds;heartbeat=0
  while time.monotonic()<end:
   self.health(check_control);value=predicate()
   if value:return value
   if time.monotonic()>=heartbeat:self.report();heartbeat=time.monotonic()+1
   time.sleep(.1)
  raise ValueError(code)
 def start(self,g):
  if g<0 or g>32 or self.jobs:raise ValueError('DEDICATED75_OLD_JOB_STILL_HELD')
  profile=self.root/f'generations/server-{g}';c=read(self.root/f'server-{g}.session',self.key)
  if inventory(profile)!=c['inventory']:raise ValueError('DEDICATED75_GENERATION_CHANGED')
  old.validate_argv(self.s['argv'],profile);props=old.properties(profile)
  if any(props[k]!=self.s[v] for k,v in [('level-name','level'),('online-mode','auth'),('server-ip','bind'),('server-port','port')]):raise ValueError('DEDICATED75_SERVER_MODE_CHANGED')
  env=old.environment(self.s['java'],profile);env.update(GENESIS_DEDICATED74_ROOT=str(self.root),GENESIS_DEDICATED74_RUN=self.s['run'],GENESIS_DEDICATED74_GENERATION=str(g),GENESIS_DEDICATED75_VERSION='75')
  self.jobs[g]=self.job_factory(self.s['argv'],profile,env,self.root/f'server-{g}.log');self.current=g
  def ready():
   if self.jobs[g].status()['rootExited']:raise ValueError('DEDICATED75_EXIT_BEFORE_READY')
   d=self.status(g);return d and d['phase']=='READY' and d['sequence']=='0'
  self.wait(ready,180,'DEDICATED75_RUNTIME_TIMEOUT');self.ready_generation1=g>0
 def checkpoint(self):
  self.health()
  if self.jobs[self.current].status()['rootExited']:raise ValueError('DEDICATED75_SOURCE_EXIT')
 def verify_stage(self):
  if not self.pending:raise ValueError('DEDICATED75_NO_STAGE')
  p=self.pending;source=self.root/f"generations/server-{p['sourceGen']}"
  if inventory(source)!=p['sourceInventory'] or old.digest(old.tree(source,self.s['level'],True))!=p['sourceSeal'] or old.digest(old.tree(p['path'],self.s['level'],True))!=p['targetSeal']:raise ValueError('DEDICATED75_STAGE_CHANGED')
  if self.tool('verify',p['release'],self.root/'publisher73/public.der')!=p['info']:raise ValueError('DEDICATED75_RELEASE_CHANGED')
  props=old.properties(source)
  if any(props[k]!=self.s[v] for k,v in [('level-name','level'),('online-mode','auth'),('server-ip','bind'),('server-port','port')]):raise ValueError('DEDICATED75_SERVER_MODE_CHANGED')
 def finish_round(self,force=False):
  if not self.external:return
  state=self.last_pump
  if not force and (not state or not state['admission'] or int(state.get('joined',0))<int(state['total'])):raise ValueError('DEDICATED75_WAIT_FOR_JOINED')
  self.pump_stop.set()
  if self.pump_thread:self.pump_thread.join(timeout=20)
  if self.pump_thread and self.pump_thread.is_alive():raise ValueError('DEDICATED75_PUBLISHER_BUSY')
  if self.pump_error:raise ValueError('DEDICATED75_PUBLISHER_FAILED')
  self.tool('close',self.root);self.external=False;self.pump_thread=None;self.pump_stop=threading.Event();self.pump_error=None;self.admit=False;self.completed_round=state['round'] if state else None;self.event('ROUND_CLOSED');self.last_pump=None;self.pending=None;(self.root/'prepared74').unlink(missing_ok=True)
 def prepare(self,name):
  if self.current>=32:raise ValueError('DEDICATED75_GENERATION_LIMIT')
  self.checkpoint();self.finish_round()
  if len(self.history.rows)>480:raise ValueError('DEDICATED75_HISTORY_LIMIT')
  if (self.root/'control73/active73').exists() and self.tool('inspect',self.root)['phase'] not in {'CANCELLED','CLOSED'}:raise ValueError('DEDICATED75_ACTIVE_ROUND')
  release=self.root/'releases'/old.label(name);info=self.tool('verify',release,self.root/'publisher73/public.der')
  if info['minecraft']!=self.s['minecraft'] or info['neoforge']!=self.s['neoforge']:raise ValueError('DEDICATED75_LOADER_CHANGE')
  if self.previous_info and (info['parent']!=self.previous_info['release'] or int(info['sequence'])<=int(self.previous_info['sequence']) or info['distribution']!=self.previous_info['distribution']):raise ValueError('DEDICATED75_RELEASE_CHAIN')
  host,port=info['game'].rsplit(':',1)
  if int(port)!=int(self.s['port']) or self.s['auth']=='false' and host not in {'localhost','127.0.0.1','[::1]'}:raise ValueError('DEDICATED75_ADDRESS')
  source=self.root/f'generations/server-{self.current}';source_inv=read(self.root/f'server-{self.current}.session',self.key)['inventory'];path=self.root/'stages'/(uuid.uuid4().hex+'.building');self.report('STAGING_CODE');baseline=old.copy_tree(source,path,self.s['level'],True,self.checkpoint);approved=policy(path/'mods',release/'server.zip',info);install(path,release/'server.zip',info,approved);shutil.rmtree(path/'mods.before70')
  self.pending=dict(info=info,release=release,path=path,approved=approved,sourceSeal=old.digest(baseline),targetSeal=old.digest(old.tree(path,self.s['level'],True)),label=name,sourceGen=self.current,targetGen=self.current+1,sourceInventory=source_inv)
  self.verify_stage();proof=dict(schema='74',run=self.s['run'],generation=str(self.current),oldInventory=source_inv,newInventory=inventory(path),release=info['release'],client=info['client'],server=info['server'],label=name,codeSeal=self.pending['targetSeal']);write(self.root/'prepared74',self.key,proof)
  dest=source/'config/mared/remote-updates'/name
  if dest.exists():
   if old.tree(dest,'unused')!=old.tree(release/'public','unused'):raise ValueError('DEDICATED75_PUBLIC_DIFFERS')
  else:old.copy_tree(release/'public',dest,'unused')
  write(self.root/'publication75',self.key,{'schema':'75','run':self.s['run'],'label':name,'release':info['release']});self.event('PREPARED');self.command(self.current,'PREPARE',name=name);self.report('PREPARED')
 def copy_saved(self):
  p=self.pending;g=p['sourceGen'] if p else -1
  if not p or g not in self.jobs or not self.jobs[g].empty():raise ValueError('DEDICATED75_WORLD_OWNER_ACTIVE')
  d=self.status(g);cmd=read(self.root/f'server-{g}.command',self.key)
  if not d or d['phase']!='STOPPED_SAVED' or cmd['action']!='SAVE_STOP' or any(d[k]!=cmd[k] for k in ('sequence','request','round','inventory')):raise ValueError('DEDICATED75_SAVE_RECEIPT')
  self.verify_stage();source=self.root/f'generations/server-{g}';target=p['path'];level=self.s['level']
  if (target/level).exists() or (target/'config').exists() or not (source/level).is_dir():raise ValueError('DEDICATED75_UNPUBLISHED_TARGET')
  old.copy_tree(source/level,target/level,'unused',checkpoint=self.health,raw=True)
  if (source/'config').is_dir():old.copy_tree(source/'config',target/'config','unused',checkpoint=self.health,raw=True)
  for name in old.MUTABLE:
   src=safe(source/name)
   if src.exists():
    if not src.is_file():raise ValueError('DEDICATED75_MUTABLE_FILE')
    shutil.copyfile(src,target/name)
  expected=read(self.root/'prepared74',self.key)['newInventory']
  if inventory(target)!=expected:raise ValueError('DEDICATED75_NEW_INVENTORY')
  final=self.root/f"generations/server-{p['targetGen']}"
  if final.exists():raise ValueError('DEDICATED75_GENERATION_EXISTS')
  old.session(self.root,self.s,self.key,p['targetGen'],expected);target.rename(final);json_write(self.root/f"saved-world75-{p['targetGen']}.json",dict(schema=75,run=self.s['run'],source=g,target=p['targetGen'],round=d['round'],sourceStoppedSaved=True));self.event('WORLD_PUBLISHED')
 def wait_all_installed(self):
  while True:
   self.health();state=self.last_pump
   if state and int(state['total'])>0 and int(state['installed'])==int(state['total']):return
   if self.jobs[self.current].status()['rootExited']:raise ValueError('DEDICATED75_SOURCE_EXIT')
   try:request=self.receive()
   except ValueError:self.report('WAITING_ALL_INSTALLED','Некорректный запрос отклонён. Сервер продолжает ждать установки.');request=None
   if request:
    if request['action']=='SHUTDOWN':self.stop_requested=True;raise InterruptedError('DEDICATED75_STOP_REQUESTED')
    self.report('WAITING_ALL_INSTALLED','Операция недоступна во время перехода; можно остановить владельца.')
   self.report('WAITING_ALL_INSTALLED');time.sleep(.5)
 def activate(self,state):
  self.verify_stage();p=self.pending;g=self.current
  if state['phase']!='COMMITTED' or state['release']!=p['info']['release'] or state['client']!=p['info']['client'] or state['admission']:raise ValueError('DEDICATED75_COMMIT_BINDING')
  self.event('COMMITTED');self.report('QUIESCING_CONTROL');cmd=self.command(g,'QUIESCE',state['round'])
  def quiesced():
   d=self.status(g);return d and d['phase']=='QUIESCED' and all(d[k]==cmd[k] for k in ('sequence','request','round'))
  self.wait(quiesced,30,'DEDICATED75_QUIESCE_TIMEOUT');self.start_pump(state['round']);self.report('WAITING_ALL_INSTALLED');self.wait_all_installed();self.report('SAVING_OLD_SERVER');self.stop(g,'SAVE_STOP',state['round']);self.event('STOPPED_SAVED');self.report('COPYING_SAVED_WORLD');self.copy_saved();self.jobs[g].close();del self.jobs[g];self.ready_generation1=False;self.current_inventory=read(self.root/f"server-{p['targetGen']}.session",self.key)['inventory'];self.report('STARTING_NEW_SERVER');self.start(p['targetGen']);self.previous_info=p['info'];self.event('SERVER_READY');self.report('WAITING_THRESHOLD')
 def advance_admission(self):
  if not self.ready_generation1 or self.current not in self.jobs:return False
  self.health();native=self.jobs[self.current].status();receipt=self.status(self.current)
  # Native operator commands may replace READY with COMMAND_ACCEPTED; readiness was verified at start.
  if native['rootExited'] or not receipt or receipt['phase'] not in {'READY','COMMAND_ACCEPTED'}:self.admit=False;return False
  state=self.last_pump
  if state and state['phase']=='COMMITTED' and int(state['installed'])>=int(state['needed']):self.admit=True
  return self.admit
 def receive(self):
  path=self.root/'request74'
  if not path.exists():return None
  if path.stat().st_size>16384:raise ValueError('DEDICATED75_REQUEST_SIZE')
  raw=path.read_bytes()
  if raw==self.last_request:return None
  self.last_request=raw;d=verify(self.key,raw)
  if set(d)!={'schema','run','generation','request','action','label'} or d['schema']!='75' or d['run']!=self.s['run'] or d['action'] not in old.ACTIONS|{'STAGE'}:raise ValueError('DEDICATED75_REQUEST')
  if str(uuid.UUID(d['request']))!=d['request'] or uuid.UUID(d['request']).int==0 or d['request'] in self.seen or len(self.seen)>=1024 and d['action']!='SHUTDOWN':raise ValueError('DEDICATED75_REPLAY')
  old.label(d['label']);self.seen.add(d['request']);write(self.root/'request74.ack',self.key,{'schema':'75','run':self.s['run'],'request':d['request']})
  if d['generation']!=str(self.current):raise ValueError('DEDICATED75_STALE_GENERATION')
  return d
 def run(self):
  independent_owner()
  try:
   if os.name!='nt':raise ValueError('DEDICATED75_WINDOWS_X64_ONLY')
   atomic(self.root/'started74',self.s['run'].encode(),new=True);self.busy75=True;self.report('STARTING_BASELINE');old.native_probe(self.project,self.s['java'],self.root);self.start(0);self.busy75=False;self.event('BASELINE_READY');self.report('ACTIVE_BASELINE')
   while not self.stop_requested:
    self.health()
    if self.jobs[self.current].status()['rootExited']:raise ValueError('DEDICATED75_UNREQUESTED_EXIT')
    try:request=self.receive()
    except ValueError:self.report(reason='Некорректный или устаревший запрос отклонён.');time.sleep(.5);continue
    if request:
     action=request['action']
     if action=='SHUTDOWN':self.stop_requested=True;break
     if action in {'OFFER','COMMIT','CANCEL','CLOSE'} and self.pending and request['label']!=self.pending['label']:self.report(reason='Имя выпуска изменилось. Обнови поле выпуска.');continue
     if action=='STAGE':
      previous=self.pending;was_external=self.external;proof=self.root/'prepared74';saved=proof.read_bytes() if proof.exists() else None;self.busy75=True
      try:self.prepare(request['label']);self.failure_code=self.failure_stage=''
      except Exception as error:
       if was_external and not self.external:previous=None;saved=None
       self.pending=previous
       if saved is not None:atomic(proof,saved)
       else:proof.unlink(missing_ok=True)
       self.failure_stage=self.phase;self.failure_code=error.args[0] if len(error.args)==1 and isinstance(error.args[0],str) and re.fullmatch('[A-Z0-9_]{1,100}',error.args[0]) else 'DEDICATED75_PREPARATION_FAILED';self.report('PREPARATION_FAILED','Подготовка отклонена; текущий сервер остаётся открыт.')
      finally:self.busy75=False
     elif action=='CLOSE' and self.external:
      self.finish_round(True);self.report('ACTIVE_UPDATED')
     elif self.external:self.report(reason='Предыдущий раунд ещё обслуживается; дождись входа участников или явно закрой его.')
     else:self.command(self.current,action,name=request['label'])
    if self.pending and not self.external and (self.root/'control73/active73').exists():
     state=self.tool('inspect',self.root);self.last_pump=state
     if state['phase']=='COMMITTED':self.busy75=True;self.activate(state);self.busy75=False
    if self.external:
     self.advance_admission()
     if self.last_pump and self.last_pump['admission']:self.report('ACTIVE_UPDATED')
     else:self.report()
    else:self.report()
    time.sleep(.5)
  except (KeyboardInterrupt,InterruptedError):self.stop_requested=True
  except Exception as error:
   self.failure_stage=self.phase;self.failure_code=error.args[0] if len(error.args)==1 and isinstance(error.args[0],str) and re.fullmatch('[A-Z0-9_]{1,100}',error.args[0]) else 'DEDICATED75_FAILED';self.report('FAILED','Переход остановлен. Поколения сохранены; автоматического отката мира нет.');raise
  finally:
   try:self.event('STOPPING')
   except Exception:pass
   super().cleanup()
