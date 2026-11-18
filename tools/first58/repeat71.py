"""Sequential remote updates under ONE existing owner. No new worker, source PID adoption or server control."""
import copy,json,secrets,time,uuid,shutil
from pathlib import Path
from .remote70 import Worker,REASONS
from .bridge67 import read,write
from .common import safe,atomic,json_write,inventory,sha
from .install70 import files,policy
from .diagnostics63 import PilotFailure
from .preflight87 import public_report

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
 metrics=public_report(getattr(cycle,'preflight87',None)) if cycle else None
 if metrics:d['preflight87']=json.dumps(metrics,separators=(',',':'))
 if cycle and isinstance(getattr(cycle,'auth89',None),dict):d['auth89']=json.dumps(cycle.auth89,separators=(',',':'))
 write(current.root/'repeat71.status',current.key,d)
 json_write(current.root/'repeat71-report.json',{'schema':71,'run':current.config['run'],'request':request,'phase':phase,'reason':reason,'sourceModified':False,'nextDirectory':str(cycle.root) if cycle else None})

class OwnedLease71:
 """Observes only the Job created by this owner, never opens/adopts a caller-supplied PID."""
 def __init__(self,job):self.job=job;self.sealed119=False
 def closed(self):return self.sealed119 or self.job.status()['rootExited']
 def normal(self):
  if self.sealed119:return True
  s=self.job.status();return s['rootExited'] and s['exitCode']==0 and not s['logFailed'] and self.job.empty()
 def seal119(self):
  if not self.normal():raise ValueError('REMOTE70_SOURCE_EXIT')
  self.sealed119=True # Normal root exit and empty retained Job were observed before closing its HANDLE.
 def close(self):pass

class Cycle71(Worker):
 def __init__(self,current,offer):
  self.previous=current;self.offer=offer;self.folder=current.folder;self.id=offer['request'];self.r={**current.r,'cache':offer['cache'],'candidate':offer['candidate']};self.spec=copy.deepcopy(current.spec);self.account=current.account;self.source=current.root/'generations/client-1';self.lease=OwnedLease71(current.job);self.job=None;self.key=secrets.token_bytes(32);self.config=None;self.phase='VALIDATING';self.seq=0;self.failure_reason='';self.pending_command=None;self.world_ready119=False;self.return_attempt119=False;self.returning119=False;self.return_result119=None
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
  if (self.info['release']==self.previous.info['release'] or self.info['archiveSha256']==self.previous.info['archiveSha256']) and not (self.previous.root/('control73-'+self.id)).exists():raise ValueError('REMOTE71_SAME_RELEASE')
  self.apply_lease76=getattr(self.previous,'apply_lease76',None)
  if self.apply_lease76 is None:
   from .bootstrap76 import ApplyLease76
   self.apply_lease76=ApplyLease76(self.r['cache'],self.account,self.info['game'],self.info['endpoint']);self.previous.apply_lease76=self.apply_lease76
  self.baseline=files(self.source/'mods');self.root.mkdir(parents=True);(self.root/'generations').mkdir();atomic(self.root/'channel.key',self.key,new=True)
  self.archive=self.root/'candidate.zip';shutil.copyfile(safe(self.r['candidate'])/'client.zip',self.archive)
  if sha(self.archive,300*1024*1024)!=self.info['archiveSha256']:raise ValueError('REMOTE70_PACKAGE_CHANGED')
  self.approved=policy(self.source/'mods',self.archive,self.info,bool(self.spec.get('devSnapshot')));self.check_storage87();self.reserve_report87();self.stage_mods()
  from .auth89 import check
  control=self.previous.root/('control73-'+self.id)
  if control.exists():
   packet=signed_control=read(control,self.previous.key)
   if set(packet)!={'run','account','inventory','request','payload'} or any(packet[k]!=self.previous.config[k] for k in ('run','account','inventory')) or packet['request']!=self.id:raise ValueError('CONTROL73_LOCAL_BINDING')
   from .control73 import prepare
   import base64
   prepare(self,base64.b64decode(packet['payload'],validate=True),before_ready=check)
  else:
   check(self)
   if (safe(self.r['candidate'])/'bootstrap76.cfg').exists():
    from .bootstrap76 import attach
    self.gate73=attach(self,None)
  self.state('READY')

 def state(self,phase):
  if phase in TERMINAL:self.release_report87();self.discard_staged_mods()
  super().state(phase)
  publish(self.previous,self.id,phase,self)

