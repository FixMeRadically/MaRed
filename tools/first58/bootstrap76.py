"""Fresh public pre-login admission gate plus a private per-account/server owner lease."""
import base64,hashlib,os,subprocess,time
from pathlib import Path
from .common import safe
from .bootstrap_wire76 import decode
from .runtime import core_classpath
class ApplyLease76:
 def __init__(self,cache,account,game,endpoint):
  root=safe(Path(cache)/'owners76');root.mkdir(exist_ok=True);name=hashlib.sha256((account+'\n'+game+'\n'+endpoint).encode()).hexdigest();self.file=safe(root/(name+'.lock')).open('a+b')
  if self.file.tell()==0:self.file.write(b'0');self.file.flush()
  self.file.seek(0)
  try:
   if os.name=='nt':
    import msvcrt
    msvcrt.locking(self.file.fileno(),msvcrt.LK_NBLCK,1)
   else:
    import fcntl
    fcntl.flock(self.file,fcntl.LOCK_EX|fcntl.LOCK_NB)
  except (OSError,BlockingIOError):self.file.close();self.file=None;raise ValueError('BOOTSTRAP76_OWNER_BUSY')
 def close(self):
  if self.file:
   self.file.seek(0)
   if os.name=='nt':
    import msvcrt
    msvcrt.locking(self.file.fileno(),msvcrt.LK_UNLCK,1)
   else:
    import fcntl
    fcntl.flock(self.file,fcntl.LOCK_UN)
   self.file.close();self.file=None
class Gate76:
 def __init__(self,w):self.w=w;self.enabled=True;self.last=0;self.expected=None;self.committed=True
 def status(self):
  w=self.w;relaxed=getattr(self,'runtime86',False)
  args=['com.fixmer.genesis.technology.server.remote.BootstrapRenewal86','join',w.r['cache'],w.r['candidate'],'1.21.1',w.r['neoforge']] if relaxed else ['com.fixmer.genesis.technology.server.remote.BootstrapGateCli76','poll',w.r['candidate'],'1.21.1',w.r['neoforge']]
  r=subprocess.run([w.spec['clientCommand'][0],'-cp',str(core_classpath(w.folder)),*args],capture_output=True,text=True,timeout=30,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
  if r.returncode:raise ValueError('BOOTSTRAP76_UNAVAILABLE')
  d=decode(base64.b64decode(r.stdout.strip(),validate=True));serial=int(d['serial'])
  if self.expected and any(d[k]!=self.expected[k] for k in (('owner','generation','inventory','game','endpoint','signer','distribution') if relaxed else ('owner','generation','release','archive','inventory','game','endpoint','signer','distribution'))) or serial<self.last:raise ValueError('BOOTSTRAP76_CHANGED')
  if relaxed and self.expected and 'sequence86' in self.expected and (int(d['sequence86'])<int(self.expected['sequence86']) or int(d['sequence86'])==int(self.expected['sequence86']) and d['offer']!=self.expected['offer']):raise ValueError('BOOTSTRAP86_ROLLBACK')
  if self.expected and serial==self.last and {k:v for k,v in d.items() if k!='sequence86'}!={k:v for k,v in self.expected.items() if k!='sequence86'}:raise ValueError('BOOTSTRAP76_EQUIVOCATION')
  self.expected=d;self.last=serial
  if d['phase'] not in {'READY','TRANSITION'}:raise ValueError('BOOTSTRAP76_UNAVAILABLE')
  return d
 def prepare(self):self.check()
 def before_join(self):
  if not getattr(self,'runtime86',False):raise ValueError('BOOTSTRAP86_RUNTIME_REQUIRED')
  if self.check()['phase']!='READY':raise ValueError('BOOTSTRAP76_UNAVAILABLE')
 def check(self):
  d=self.status()
  if not getattr(self,'runtime86',False) and any(d[k]!=self.w.info[j] for k,j in {'release':'release','archive':'archiveSha256','distribution':'distribution','game':'game','endpoint':'endpoint'}.items()):raise ValueError('BOOTSTRAP76_CHANGED')
  return d
 def installed(self,notify,cancel,ask):
  # The normal worker's READY has already verified the loaded final runtime inventory.
  ready=self.w.status()
  if not ready or ready['phase']!='READY':raise ValueError('BOOTSTRAP86_RUNTIME_REQUIRED')
  self.runtime86=True
  question=time.monotonic()+30
  while True:
   if cancel() or self.w.cancelled():raise InterruptedError('BOOTSTRAP76_CANCELLED')
   s=self.w.job.status()
   if s['rootExited'] or s['logFailed']:raise ValueError('REMOTE70_CHILD_ERROR')
   try:
    if self.check()['phase']=='READY':return
   except (ValueError,subprocess.SubprocessError):pass
   notify('Сервер ещё обновляется. Ждём подтверждённой готовности.')
   if time.monotonic()>=question:
    if not ask('Сервер пока не готов. Продолжать ожидание?'):raise InterruptedError('BOOTSTRAP76_CANCELLED')
    question=time.monotonic()+30
   time.sleep(1)
 def joined(self):pass
 def abort(self):pass
class Combined76:
 def __init__(self,public,member):self.public=public;self.member=member;self.committed=True
 def check(self):self.public.check();return self.member.check()
 def installed(self,*args):self.member.installed(*args);self.public.installed(*args)
 def joined(self):return self.member.joined()
 def abort(self):self.member.abort()

def attach(worker,member):
 if not (safe(worker.r['candidate'])/'bootstrap76.cfg').exists():return member
 public=Gate76(worker);public.prepare()
 if member:
  from .admission82 import Combined82
  return Combined82(public,member)
 return public
