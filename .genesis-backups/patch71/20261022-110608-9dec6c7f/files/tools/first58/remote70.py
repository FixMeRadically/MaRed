"""External one-client remote updater. Source is read-only; only new children belong to its Job.
No server control, unknown PID kill, LIVE install, world copy or automatic rollback.
"""
from pathlib import Path
import json,os,re,secrets,shutil,subprocess,time,uuid
from .common import safe,atomic,json_read,json_write,inventory,sha,clone
from .bridge67 import read as read_signed,write as write_signed
from .capture import read_client
from .session61 import consume,for_mode
from .wizard_model import client_details
from .runtime import core_classpath
from .desktop66 import live_client_snapshot
from .launch62 import snapshot,runtime,wrap_long
from .native70 import SourceLease70,native_probe,environment
from .windows_job import WindowsJob
from .install70 import policy,install,files
from .diagnostics63 import failure_data,PilotFailure

PHASES={'VALIDATING','PREPARING','READY','ARMING','ARMED','STARTING','CONNECTING','ACTIVE','COMPLETE','CANCELLED','FAILED'}
FIELDS={'schema','request','pid','birth','profile','account','neoforge','cache','candidate','authMode'}

def request(folder):
 r=json_read(folder/'request70.json',16384)
 if set(r)!=FIELDS or r['schema']!=70 or str(uuid.UUID(r['request']))!=r['request'] or type(r['pid']) is not int or not 0<r['pid']<=0xffffffff or type(r['birth']) is not int or r['birth']<=0:raise ValueError('REMOTE70_REQUEST')
 if r['authMode'] not in {'ONLINE','LOCAL_OFFLINE'} or not re.fullmatch(r'21\.1\.[0-9]{1,6}',r['neoforge']):raise ValueError('REMOTE70_REQUEST')
 for name in ('profile','cache','candidate'):
  if not isinstance(r[name],str) or len(r[name])>4096 or not Path(r[name]).is_absolute():raise ValueError('REMOTE70_REQUEST')
 uuid.UUID(r['account']);return r

def action(folder,request):
 p=folder/'command70.json'
 if not p.exists():return None
 d=json_read(p,4096)
 if set(d)!={'schema','request','action'} or d['schema']!=70 or d['request']!=request or d['action'] not in {'ARM','COMMIT','CANCEL'}:raise ValueError('REMOTE70_COMMAND')
 return d['action']

REASONS={
 'Для online-теста нужен вход через online-аккаунт. Для VSCode выбери локальный offline-тест.':'Для VSCode выбери «Локальный offline». Для публичного online-сервера нужен вход через лаунчер.',
 'REMOTE70_JAVA21':'Для исходного и нового клиента нужен JDK21.',
 'REMOTE70_NESTED_MODS':'Обнаружена вложенная папка в mods. Первый установщик поддерживает только обычные JAR верхнего уровня.',
 'REMOTE70_ARGUMENTS':'Команда запуска содержит неподдерживаемое перенаправление профиля или agent.',
 'REMOTE70_SOURCE_CHANGED':'Исходный набор модов изменился. Подготовь установку заново.',
 'REMOTE70_BOOTSTRAP':'Не найден один подходящий MaRed70. Обнови клиент до70 и открой установку заново.',
 'REMOTE70_BOOTSTRAP_CHANGE':'Пакет пытается заменить MaRed. Его обновление требует отдельного шага.',
 'REMOTE70_SOURCE_EXIT':'Исходный клиент не завершился штатно. Новая игра не запускается.',
 'REMOTE70_MANAGED':'Эта игра уже имеет владельца. Первый переход выполняется из обычного клиента.',
 'REMOTE70_OFFLINE':'Локальный offline разрешён только для localhost/127.0.0.1/[::1].',
 'REMOTE70_CANDIDATE':'Проверка пакета или его актуальности не прошла. Открой сетевую панель и проверь выпуск заново.',
 'REMOTE70_PACKAGE_CHANGED':'Подготовленный пакет изменился. Установка остановлена.',
 'REMOTE70_NATIVE_PROBE':'Windows не подтвердила управление деревом процессов. Исходный клиент не закрыт.',
 'REMOTE70_WINDOWS':'Для первого внешнего установщика нужна Windows x64.',
 'REMOTE70_MOD_METADATA':'Моды имеют неподдерживаемые метаданные или конфликтующие modId.',
 'REMOTE70_LOADER_CHANGE':'Пакет не может заменять Minecraft/NeoForge.',
 'REMOTE70_READY':'Клиент не подтвердил фактическую загрузку нужных модов.',
 'REMOTE70_CHILD_ERROR':'Новая игра завершилась с ошибкой. Исходный профиль не изменён.',
 'REMOTE70_SOURCE_IDENTITY':'Исходный процесс сменился или недоступен. Открой установку заново.',
 'REMOTE70_CONFIRM_EXPIRED':'Время подтверждения истекло. Подготовь установку заново.'}

