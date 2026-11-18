"""Owner-local HTTPS configuration and lifetime. No remote paths, shell or detached helper."""
from pathlib import Path
import ipaddress,json,os,re,ssl,subprocess,threading,time,urllib.parse,uuid
from . import dedicated74 as base
from .bridge67 import read,write
from .common import safe,sha
from .runtime import core_classpath
FIELDS={'schema','run','revision','enabled','public','cert','key','bind','port','endpoint','certSha','keySha','publicSeal','fingerprint','expires'}


def code(error):
 value=error.args[0] if len(error.args)==1 else ''
 return value if isinstance(value,str) and re.fullmatch('HTTPS78_[A-Z0-9_]{1,80}',value) else 'HTTPS78_FAILED'


def java_check(project,java,action,*args):
 p=subprocess.run([str(java),'-XX:-UsePerfData','-cp',str(core_classpath(project)),'com.fixmer.genesis.technology.server.remote.Tls78',action,*map(str,args)],capture_output=True,text=True,timeout=30,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
 if p.returncode:raise ValueError(p.stderr.strip() if re.fullmatch('HTTPS78_[A-Z0-9_]{1,80}',p.stderr.strip()) else 'HTTPS78_CHECK_FAILED')
 return json.loads(p.stdout)


def public_info(project,java,folder,historical=False):
 p=subprocess.run([str(java),'-XX:-UsePerfData','-cp',str(core_classpath(project)),'com.fixmer.genesis.technology.server.remote.Tls78','public-history' if historical else 'public',str(folder)],capture_output=True,text=True,timeout=180,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
 if p.returncode:raise ValueError('HTTPS78_OFFER_TIME' if p.stderr.strip()=='HTTPS78_OFFER_TIME' else 'HTTPS78_PUBLIC_REJECTED')
 return json.loads(p.stdout)


def load(root):
 s,key=base.settings(root);p=safe(root/'https78.cfg')
 if not p.exists():return s,key,None
 d=read(p,key)
 if set(d)!=FIELDS or d['schema']!='78' or d['run']!=s['run'] or d['enabled'] not in {'true','false'} or str(uuid.UUID(d['revision']))!=d['revision']:raise ValueError('HTTPS78_CONFIG')
 for field in ('certSha','keySha','publicSeal','fingerprint'):
  if not re.fullmatch('[a-f0-9]{64}',d[field]):raise ValueError('HTTPS78_CONFIG')
 if not d['port'].isdigit() or not 1<=int(d['port'])<=65535 or not d['expires'].isdigit():raise ValueError('HTTPS78_CONFIG')
 for field in ('public','cert','key'):safe(Path(d[field]))
 return s,key,d


def check(project,root,public,cert,key,bind,java):
 root=safe(root);s,owner_key=base.settings(root)
 if read(root/'owner77',owner_key)!=dict(schema='77',run=s['run']):raise ValueError('HTTPS78_NEW_OWNER_REQUIRED')
 public=safe(public);cert=safe(cert);key=safe(key)
 if public in key.parents or public==key.parent or key==cert:raise ValueError('HTTPS78_PRIVATE_KEY_LOCATION')
 if not cert.is_file() or not key.is_file() or not 1<=cert.stat().st_size<=1048576 or not 1<=key.stat().st_size<=1048576:raise ValueError('HTTPS78_CERTIFICATE_FILES')
 try:ip=ipaddress.ip_address(bind)
 except ValueError:raise ValueError('HTTPS78_BIND') from None
 if str(ip)!=bind or s['auth']=='false' and not ip.is_loopback:raise ValueError('HTTPS78_OFFLINE_LOOPBACK')
 info=public_info(project,java,public,historical=True) if (root/'owner118').exists() else public_info(project,java,public)
 if info['minecraft']!=s['minecraft'] or info['neoforge']!=s['neoforge'] or urllib.parse.urlsplit('minecraft://'+info['game']).port!=int(s['port']):raise ValueError('HTTPS78_VERSION_OR_GAME')
 url=urllib.parse.urlsplit(info['endpoint']);port=url.port or 443
 if sha(public/'public.der',256)!=sha(root/'publisher73/public.der',256):raise ValueError('HTTPS78_PUBLISHER')
 if s['auth']=='false':
  hostname=(url.hostname or '').lower()
  if hostname not in {'localhost','127.0.0.1','::1'}:raise ValueError('HTTPS78_OFFLINE_LOOPBACK')
 if set(p.name for p in public.iterdir())!={'public.der','offer.bin','offer.sig',info['archive']}:raise ValueError('HTTPS78_PUBLIC_FILES')
 certificate=java_check(project,java,'inspect',cert,info['endpoint'])
 try:
  context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.minimum_version=ssl.TLSVersion.TLSv1_2;context.load_cert_chain(cert,key,password=lambda:'')
 except (ssl.SSLError,ValueError,TypeError):raise ValueError('HTTPS78_KEY_PAIR') from None
 config=dict(schema='78',run=s['run'],revision=str(uuid.uuid4()),enabled='true',public=str(public),cert=str(cert),key=str(key),bind=bind,port=str(port),endpoint=info['endpoint'],certSha=sha(cert,1048576),keySha=sha(key,1048576),publicSeal=base.digest(base.tree(public,'unused')),fingerprint=certificate['fingerprint'],expires=certificate['expires'])
 return config


def busy(root,key):
 if not (root/'view75').exists():return False
 view=read(root/'view75',key)
 if view.get('phase') in {'STOPPED_SAVED','STOP_NOT_CONFIRMED','FAILED'} or int(time.time()*1000)-int(view.get('updated','0'))>10000:return False
 try:control=json.loads(view.get('control','{}'))
 except ValueError:raise ValueError('HTTPS78_OWNER_VIEW') from None
 return view.get('busy')=='true' or control.get('phase')=='COMMITTED'


def repair_allowed(root,key,previous):
 if not previous or previous['enabled']!='true' or not (root/'https78.status').is_file():return False
 state=read(root/'https78.status',key)
 return state.get('schema')=='78' and state.get('run')==previous['run'] and state.get('revision')==previous['revision'] and state.get('state')=='FAILED' and -30000<=int(time.time()*1000)-int(state.get('at','0'))<=10000


def save(project,root,public,cert,key,bind,java):
 root=safe(root);s,owner_key,previous=load(root);active_update=busy(root,owner_key)
 if active_update and not repair_allowed(root,owner_key,previous):raise ValueError('HTTPS78_UPDATE_BUSY')
 d=check(project,root,public,cert,key,bind,java)
 if active_update and any(d[field]!=previous[field] for field in ('endpoint','bind','port')):raise ValueError('HTTPS78_UPDATE_BUSY')
 write(root/'https78.cfg',owner_key,d);return status(root)


def disable(root):
 root=safe(root);s,key,d=load(root)
 if not d:raise ValueError('HTTPS78_NOT_CONFIGURED')
 if busy(root,key):raise ValueError('HTTPS78_UPDATE_BUSY')
 d.update(enabled='false',revision=str(uuid.uuid4()));write(root/'https78.cfg',key,d);return status(root)


def status(root):
 root=safe(root);s,key,d=load(root);out=dict(schema=78,run=s['run'],configuration=d or {},state='NOT_CONFIGURED',code='',endpoint=d['endpoint'] if d else '',clientTrust='NOT_TESTED')
 if d:out['state']='SAVED_FOR_OWNER'
 if d and (root/'https78.status').exists():
  view=read(root/'https78.status',key)
  if set(view)!={'schema','run','at','state','code','revision','endpoint','fingerprint'} or view['schema']!='78':raise ValueError('HTTPS78_STATUS')
  if view['run']!=s['run']:return out
  age=int(time.time()*1000)-int(view['at'])
  if not -30000<=age<=10000:out['state']='OWNER_UNAVAILABLE'
  elif view['revision']!=d['revision']:out['state']='CHANGE_PENDING'
  else:out.update({k:view[k] for k in ('state','code','endpoint','fingerprint')})
 return out


def inherit(old_root,new_root):
 # Rebind only a verified local configuration; never copy owner keys or HTTP participant capabilities.
 old_root=safe(old_root);new_root=safe(new_root)
 if (new_root/'https78.cfg').exists():load(new_root);return
 _,_,d=load(old_root)
 if d:
  ns,nk,_=load(new_root);d=dict(d,run=ns['run'],revision=str(uuid.uuid4()))
  # If the original public export is inside the old root, use its unchanged current pair in the new root.
  from .bridge67 import read as packet
  if (new_root/'recovery77').exists():
   p=packet(new_root/'recovery77',nk)
   if p['label']!='none':d['public']=str(new_root/'releases'/p['label']/'public');d['publicSeal']=base.digest(base.tree(Path(d['public']),'unused'))
  write(new_root/'https78.cfg',nk,d)


class Manager78:
 def __init__(self,project,root,java,builder=None):
  self.project=safe(project);self.root=safe(root);self.java=java;self.s,self.key=base.settings(root);self.builder=builder;self.server=None;self.thread=None;self.token=None;self.failed_token=None;self.active=None;self.state='NOT_CONFIGURED';self.reason='';self.due=0;self.retry_at=0
 def emit(self):
  d=self.active
  try:write(self.root/'https78.status',self.key,dict(schema='78',run=self.s['run'],at=str(int(time.time()*1000)),state=self.state,code=self.reason,revision=d['revision'] if d else 'none',endpoint=d['endpoint'] if d else '',fingerprint=d['fingerprint'] if d else ''))
  except Exception:pass # A diagnostic write failure must not terminate the world owner.
 def stop_transport(self):
  server=self.server;thread=self.thread
  if not server:return True
  if thread and thread.is_alive():
   def shutdown():
    try:server.shutdown()
    except Exception:pass
   signal=threading.Thread(target=shutdown,name='genesis-https78-stop',daemon=True);signal.start();signal.join(timeout=2)
  for action in (server.close_connections,server.server_close):
   try:action()
   except Exception:pass
  if thread:
   try:thread.join(timeout=2)
   except Exception:pass
  if thread and thread.is_alive():return False
  self.server=self.thread=None;return True
 def configuration(self,d):return d
 def tick(self,force=False):
  if not force and time.monotonic()<self.due:return
  self.due=time.monotonic()+2
  try:
   _,_,d=load(self.root);token=sha(self.root/'https78.cfg',131072) if d else 'none'
   if self.active and self.server:
    if sha(Path(self.active['cert']),1048576)!=self.active['certSha'] or sha(Path(self.active['key']),1048576)!=self.active['keySha'] or int(self.active['expires'])<=int(time.time()*1000):raise ValueError('HTTPS78_CERTIFICATE_CHANGED')
    if not self.thread.is_alive():raise ValueError('HTTPS78_LISTENER_EXITED')
   retry=self.retry_at and time.monotonic()>=self.retry_at and token==self.failed_token
   if retry:
    self.token=None;self.failed_token=None;self.retry_at=0
   if token!=self.token and token!=self.failed_token:
    if busy(self.root,self.key) and self.server and self.state in {'RUNNING_TLS','CHANGE_DEFERRED'}:self.state='CHANGE_DEFERRED';self.emit();return
    if not self.stop_transport():raise ValueError('HTTPS78_LISTENER_STOP_UNCONFIRMED')
    self.active=d;self.token=token;self.failed_token=None;self.retry_at=0;self.reason=''
    if not d:self.state='NOT_CONFIGURED'
    elif d['enabled']=='false':self.state='DISABLED'
    else:
     d=self.configuration(d);self.active=d
     actual=check(self.project,self.root,Path(d['public']),Path(d['cert']),Path(d['key']),d['bind'],self.java)
     if any(actual[k]!=d[k] for k in FIELDS-{'revision'}):raise ValueError('HTTPS78_CONFIG_CHANGED')
     if self.builder:builder=self.builder
     else:
      from remote_update import publisher_server
      builder=publisher_server
     try:self.server=builder(self.project,self.java,Path(d['public']),Path(d['cert']),Path(d['key']),d['bind'],int(d['port']),self.root)
     except OSError:raise ValueError('HTTPS78_LISTEN_FAILED') from None
     self.thread=threading.Thread(target=self.server.serve_forever,name='genesis-owner78-https',daemon=True);self.thread.start();self.state='RUNNING_TLS'
   self.emit()
  except Exception as error:
   self.stop_transport();self.reason=code(error);self.state='FAILED';self.failed_token=locals().get('token',None)
   self.retry_at=time.monotonic()+2 if self.reason in {'HTTPS78_LISTEN_FAILED','HTTPS78_LISTENER_EXITED'} else 0
   self.emit()
 def close(self):
  stopped=self.stop_transport();self.state='STOPPED' if stopped else 'FAILED';self.reason='' if stopped else 'HTTPS78_LISTENER_STOP_UNCONFIRMED';self.emit()
