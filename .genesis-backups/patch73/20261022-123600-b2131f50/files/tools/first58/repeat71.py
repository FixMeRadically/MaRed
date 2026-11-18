"""Sequential remote updates under ONE existing owner. No new worker, source PID adoption or server control."""
import copy,json,secrets,time,uuid,shutil
from pathlib import Path
from .remote70 import Worker,REASONS
from .bridge67 import read,write
from .common import safe,atomic,json_write,inventory,sha
from .install70 import files,policy
from .diagnostics63 import PilotFailure

REQUEST_FIELDS={'schema','run','account','inventory','request','action','cache','candidate'}
STATUS_FIELDS={'schema','run','account','inventory','request','phase','at','reason','delta','reports'}
TERMINAL={'COMPLETE','CANCELLED','FAILED'}

def packet(current):
 p=current.root/'repeat71.request'
 if not p.exists():return None
 d=read(p,current.key)
 if set(d)!=REQUEST_FIELDS or d['schema']!='71' or any(d[k]!=current.config[k] for k in ('run','account','inventory')) or d['action'] not in {'PREPARE','ARM','CANCEL'} or str(uuid.UUID(d['request']))!=d['request'] or d['request']==str(uuid.UUID(int=0)):raise ValueError('REMOTE71_REQUEST')
 for field in ('cache','candidate'):
  if not Path(d[field]).is_absolute() or len(d[field])>4096:raise ValueError('REMOTE71_PATH')
 return d

def matching(first,next):
 return next is not None and REQUEST_FIELDS-{'action'} <= first.keys() and all(first[k]==next[k] for k in REQUEST_FIELDS-{'action'})

def publish(current,request,phase,cycle=None,reason=''):
 d={k:current.config[k] for k in ('run','account','inventory')}
 d.update(schema='71',request=request,phase=phase,at=str(int(time.time()*1000)),reason=reason,delta=json.dumps(cycle.approved['delta'] if cycle and hasattr(cycle,'approved') else {},ensure_ascii=False,separators=(',',':')),reports=str(current.root))
 write(current.root/'repeat71.status',current.key,d)
 json_write(current.root/'repeat71-report.json',{'schema':71,'run':current.config['run'],'request':request,'phase':phase,'reason':reason,'sourceModified':False,'nextDirectory':str(cycle.root) if cycle else None})

class OwnedLease71:
 """Observes only the Job created by this owner, never opens/adopts a caller-supplied PID."""
 def __init__(self,job):self.job=job
 def closed(self):return self.job.status()['rootExited']
 def normal(self):
  s=self.job.status();return s['rootExited'] and s['exitCode']==0 and not s['logFailed'] and self.job.empty()
 def close(self):pass

class Cycle71(Worker):
 def __init__(self,current,offer):
  self.previous=current;self.offer=offer;self.folder=current.folder;self.id=offer['request'];self.r={**current.r,'cache':offer['cache'],'candidate':offer['candidate']};self.spec=copy.deepcopy(current.spec);self.account=current.account;self.source=current.root/'generations/client-1';self.lease=OwnedLease71(current.job);self.job=None;self.key=secrets.token_bytes(32);self.config=None;self.phase='VALIDATING';self.seq=0;self.failure_reason='';self.pending_command=None
  self.root=safe(self.folder/'repeats71'/self.id/'run')
 def cancelled(self):
  p=packet(self.previous)
  return matching(self.offer,p) and p['action']=='CANCEL'
 def prepare(self):
  self.require_source()
  if inventory(self.source)!=self.previous.config['inventory']:raise ValueError('REMOTE70_SOURCE_CHANGED')
  # The existing owner's authority never silently changes server, publisher or cache root.
  if safe(self.r['cache'])!=safe(self.previous.r['cache']):raise ValueError('REMOTE71_ORIGIN')
  self.info=self.verify_remote()
  if any(self.info[k]!=self.previous.info[k] for k in ('game','distribution','endpoint')):raise ValueError('REMOTE71_ORIGIN')
  if self.info['release']==self.previous.info['release'] or self.info['archiveSha256']==self.previous.info['archiveSha256']:raise ValueError('REMOTE71_SAME_RELEASE')
  self.baseline=files(self.source/'mods');self.root.mkdir(parents=True);(self.root/'generations').mkdir();atomic(self.root/'channel.key',self.key,new=True)
  self.archive=self.root/'candidate.zip';shutil.copyfile(safe(self.r['candidate'])/'client.zip',self.archive)
  if sha(self.archive,300*1024*1024)!=self.info['archiveSha256']:raise ValueError('REMOTE70_PACKAGE_CHANGED')
  self.approved=policy(self.source/'mods',self.archive,self.info,bool(self.spec.get('devSnapshot')));self.require_source();self.state('READY')

