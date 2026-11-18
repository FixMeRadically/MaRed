"""Signed server decision gate. Never trusts HTTP202 as installation/transfer authority."""
import base64,hashlib,re,subprocess,time,uuid
from pathlib import Path
from .bridge67 import body,read as signed_read
from .common import atomic,safe,inventory
from .runtime import core_classpath

def decode(data):
 if len(data)>130000:raise ValueError('CONTROL73_SIZE')
 values={}
 for line in data.decode('ascii').splitlines():
  k,v=line.split('=',1)
  if k in values:raise ValueError('CONTROL73_DUPLICATE')
  raw=base64.b64decode(v+'='*((-len(v))%4),altchars=b'-_',validate=True)
  if base64.urlsafe_b64encode(raw).decode().rstrip('=')!=v:raise ValueError('CONTROL73_CANONICAL')
  values[k]=raw.decode('utf8')
 if body(values)!=data:raise ValueError('CONTROL73_CANONICAL')
 return values

class Gate73:
 automatic73=True
 def __init__(self,worker,raw):
  self.w=worker;self.cfg=decode(raw);self.inventory=hashlib.sha256(''.join('mods/'+k+'='+v+'\n' for k,v in sorted(worker.approved['after'].items())).encode('ascii')).hexdigest();self.last=0;self.committed=False;self.last_state=None;self.enabled=True;self.join_request=None
  if set(self.cfg)!={'schema','authMode','instance','round','client','secret','account','ticket','release','archive','distribution','game','endpoint','signer'} or self.cfg['schema']!='73' or self.cfg['authMode']!=worker.r['authMode'] or self.cfg['account']!=worker.account or any(self.cfg[k]!=worker.info[j] for k,j in {'release':'release','archive':'archiveSha256','distribution':'distribution','game':'game','endpoint':'endpoint'}.items()):raise ValueError('CONTROL73_BINDING')
  for k in ('secret','archive','signer'):
   if not re.fullmatch('[a-f0-9]{64}',self.cfg[k]):raise ValueError('CONTROL73_CONFIG')
  for k in ('instance','round','client','account','ticket','release','distribution'):uuid.UUID(self.cfg[k])
  self.file=worker.root/'control73.cfg';atomic(self.file,raw,new=True);self.key=safe(worker.r['candidate'])/'public.der'
  if hashlib.sha256(self.key.read_bytes()).hexdigest()!=self.cfg['signer']:raise ValueError('CONTROL73_PIN')
 def call(self,*args,timeout=30):
  w=self.w;r=subprocess.run([w.spec['clientCommand'][0],'-XX:-UsePerfData','-cp',str(core_classpath(w.folder)),'com.fixmer.genesis.technology.server.remote.ControlHttp73',*map(str,args)],capture_output=True,text=True,timeout=timeout,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
  if r.returncode:raise ValueError('CONTROL73_NETWORK')
  return r.stdout.strip()
 def status(self,timeout=30):
  s=decode(base64.b64decode(self.call('poll',self.file,self.key,timeout=timeout),validate=True));seq=int(s['sequence'])
  if seq<self.last:raise ValueError('CONTROL73_ROLLBACK')
  if self.last_state is None or seq>self.last:
   atomic(self.w.root/'control73.sequence',str(seq).encode('ascii'))
  self.last=seq;self.last_state=s
  if s['phase'] in {'CANCELLED','CLOSED'}:raise InterruptedError('CONTROL73_CANCELLED')
  return s
 def receipt(self,action,until,notify=None,cancel=lambda:False,ask=None):
  request=uuid.uuid4();next_post=0;deadline=time.monotonic()+900
  while time.monotonic()<deadline:
   if cancel() or self.w.cancelled():raise InterruptedError('CONTROL73_CANCELLED')
   if until!='COMMIT' and getattr(self.w,'job',None):
    native=self.w.job.status()
    if native['rootExited'] or native['logFailed']:raise ValueError('REMOTE70_CHILD_ERROR')
   if until=='COMMIT':self.w.require_source()
   if time.monotonic()>=next_post:
    try:self.call('post',self.file,action,self.inventory,request)
    except (ValueError,subprocess.SubprocessError):pass # The server may have received the POST.
    next_post=time.monotonic()+3
   try:
    s=self.status();member=next(row.split(',') for row in s['clients'].split(';') if row.split(',')[0]==self.cfg['client'])
    if member[2]==str(request) and member[1] in ({'PREPARED','INSTALLED','JOINED'} if action=='PREPARED' else {'INSTALLED','JOINED'}) and (until!='COMMIT' or s['phase']=='COMMITTED') and (until!='ADMISSION' or s['admission']=='true' and int(s['installed'])>=int(s['needed'])):
     if until=='COMMIT':self.committed=True
     return s
    if notify:notify('Ожидаем обновления игроков: '+s['installed']+' / '+s['needed']+'. Разрешение сервера: '+('получено' if s['admission']=='true' else 'ожидается'))
   except (ValueError,subprocess.SubprocessError):pass
   time.sleep(1)
  raise ValueError('CONTROL73_WAIT_TIMEOUT')
 def prepare(self):
  self.w.state('WAITING_SERVER');self.receipt('PREPARED','COMMIT',cancel=self.w.cancelled)
 def check(self):
  s=self.status()
  if s['phase']!='COMMITTED':raise ValueError('CONTROL73_NOT_COMMITTED')
  return s
 def installed(self,notify,cancel,ask):
  if inventory(self.w.root/'generations/client-1')!=self.inventory:raise ValueError('CONTROL73_INVENTORY')
  self.receipt('INSTALLED','ADMISSION',notify,cancel,ask)
 def joined(self):
  # Keep the identical signed receipt until the server confirms it.
  if self.join_request is None:self.join_request=uuid.uuid4()
  try:
   try:self.call('post',self.file,'JOINED',self.inventory,self.join_request,timeout=5)
   except (ValueError,OSError,subprocess.SubprocessError):pass # The server may have received the POST.
   state=self.status(timeout=5)
   member=next((row.split(',') for row in state['clients'].split(';') if row.split(',')[0]==self.cfg['client']),None)
   return bool(member and member[1:3]==['JOINED',str(self.join_request)])
  except InterruptedError:
   return True # A closed or cancelled round can no longer accept receipts.
  except (KeyError,ValueError,OSError,subprocess.SubprocessError):
   return False
 def abort(self):
  if self.committed:return
  try:self.call('post',self.file,'ABORT',self.inventory,uuid.uuid4())
  except Exception:pass

def prepare(worker,raw=None,before_ready=None):
 if raw is None:
  f=worker.folder/'control73.cfg'
  if f.exists():raw=safe(f).read_bytes()
 gate=None
 if raw is not None:
  gate=Gate73(worker,raw);worker.gate73=gate
 if before_ready:before_ready(worker)
 if gate:gate.prepare()
 from .bootstrap76 import attach
 worker.gate73=attach(worker,gate);return worker.gate73
