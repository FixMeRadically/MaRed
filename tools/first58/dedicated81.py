"""Persistent owner81: fenced COMMIT, fresh receipt barriers and recoverable control polling."""
import json,subprocess,threading,time,tempfile
from .dedicated78 import Owner78
from .dedicated75 import Owner75
from . import dedicated74 as base
from .common import safe,inventory
from .bridge67 import read,write
from .runtime import core_classpath
from .transition81 import Progress81,validate

class Owner81(Owner78):
 def __init__(self,*args,**kwargs):
  super().__init__(*args,**kwargs);self.commit81=None;self.progress81=None;self.control81=None;self.control_at81=0;self.control_error81='';self.closing81=False;self.control_lock81=threading.Lock()
 def fence(self,action,round_id):
  p=self.pending
  if not p:raise ValueError('DEDICATED81_NO_STAGE')
  args=[self.s['java'],'-cp',str(core_classpath(self.project)),'com.fixmer.genesis.technology.server.remote.CommitFence81',action,str(self.root),round_id,p['label'],str(p['sourceGen']),str(p['targetGen'])]
  args.append('--owned')
  with tempfile.TemporaryFile() as output,tempfile.TemporaryFile() as error:
   child=subprocess.Popen(args,stdin=subprocess.PIPE,stdout=output,stderr=error,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0),close_fds=True)
   try:
    until=time.monotonic()+180
    while child.poll() is None:
     if time.monotonic()>until:raise ValueError('DEDICATED81_FENCE_TIMEOUT')
     self.check_stop81();self.report();time.sleep(.1)
    output.seek(0);error.seek(0);data=output.read(65537);code=error.read(4097).decode('utf8','replace').strip()
    if child.returncode or len(data)>65536:raise ValueError(code if __import__('re').fullmatch('COMMIT81_[A-Z_]{1,60}',code) else 'DEDICATED81_FENCE_REJECTED')
    return json.loads(data)
   finally:
    if child.stdin:child.stdin.close()
    if child.poll() is None:
     try:child.wait(timeout=3)
     except subprocess.TimeoutExpired:child.kill();child.wait(timeout=3)
 def check_stop81(self):
  if not self.stop_requested and (self.root/'request74').is_file():
   try:
    d=read(self.root/'request74',self.key)
    if d.get('action')=='SHUTDOWN':
     request=Owner75.receive(self)
     if request:self.stop_requested=True
   except ValueError:pass
  if self.stop_requested:raise InterruptedError('DEDICATED75_STOP_REQUESTED')
 def target_ready81(self):
  p=self.pending
  if not p or self.current!=p['targetGen'] or not self.ready_generation1 or self.current not in self.jobs:return False
  native=self.jobs[self.current].status();receipt=self.status(self.current)
  return not native['rootExited'] and not native['logFailed'] and bool(receipt and receipt['phase'] in {'READY','COMMAND_ACCEPTED'})
 def snapshot81(self):
  with self.control_lock81:return (dict(self.control81) if self.control81 else None,time.monotonic()-self.control_at81<=5 and bool(self.control81) and not self.control_error81,self.control_error81)
 def report(self,phase=None,reason=''):
  if getattr(self,'closing81',False) and phase in {'ACTIVE_UPDATED','WAITING_THRESHOLD'}:phase='CLOSE_UNCONFIRMED'
  if getattr(self,'progress81',None):
   state,fresh,error=self.snapshot81();state=state or dict(phase='COMMITTED',round=self.commit81['round'],release=self.commit81['release'],client=self.commit81['client'],total=int(self.commit81['total']),needed=(int(self.commit81['total'])+3)//4,installed=0,joined=0,admission=False)
   state['progress81']=self.progress81.view(phase or self.phase,state,fresh,error,self.target_ready81());state['progress81']['closePending']=self.closing81
   if self.closing81:state['progress81']['canClose']=self.target_ready81()
   self.last_pump=state
  super().report(phase,reason)
 def verify_stage(self):
  if not self.commit81:return super().verify_stage()
  p=self.pending
  if not p or self.fence('check',self.commit81['round'])!=self.commit81:raise ValueError('DEDICATED81_FENCE_CHANGED')
  source=self.root/f"generations/server-{p['sourceGen']}"
  if inventory(source)!=p['sourceInventory'] or base.digest(base.tree(source,self.s['level'],True))!=p['sourceSeal'] or base.digest(base.tree(p['path'],self.s['level'],True))!=p['targetSeal']:raise ValueError('DEDICATED75_STAGE_CHANGED')
  if self.tool('verify-history',p['release'],self.root/'publisher73/public.der')!=p['info']:raise ValueError('DEDICATED75_RELEASE_CHANGED')
  props=base.properties(source)
  if any(props[k]!=self.s[v] for k,v in [('level-name','level'),('online-mode','auth'),('server-ip','bind'),('server-port','port')]):raise ValueError('DEDICATED75_SERVER_MODE_CHANGED')
 def start_pump(self,round_id):
  self.external=True;self.pump_round=round_id;self.pump_error=None;delay=1
  def loop():
   backoff=delay
   while not self.pump_stop.is_set():
    try:
     state=self.tool('admit' if self.admit and self.target_ready81() else 'pulse',self.root)
     with self.control_lock81:
      validate(state,self.commit81,self.control81,self.target_ready81());self.control81=state;self.control_at81=time.monotonic();self.control_error81=''
     backoff=1
    except Exception:
     with self.control_lock81:self.control_error81='CONTROL_UNAVAILABLE'
     backoff=min(8,backoff*2)
    self.pump_stop.wait(backoff)
  self.pump_thread=threading.Thread(target=loop,name='genesis-control81-owner',daemon=True);self.pump_thread.start()
 def wait_all_installed(self):
  while True:
   self.health();state,fresh,_=self.snapshot81()
   if fresh and state['installed']==int(self.commit81['total']):return
   if self.jobs[self.current].status()['rootExited']:raise ValueError('DEDICATED75_SOURCE_EXIT')
   try:request=self.receive()
   except ValueError:request=None
   if request and request['action']=='SHUTDOWN':self.stop_requested=True;raise InterruptedError('DEDICATED75_STOP_REQUESTED')
   self.report('WAITING_ALL_INSTALLED');time.sleep(.25)
 def activate(self,state):
  p=self.pending
  if not p or self.commit81 or self.current!=p['sourceGen'] or state.get('phase')!='COMMITTED' or state.get('release')!=p['info']['release'] or state.get('client')!=p['info']['client'] or state.get('admission'):raise ValueError('DEDICATED81_COMMIT_BINDING')
  proof=self.fence('capture',state['round'])
  if proof['release']!=p['info']['release'] or proof['client']!=p['info']['client'] or proof['server']!=p['info']['server']:raise ValueError('DEDICATED81_COMMIT_BINDING')
  write(self.root/'owner81',self.key,dict(schema='81',run=self.s['run']));self.commit81=proof;self.control81=validate(dict(state),proof,None,False);self.control_at81=time.monotonic()-6;self.progress81=Progress81();self.verify_stage();g=self.current;self.event('COMMITTED');self.report('QUIESCING_CONTROL');cmd=self.command(g,'QUIESCE',state['round'])
  def quiesced():
   d=self.status(g);return d and d['phase']=='QUIESCED' and all(d[k]==cmd[k] for k in ('sequence','request','round'))
  self.wait(quiesced,30,'DEDICATED75_QUIESCE_TIMEOUT');self.start_pump(state['round']);self.report('WAITING_ALL_INSTALLED');self.wait_all_installed();self.report('SAVING_OLD_SERVER');self.stop(g,'SAVE_STOP',state['round']);self.event('STOPPED_SAVED');self.report('COPYING_SAVED_WORLD');self.copy_saved();self.jobs[g].close();del self.jobs[g];self.ready_generation1=False;self.current_inventory=read(self.root/f"server-{p['targetGen']}.session",self.key)['inventory'];self.report('STARTING_NEW_SERVER');self.start(p['targetGen']);self.previous_info=p['info'];self.event('SERVER_READY');self.report('WAITING_THRESHOLD')
 def advance_admission(self):
  state,fresh,_=self.snapshot81()
  if not fresh or not self.target_ready81():self.admit=False;return False
  self.admit=state['installed']>=state['needed'];return self.admit
 def finish_round(self,force=False):
  if not self.external:return
  state,fresh,_=self.snapshot81()
  if not self.target_ready81() or not self.closing81 and (not fresh or not state['admission'] or not force and state['joined']<state['total']):raise ValueError('DEDICATED75_WAIT_FOR_JOINED')
  self.closing81=True
  self.pump_stop.set();until=time.monotonic()+20
  while self.pump_thread and self.pump_thread.is_alive() and time.monotonic()<until:self.pump_thread.join(.1);self.report('CLOSING_ROUND')
  if self.pump_thread and self.pump_thread.is_alive():raise ValueError('DEDICATED75_PUBLISHER_BUSY')
  result=self.fence('close',self.commit81['round'])
  if result!=dict(round=self.commit81['round'],phase='CLOSED'):raise ValueError('DEDICATED81_CLOSE_UNCONFIRMED')
  self.event('ROUND_CLOSED');self.completed_round=state['round'];self.external=False;self.pump_thread=None;self.pump_stop=threading.Event();self.pump_error=None;self.admit=False;self.pending=None;self.last_pump=None;self.commit81=None;self.progress81=None;self.control81=None;self.control_at81=0;self.closing81=False;(self.root/'prepared74').unlink(missing_ok=True)
 def receive(self):
  request=super().receive()
  if not request:return None
  action=request['action']
  if action=='CONTINUE_WAIT' and not self.commit81:self.report(reason='Нет активного ожидания перехода.');return None
  if action in {'CONTINUE_WAIT','CLOSE'} and self.commit81:
   if request['label']!=self.pending['label']:self.report(reason='Имя выпуска не совпадает с активным переходом.');return None
   if action=='CONTINUE_WAIT' and self.closing81:self.report(reason='Сначала нужно подтвердить исход закрытия раунда.');return None
   if action=='CONTINUE_WAIT':self.progress81.acknowledge(request['request']);self.report(reason='Ожидание продолжается. Участники и пороги не изменены.');return None
   try:self.finish_round(True);self.report('ACTIVE_UPDATED')
   except InterruptedError:raise
   except Exception:self.report('CLOSE_UNCONFIRMED' if self.closing81 else 'WAITING_THRESHOLD' if self.target_ready81() else 'WAITING_ALL_INSTALLED','Закрытие раунда не подтверждено. Сервер сохранён под управлением владельца; доступны ожидание и штатная остановка.')
   return None
  return request
