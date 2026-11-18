"""One bounded old-code return after a failed remote update. No server/world rollback.
Only a normal, retained source exit plus quiescent owned candidate permits a NEW menu copy.
Network silence is not rollback authority; explicit server cancellation or a failed candidate is.
"""
from pathlib import Path
import os,re,secrets,time,uuid,zipfile
from .common import safe,atomic,json_write,inventory,clone
from .bridge67 import write
from .install70 import files
from .desktop66 import live_client_snapshot

ADAPTER='com/fixmer/mared/technology/server/firstlaunch/RemoteReturn119.class'
FAILURES={'ADMISSION82_WAIT_TIMEOUT','CONTROL73_WAIT_TIMEOUT','REMOTE70_JOIN_TIMEOUT','AUTH89_LOGIN_REQUIRED','AUTH89_UNAVAILABLE','AUTH89_HANDOFF_TIMEOUT','REMOTE70_CHILD_ERROR','REMOTE70_READY','CONTROL73_CANCELLED','BOOTSTRAP76_CANCELLED','REMOTE70_CANDIDATE','REMOTE70_PACKAGE_CHANGED'}

def eligible(w,error,cancel):
 code=error.args[0] if len(error.args)==1 and isinstance(error.args[0],str) else ''
 return bool(code in FAILURES and not getattr(w,'return_attempt119',False) and not getattr(w,'world_ready119',False) and w.lease and w.lease.closed() and w.lease.normal() and not cancel() and not w.cancelled())

def menu_argv(argv):
 """Strip native quick-play entry points. Never synthesize credentials or shell commands."""
 flags={'--server','--port','--quickPlayPath','--quickPlaySingleplayer','--quickPlayMultiplayer','--quickPlayRealms'}
 out=[];i=0
 while i<len(argv):
  a=argv[i];key=a.split('=',1)[0]
  if key in flags:
   if '=' not in a:
    if i+1>=len(argv) or argv[i+1].startswith('--'):raise ValueError('RETURN119_ARGUMENTS')
    i+=1
  else:out.append(a)
  i+=1
 return out

def adapter(w):
 if w.spec.get('devSnapshot'):
  root=safe(Path(w.spec['devSnapshot']['source']))
  return any(safe(root/'mods'/group/ADAPTER).is_file() for group,_ in w.spec['devSnapshot']['roots'])
 for name in w.approved['bootstrap']:
  with zipfile.ZipFile(safe(w.source/'mods'/name)) as jar:
   if ADAPTER in jar.namelist():return True
 return False

def quiet(w):
 """Retain the Job while observing active=0; exit code need not be zero for a failed candidate."""
 if not w.job:return
 state=w.job.status()
 if not state['rootExited']:w.command('STOP')
 end=time.monotonic()+30
 while True:
  s=w.job.status()
  if s['rootExited'] and s['active']==0:break
  if time.monotonic()>=end:
   terminate=getattr(w.job,'terminate_failed119',None)
   if terminate is None:raise ValueError('RETURN119_CHILD_BUSY')
   terminate()
   state=w.job.status()
   if not state['rootExited'] or state['active']!=0:raise ValueError('RETURN119_CHILD_BUSY')
   break
  time.sleep(.1)
 w.job.close();w.job=None

def redact(text):
 text=re.sub(r'(?i)((?:access[_-]?token|refresh[_-]?token|client[_-]?secret|password|GENESIS_SUPERVISOR_TOKEN)\s*[\"\x27]?\s*[=:]\s*[\"\x27]?)[^\s\"\x27,;}]+',r'\1[REDACTED]',text)
 text=re.sub(r'(?i)(--(?:accessToken|refreshToken|password)\s+)[^\s]+',r'\1[REDACTED]',text)
 return re.sub(r'(?i)(Bearer\s+)[A-Za-z0-9._~+/-]+',r'\1[REDACTED]',text)

def tail(path,limit=90*1024):
 try:
  path=safe(path)
  if not path.exists():return ''
  with path.open('rb') as f:
   size=f.seek(0,2);f.seek(max(0,size-limit));raw=f.read(limit)
  return redact(raw.decode('utf8','replace'))
 except (OSError,ValueError):return '[log unavailable; original retained]'

def crash_tail(oldroot):
 try:
  folder=safe(oldroot/'generations/client-1/crash-reports')
  if not folder.exists():return ''
  newest=None;stamp=-1
  for i,path in enumerate(folder.iterdir()):
   if i>=128:break
   path=safe(path)
   if path.is_file() and path.stat().st_mtime_ns>stamp:newest=path;stamp=path.stat().st_mtime_ns
  return tail(newest) if newest else ''
 except (OSError,ValueError):return '[crash log unavailable; original retained]'

