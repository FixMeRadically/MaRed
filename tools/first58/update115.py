"""Connect a reviewed Script snapshot to the existing folder owner and Changes104.
Local OS owner only. No remote permit, client distribution or world downgrade.
"""
from pathlib import Path
import hashlib,json,re,subprocess,time,uuid
from .common import safe,sha,inventory,json_write
from .bridge67 import write
from .changes104 import Changes104
from . import runtime,dedicated74

FIELDS={'schema','run','generation','request','action','label','snapshot','seal','inventory','target','sha256','previous'}
def packet(d):
 if set(d)!=FIELDS or d['schema']!='115' or d['action']!='APPLY115' or d['label']!='r1':raise ValueError('SERVER115_REQUEST')
 for field in ('request','run'):
  if str(uuid.UUID(d[field]))!=d[field] or uuid.UUID(d[field]).int==0:raise ValueError('SERVER115_REQUEST')
 if not re.fullmatch('[1-9][0-9]{0,8}',d['generation']):raise ValueError('SERVER115_REQUEST')
 for field in ('seal','inventory','sha256'):
  if not re.fullmatch('[a-f0-9]{64}',d[field]):raise ValueError('SERVER115_REQUEST')
 if d['previous'] and not re.fullmatch('[a-f0-9]{64}',d['previous']):raise ValueError('SERVER115_REQUEST')
 if '..' in d['target'] or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]{0,119}\.jar',d['target']):raise ValueError('SERVER115_REQUEST')
 if not Path(d['snapshot']).is_absolute() or len(d['snapshot'])>4096:raise ValueError('SERVER115_REQUEST')
 return d

def verify_snapshot(owner,d):
 """Read-only verification with the controller's bundled core, never the project's classpath."""
 cp=runtime.core_classpath(owner.app)
 args=[owner.c['java'],'-cp',str(cp),'com.fixmer.genesis.technology.server.remote.ServerUpdate115',str(owner.server),d['request']]
 log=safe(owner.root/'logs/update115-verification.log')
 with log.open('wb') as output:
  with subprocess.Popen(args,cwd=owner.app,env=dedicated74.environment(owner.c['java'],owner.server),stdout=output,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0),close_fds=True) as p:
   deadline=time.monotonic()+60
   try:
    while p.poll() is None:
     if time.monotonic()>=deadline:p.kill();p.wait();raise ValueError('SERVER115_VERIFY_TIMEOUT')
     owner.report();time.sleep(.1)
   except BaseException:
    if p.poll() is None:p.kill();p.wait()
    raise
   if p.returncode:raise ValueError('SERVER115_REVIEW_CHANGED')

def inventory_hash(entries):
 return hashlib.sha256(''.join('mods/'+n+'='+v+'\n' for n,v in sorted(entries.items())).encode('ascii')).hexdigest()

def prepare(owner,d):
 packet(d)
 if d['run']!=owner.run or d['generation']!=str(owner.generation) or d['inventory']!=owner.expected or inventory(owner.server)!=owner.expected:raise ValueError('SERVER115_REVIEW_CHANGED')
 if owner.changes.load() or (owner.root/'review104').exists():raise ValueError('SERVER115_CHANGE_PENDING')
 verify_snapshot(owner,d)
 # Check again after provenance verification and after copying; never trust a mutable source path.
 if inventory(owner.server)!=d['inventory']:raise ValueError('SERVER115_REVIEW_CHANGED')
 source=safe(Path(d['snapshot'])/'artifact.jar')
 if sha(source,64*1024*1024)!=d['sha256']:raise ValueError('SERVER115_REVIEW_CHANGED')
 owner.update_change115=owner.changes.stage({d['target']:source},approval=owner.changes.approve(owner.changes.review({d['target']:source})))
 loaded=owner.changes.load()
 if not loaded:raise ValueError('SERVER115_UNCHANGED')
 _,plan,_=loaded
 if inventory_hash(plan['before'])!=d['inventory'] or set(plan['changes'])!={d['target']} or plan['changes'][d['target']]!={'old':d['previous'],'new':d['sha256']} or inventory(owner.server)!=d['inventory']:raise ValueError('SERVER115_REVIEW_CHANGED')
 return loaded[0]['id']