class RepeatingWorker71(Worker):
 def __init__(self,*args,**kwargs):super().__init__(*args,**kwargs);self.active71=self;self.seen71=set()
 def begin(self,current,request):return Cycle71(current,request)
 def watch(self,notify,cancel,ask):
  current=self;pending=None;offer=None;phase='ACTIVE';reason='';last=0
  while True:
   s=current.job.status()
   if s['logFailed'] or s['rootExited'] and s['exitCode']!=0:raise ValueError('REMOTE70_CHILD_ERROR')
   if s['rootExited']:
    current.stop();current.state('COMPLETE');return
   if cancel():current.stop();current.state('COMPLETE');return
   try:d=packet(current)
   except Exception:
    # A malformed local message is not authority to stop the running game.
    d=None;phase='FAILED';reason='Некорректный запрос обновления. Игра остаётся открыта.'
   if d and offer and d['request']==offer['request'] and not matching(offer,d):
    phase='FAILED';reason='Параметры подготовленного обновления изменились.';pending=None
   elif d and d['action']=='PREPARE' and d['request'] not in self.seen71:
    if pending:
     pending.state('CANCELLED');pending=None;self.seen71.add(d['request']);offer=d;phase='FAILED';reason='Параллельный запрос отклонён. Подготовь обновление заново.'
    elif len(self.seen71)>=128:
     offer=d;phase='FAILED';reason='Достигнут лимит обновлений владельца. Закрой игру и начни новый запуск.'
    else:
     self.seen71.add(d['request']);offer=d;phase='VALIDATING';reason='';publish(current,d['request'],phase)
     try:
      pending=self.begin(current,d);pending.prepare();phase='READY'
     except InterruptedError:pending=None;phase='CANCELLED'
     except Exception as error:
      reason=reason_for(error);pending=None;phase='FAILED'
   elif d and matching(offer or {},d) and d['action']=='CANCEL' and pending:
    pending.state('CANCELLED');pending=None;phase='CANCELLED';reason=''
   elif d and matching(offer or {},d) and d['action']=='ARM' and pending and phase=='READY':
    cycle=pending;phase='ARMING';publish(current,d['request'],phase,cycle)
    committed=False
    try:
     cycle.final_check();notify('COVER');cycle.require_source()
     publish(current,d['request'],'ARMED',cycle)
     # This owner already owns current.job. User ARM + shown cover authorize cooperative STOP.
     committed=True;current.stop()
     if not cycle.lease.normal():raise ValueError('REMOTE70_SOURCE_EXIT')
     if cycle.cancelled() or cancel():raise InterruptedError('REMOTE70_CANCELLED')
     if cycle.verify_remote()!=cycle.info:raise ValueError('REMOTE70_CANDIDATE')
     notify('PREPARING_GENERATION');cycle.build();current.job.close();current.job=None;self.active71=cycle;cycle.state('STARTING');notify('STARTING_UPDATED_CLIENT');cycle.start()
     cycle.wait(lambda:cycle.status() and cycle.status()['phase']=='READY',120,'REMOTE70_READY')
     if cycle.join(notify,cancel,ask):cycle.state('ACTIVE')
     else:cycle.state('CANCELLED')
     current=cycle;pending=None;offer=None;phase='ACTIVE';reason='';last=0
    except Exception as error:
     if committed:
      cycle.record_failure(error);raise PilotFailure(reason_for(error)+'\nОткрой отчёты обновления.') from None
     notify('CANCELLED');phase='CANCELLED' if isinstance(error,InterruptedError) else 'FAILED';reason='' if phase=='CANCELLED' else reason_for(error);pending=None
   if time.monotonic()-last>=1:
    publish(current,offer['request'] if offer else str(uuid.UUID(int=0)),phase,pending,reason);last=time.monotonic()
   time.sleep(.15)
 def run(self):
  try:super().run()
  finally:
   active=self.active71
   if active is not self and active.job:
    try:active.stop()
    except Exception:pass
    finally:active.job.close();active.job=None

def reason_for(error):
 code=error.args[0] if len(error.args)==1 and isinstance(error.args[0],str) else ''
 return {'REMOTE71_SAME_RELEASE':'Этот выпуск уже установлен. Скачай следующий выпуск.','REMOTE71_ORIGIN':'Выпуск относится к другому серверу или издателю. Текущая игра не изменена.'}.get(code,REASONS.get(code,'Подготовка остановлена. Исходная игра не изменена; проверь выпуск и отчёты.'))
