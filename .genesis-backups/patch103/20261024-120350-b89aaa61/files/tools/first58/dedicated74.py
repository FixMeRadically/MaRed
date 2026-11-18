"""One owner, two dedicated generations. Never adopts a PID or installs into a live profile."""
from pathlib import Path
import contextlib,hashlib,json,os,re,secrets,shutil,subprocess,threading,time,uuid,zipfile
from .common import safe,atomic,sha,inventory,json_read,json_write
from .bridge67 import read,write
from .runtime import core_classpath
from .install70 import policy,install,files
from .native70 import environment,native_probe
from .windows_job import WindowsJob

MUTABLE={'server.properties','eula.txt','ops.json','whitelist.json','banned-players.json','banned-ips.json','usercache.json','permissions.json'}
IGNORED={'logs','crash-reports','backups','screenshots','first58-temp','remote70-temp','dedicated74-temp','.git'}
ACTIONS={'LOCAL_TEST_ON','LOCAL_TEST_OFF','PREPARE','OFFER','STATUS','COMMIT','CANCEL','CLOSE','SHUTDOWN'}

def label(value):
 if not isinstance(value,str) or not re.fullmatch('[A-Za-z0-9_-]{1,40}',value):raise ValueError('DEDICATED74_LABEL')
 return value

def properties(profile):
 out={}
 for line in safe(Path(profile)/'server.properties').read_text(encoding='utf-8-sig').splitlines():
  if not line.strip() or line.lstrip().startswith(('#','!')):continue
  if '=' not in line:raise ValueError('DEDICATED74_PROPERTIES')
  k,v=line.split('=',1);k=k.strip();v=v.strip()
  if k in out:raise ValueError('DEDICATED74_DUPLICATE_PROPERTY')
  out[k]=v
 level=out.get('level-name','world')
 if not re.fullmatch('[A-Za-z0-9_-]{1,80}',level) or level in {'mods','config','libraries'}:raise ValueError('DEDICATED74_WORLD_PATH')
 if out.get('online-mode','true') not in {'true','false'}:raise ValueError('DEDICATED74_AUTH_MODE')
 port=out.get('server-port','25565')
 if not port.isdigit() or not 1<=int(port)<=65535:raise ValueError('DEDICATED74_PORT')
 out.update({'level-name':level,'server-port':str(int(port)),'online-mode':out.get('online-mode','true'),'server-ip':out.get('server-ip','')})
 return out

def excludes(rel,level,code=False,raw=False):
 parts=rel.parts
 if not parts:return False
 if raw:return parts[-1]=='session.lock'
 if parts[0] in IGNORED or parts[-1] in {'session.lock','controller.lock'}:return True
 if parts[:3] in {('config','mared','control73'),('config','mared','publisher73')}:return True
 if code and (parts[0] in {level,level+'_nether',level+'_the_end','config'} or parts[0] in MUTABLE):return True
 return False

def tree(source,level,code=False,raw=False):
 source=safe(source);out={};count=total=nodes=0
 for folder,dirs,names in os.walk(source,followlinks=False):
  folder=safe(folder);rel=folder.relative_to(source)
  if len(rel.parts)>32:raise ValueError('DEDICATED74_DEPTH')
  keep=[]
  for name in dirs:
   path=safe(folder/name)
   if not excludes(path.relative_to(source),level,code,raw):keep.append(name)
  dirs[:]=keep;nodes+=len(names)+len(dirs)+1
  if nodes>400000:raise ValueError('DEDICATED74_COPY_LIMIT')
  for name in names:
   path=safe(folder/name);r=path.relative_to(source)
   if excludes(r,level,code,raw):continue
   if not path.is_file():raise ValueError('DEDICATED74_SPECIAL_FILE')
   count+=1;size=path.stat().st_size;total+=size
   if count>200000 or total>20*1024**3:raise ValueError('DEDICATED74_COPY_LIMIT')
   out[r.as_posix()]=sha(path,20*1024**3)
 return out

def digest(values):return hashlib.sha256(''.join(k+'='+v+'\n' for k,v in sorted(values.items())).encode('utf8')).hexdigest()