def cancel_prepared(owner,change):
 """Only a never-applied transaction with exactly the old inventory can be discarded."""
 loaded=owner.changes.load()
 if change and loaded and loaded[0]['id']==change and loaded[0]['phase']=='PREPARED' and owner.changes.inventory()==loaded[1]['before']:
  owner.changes.restore();owner.changes.finish(False)

def result(owner,d,status,error='',candidate='',change=''):
 try:
  json_write(owner.root/'logs/update115.json',dict(schema=115,request=d['request'],run=owner.run,status=status,error=error,target=d['target'],sha256=d['sha256'],previous=d['previous'],change=change,candidateLaunch=candidate,generation=owner.generation))
 except OSError:
  # The authenticated owner state and the change journal remain authoritative.
  pass

def execute(owner,d):
 """Return True to keep supervising, False for a terminal/review state. Stop/save gates are real owner methods."""
 from .inplace104 import config,lease,error_code
 owner.update_change115=None;owner.busy=True;owner.report('CHECKING','');change='';candidate='';stopped=False;original_launch=owner.launch
 try:
  next_config=config(owner.server,Path(owner.c['java']))
  if next_config!=owner.c:raise ValueError('SERVER115_CONFIG_CHANGED')
  with lease(owner.root/'game104.lock'):change=prepare(owner,d)
 except Exception as failed:
  cancel_prepared(owner,owner.update_change115);owner.busy=False;owner.request_result='REJECTED';owner.report('RUNNING',error_code(failed));result(owner,d,'REJECTED',error_code(failed));return True
 try:
  owner.stop_game() # matching saved receipt, normal root exit and empty Job are all mandatory
  owner.reap();stopped=True
  with lease(owner.root/'game104.lock'),owner.world_idle():
   if inventory(owner.server)!=d['inventory']:raise ValueError('SERVER115_REVIEW_CHANGED')
   owner.changes.apply()
  owner.c=next_config;owner.report('RESTARTING','');owner.start_game();candidate=owner.launch
  owner.busy=False;owner.request_result='COMPLETED';owner.report('RUNNING','');result(owner,d,'APPLIED',candidate=candidate,change=change);return True
 except Exception as failed:
  reason=error_code(failed);candidate=owner.launch if owner.launch!=original_launch else ''
  result(owner,d,'FAILED',reason,candidate,change)
  owner.hold_live(reason) # never close/kill a live or unknown Job just because a timer expired
  owner.reap()
  # Once world loading began, restoring code and restarting an old world format is not automatic.
  world_started=bool(candidate and owner.channel and (owner.channel/'world104').exists())
  if not stopped or world_started:
   review='WORLD_MAY_HAVE_CHANGED' if world_started else 'UPDATE115_SAVE_UNCONFIRMED'
   write(owner.root/'review104',owner.key,dict(schema='104',reason=review))
   owner.busy=False;owner.request_result='FAILED';owner.report('RECOVERY_REQUIRED','SERVER115_WORLD_REVIEW');result(owner,d,'REVIEW_REQUIRED',reason,candidate,change);return False
  try:
   with lease(owner.root/'game104.lock'),owner.world_idle():
    pending=owner.changes.load()
    if not pending:raise ValueError('SERVER115_JOURNAL_MISSING')
    if pending[0]['phase']!='RESTORED':owner.changes.restore()
    owner.changes.finish(False)
   owner.report('RESTARTING','SERVER115_RESTORING');owner.start_game() # exactly one old-version attempt
  except Exception as restore_error:
   owner.hold_live(error_code(restore_error));owner.reap();owner.busy=False;owner.request_result='FAILED'
   write(owner.root/'review104',owner.key,dict(schema='104',reason='UPDATE115_RETURN_FAILED'))
   owner.report('RECOVERY_REQUIRED','SERVER115_RETURN_FAILED');result(owner,d,'RETURN_FAILED',reason,candidate,change);return False
  owner.busy=False;owner.request_result='ROLLED_BACK';owner.report('RUNNING','SERVER115_RESTORED');result(owner,d,'RESTORED',reason,candidate,change);return True
