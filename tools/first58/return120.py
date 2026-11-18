"""Reconnect an already-restored client ONLY with a fresh signed old-code server permit."""
from pathlib import Path
import base64,hashlib,json,subprocess,zipfile,time,uuid
from .common import safe,atomic
from .runtime import core_classpath
from .control73 import decode as config_decode
from .bootstrap_wire76 import decode
SENTINEL='com/fixmer/mared/technology/server/firstlaunch/RemoteReturn120.class'

def prepare(w,root):
 gate=getattr(w,'gate73',None);member=getattr(gate,'member',None) if gate else None
 if member is None and gate and hasattr(gate,'cfg'):member=gate
 if member is None or not hasattr(member,'file') or not hasattr(member,'key'):return None
 if w.spec.get('devSnapshot'):
  frozen=Path(w.spec['devSnapshot']['source']);supported=any(safe(frozen/'mods'/group/SENTINEL).is_file() for group,_ in w.spec['devSnapshot']['roots'])
 else:
  supported=False
  for name in w.approved['bootstrap']:
   with zipfile.ZipFile(safe(w.source/'mods'/name)) as jar:supported=supported or SENTINEL in jar.namelist()
 if not supported:return None
 raw=safe(member.file).read_bytes();cfg=config_decode(raw);pub=safe(member.key).read_bytes()
 if cfg['account']!=w.account or cfg['game']!=w.info['game'] or hashlib.sha256(pub).hexdigest()!=cfg['signer']:raise ValueError('RETURN120_CONTEXT')
 atomic(root/'return120.cfg',raw,new=True);atomic(root/'return120.public',pub,new=True)
 return hashlib.sha256((hashlib.sha256(raw).hexdigest()+hashlib.sha256(pub).hexdigest()).encode('ascii')).hexdigest()

class ReturnGate120:
 automatic73=True
 def __init__(self,w):self.w=w;self.last=None;self.committed=True
 def before_join(self):
  w=self.w;profile=w.root/'generations/client-1'
  from .install70 import files
  rows=files(profile/'mods');rows={k:v for k,v in rows.items() if k not in w.approved['bootstrap']}
  inventory=hashlib.sha256(''.join('mods/'+k+'='+v+'\n' for k,v in sorted(rows.items())).encode('ascii')).hexdigest()
  r=subprocess.run([w.spec['clientCommand'][0],'-cp',str(core_classpath(w.folder)),'com.fixmer.genesis.technology.server.remote.ReturnAdmission120','poll',str(w.root/'return120.cfg'),str(w.root/'return120.public'),inventory,'1.21.1',w.r['neoforge'],str(w.root/'return120.grant')],capture_output=True,text=True,timeout=30,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
  if r.returncode:raise ValueError('RETURN120_UNAVAILABLE')
  d=decode(base64.b64decode(r.stdout.strip(),validate=True))
  if self.last and (any(d[k]!=self.last[k] for k in ('owner','generation','round','inventory','serverInventory','release')) or int(d['serial'])<int(self.last['serial']) or d['serial']==self.last['serial'] and d!=self.last):raise ValueError('RETURN120_CHANGED')
  self.last=d;return d
 def heartbeat120(self):
  now=time.monotonic()
  if now-getattr(self,"heartbeat_at120",-10)<1:return
  from .repeat71 import publish
  publish(self.w,str(uuid.UUID(int=0)),"CONNECTING");self.heartbeat_at120=now
 def joined(self):return True
 def abort(self):pass

def reconnect(w,notify,cancel,ask):
 if not w.config.get('return120'):return False
 w.gate73=ReturnGate120(w)
 try:
  try:joined=w.join(notify,cancel,ask)
  except ValueError as error:
   code=error.args[0] if len(error.args)==1 and isinstance(error.args[0],str) else ''
   if code not in {'REMOTE70_JOIN_TIMEOUT','AUTH89_LOGIN_REQUIRED','AUTH89_HANDOFF_TIMEOUT','AUTH89_UNAVAILABLE'}:raise
   # The previous client is already usable. A missing old-code admission must not kill it.
   w.command('CANCEL');w.return_result119['reconnectReason']=code;w.state('RESTORED');return False
  if joined:
   w.return_result119['connected']=True;w.state('RESTORED_CONNECTED')
  else:w.state('RESTORED')
  return joined
 finally:
  # No private failed-round receipts or JOINED are fabricated for the restored code.
  w.gate73=None
