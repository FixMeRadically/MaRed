"""Join-only path for installed private members, with strict fresh-public fallback for older servers."""
import base64,subprocess,time
from .bootstrap_wire76 import decode
from .runtime import core_classpath

class Combined82:
 def __init__(self,public,member):self.public=public;self.member=member;self.w=member.w;self.committed=True;self.last=None;self.runtime82=False;self.automatic73=getattr(member,'automatic73',False) is True
 def check(self):self.public.check();return self.member.check() # before source closes: install offer stays mandatory
 def proof(self):
  w=self.w
  r=subprocess.run([w.spec['clientCommand'][0],'-cp',str(core_classpath(w.folder)),'com.fixmer.genesis.technology.server.remote.Admission82','poll',str(self.member.file),str(self.member.key),self.member.inventory,'1.21.1',w.r['neoforge']],capture_output=True,text=True,timeout=30,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
  if r.returncode:raise ValueError('ADMISSION82_UNAVAILABLE')
  d=decode(base64.b64decode(r.stdout.strip(),validate=True))
  if self.last and (d['owner']!=self.last['owner'] or d['target']!=self.last['target'] or d['serverInventory']!=self.last['serverInventory'] or int(d['serial'])<int(self.last['serial']) or int(d['controlSequence'])<int(self.last['controlSequence']) or int(d['serial'])==int(self.last['serial']) and d!=self.last):raise ValueError('ADMISSION82_ROLLBACK')
  self.last=d;return d
 def before_join(self):
  if not self.runtime82:raise ValueError('ADMISSION82_RUNTIME_REQUIRED')
  state=self.member.check()
  row=next((r.split(',') for r in state['clients'].split(';') if r.split(',')[0]==self.member.cfg['client']),None)
  if state['admission']!='true' or int(state['installed'])<int(state['needed']) or not row or row[1] not in {'INSTALLED','JOINED'}:raise ValueError('ADMISSION82_WAITING')
  try:
   proof=self.proof()
   # The independently signed control heartbeat must not predate the readiness proof.
   if int(state['sequence'])<int(proof['controlSequence']):raise ValueError('ADMISSION82_CONTROL_BEHIND')
   return 'MEMBER82'
  except (ValueError,subprocess.SubprocessError):
   if self.last is not None:raise
   # Legacy servers remain usable only while their ordinary offer + READY are fresh.
   if self.public.check()['phase']!='READY':raise ValueError('ADMISSION82_WAITING')
   return 'PUBLIC76'
 def installed(self,notify,cancel,ask):
  status=self.w.status()
  if not status or status['phase']!='READY':raise ValueError('ADMISSION82_RUNTIME_REQUIRED')
  self.member.installed(notify,cancel,ask);self.runtime82=True;deadline=time.monotonic()+(180 if self.automatic73 else 30)
  while True:
   if cancel() or self.w.cancelled():raise InterruptedError('CONTROL73_CANCELLED')
   native=self.w.job.status()
   if native['rootExited'] or native['logFailed']:raise ValueError('REMOTE70_CHILD_ERROR')
   try:self.before_join();return
   except (ValueError,subprocess.SubprocessError):notify('Моды установлены. Ждём подтверждённой готовности сервера для входа.')
   if time.monotonic()>=deadline:
    if self.automatic73:raise ValueError('ADMISSION82_WAIT_TIMEOUT')
    if not ask('Сервер пока не подтвердил вход. Продолжать ожидание?'):raise InterruptedError('CONTROL73_CANCELLED')
    deadline=time.monotonic()+30
   time.sleep(1)
 def joined(self):return self.member.joined()
 def abort(self):return self.member.abort()