def copy_tree(source,target,level,code=False,checkpoint=lambda:None,raw=False):
 source=safe(source);target=safe(target)
 if target.exists() or source==target or source in target.parents or target in source.parents:raise ValueError('DEDICATED74_NEW_COPY')
 baseline=tree(source,level,code,raw);target.mkdir(parents=True)
 for rel,expected in sorted(baseline.items()):
  checkpoint();p=safe(source/rel);dest=safe(target/rel);dest.parent.mkdir(parents=True,exist_ok=True)
  with p.open('rb') as src,dest.open('xb') as dst:shutil.copyfileobj(src,dst,1024*1024)
  if sha(dest,20*1024**3)!=expected:raise ValueError('DEDICATED74_CHANGED_DURING_COPY')
 if tree(source,level,code,raw)!=baseline or tree(target,level,code,raw)!=baseline:raise ValueError('DEDICATED74_CHANGED_DURING_COPY')
 # Empty mods/config directories remain valid inputs (including a server with no extra managed mods).
 for directory in ('mods','config'):
  if (source/directory).is_dir() and (not code or directory!='config'):(target/directory).mkdir(exist_ok=True)
 return baseline

@contextlib.contextmanager
def stopped_world(profile,level):
 world=safe(Path(profile)/level)
 if not world.exists():yield;return
 lock=safe(world/'session.lock')
 if not lock.is_file():raise ValueError('DEDICATED74_EXISTING_WORLD_LOCK_REQUIRED')
 with lock.open('r+b') as stream:
  try:
   if os.name=='nt':
    import msvcrt
    msvcrt.locking(stream.fileno(),msvcrt.LK_NBLCK,1)
   else:
    import fcntl
    fcntl.lockf(stream,fcntl.LOCK_EX|fcntl.LOCK_NB,1)
  except OSError:raise ValueError('DEDICATED74_SOURCE_WORLD_BUSY') from None
  try:yield
  finally:
   stream.seek(0)
   if os.name=='nt':msvcrt.locking(stream.fileno(),msvcrt.LK_UNLCK,1)
   else:fcntl.lockf(stream,fcntl.LOCK_UN,1)

