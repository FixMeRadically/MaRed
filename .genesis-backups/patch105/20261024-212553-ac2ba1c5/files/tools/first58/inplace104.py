"""Server-folder owner. No generations, world copies, external client profile or CMD files.
WindowsJob owns only a newly created Java tree; all restart/changes happen after its exit.
"""
from pathlib import Path
import contextlib,hashlib,json,os,re,secrets,socket,subprocess,sys,time,uuid,zipfile
from .common import safe,sha,atomic,json_read,json_write,inventory
from .bridge67 import read,write
from .changes104 import Changes104
from . import dedicated74 as old,access88
from .windows_job import WindowsJob
from .dedicated75 import independent_owner

@contextlib.contextmanager
def lease(path):
 path=safe(path);stream=path.open('a+b')
 try:
  if path.stat().st_size==0:stream.write(b'0');stream.flush()
  stream.seek(0)
  if os.name=='nt':
   import msvcrt
   msvcrt.locking(stream.fileno(),msvcrt.LK_NBLCK,1)
  else:
   import fcntl
   fcntl.lockf(stream,fcntl.LOCK_EX|fcntl.LOCK_NB,1)
  yield
 finally:
  stream.close()

def root_for(server):return safe(server)/'.genesis/server104'
def identity(server):
 server=safe(server);root=root_for(server);key=safe(root/'owner74.key').read_bytes()
 if len(key)!=32:raise ValueError('SERVER104_KEY')
 return root,key

def config(server,java):
 server=safe(server);java=safe(java)
 if not server.is_dir() or java.name.lower()!='java.exe' or not java.is_file():raise ValueError('SERVER104_SERVER_OR_JAVA')
 if not (server/'eula.txt').is_file() or not re.search(r'(?m)^\s*eula\s*=\s*true\s*$',(server/'eula.txt').read_text(encoding='utf-8-sig')):raise ValueError('SERVER104_ACCEPT_EULA')
 check=subprocess.run([str(java),'-version'],capture_output=True,text=True,timeout=15)
 if check.returncode or not re.search(r'version "21(?:\.|\")',check.stdout+check.stderr):raise ValueError('SERVER104_JAVA21')
 args=list((server/'libraries/net/neoforged/neoforge').glob('21.1.*/win_args.txt'))
 if len(args)>1 and (server/'run.bat').is_file():
  # Read only the fixed NeoForge argfile reference; never execute a shell script.
  run_text=safe(server/'run.bat').read_text(encoding='utf-8-sig',errors='replace')
  selected=set(re.findall(r'libraries[/\\]net[/\\]neoforged[/\\]neoforge[/\\](21\.1\.[0-9]+)[/\\]win_args\.txt',run_text))
  args=[path for path in args if path.parent.name in selected]
 if len(args)!=1:raise ValueError('SERVER104_ONE_NEOFORGE_RUNTIME')
 p=old.properties(server) if (server/'server.properties').exists() else {'level-name':'world','server-port':'25565','server-ip':'','online-mode':'true'}
 if not re.fullmatch('[A-Za-z0-9_-]{1,80}',p['level-name']) or p['level-name'].lower() in {'mods','config','libraries','.genesis'}:raise ValueError('SERVER104_WORLD_NAME')
 argv=[str(java)]
 if (server/'user_jvm_args.txt').exists():argv.append('@user_jvm_args.txt')
 argv+=['@'+str(args[0].relative_to(server)),'nogui'];old.validate_argv(argv,server)
 return dict(argv=argv,java=str(java),neoforge=args[0].parent.name,minecraft='1.21.1',level=p['level-name'],port=p['server-port'],bind=p['server-ip'],auth=p['online-mode'])

def current(server):
 root,key=identity(server)
 if not (root/'state104').exists():return {'phase':'STOPPED'}
 d=read(root/'state104',key);owner=read(root/'owner104',key)
 if d.get('schema')!='104' or d.get('run')!=owner.get('run') or owner.get('profile')!=str(safe(server)):raise ValueError('SERVER104_STATE_BINDING')
 if d['phase'] not in {'STOPPED','FAILED','RECOVERY_REQUIRED','BOOTSTRAP_FAILED_RESTORED'} and abs(int(time.time()*1000)-int(d['updated']))>10000:d=dict(d,phase='OWNER_UNAVAILABLE')
 return d