class RepeatingWorker71(Worker):
 def __init__(self,*args,**kwargs):super().__init__(*args,**kwargs);self.active71=self;self.seen71=set()
 def begin(self,current,request):return Cycle71(current,request)
 def watch(self,notify,cancel,ask):
  current=self;pending=None;offer=None;phase=('RESTORED_CONNECTED' if getattr(self,'world_ready119',False) else 'RESTORED') if getattr(self,'returning119',False) else 'ACTIVE';reason='';last=0
  while True:
   s=current.job.status()
   if s['logFailed'] or s['rootExited'] and s['exitCode']!=0:raise ValueError('REMOTE70_CHILD_ERROR')
   if s['rootExited']:
    current.stop();current.state('COMPLETE');return
   if cancel():current.stop();current.state('COMPLETE');return
   current.confirm_join_receipt()
   try:d=packet(current)
   except Exception:
    # A malformed local message is not authority to stop the running game.
    d=None;phase='FAILED';reason='Некорректный запрос обновления. Игра остаётся открыта.'
   if d and offer and d['request']==offer['request'] and not matching(offer,d):
    phase='FAILED';reason='Параметры подготовленного обновления изменились.';pending=None
   elif d and d['action']=='PREPARE' and d['request'] not in self.seen71:
    if pending:
     if getattr(pending,'gate73',None):pending.gate73.abort()
     pending.state('CANCELLED');pending=None;self.seen71.add(d['request']);offer=d;phase='FAILED';reason='Параллельный запрос отклонён. Подготовь обновление заново.'
    elif len(self.seen71)>=128:
     offer=d;phase='FAILED';reason='Достигнут лимит обновлений владельца. Закрой игру и начни новый запуск.'
    else:
     self.seen71.add(d['request']);offer=d;phase='VALIDATING';reason='';publish(current,d['request'],phase)
     try:
      pending=self.begin(current,d);pending.prepare();phase='READY'
     except InterruptedError:
      if pending:pending.release_report87();pending.discard_staged_mods()
      if pending and getattr(pending,'gate73',None):pending.gate73.abort()
      pending=None;phase='CANCELLED'
     except Exception as error:
      if pending:pending.release_report87();pending.discard_staged_mods()
      if pending and getattr(pending,'gate73',None):pending.gate73.abort()
      reason=record_prepare_failure(current,pending,error);pending=None;phase='FAILED'
   elif d and matching(offer or {},d) and d['action']=='CANCEL' and pending:
    pending.state('CANCELLED');
    if getattr(pending,'gate73',None):pending.gate73.abort()
    pending=None;phase='CANCELLED';reason=''
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
     notify('PREPARING_GENERATION');cycle.build();cycle.lease.seal119();current.job.close();current.job=None;self.active71=cycle;cycle.state('STARTING');notify('STARTING_UPDATED_CLIENT');cycle.start()
     cycle.wait(lambda:cycle.has_status('READY'),120,'REMOTE70_READY')
     if getattr(cycle,'gate73',None):cycle.gate73.installed(notify,cancel,ask)
     if cycle.join(notify,cancel,ask):
      cycle.state('ACTIVE')
      cycle.discard_successful_snapshot_files()
      cycle.join_receipt_pending=bool(getattr(cycle,'gate73',None))
     else:cycle.state('CANCELLED')
     current=cycle;pending=None;offer=None;phase='ACTIVE';reason='';last=0
    except Exception as error:
     if committed:
      if Worker.return_or_fail119(cycle,error,notify,cancel,ask,wait_lifetime=False):
       if current.job is not None and current.job is not cycle.job:current.job.close();current.job=None
       self.active71=cycle;current=cycle;pending=None;offer=None;phase='RESTORED_CONNECTED' if cycle.world_ready119 else 'RESTORED';reason='';last=0
       continue
      cycle.record_failure(error);raise PilotFailure(reason_for(error)+'\nОткрой отчёты обновления.') from None
     if getattr(cycle,'gate73',None):cycle.gate73.abort()
     cycle.discard_staged_mods()
     notify('CANCELLED');phase='CANCELLED' if isinstance(error,InterruptedError) else 'FAILED';reason='' if phase=='CANCELLED' else record_prepare_failure(current,cycle,error);pending=None
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

def record_prepare_failure(current,cycle,error):
 # Keep the reason and safe stack in the same reports directory exposed to the running game.
 from .diagnostics63 import failure_data
 reason=reason_for(error)
 report=failure_data(error,{'phase':getattr(cycle,'phase','VALIDATING'),'action':'run'})
 report['reason']=reason
 code=error.args[0] if len(error.args)==1 and isinstance(error.args[0],str) else ''
 if code in REASONS or code in {'REMOTE71_SAME_RELEASE','REMOTE71_ORIGIN'}:report['code']=code
 try:json_write(current.root/'failure71.json',report)
 except OSError:pass
 return reason

def reason_for(error):
 code=error.args[0] if len(error.args)==1 and isinstance(error.args[0],str) else ''
 return {'REMOTE71_SAME_RELEASE':'Этот выпуск уже установлен. Скачай следующий выпуск.','REMOTE71_ORIGIN':'Выпуск относится к другому серверу или издателю. Текущая игра не изменена.'}.get(code,REASONS.get(code,'Подготовка остановлена. Исходная игра не изменена; проверь выпуск и отчёты.'))