def java_call(project,java,clazz,*args,timeout=180):
 r=subprocess.run([str(java),'-cp',str(core_classpath(project)),clazz,*map(str,args)],capture_output=True,text=True,timeout=timeout,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
 if r.returncode:raise ValueError('DEDICATED74_CORE_REJECTED')
 return r.stdout.strip()

def core(project,java,*args,timeout=180):return json.loads(java_call(project,java,'com.fixmer.genesis.technology.server.remote.DedicatedTools74',*args,timeout=timeout))

def settings(root):
 root=safe(root);key=safe(root/'owner74.key').read_bytes()
 if len(key)!=32:raise ValueError('DEDICATED74_KEY')
 seal=read(root/'settings74',key)
 if set(seal)!={'schema','sha'} or seal['schema']!='74' or seal['sha']!=sha(root/'settings.json',65536):raise ValueError('DEDICATED74_SETTINGS_CHANGED')
 s=json_read(root/'settings.json');uuid.UUID(s['run'])
 g=s.get('initialGeneration',0)
 if type(g) is not int or not 0<=g<=32:raise ValueError('DEDICATED77_INITIAL_GENERATION')
 if s['schema']!=74 or s['level']!=properties(root/f"generations/server-{s.get('initialGeneration',0)}")['level-name']:raise ValueError('DEDICATED74_SETTINGS')
 return s,key

def initialize(project,source,root,java,publisher=None,recovering=False,source_locked=False,allow_first83=False):
 project=safe(project);source=safe(source);root=safe(root);java=safe(java)
 if root.exists() or source==root or source in root.parents or root in source.parents or project==root or project in root.parents or root in project.parents or not root.parent.is_dir():raise ValueError('DEDICATED74_NEW_DISJOINT_ROOT')
 version=subprocess.run([str(java),'-version'],capture_output=True,text=True,timeout=15)
 if version.returncode or not re.search(r'version "21(?:\.|\")',version.stdout+version.stderr):raise ValueError('DEDICATED74_JAVA21')
 p=properties(source);level=p['level-name']
 if any((source/(level+suffix)).exists() for suffix in ('_nether','_the_end')):raise ValueError('DEDICATED74_SPLIT_WORLD_UNSUPPORTED')
 if not re.search(r'(?m)^\s*eula\s*=\s*true\s*$',safe(source/'eula.txt').read_text(encoding='utf8')):raise ValueError('DEDICATED74_EULA')
 candidates=list((source/'libraries/net/neoforged/neoforge').glob('21.1.*/win_args.txt'))
 if len(candidates)!=1:raise ValueError('DEDICATED74_ONE_NEOFORGE_RUNTIME_REQUIRED')
 args=safe(candidates[0]);neo=args.parent.name
 if not re.fullmatch('21\\.1\\.[0-9]{1,6}',neo) or not (source/'user_jvm_args.txt').is_file():raise ValueError('DEDICATED74_RUNTIME')
 root.mkdir()
 if recovering:atomic(root/'import77.pending',b'77\n',new=True)
 java_call(project,java,'com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchArtifacts','private',root)
 json_write(root/'report.json',dict(schema=74,phase='INITIALIZING',sourceModified=False,root=str(root)))
 (root/'generations').mkdir();(root/'releases').mkdir();(root/'stages').mkdir()
 key=secrets.token_bytes(32);atomic(root/'owner74.key',key,new=True)
 # Recovery77 holds the same world lock across validation AND this copy. Windows locks are not reentrant.
 with contextlib.nullcontext() if source_locked else stopped_world(source,level):copy_tree(source,root/'generations/server-0',level)
 profile=root/'generations/server-0'
 # Offline tests are loopback-only; source properties remain unchanged.
 if p['online-mode']=='false' and p['server-ip'] not in {'127.0.0.1','localhost','::1'}:
  file=profile/'server.properties';file.write_text('\n'.join(line for line in file.read_text(encoding='utf8').splitlines() if not re.match(r'\s*server-ip\s*=',line))+'\nserver-ip=127.0.0.1\n',encoding='utf8');p['server-ip']='127.0.0.1'
 identity_source=safe(Path(publisher) if publisher is not None else source/'config/mared/publisher73')
 missing=[not (identity_source/name).exists() for name in ('public.der','private.der')]
 new_identity83=allow_first83 and publisher is None and all(missing)
 if new_identity83:
  java_call(project,java,'com.fixmer.genesis.technology.server.remote.Publication83','init',root/'publisher73')
 else:
  for name in ('public.der','private.der'):
   src=safe(identity_source/name)
   if not src.is_file() or not 1<=src.stat().st_size<=256:raise ValueError('DEDICATED74_SETUP_PUBLISHER73_FIRST')
   (root/'publisher73').mkdir(exist_ok=True);atomic(root/'publisher73'/name,src.read_bytes(),new=True)
  if (identity_source/'identity83').exists():
   if not 1<=(identity_source/'identity83').stat().st_size<=16384:raise ValueError('PUBLICATION83_IDENTITY')
   atomic(root/'publisher73/identity83',safe(identity_source/'identity83').read_bytes(),new=True)
   java_call(project,java,'com.fixmer.genesis.technology.server.remote.Publication83','identity',root/'publisher73')
 # Preserve the signed consumed-number ledger across clean owner recovery.
 counter=safe((Path(publisher) if publisher is not None else source/'config/mared/publisher73')/'counter79')
 if counter.exists():
  if not counter.is_file() or not 1<=counter.stat().st_size<=16384:raise ValueError('PUBLICATION79_COUNTER')
  atomic(root/'publisher73/counter79',counter.read_bytes(),new=True)
 files(profile/'mods') # Apply the existing name/count/size/nested-folder bounds before inspecting bootstrap.
 boot=[]
 for jar in (profile/'mods').glob('*.jar'):
  with zipfile.ZipFile(jar) as z:
   if 'com/fixmer/mared/Mared.class' in z.namelist():
    if 'com/fixmer/mared/technology/server/firstlaunch/DedicatedServer74.class' not in z.namelist():raise ValueError('DEDICATED74_BOOTSTRAP_VERSION')
    boot.append(jar.name)
 if len(boot)!=1:raise ValueError('DEDICATED74_BOOTSTRAP')
 argv=[str(java),'@user_jvm_args.txt','@'+args.relative_to(source).as_posix(),'nogui'];validate_argv(argv,profile)
 initial=1 if new_identity83 else 0
 if initial==1:profile.rename(root/'generations/server-1');profile=root/'generations/server-1'
 s=dict(schema=74,initialGeneration=initial,run=str(uuid.uuid4()),source=str(source),java=str(java),argv=argv,minecraft='1.21.1',neoforge=neo,auth=p['online-mode'],bind=p['server-ip'],port=p['server-port'],level=level,initialInventory=inventory(profile))
 json_write(root/'settings.json',s,new=True);write(root/'settings74',key,{'schema':'74','sha':sha(root/'settings.json',65536)})
 session(root,s,key,initial,s['initialInventory']);json_write(root/'report.json',dict(schema=74,phase='INITIALIZED',sourceModified=False,root=str(root)))
 return root

def validate_argv(argv,profile):
 forbidden=r'(?:^|[\s\"])(?:-Duser\.dir=|-Dfml\.modFolders=|-javaagent:|-agentlib:|-agentpath:|--universe(?:[=\s]|$)|--world(?:[=\s]|$)|--gameDir(?:[=\s]|$))'
 for arg in argv[1:]:
  text=arg
  if arg.startswith('@'):
   raw=arg[1:]
   if Path(raw).is_absolute() or '..' in Path(raw).parts:raise ValueError('DEDICATED74_ARGFILE_PATH')
   path=safe(profile/raw)
   if path.stat().st_size>1024*1024:raise ValueError('DEDICATED74_ARGFILE_SIZE')
   text='\n'.join(line for line in path.read_text(encoding='utf-8-sig').splitlines() if not line.lstrip().startswith('#'))
   if re.search(forbidden,text):raise ValueError('DEDICATED74_ARGV_REDIRECT')
   if re.search(r'[A-Za-z]:[\\/]|\\\\',text):raise ValueError('DEDICATED74_EXTERNAL_RUNTIME_PATH')
   if re.search(r'(?m)(?:^|\s)@',text):raise ValueError('DEDICATED74_NESTED_ARGFILE')
  if re.search(forbidden,text):raise ValueError('DEDICATED74_ARGV_REDIRECT')
 return argv

def session(root,s,key,generation,expected):
 c=dict(schema='74',run=s['run'],generation=str(generation),inventory=expected,minecraft=s['minecraft'],neoforge=s['neoforge'],auth=s['auth'],bind=s['bind'],port=s['port']);write(root/f'server-{generation}.session',key,c);return c

def submit(root,action,name='r1'):
 root=safe(root);s,key=settings(root);action=action.upper()
 if action not in ACTIONS|{'STAGE'}:raise ValueError('DEDICATED74_ACTION')
 if (root/'request74').exists():
  previous=read(root/'request74',key);ack=read(root/'request74.ack',key) if (root/'request74.ack').exists() else {}
  if ack.get('request')!=previous.get('request') or ack.get('run')!=s['run']:raise ValueError('DEDICATED74_REQUEST_PENDING')
 d=dict(schema='74',run=s['run'],request=str(uuid.uuid4()),action=action,label=label(name));write(root/'request74',key,d);return d['request']

class Owner74:
 def __init__(self,project,root,job_factory=WindowsJob):
  self.project=safe(project);self.root=safe(root);self.s,self.key=settings(root);self.job_factory=job_factory;self.jobs={};self.seq={};self.current=0;self.pending=None;self.external=False;self.closed=False;self.last_request=None;self.seen=set();self.stop_requested=False;self.phase='INITIALIZED';self.reason='';self.pump_thread=None;self.pump_stop=threading.Event();self.pump_error=None;self.admit=False;self.ready_generation1=False;self.last_pump=None;self.failure_code='';self.failure_stage=''
 def tool(self,*args):return core(self.project,self.s['java'],*args,timeout=15 if args[0] in {'inspect','pulse','admit','close'} else 180)
 def report(self,phase=None,reason=''):
  if phase:self.phase=phase
  self.reason=reason;value=dict(schema=74,phase=self.phase,reason=reason,sourceModified=False,serverGeneration=self.current,serverReady=self.ready_generation1,root=str(self.root),failureCode=self.failure_code,failureStage=self.failure_stage)
  if self.last_pump:value['control']=self.last_pump
  if self.pending:value['release']=self.pending['info']['release'];value['delta']=self.pending['approved']['delta']
  json_write(self.root/'report.json',value)
 def status(self,g):
  p=self.root/f'server-{g}.status'
  if not p.exists():return None
  d=read(p,self.key);c=read(self.root/f'server-{g}.session',self.key)
  if set(d)!={'schema','run','generation','inventory','sequence','request','phase','round','at'} or any(d[k]!=c[k] for k in ('schema','run','generation','inventory')) or d['phase'] not in {'READY','QUIESCED','SAVED','STOPPED_SAVED','STOPPED','ERROR','COMMAND_ACCEPTED'}:raise ValueError('DEDICATED74_STATUS_BINDING')
  if d['phase']=='ERROR':raise ValueError('DEDICATED74_RUNTIME_REJECTED')
  return d
 def health(self,check_control=True):
  if check_control and self.pump_error:raise ValueError('DEDICATED74_CONTROL_PUBLISHER_FAILED')
  for job in self.jobs.values():
   s=job.status()
   if s['logFailed'] or s['rootExited'] and s['exitCode']!=0:raise ValueError('DEDICATED74_CHILD_FAILURE')
 def wait(self,predicate,seconds,code,check_control=True):
  end=time.monotonic()+seconds
  while time.monotonic()<end:
   self.health(check_control);v=predicate()
   if v:return v
   time.sleep(.1)
  raise ValueError(code)
 def command(self,g,action,round_id='none',name='r1'):
  c=read(self.root/f'server-{g}.session',self.key);self.seq[g]=self.seq.get(g,0)+1;d={k:c[k] for k in ('schema','run','generation','inventory')};d.update(sequence=str(self.seq[g]),request=str(uuid.uuid4()),action=action,round=round_id,label=label(name));write(self.root/f'server-{g}.command',self.key,d);return d
 def start(self,g):
  if self.jobs:raise ValueError('DEDICATED74_OLD_JOB_STILL_HELD')
  profile=self.root/f'generations/server-{g}';c=read(self.root/f'server-{g}.session',self.key)
  if inventory(profile)!=c['inventory']:raise ValueError('DEDICATED74_GENERATION_CHANGED')
  validate_argv(self.s['argv'],profile);p=properties(profile)
  if any(p[k]!=self.s[v] for k,v in [('level-name','level'),('online-mode','auth'),('server-ip','bind'),('server-port','port')]):raise ValueError('DEDICATED74_SERVER_MODE_CHANGED')
  env=environment(self.s['java'],profile);env.update(GENESIS_DEDICATED74_ROOT=str(self.root),GENESIS_DEDICATED74_RUN=self.s['run'],GENESIS_DEDICATED74_GENERATION=str(g))
  self.jobs[g]=self.job_factory(self.s['argv'],profile,env,self.root/f'server-{g}.log');self.current=g
  def ready():
   if self.jobs[g].status()['rootExited']:raise ValueError('DEDICATED74_EXIT_BEFORE_READY')
   d=self.status(g);return d and d['phase']=='READY' and d['sequence']=='0'
  self.wait(ready,180,'DEDICATED74_RUNTIME_TIMEOUT')
  if g==1:self.ready_generation1=True
 def receive(self):
  p=self.root/'request74'
  if not p.exists():return None
  if p.stat().st_size>16384:raise ValueError('DEDICATED74_REQUEST_SIZE')
  raw=p.read_bytes()
  if raw==self.last_request:return None
  self.last_request=raw;d=read(p,self.key)
  if set(d)!={'schema','run','request','action','label'} or d['schema']!='74' or d['run']!=self.s['run'] or d['action'] not in ACTIONS|{'STAGE'}:raise ValueError('DEDICATED74_REQUEST')
  if str(uuid.UUID(d['request']))!=d['request'] or d['request'] in self.seen or len(self.seen)>=128:raise ValueError('DEDICATED74_REPLAY')
  label(d['label']);self.seen.add(d['request']);write(self.root/'request74.ack',self.key,{'schema':'74','run':self.s['run'],'request':d['request']});return d
 def checkpoint(self):
  self.health()
  if self.jobs[0].status()['rootExited']:raise ValueError('DEDICATED74_SOURCE_EXIT')
 def verify_stage(self):
  if not self.pending:raise ValueError('DEDICATED74_NO_STAGE')
  p=self.pending;source=self.root/'generations/server-0'
  if inventory(source)!=self.s['initialInventory'] or digest(tree(source,self.s['level'],True))!=p['sourceSeal'] or digest(tree(p['path'],self.s['level'],True))!=p['targetSeal']:raise ValueError('DEDICATED74_STAGE_CHANGED')
  if self.tool('verify',p['release'],self.root/'publisher73/public.der')!=p['info']:raise ValueError('DEDICATED74_RELEASE_CHANGED')
  props=properties(source)
  if any(props[k]!=self.s[v] for k,v in [('level-name','level'),('online-mode','auth'),('server-ip','bind'),('server-port','port')]):raise ValueError('DEDICATED74_SERVER_MODE_CHANGED')
 def prepare(self,name):
  if self.current!=0 or self.external:raise ValueError('DEDICATED74_ONE_TRANSITION')
  self.checkpoint()
  if (self.root/'control73/active73').exists() and self.tool('inspect',self.root)['phase'] not in {'CANCELLED','CLOSED'}:raise ValueError('DEDICATED74_ACTIVE_ROUND')
  release=self.root/'releases'/label(name);info=self.tool('verify',release,self.root/'publisher73/public.der')
  if info['minecraft']!=self.s['minecraft'] or info['neoforge']!=self.s['neoforge']:raise ValueError('DEDICATED74_LOADER_CHANGE')
  # Stable gameplay address and exact server port; offline offer must remain loopback.
  from urllib.parse import urlsplit
  address=info['game'];port=int(address.rsplit(':',1)[1]);host=address.rsplit(':',1)[0]
  if port!=int(self.s['port']) or self.s['auth']=='false' and host not in {'localhost','127.0.0.1','[::1]'}:raise ValueError('DEDICATED74_ADDRESS')
  source=self.root/'generations/server-0';path=self.root/'stages'/(uuid.uuid4().hex+'.building');self.report('STAGING_CODE')
  baseline=copy_tree(source,path,self.s['level'],True,self.checkpoint);approved=policy(path/'mods',release/'server.zip',info);install(path,release/'server.zip',info,approved)
  # Only the unpublished staging copy; original generation0 is preserved separately.
  shutil.rmtree(path/'mods.before70');source_seal=digest(baseline);target_seal=digest(tree(path,self.s['level'],True))
  self.pending=dict(info=info,release=release,path=path,approved=approved,sourceSeal=source_seal,targetSeal=target_seal,label=name)
  self.verify_stage();proof=dict(schema='74',run=self.s['run'],generation='0',oldInventory=self.s['initialInventory'],newInventory=inventory(path),release=info['release'],client=info['client'],server=info['server'],label=name,codeSeal=target_seal);write(self.root/'prepared74',self.key,proof)
  destination=source/'config/mared/remote-updates'/name
  if destination.exists():
   if tree(destination,'__no_world__')!=tree(release/'public','__no_world__'):raise ValueError('DEDICATED74_PUBLIC_EXPORT_DIFFERS')
  else:copy_tree(release/'public',destination,'__no_world__')
  self.command(0,'PREPARE',name=name);self.report('PREPARED')
 def stop(self,g,action='SHUTDOWN',round_id='none'):
  job=self.jobs.get(g)
  if not job:return
  if job.status()['rootExited']:raise ValueError('DEDICATED74_UNCONFIRMED_EXIT')
  cmd=self.command(g,action,round_id)
  def saved():
   d=self.status(g)
   return d and d['phase']=='STOPPED_SAVED' and all(d[k]==cmd[k] for k in ('sequence','request','round','inventory')) and job.empty()
  self.wait(saved,120,'DEDICATED74_SAVE_STOP_UNCONFIRMED',False);self.confirmed_stop(g);return cmd
 def confirmed_stop(self,g):
  pass # Optional sealed checkpoint, only after a matching stop receipt and retained empty Job.
 def start_pump(self,round_id):
  self.external=True;self.pump_round=round_id
  def loop():
   try:
    while not self.pump_stop.is_set():
     state=self.tool('admit' if self.admit else 'pulse',self.root)
     if state['round']!=round_id or not self.pending or state['release']!=self.pending['info']['release'] or state['client']!=self.pending['info']['client'] or state['phase']!='COMMITTED':raise ValueError('DEDICATED74_CONTROL_BINDING')
     self.last_pump=state;self.pump_stop.wait(1)
   except Exception:self.pump_error=True
  self.pump_thread=threading.Thread(target=loop,name='genesis-control74-owner',daemon=True);self.pump_thread.start()
 def copy_saved(self):
  if not self.pending or 0 not in self.jobs or not self.jobs[0].empty():raise ValueError('DEDICATED74_WORLD_OWNER_ACTIVE')
  # Native empty + matching final stop receipt are checked again at the destructive boundary.
  d=self.status(0)
  if not d or not (self.root/'server-0.command').is_file():raise ValueError('DEDICATED74_SAVE_RECEIPT')
  cmd=read(self.root/'server-0.command',self.key)
  if not d or d['phase']!='STOPPED_SAVED' or cmd['action']!='SAVE_STOP' or any(d[k]!=cmd[k] for k in ('sequence','request','round','inventory')):raise ValueError('DEDICATED74_SAVE_RECEIPT')
  self.verify_stage();source=self.root/'generations/server-0';target=self.pending['path'];level=self.s['level']
  if (target/level).exists():raise ValueError('DEDICATED74_WORLD_PREEXISTED')
  if not (source/level).is_dir():raise ValueError('DEDICATED74_WORLD_MISSING')
  copy_tree(source/level,target/level,'__world_data__',checkpoint=self.health,raw=True)
  if (target/'config').exists():raise ValueError('DEDICATED74_CONFIG_PREEXISTED')
  if (source/'config').is_dir():copy_tree(source/'config',target/'config','__config__',checkpoint=self.health,raw=True)
  for name in MUTABLE:
   src=safe(source/name)
   if src.exists():
    if not src.is_file():raise ValueError('DEDICATED74_MUTABLE_FILE')
    shutil.copyfile(src,target/name)
  if inventory(target)!=read(self.root/'prepared74',self.key)['newInventory']:raise ValueError('DEDICATED74_NEW_INVENTORY')
  final=self.root/'generations/server-1'
  if final.exists():raise ValueError('DEDICATED74_GENERATION_EXISTS')
  session(self.root,self.s,self.key,1,inventory(target));target.rename(final);json_write(self.root/'saved-world74.json',dict(schema=74,run=self.s['run'],round=d['round'],source=str(source),destination=str(final),savedAfterNormalEmptyJob=True))
 def wait_all_installed(self):
  # Keep the old server alive until every committed original participant reports runtime INSTALLED.
  # The 25% transfer barrier still also requires the new server READY; no early world writer.
  while True:
   self.health();progress=self.last_pump
   if progress and int(progress['total'])>0 and int(progress['installed'])==int(progress['total']):return
   if self.jobs[0].status()['rootExited']:raise ValueError('DEDICATED74_SOURCE_EXIT')
   request=self.receive()
   if request and request['action']=='SHUTDOWN':self.stop_requested=True;raise InterruptedError('DEDICATED74_STOP_REQUESTED')
   self.report('WAITING_ALL_INSTALLED');time.sleep(.5)
 def activate(self,state):
  self.verify_stage()
  if state['phase']!='COMMITTED' or state['release']!=self.pending['info']['release'] or state['client']!=self.pending['info']['client'] or state['admission']:raise ValueError('DEDICATED74_COMMIT_BINDING')
  self.report('QUIESCING_CONTROL');cmd=self.command(0,'QUIESCE',state['round'])
  def quiesced():
   d=self.status(0);return d and d['phase']=='QUIESCED' and all(d[k]==cmd[k] for k in ('sequence','request','round'))
  self.wait(quiesced,30,'DEDICATED74_QUIESCE_TIMEOUT');self.start_pump(state['round']);self.report('WAITING_ALL_INSTALLED')
  self.wait_all_installed()
  self.report('SAVING_OLD_SERVER');self.stop(0,'SAVE_STOP',state['round'])
  self.report('COPYING_SAVED_WORLD');self.copy_saved();self.jobs[0].close();del self.jobs[0]
  self.report('STARTING_NEW_SERVER');self.start(1);self.report('WAITING_THRESHOLD')
 def advance_admission(self):
  if not self.ready_generation1 or self.current!=1 or 1 not in self.jobs:return False
  self.health();native=self.jobs[1].status();receipt=self.status(1);state=self.last_pump
  if native['rootExited'] or not receipt or receipt['phase']!='READY':self.admit=False;return False
  if state and state['phase']=='COMMITTED' and int(state['installed'])>=int(state['needed']):self.admit=True
  return self.admit
 def run(self):
  try:
   if os.name!='nt':raise ValueError('DEDICATED74_WINDOWS_X64_ONLY')
   atomic(self.root/'started74',self.s['run'].encode(),new=True);native_probe(self.project,self.s['java'],self.root);self.report('STARTING_BASELINE');self.start(0);self.report('ACTIVE_BASELINE')
   while not self.stop_requested:
    self.health();job=self.jobs[self.current]
    if job.status()['rootExited']:raise ValueError('DEDICATED74_UNREQUESTED_EXIT')
    request=self.receive()
    if request:
     if request['action']=='SHUTDOWN':self.stop_requested=True;break
     if request['action']=='STAGE':
      previous=self.pending;proof=self.root/'prepared74';previous_proof=proof.read_bytes() if proof.exists() else None
      try:self.prepare(request['label'])
      except Exception as error:
       self.failure_stage=self.phase;self.failure_code=error.args[0] if len(error.args)==1 and isinstance(error.args[0],str) and re.fullmatch('[A-Z0-9_]{1,100}',error.args[0]) else 'DEDICATED74_PREPARATION_FAILED'
       self.pending=previous
       if previous_proof is not None:atomic(proof,previous_proof)
       else:proof.unlink(missing_ok=True)
       self.report('PREPARATION_FAILED','Подготовка не прошла; текущий сервер остаётся открыт. Проверь выпуск/пути/состав.')
     else:
      if self.external:self.report(reason='После перехода доступен status/stop владельца; новый цикл требует следующего этапа.')
      else:self.command(0,request['action'],name=request['label'])
    if self.pending and not self.external and (self.root/'control73/active73').exists():
     state=self.tool('inspect',self.root);self.last_pump=state;self.report()
     if state['phase']=='COMMITTED':self.activate(state)
    if self.ready_generation1:
     state=self.last_pump
     self.advance_admission()
     if state and state['admission']:self.report('ACTIVE_UPDATED')
    time.sleep(.5)
  except KeyboardInterrupt:self.stop_requested=True
  except InterruptedError:self.stop_requested=True
  except Exception as error:
   self.failure_stage=self.phase;self.failure_code=error.args[0] if len(error.args)==1 and isinstance(error.args[0],str) and re.fullmatch('[A-Z0-9_]{1,100}',error.args[0]) else 'DEDICATED74_FAILED'
   self.report('FAILED','Переход остановлен. Поколения и мир сохранены; автоматического отката нет. Проверь журналы.')
   raise
  finally:self.cleanup()
 def cleanup(self):
  self.admit=False
  for g,job in list(self.jobs.items()):
   try:
    if not job.status()['rootExited']:self.stop(g)
    else:
     d=self.status(g);cmd=read(self.root/f'server-{g}.command',self.key) if (self.root/f'server-{g}.command').exists() else {}
     if not job.empty() or not d or d['phase']!='STOPPED_SAVED' or cmd.get('action') not in {'SAVE_STOP','SHUTDOWN'} or any(d.get(k)!=cmd.get(k) for k in ('sequence','request','round','inventory')):raise ValueError('DEDICATED74_UNCONFIRMED_EXIT')
     self.confirmed_stop(g)
   except Exception:self.report('STOP_NOT_CONFIRMED','Штатное сохранение не подтверждено. Закрывается только собственная Job; несохранённые изменения могли потеряться.')
   finally:job.close();self.jobs.pop(g,None)
  self.pump_stop.set()
  if self.pump_thread:self.pump_thread.join(timeout=35)
  if self.external and (self.pump_thread is None or not self.pump_thread.is_alive()):
   try:self.tool('close',self.root)
   except Exception:pass
  if self.stop_requested and self.phase!='STOP_NOT_CONFIRMED':self.report('STOPPED_SAVED')