def request(server,action):
 if action not in {'RESTART','SHUTDOWN'}:raise ValueError('SERVER104_ACTION')
 root,key=identity(server);d=current(server)
 if d['phase'] not in {'RUNNING','STARTING','WAITING_FOR_STOP'} or d['busy']=='true' and action!='SHUTDOWN':raise ValueError('SERVER104_BUSY_OR_STOPPED')
 with lease(root/'submit75.lock'):
  oldreq=read(root/'request74',key) if (root/'request74').exists() else None
  ack=read(root/'request74.ack',key) if (root/'request74.ack').exists() else {}
  if oldreq and oldreq.get('request')!=ack.get('request'):raise ValueError('SERVER104_REQUEST_PENDING')
  write(root/'request74',key,dict(schema='75',run=d['run'],generation=d['generation'],request=str(uuid.uuid4()),action=action,label='r1'))
 return {'phase':'QUEUED','action':action}

class Owner104:
 def __init__(self,server,app,java,job_factory=WindowsJob):
  self.server=safe(server);self.app=safe(app);self.root=root_for(server);self.c=config(server,java);self.key=safe(self.root/'owner74.key').read_bytes();self.run=str(uuid.uuid4());self.job_factory=job_factory;self.job=None;self.channel=None;self.launch='';self.generation=0;self.sequence=0;self.phase='STARTING';self.error='';self.busy=False;self.seen=set();self.changes=Changes104(self.server,self.root,self.key);self.expected='';self.last_receipt=None;self.shutdown=False;self.last_request=b'';self.last_report=None;self.last_report_at=0
  # A new owner may preserve signed role assignments, but old run-bound permits must expire.
  subjects=''
  if (self.root/'access88.state').exists():
   prior=read(self.root/'access88.state',self.key);oldmarker=read(self.root/'owner88',self.key)
   if prior.get('run')!=oldmarker.get('run') or prior.get('schema')!='88':raise ValueError('SERVER104_OLD_POLICY')
   access88.state(self.root,self.key,prior['run']);subjects=prior['subjects']
  write(self.root/'owner104',self.key,dict(schema='104',run=self.run,profile=str(self.server)))
  for version in (75,88,103):write(self.root/f'owner{version}',self.key,dict(schema=str(version),run=self.run))
  write(self.root/'access88.state',self.key,dict(schema='88',run=self.run,revision='0',subjects=subjects,sequence='0',audit=''))
  for p in ['request74','request74.ack']:(self.root/p).unlink(missing_ok=True)
  (self.root/'runs').mkdir(exist_ok=True);(self.root/'logs').mkdir(exist_ok=True)
 def report(self,phase=None,error=None):
  if phase:self.phase=phase
  if error is not None:self.error=error
  fingerprint=(self.phase,self.error,self.generation,self.busy,self.launch);clock=time.monotonic()
  if fingerprint==self.last_report and clock-self.last_report_at<1:return
  self.last_report=fingerprint;self.last_report_at=clock
  now=str(int(time.time()*1000));write(self.root/'state104',self.key,dict(schema='104',run=self.run,phase=self.phase,generation=str(self.generation),updated=now,busy=str(self.busy).lower(),error=self.error,launch=self.launch))
  nativephase={'RUNNING':'ACTIVE_BASELINE','STOPPED':'STOPPED_SAVED','STOPPING':'SAVING_OLD_SERVER','RESTARTING':'STARTING_NEW_SERVER'}.get(self.phase,self.phase)
  write(self.root/'view75',self.key,dict(schema='75',run=self.run,generation=str(self.generation),phase=nativephase,updated=now,ready=str(self.phase=='RUNNING').lower(),control='{}',delta='{}',history='[]',failureCode=self.error,failureStage='',reason='',busy=str(self.busy).lower()))
  json_write(self.root/'logs/status.json',dict(phase=self.phase,error=self.error,generation=self.generation,launch=self.launch,server=str(self.server)))
 def world_idle(self):
  return old.stopped_world(self.server,self.c['level'])
 def all_exited(self):
  if self.job is None:return True
  d=self.job.status();return d['rootExited'] and d['active']==0
 def reap(self):
  if not self.all_exited():raise ValueError('SERVER104_GAME_STILL_ACTIVE')
  if self.job:self.job.close();self.job=None
 def bootstrap(self):
  candidate=safe(self.app/'mared.jar')
  with zipfile.ZipFile(candidate) as z:
   if 'com/fixmer/mared/technology/server/firstlaunch/InPlaceWorld104.class' not in z.namelist():raise ValueError('SERVER104_BOOTSTRAP_VERSION')
  existing=[]
  for p in (self.server/'mods').glob('*.jar'):
   with zipfile.ZipFile(p) as z:
    if 'com/fixmer/mared/Mared.class' in z.namelist():existing.append(p)
  if len(existing)>1:raise ValueError('SERVER104_DUPLICATE_GENESIS')
  name=existing[0].name if existing else 'mared-genesis.jar'
  self.changes.stage({name:candidate})
  if self.changes.load():self.changes.apply()
 def start_game(self):
  self.reap();self.generation+=1
  if self.generation>999999999:raise ValueError('SERVER104_COUNTER_LIMIT')
  self.seen.clear();self.launch=str(uuid.uuid4());self.sequence=0;self.last_receipt=None;self.channel=self.root/'runs'/self.launch;self.channel.mkdir();self.expected=inventory(self.server)
  c=dict(schema='74',run=self.run,generation=str(self.generation),inventory=self.expected,minecraft=self.c['minecraft'],neoforge=self.c['neoforge'],auth=self.c['auth'],bind=self.c['bind'],port=self.c['port'])
  write(self.channel/f'server-{self.generation}.session',self.key,c);write(self.channel/'launch104',self.key,dict(schema='104',run=self.run,launch=self.launch,generation=str(self.generation),profile=str(self.server)))
  env=old.environment(self.c['java'],self.server);env.update(GENESIS_DEDICATED74_ROOT=str(self.root),GENESIS_DEDICATED74_RUN=self.run,GENESIS_DEDICATED74_GENERATION=str(self.generation),GENESIS_DEDICATED75_VERSION='75',GENESIS_INPLACE_VERSION='104',GENESIS_INPLACE_LAUNCH=self.launch)
  self.report('STARTING');self.job=self.job_factory(self.c['argv'],self.server,env,self.root/'logs'/('server-'+self.launch+'.log'));deadline=time.monotonic()+180
  while time.monotonic()<deadline:
   d=self.receipt()
   if d and d['phase']=='READY' and d['sequence']=='0':
    if inventory(self.server)!=self.expected:raise ValueError('SERVER104_MODS_CHANGED_DURING_START')
    if self.changes.load():self.changes.finish(True)
    self.report('RUNNING');return
   if self.all_exited():raise ValueError('SERVER104_STARTUP_EXIT')
   if d and d['phase']=='ERROR':raise ValueError('SERVER104_RUNTIME_ERROR')
   self.report();time.sleep(.25)
  raise ValueError('SERVER104_STARTUP_TIMEOUT')
 def receipt(self):
  if not self.channel:return None
  p=self.channel/f'server-{self.generation}.status'
  if not p.exists():return None
  d=read(p,self.key)
  if set(d)!={'schema','run','generation','inventory','sequence','request','phase','round','at'} or any(d[k]!=v for k,v in {'schema':'74','run':self.run,'generation':str(self.generation),'inventory':self.expected}.items()):raise ValueError('SERVER104_RECEIPT_BINDING')
  if d['phase'] not in {'READY','SAVED','STOPPED_SAVED','STOPPED','ERROR','COMMAND_ACCEPTED','QUIESCED'}:raise ValueError('SERVER104_RECEIPT_PHASE')
  self.last_receipt=d;return d
 def stop_game(self):
  if self.job is None:return
  if self.all_exited():raise ValueError('SERVER104_EXIT_WITHOUT_SAVE')
  self.sequence+=1;nonce=str(uuid.uuid4());round_id=str(uuid.uuid4());cmd=dict(schema='74',run=self.run,generation=str(self.generation),inventory=self.expected,sequence=str(self.sequence),request=nonce,action='SHUTDOWN',round=round_id,label='r1');write(self.channel/f'server-{self.generation}.command',self.key,cmd);deadline=time.monotonic()+120;self.report('STOPPING')
  while time.monotonic()<deadline:
   d=self.receipt()
   if self.all_exited():
    if not d or d['phase']!='STOPPED_SAVED' or any(d[k]!=cmd[k] for k in ('sequence','request','round','inventory')) or self.job.status()['exitCode']!=0:raise ValueError('SERVER104_SAVE_UNCONFIRMED')
    self.reap();return
   self.report();time.sleep(.2)
  raise ValueError('SERVER104_SAVE_TIMEOUT')
 def receive(self):
  p=self.root/'request74'
  if not p.exists():return None
  raw=p.read_bytes()
  if raw==self.last_request:return None
  d=read(p,self.key)
  if set(d) not in ({'schema','run','generation','request','action','label'},{'schema','run','generation','request','action','label','permit88'}) or d.get('schema')!='75' or d.get('action') not in {'RESTART','SHUTDOWN'} or d.get('label')!='r1' or str(uuid.UUID(d['request']))!=d['request'] or uuid.UUID(d['request']).int==0:raise ValueError('SERVER104_REQUEST')
  def acknowledge():
   self.last_request=raw;write(self.root/'request74.ack',self.key,dict(schema='75',run=self.run,request=d['request']))
  if d.get('run')!=self.run or d.get('generation')!=str(self.generation) or d['request'] in self.seen:
   acknowledge();raise ValueError('SERVER104_STALE_REQUEST')
  if len(self.seen)>=8192:acknowledge();raise ValueError('SERVER104_REQUEST_LIMIT')
  try:access88.consume(self.root,self.key,self.run,d)
  except ValueError:
   acknowledge();raise
  self.seen.add(d['request']);acknowledge();return d['action']

 def run_loop(self):
  if (self.root/'review104').exists():self.report('RECOVERY_REQUIRED','SERVER104_REVIEW_REQUIRED');return
  try:
   with lease(self.root/'game104.lock'),self.world_idle():
    pending=self.changes.load()
    if pending and pending[0]['phase']=='COMMITTED':self.changes.clear_terminal();pending=None
    if pending:
     write(self.root/'review104',self.key,dict(schema='104',reason='INTERRUPTED_CHANGE'))
     if pending[0]['phase']!='RESTORED':self.changes.restore()
     self.changes.finish(False);self.report('RECOVERY_REQUIRED','SERVER104_INTERRUPTED_CHANGE_RESTORED');return
    self.bootstrap()
   self.start_game()
  except Exception as error:
   self.report('FAILED',error_code(error))
   self.hold_live(error_code(error))
   # A live/hung server is never assumed stopped because its startup timer expired.
   if self.all_exited() and self.changes.load():
    world=bool(self.channel and (self.channel/'world104').exists());self.reap()
    with lease(self.root/'game104.lock'),self.world_idle():self.changes.restore();self.changes.finish(False)
    if world:write(self.root/'review104',self.key,dict(schema='104',reason='WORLD_MAY_HAVE_CHANGED'));self.report('RECOVERY_REQUIRED','SERVER104_RESTORED_WORLD_REVIEW')
    else:self.report('BOOTSTRAP_FAILED_RESTORED','SERVER104_BOOTSTRAP_FAILED_RESTORED')
   raise
  while True:
   if self.all_exited():
    code=self.job.status()['exitCode'];self.reap();self.report('STOPPED' if code==0 else 'FAILED','' if code==0 else 'SERVER104_UNEXPECTED_EXIT');return
   try:action=self.receive()
   except access88.AccessBusy88:action=None
   except ValueError as error:self.error=error_code(error);action=None
   if action:
    self.busy=True
    try:self.stop_game()
    except Exception as error:self.hold_live(error_code(error));self.reap();self.report('FAILED',error_code(error));return
    if action=='SHUTDOWN':self.busy=False;self.report('STOPPED');return
    # No files/world are copied or overwritten for an ordinary restart.
    self.report('RESTARTING')
    try:self.start_game()
    except Exception as error:self.hold_live(error_code(error));self.reap();self.report('FAILED',error_code(error));return
    self.busy=False
   self.report();time.sleep(.25)
 def hold_live(self,reason):
  while True:
   try:
    if self.all_exited():return
   except Exception:pass # Unknown native liveness is never treated as an empty Job.
   self.busy=False
   try:self.report('WAITING_FOR_STOP',reason)
   except Exception:pass # Full disk must not end the owner and kill an unsaved game.
   try:
    if self.receive()=='SHUTDOWN':self.stop_game()
   except Exception:pass
   time.sleep(.25)
 def close(self):
  # Never close a live/unknown Job from a recoverable timeout or disk-error path.
  try:
   if self.all_exited():self.reap()
  except Exception:pass

def error_code(error):
 code=str(error);return code if re.fullmatch('[A-Z][A-Z0-9_]{1,100}',code) else 'SERVER104_OPERATION_FAILED'