class Worker:
 def __init__(self,folder,lease_factory=SourceLease70,job_factory=WindowsJob):
  self.folder=safe(folder);self.r=request(self.folder);self.id=self.r['request'];self.lease_factory=lease_factory;self.job_factory=job_factory;self.lease=None;self.job=None;self.phase='VALIDATING';self.seq=0;self.root=self.folder/'run';self.spec=None;self.key=None;self.config=None;self.pending_command=None;self.failure_reason='';self.received_source=False
 def state(self,phase):
  if phase not in PHASES:raise ValueError('REMOTE70_PHASE')
  self.phase=phase;value={'schema':70,'request':self.id,'phase':phase,'reportDirectory':str(self.root if self.root.exists() else self.folder),'failureReason':self.failure_reason}
  if hasattr(self,'approved'):value['delta']=self.approved['delta'];value['bootstrapPreserved']=len(self.approved['bootstrap'])
  json_write(self.folder/'state70.json',value)
  if self.root.exists():json_write(self.root/'report.json',{'schema':70,'phase':phase,'sourceModified':False,'serverControlled':False,'request':self.id,'reason':self.failure_reason})
 def cancelled(self):return action(self.folder,self.id)=='CANCEL'
 def require_source(self):
  if self.cancelled():raise InterruptedError('REMOTE70_CANCELLED')
  if self.lease.closed():raise ValueError('REMOTE70_SOURCE_EXIT')
 def verify_remote(self):
  r=self.r;result=subprocess.run([self.spec['clientCommand'][0],'-cp',str(core_classpath(self.folder)),'com.fixmer.genesis.technology.server.remote.RemoteInstallCli70','verify',r['cache'],r['candidate'],'1.21.1',r['neoforge']],capture_output=True,text=True,timeout=180,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
  if result.returncode:raise ValueError('REMOTE70_CANDIDATE')
  value=json.loads(result.stdout)
  if set(value)!={'game','release','archiveSha256','files','installAuthority'} or value['installAuthority'] is not False:raise ValueError('REMOTE70_CANDIDATE')
  return value
 def prepare(self):
  self.state('VALIDATING');r=self.r;self.lease=self.lease_factory(r['pid'],r['birth']);self.require_source()
  session=consume(self.folder,self.folder/'client-session61.json',r['pid'],r['profile'],r['account'],allow_offline=True);argv,profile=read_client(r['pid'],r['profile'],session.get('cwd'));argv,account=for_mode(argv,session,r['authMode']);details=client_details(argv,profile,account,r['neoforge'],auth_mode=r['authMode'],dev_mods=session.get('devMods',''))
  self.spec={'clientCommand':details['argv']};self.account=account;self.source=safe(profile);self.baseline=files(profile/'mods');self.info=self.verify_remote()
  if r['authMode']=='LOCAL_OFFLINE' and self.info['game'].rsplit(':',1)[0] not in {'localhost','127.0.0.1','[::1]'}:raise ValueError('REMOTE70_OFFLINE')
  if self.source==self.folder or self.source in self.folder.parents or self.folder in self.source.parents:raise ValueError('REMOTE70_SOURCE_CHANGED')
  self.root.mkdir();(self.root/'generations').mkdir();self.key=secrets.token_bytes(32);atomic(self.root/'channel.key',self.key,new=True);self.state('PREPARING')
  source_java=argv[0];version=subprocess.run([source_java,'-version'],capture_output=True,text=True,timeout=15,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
  if version.returncode or not re.search(r'version "21(?:\.|\")',version.stderr+version.stdout):raise ValueError('REMOTE70_JAVA21')
  self.argv(argv) # reject redirect/agent flags before closing the source client
  if details.get('devModClasses'):
   dev_spec={'clientCommand':argv,'devModClasses':details['devModClasses']};frozen=snapshot(dev_spec,self.root/'dev-snapshot',core_classpath(self.folder));self.spec['clientCommand']=frozen.pop('argv');self.spec['devSnapshot']={**frozen,'source':str(self.root/'dev-snapshot')}
   if not any((self.root/'dev-snapshot'/'mods'/group/'com/fixmer/mared/technology/server/firstlaunch/RemoteClient70.class').is_file() for group,_ in frozen['roots']):raise ValueError('REMOTE70_BOOTSTRAP')
  self.archive=self.root/'candidate.zip';shutil.copyfile(safe(r['candidate'])/'client.zip',self.archive)
  if sha(self.archive,300*1024*1024)!=self.info['archiveSha256']:raise ValueError('REMOTE70_PACKAGE_CHANGED')
  self.approved=policy(self.source/'mods',self.archive,self.info,bool(self.spec.get('devSnapshot')))
  native_probe(self.folder,source_java,self.root);self.require_source();self.state('READY')
 def argv(self,argv):
  if argv.count('--gameDir')!=1 or argv[argv.index('--gameDir')+1]!='{profile}':raise ValueError('REMOTE70_ARGUMENTS')
  for arg in argv[1:]:
   if re.search(r'(?:^|[\s\"])(?:-Duser\.dir=|-Dfml\.modFolders=|-javaagent:|-agentlib:|-agentpath:|--universe(?:[=\s]|$)|--world(?:[=\s]|$))',arg) or arg.startswith('@'):raise ValueError('REMOTE70_ARGUMENTS')
  return argv
 def wait_action(self,wanted,seconds=900):
  until=time.monotonic()+seconds
  while time.monotonic()<until:
   self.require_source()
   if action(self.folder,self.id)==wanted:return
   time.sleep(.1)
  raise ValueError('REMOTE70_CONFIRM_EXPIRED')
 def final_check(self):
  self.require_source()
  if self.verify_remote()!=self.info or sha(self.archive,300*1024*1024)!=self.info['archiveSha256'] or files(self.source/'mods')!=self.baseline or policy(self.source/'mods',self.archive,self.info,bool(self.spec.get('devSnapshot')))!=self.approved:raise ValueError('REMOTE70_SOURCE_CHANGED')
  self.require_source()
 def build(self):
  if not self.lease.closed() or not self.lease.normal():raise ValueError('REMOTE70_SOURCE_EXIT')
  if files(self.source/'mods')!=self.baseline:raise ValueError('REMOTE70_SOURCE_CHANGED')
  profile=self.root/'generations/client-1.building';live_client_snapshot(self.source,profile,lambda:False)
  if self.spec.get('devSnapshot'):clone(Path(self.spec['devSnapshot']['source']),profile/'.genesis-dev62')
  expected=install(profile,self.archive,self.info,self.approved,bool(self.spec.get('devSnapshot')))
  self.config={'run':self.id,'account':self.account,'game':self.info['game'],'inventory':expected,'minecraft':'1.21.1','neoforge':self.r['neoforge'],'devHash':self.spec.get('devSnapshot',{}).get('hash','none')}
  write_signed(self.root/'session70',self.key,self.config);write_signed(profile/'remote70.marker',self.key,self.config);profile.rename(self.root/'generations/client-1')
  json_write(self.root/'launch-private70.json',self.spec,new=True)
 def start(self):
  profile=self.root/'generations/client-1';env=environment(self.spec['clientCommand'][0],profile);env.update(runtime(profile,self.spec));env.update({'GENESIS_REMOTE70_ROOT':str(self.root),'GENESIS_REMOTE70_RUN':self.id})
  if inventory(profile)!=self.config['inventory'] or read_signed(profile/'remote70.marker',self.key)!=self.config:raise ValueError('REMOTE70_SOURCE_CHANGED')
  argv=[s.replace('{profile}',str(profile)) for s in self.argv(self.spec['clientCommand'])];self.job=self.job_factory(wrap_long(argv,profile),profile,env,self.root/'client.log')
 def command(self,phase):
  self.seq+=1;command={'run':self.id,'account':self.account,'inventory':self.config['inventory'],'phase':phase,'sequence':str(self.seq),'request':str(uuid.uuid4())};write_signed(self.root/'client.command',self.key,command);self.pending_command=command;return command
 def status(self):
  if not (self.root/'client.status').exists():return None
  value=read_signed(self.root/'client.status',self.key)
  if set(value)!={'run','account','inventory','phase','sequence','request'} or any(value[k]!=self.config[k] for k in ('run','account','inventory')) or value['phase']=='ERROR':raise ValueError('REMOTE70_CHILD_ERROR')
  return value
 def wait(self,condition,seconds,code,allow_cancel=True):
  until=time.monotonic()+seconds
  while time.monotonic()<until:
   if allow_cancel and self.cancelled():raise InterruptedError('REMOTE70_CANCELLED')
   if self.job:
    state=self.job.status()
    if state['logFailed'] or state['rootExited'] and state['exitCode']!=0:raise ValueError('REMOTE70_CHILD_ERROR')
   value=condition()
   if value:return value
   time.sleep(.1)
  raise ValueError(code)
 def join(self,notify,cancel,ask):
  command=self.command('JOIN');deadline=time.monotonic()+30;failures=0;next_try=0;self.state('CONNECTING')
  while True:
   if cancel() or self.cancelled():self.command('CANCEL');notify('RECONNECT_CANCELLED');return False
   state=self.job.status()
   if state['logFailed'] or state['rootExited']:raise ValueError('REMOTE70_CHILD_ERROR')
   result=self.status()
   if result and all(result[k]==command[k] for k in ('sequence','request')):
    if result['phase']=='WORLD_FRAME_READY':notify('WORLD_READY');self.command('FOCUS');return True
    if result['phase']=='JOIN_FAILED' and time.monotonic()>=next_try:failures+=1;next_try=time.monotonic()+min(8,2**min(failures,3));command=self.command('JOIN')
   if time.monotonic()>=deadline:
    if not ask('Подключение занимает больше 30 секунд. Продолжать попытки?'):self.command('CANCEL');notify('RECONNECT_CANCELLED');return False
    deadline=time.monotonic()+30
   time.sleep(.1)
 def stop(self):
  if not self.job:return
  state=self.job.status()
  if not state['rootExited']:
   cmd=self.command('STOP')
   def observed():
    reply=self.status();return reply and reply['phase']=='STOP_REQUESTED' and all(reply[k]==cmd[k] for k in ('sequence','request')) and self.job.empty()
   self.wait(observed,30,'REMOTE70_CHILD_STOP',False)
  else:self.wait(self.job.empty,15,'REMOTE70_CHILD_STOP',False)
 def record_failure(self,error):
  code=error.args[0] if len(error.args)==1 and isinstance(error.args[0],str) else ''
  self.failure_reason=REASONS.get(code,'Операция остановлена. Смотри безопасный отчёт и журнал клиента; исходный профиль не изменён.')
  self.state('FAILED');json_write(self.root/'failure70.json' if self.root.exists() else self.folder/'failure70.json',failure_data(error,{'phase':self.phase,'action':'run'}))
 def activate(self,notify,cancel,ask):
  try:self._activate(notify,cancel,ask)
  except InterruptedError:self.state('CANCELLED');notify('CANCELLED')
  except Exception as error:self.record_failure(error);raise PilotFailure(self.failure_reason+'\nОткрой папку отчётов этой установки.') from None
 def _activate(self,notify,cancel,ask):
  self.state('ARMING');self.final_check();notify('COVER');self.state('ARMED');self.wait_action('COMMIT',30)
  self.wait(lambda:self.lease.closed(),120,'REMOTE70_SOURCE_EXIT',False)
  if not self.lease.normal():raise ValueError('REMOTE70_SOURCE_EXIT')
  if self.cancelled() or cancel():raise InterruptedError('REMOTE70_CANCELLED')
  if self.verify_remote()!=self.info:raise ValueError('REMOTE70_CANDIDATE')
  notify('PREPARING_GENERATION');self.build();self.state('STARTING');notify('STARTING_UPDATED_CLIENT');self.start()
  self.wait(lambda:self.status() and self.status()['phase']=='READY',120,'REMOTE70_READY')
  if self.join(notify,cancel,ask):self.state('ACTIVE')
  else:self.state('CANCELLED')
  while not self.job.status()['rootExited']:
   if cancel() or self.cancelled():self.stop();break
   if self.job.status()['logFailed']:raise ValueError('REMOTE70_CHILD_ERROR')
   time.sleep(.2)
  self.stop();self.state('COMPLETE')
 def run(self):
  try:
   if os.name!='nt':raise ValueError('REMOTE70_WINDOWS')
   atomic(self.folder/'started70',self.id.encode(),new=True)
   self.prepare();self.wait_action('ARM');from .ui import run_cover
   run_cover(self.activate,self.source,stop_message='Закрыть обновлённый клиент? Установщик завершит только запущенную им игру. Сервер продолжит работу.')
  except InterruptedError:self.state('CANCELLED')
  except Exception as error:
   if not self.failure_reason:self.record_failure(error)
  finally:
   if self.job:
    try:self.stop()
    except Exception:pass
    finally:self.job.close();self.job=None
   if self.lease:self.lease.close()
