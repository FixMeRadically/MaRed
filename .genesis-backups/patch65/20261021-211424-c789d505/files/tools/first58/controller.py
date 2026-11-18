"""One owner, two isolated generations, real game acknowledgements. Windows pilot only."""
from pathlib import Path
import contextlib,hashlib,json,os,re,secrets,socket,subprocess,threading,time,uuid
from .common import *
from .windows_job import WindowsJob
from .runtime import core_classpath
from .transfer import PackageServer,download
from .diagnostics63 import Diagnostics

class Pilot:
 def __init__(self,project,root,notify=lambda message:None,cancel=lambda:False,ask=lambda message:False):
  self.project=safe(project);self.root=safe(root);self.notify=notify;self.cancel=cancel;self.ask=ask;self.jobs={};self.seq={};self.attempt=uuid.uuid4().hex
  self.diagnostics=Diagnostics(self.root,self.attempt);self.diagnostics.step('load_state')
  try:
   self.spec=json_read(self.root/'launch.json',512*1024);self.artifacts=json_read(self.root/'artifacts.json',512*1024);self.key=(self.root/'channel.key').read_bytes();self.config=verify(self.key,(self.root/'session.v58').read_bytes());self.job_factory=WindowsJob
  except Exception as failure:raise self.diagnostics.failure(failure) from None
 def java(self,*args):
  self.diagnostics.step('package_verify')
  java=self.spec['serverCommand'][0];classes=core_classpath(self.project)
  result=subprocess.run([java,'-cp',str(classes),'com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchArtifacts',*map(str,args)],capture_output=True,text=True,timeout=120)
  if result.returncode:raise ValueError('Package/permission verification failed; no game profile was installed')
  return json.loads(result.stdout)
 def report(self,phase,**extra):
  self.diagnostics.step('run',phase=phase)
  json_write(self.root/'report.json',{'schema':58,'scope':'ISOLATED_ONE_PC_PILOT','run':self.config['run'],'attempt':self.attempt,'phase':phase,'remoteUpdater':False,'liveInstallAuthority':False,**extra})
  self.notify(phase)
 def command(self,role,generation,action):
  name=(role,generation);self.seq[name]=self.seq.get(name,0)+1;nonce=str(uuid.uuid4());m=record(self.config,role,generation,action,self.seq[name],nonce);atomic(self.root/f'{role}.{generation}.command',sign(self.key,m));return m
 def status(self,role,generation):
  self.diagnostics.step('runtime_status',role,generation)
  m=read_record(self.root,self.key,f'{role}.{generation}.status',self.config,role,generation)
  if m and (m['inventory']!=self.config[role+str(generation)] or m['phase']=='ERROR'):raise ValueError('Game runtime rejected expected generation or inventory')
  return m
 def wait(self,condition,seconds,message,allow_cancel=True):
  end=time.monotonic()+seconds
  while time.monotonic()<end:
   if allow_cancel and self.cancel():raise InterruptedError('Pilot cancelled')
   self.check_jobs()
   result=condition()
   if result:return result
   time.sleep(.1)
  raise TimeoutError(message)
 def check_jobs(self):
  for (role,generation),job in self.jobs.items():
   state=job.status();self.diagnostics.process(role,generation,state)
   if state['logFailed'] or state['rootExited'] and state['exitCode']!=0:
    self.diagnostics.step('job_health',role,generation)
    raise ValueError('Managed game exited with an error; no update activation')
 def argv(self,role,generation):
  self.diagnostics.step('validate_args',role,generation)
  profile=self.root/'generations'/f'{role}-{generation}';argv=[arg.replace('{profile}',str(profile)) for arg in self.spec[role+'Command']]
  forbidden=r'(?:^|[\s\"])(?:-Duser\.dir=|-Dfml\.modFolders=|-javaagent:|-agentlib:|-agentpath:|--universe(?:[=\s]|$)|--world(?:[=\s]|$))'
  for arg in argv[1:]:
   text=arg
   if arg.startswith('@') and not arg.startswith('@@'):
    path=safe(profile/arg[1:]);size=path.stat().st_size
    if size>1024*1024:raise ValueError('Launch argfile size limit')
    text=path.read_text(encoding='utf-8-sig')
    if '--gameDir' in text:raise ValueError('Hidden gameDir in argfile is not supported')
   if re.search(forbidden,text):raise ValueError('Launch command redirects profile or loads an external agent')
  return argv
 def start(self,role,generation):
  self.diagnostics.step('verify_generation',role,generation)
  profile=self.root/'generations'/f'{role}-{generation}'
  if inventory(profile)!=self.config[role+str(generation)]:raise ValueError('Generation changed before launch')
  marker=verify(self.key,(profile/'first58.marker').read_bytes())
  if marker!=record(self.config,role,generation,'SEALED'):raise ValueError('Generation seal mismatch')
  env={k:v for k,v in os.environ.items() if k.upper() in {'SYSTEMROOT','WINDIR','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA','COMSPEC'}}
  java=Path(self.spec[role+'Command'][0]);temp=profile/'first58-temp';temp.mkdir(exist_ok=True)
  if role=='client' and self.spec.get('devSnapshot'):
   self.diagnostics.step('dev_snapshot',role,generation)
   from .launch62 import runtime
   env.update(runtime(profile,self.spec))
  env.update({'JAVA_HOME':str(java.parent.parent),'PATH':str(java.parent)+';'+str(Path(os.environ.get('SystemRoot','C:\\Windows'))/'System32'),'TEMP':str(temp),'TMP':str(temp),'GENESIS_FIRST58_ROOT':str(self.root),'GENESIS_FIRST58_ROLE':role,'GENESIS_FIRST58_GENERATION':str(generation),'GENESIS_FIRST58_RUN':self.config['run']})
  argv=self.argv(role,generation)
  self.diagnostics.step('prepare_command',role,generation)
  if role=='client':
   from .launch62 import wrap_long
   argv=wrap_long(argv,profile)
  self.diagnostics.step('spawn',role,generation)
  self.jobs[role,generation]=self.job_factory(argv,profile,env,self.root/f'{role}-{generation}-{self.attempt}.log')
 def ready(self,role,generation):
  self.diagnostics.step('await_ready',role,generation)
  def observed():
   if self.jobs[role,generation].status()['rootExited']:raise RuntimeError('Game exited before runtime acknowledgement')
   m=self.status(role,generation);return m and m['phase']=='READY'
  self.wait(observed,120,'Game did not confirm loaded jars and versions')
 def join(self,generation):
  self.diagnostics.step('join','client',generation)
  deadline=time.monotonic()+30;command=self.command('client',generation,'JOIN');failures=0;next_try=0
  while True:
   self.check_jobs()
   if self.jobs['client',generation].status()['rootExited']:raise InterruptedError('Client closed during connection')
   if self.cancel():
    self.command('client',generation,'CANCEL');return False
   m=self.status('client',generation)
   if m and m['sequence']==command['sequence'] and m['request']==command['request']:
    if m['phase']=='WORLD_FRAME_READY':return True
    if m['phase']=='JOIN_FAILED' and time.monotonic()>=next_try:
     failures+=1;next_try=time.monotonic()+min(8,2**min(failures,3));command=self.command('client',generation,'JOIN')
   if time.monotonic()>=deadline:
    if not self.ask('Подключение занимает больше 30 секунд. Продолжать попытки?'):
     self.command('client',generation,'CANCEL');return False
    deadline=time.monotonic()+30
   time.sleep(.1)
 def stop(self,role,generation):
  self.diagnostics.step('stop',role,generation)
  job=self.jobs.get((role,generation))
  if not job:return
  if job.empty():raise ValueError('Game exited before requested checkpoint; update outcome not confirmed')
  command=self.command(role,generation,'STOP');expected='STOPPED_SAVED' if role=='server' else 'STOP_REQUESTED'
  def complete():
   m=self.status(role,generation)
   return bool(m and m['phase']==expected and m['sequence']==command['sequence'] and m['request']==command['request'] and job.empty())
  self.wait(complete,90,'World save/process-tree stop was not confirmed',allow_cancel=False);job.close();del self.jobs[role,generation]
 def next_generation(self,archives):
  if self.cancel():raise InterruptedError('Cancelled before generation preparation')
  self.report('PREPARING_GENERATION')
  for role in ('server','client'):
   if self.cancel():raise InterruptedError('Cancelled before generation publication')
   temporary=self.root/'generations'/f'{role}-1.building';target=temporary.with_name(f'{role}-1')
   clone(self.root/'generations'/f'{role}-0',temporary)
   install_new_generation(temporary,archives[role],self.artifacts[role+'-0'],self.artifacts[role+'-1'])
   atomic(temporary/'first58.marker',sign(self.key,record(self.config,role,1,'SEALED')),new=True);temporary.rename(target)
  json_write(self.root/'prepared-generation.json',{'run':self.config['run'],'generation':1,'server':self.config['server1'],'client':self.config['client1']},new=True)
 def watch(self,generation):
  self.notify('Игра запущена. Закрой клиент обычным способом, чтобы завершить стенд.')
  while not self.jobs['client',generation].status()['rootExited']:
   self.check_jobs()
   if self.cancel():self.stop('client',generation);break
   time.sleep(.25)
  client=self.jobs.pop(('client',generation),None)
  if client:
   self.wait(lambda:client.empty(),15,'Client descendants did not exit',False);client.close()
  self.stop('server',generation)
 def run(self):
  primary=None
  try:
   if os.name!='nt':raise ValueError('Actual first launch requires Windows x64')
   if (self.root/'started.v58').exists():raise ValueError('This pilot root has already been run; do not replay an unknown update')
   atomic(self.root/'started.v58',self.attempt.encode(),new=True)
   self.report('STARTING_BASELINE');self.argv('server',0);self.argv('client',0);self.start('server',0);self.ready('server',0);self.start('client',0);self.ready('client',0)
   if not self.join(0):self.watch(0);self.report('CANCELLED');return
   self.report('AWAITING_CONSENT')
   def choice():
    if self.jobs['client',0].status()['rootExited']:raise InterruptedError('Client closed before consent')
    m=read_record(self.root,self.key,'consent.status',self.config,'server',0)
    if m and m['inventory']!=self.config['serverPackage']:raise ValueError('Consent package mismatch')
    return m
   decision=self.wait(choice,45,'No authenticated player consent was recorded')
   if decision['phase']!='ACCEPTED':self.report('DECLINED_OR_EXPIRED');self.watch(0);return
   self.report('DOWNLOADING')
   downloaded={}
   with PackageServer([self.root/'packages/server-1.zip',self.root/'packages/client-1.zip']) as server:
    for role in ('server','client'):
     expected=self.artifacts[role+'-1'];source=self.root/'packages'/f'{role}-1.zip'
     p=download(server,expected['archiveSha256'],source.stat().st_size,self.root/'downloads',lambda current,total:self.notify(f'Загрузка {role}: {current*100//total}%'),self.cancel)
     current=self.java('verify',p,self.root/'packages/public.der',self.artifacts['distribution'],role.upper(),self.config['minecraft'],self.config['neoforge'],'1')
     if current!=expected:raise ValueError('Downloaded signed inventory changed')
     downloaded[role]=p
   if self.cancel():raise InterruptedError('Cancelled before shutdown')
   self.report('COVER')
   # Client leaves first, so its final server-side player state is part of the server save.
   self.stop('client',0);self.report('SAVING_SERVER');self.stop('server',0)
   self.next_generation(downloaded)
   if self.cancel():raise InterruptedError('Cancelled before updated launch')
   self.report('STARTING_UPDATED_SERVER');self.start('server',1);self.ready('server',1)
   self.report('STARTING_UPDATED_CLIENT');self.start('client',1);self.ready('client',1)
   if not self.join(1):self.report('RECONNECT_CANCELLED');self.watch(1);return
   self.report('WORLD_READY',actualGameAcknowledged=True,actualFrameAcknowledged=True);self.command('client',1,'FOCUS');self.watch(1);self.report('COMPLETE',actualGameAcknowledged=True,actualFrameAcknowledged=True)
  except Exception as failure:
   primary=self.diagnostics.failure(failure,self.config)
   raise primary from None
  finally:
   # This owner's Job handles are never adopted on a later run. Abrupt owner death also
   # closes them in the kernel. On failure, original templates and saved gen0 stay intact.
   try:self.cleanup()
   except Exception as failure:
    if primary is None:raise self.diagnostics.failure(failure,self.config) from None
 def cleanup(self):
  # Remove each closed handle before waiting for another process. A normal early
  # client exit must not make check_jobs() abort the server's graceful world save.
  failures=[]
  for name,job in sorted(list(self.jobs.items())):
   try:
    if not job.empty():
     try:self.stop(*name)
     except Exception:pass
   except Exception as error:failures.append(error)
   finally:
    try:job.close()
    except Exception as error:failures.append(error)
    finally:self.jobs.pop(name,None)
  if failures:raise failures[0]