def report(profile,oldroot,code,run):
 """Use the existing in-game failure101 viewer, not a second external error window."""
 folder=safe(profile/'config/mared/dev/failures101'/run);folder.mkdir(parents=True,exist_ok=True)
 text=('Remote update failed: '+code+'\nPrevious mod set restored to a NEW menu-only client.\n'
       'Server readiness/version is not assumed; reconnection requires a fresh signed old-code permit.\n\n'
       '=== failed candidate console ===\n'+tail(oldroot/'client.log')+'\n\n=== failed candidate latest.log ===\n'+tail(oldroot/'generations/client-1/logs/latest.log'))
 atomic(folder/'console.log',text.encode('utf8'))
 crash=crash_tail(oldroot)
 if crash:atomic(folder/'crash.log',crash.encode('utf8'))
 atomic(folder/'report.properties',('schema=101\nid='+run+'\nstatus=RESTORED\nreason=PROCESS_EXIT\nack=false\ncreated='+str(int(time.time()*1000))+'\n').encode('ascii'))
 atomic(folder.parent/'latest.properties',('schema=101\nid='+run+'\n').encode('ascii'))


def restore_menu(w,error,notify,cancel,ask,wait_lifetime=True):
 # All exceptions propagate: one failed return never recursively starts another game.
 w.return_attempt119=True;oldroot=w.root;code=error.args[0];result={'schema':119,'status':'PREPARING','reason':code,'automaticJoin':False,'attempts':1}
 w.return_result119=result;w.state('RETURNING');notify('RETURNING_PREVIOUS_CLIENT')
 try:
  quiet(w)
  if cancel() or w.cancelled():raise InterruptedError('REMOTE70_CANCELLED')
  if not w.lease.closed() or not w.lease.normal() or files(w.source/'mods')!=w.baseline:raise ValueError('REMOTE70_SOURCE_CHANGED')
  if not adapter(w):raise ValueError('RETURN119_ADAPTER_REQUIRED')
  root=safe(oldroot/'return119');root.mkdir();(root/'generations').mkdir();profile=root/'generations/client-1.building'
  live_client_snapshot(w.source,profile,lambda:cancel() or w.cancelled(),excluded={'mods.before70','remote70-temp'})
  if w.spec.get('devSnapshot'):clone(safe(Path(w.spec['devSnapshot']['source'])),profile/'.genesis-dev62')
  if files(profile/'mods')!=w.baseline or files(w.source/'mods')!=w.baseline:raise ValueError('REMOTE70_SOURCE_CHANGED')
  # Never replay inherited world/server resume intent. Local worlds were excluded by the copier.
  resume=safe(profile/'config/mared/dev/resume.properties');resume.parent.mkdir(parents=True,exist_ok=True)
  atomic(resume,('id='+str(uuid.uuid4())+'\nkind=hold91\nsourcePid=0\ncreatedAt='+str(int(time.time()*1000))+'\n').encode('ascii'))
  key=secrets.token_bytes(32);config=dict(run=w.id,account=w.account,game=w.info['game'],inventory=inventory(profile),minecraft='1.21.1',neoforge=w.r['neoforge'],devHash=w.spec.get('devSnapshot',{}).get('hash','none'),return119='MENU_ONLY')
  if w.r.get('auth89') is True:config['authMode']=w.r['authMode']
  from .return120 import prepare as prepare_return120
  context120=prepare_return120(w,root)
  if context120:config['return120']=context120
  report(profile,oldroot,code,w.id)
  atomic(root/'channel.key',key,new=True);write(root/'session70',key,config);write(profile/'remote70.marker',key,config)
  profile.rename(root/'generations/client-1');profile=root/'generations/client-1'
  # Original update gate is never reused to authorize old-code JOIN. Source credentials stay private.
  w.root=root;w.key=key;w.config=config;w.seq=0;w.pending_command=None;w.gate73=None;w.returning119=True
  w.spec=dict(w.spec,clientCommand=menu_argv(w.spec['clientCommand']))
  json_write(root/'launch-private70.json',w.spec,new=True)
  if cancel() or w.cancelled():raise InterruptedError('REMOTE70_CANCELLED')
  w.start()
  w.wait(lambda:w.status() and w.status()['phase']=='MENU_FRAME_READY',120,'RETURN119_MENU_TIMEOUT')
  result['status']='RESTORED';json_write(oldroot/'return119.json',result);w.state('RESTORED');notify('BASELINE_READY');w.command('FOCUS')
  # This is menu recovery, not admission, JOINED or WORLD_FRAME_READY. Keep owner alive for this child.
  if context120:
   from .return120 import reconnect
   reconnect(w,notify,cancel,ask)
   json_write(oldroot/'return119.json',result)
  if wait_lifetime:w.watch(notify,cancel,ask)
 except BaseException:
  if result['status']!='RESTORED':result['status']='ACTION_REQUIRED'
  try:json_write(oldroot/'return119.json',result)
  except OSError:pass
  raise
