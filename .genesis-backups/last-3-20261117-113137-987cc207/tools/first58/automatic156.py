"""Owner-local automatic Script publication. Reuse signed pair/consent/install; never bypass participant decisions."""
from pathlib import Path
import hashlib,json,os,re,tempfile,time,uuid
from . import dedicated74 as base,publication79,publication83,publication84,https78
from .common import safe,sha,json_write,valid_mod_name
from .bridge67 import read,write

LIMIT=512*1024*1024

def copy_mods(source,target):
 source=safe(source);target=safe(target)
 if not source.is_dir() or source==target or source in target.parents or target in source.parents:raise ValueError('AUTO156_MODS_SCOPE')
 target.mkdir(parents=True,exist_ok=False);total=0;names=set();receipts={}
 for p in sorted(source.iterdir()):
  if p.suffix.lower()!='.jar':continue
  if p.is_symlink() or not p.is_file() or not valid_mod_name(p.name) or p.name.lower() in names:raise ValueError('AUTO156_MOD_NAME')
  p=safe(p);size=p.stat().st_size
  names.add(p.name.lower());total+=size
  if len(names)>256 or total>LIMIT or size>64*1024*1024:raise ValueError('AUTO156_MOD_LIMIT')
  digest=hashlib.sha256();copied=0
  with p.open('rb') as src,(target/p.name).open('xb') as dest:
   while chunk:=src.read(1024*1024):
    copied+=len(chunk)
    if copied>size:raise ValueError('AUTO156_INPUT_CHANGED')
    digest.update(chunk);dest.write(chunk)
  checksum=digest.hexdigest()
  if copied!=size or sha(target/p.name,64*1024*1024)!=checksum:raise ValueError('AUTO156_INPUT_CHANGED')
  receipts[p.name]=checksum
 if not receipts:raise ValueError('AUTO156_EMPTY_MOD_SET')
 after={p.name:sha(p,64*1024*1024) for p in source.iterdir() if p.suffix.lower()=='.jar'}
 if after!=receipts:raise ValueError('AUTO156_INPUT_CHANGED')
 return receipts

def label():return 'auto-'+time.strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:10]
def job_path(root,job):
 if str(uuid.UUID(job))!=job:raise ValueError('AUTO156_JOB')
 return root/'automatic156'/job

def store(root,key,job,data):
 path=job_path(root,job);path.mkdir(parents=True,exist_ok=True);write(path/'receipt156',key,{k:str(v) for k,v in data.items()});json_write(path/'status.json',data)
 return data

def load(root,key,job):return read(job_path(root,job)/'receipt156',key)
def view(root,key):
 p=root/'view75'
 return read(p,key) if p.is_file() else {}
def command_failure(root,key,run,request,state):
 owner=root/'state104'
 if owner.is_file():
  state=read(owner,key)
  if state.get('run')!=run:raise ValueError('AUTO156_STALE_JOB')
 if state.get('lastRequest')==request or state.get('request')==request:
  result=state.get('requestResult',state.get('result',''))
  if result in {'REJECTED','FAILED','DENIED'}:
   return result,state.get('error') or 'AUTO156_COMMAND_REJECTED'
 return None

def wait_command(root,key,run,request,timeout=90):
 deadline=time.monotonic()+timeout
 while time.monotonic()<deadline:
  state=view(root,key)
  failed=command_failure(root,key,run,request,state)
  if failed:raise ValueError(failed[1])
  yield state
  time.sleep(.4)
 raise ValueError('AUTO156_COMMAND_TIMEOUT')

def prepare(project,root,snapshot,clientmods,job,game='',endpoint='',side='BOTH'):
 from .inplace104 import lease
 root=safe(root);path=job_path(root,job)
 base.settings(root) # Validate that this is a configured server before creating staging files.
 path.parent.mkdir(parents=True,exist_ok=True)
 with lease(path.parent/(job+'.prepare.lock')):
  return prepare_locked(project,root,snapshot,clientmods,job,game,endpoint,side)

def prepare_locked(project,root,snapshot,clientmods,job,game,endpoint,side):
 s,key=base.settings(root);publication79.available(root,key);path=job_path(root,job)
 if side not in {'CLIENT','SERVER','BOTH'}:raise ValueError('SCRIPT84_SIDE_REQUIRED')
 if (path/'receipt156').exists():
  prior=load(root,key,job)
  if prior.get('run')!=s['run'] or prior.get('snapshot')!=str(safe(snapshot)) or prior.get('side','BOTH')!=side:raise ValueError('AUTO156_JOB_CHANGED')
  return prior
 # Existing signed publication is the source of unchanged files. Initial import alone copies current mods.
 try:seed,_=publication79.baseline(root,key,None,project)
 except ValueError as error:
  if str(error)!='PUBLICATION79_BASELINE_REQUIRED':raise
  seed=None
 if seed is None:
  _,_,tls=https78.load(root)
  endpoint=(tls or {}).get('endpoint') or endpoint
  if not endpoint:raise ValueError('AUTO156_DISTRIBUTION_SETUP_REQUIRED')
  if not game:
   from urllib.parse import urlsplit
   host=urlsplit(endpoint).hostname
   if not host:raise ValueError('AUTO156_DISTRIBUTION_SETUP_REQUIRED')
   game=(('['+host+']') if ':' in host else host)+':'+str(s['port'])
  if game.rsplit(':',1)[-1]!=str(s['port']):raise ValueError('AUTO156_GAME_PORT')
  if s['auth']=='false':
   from urllib.parse import urlsplit
   if game.rsplit(':',1)[0] not in {'localhost','127.0.0.1','[::1]'} or urlsplit(endpoint).hostname not in {'localhost','127.0.0.1','::1'}:raise ValueError('AUTO156_OFFLINE_CLIENT_ROLLOUT_UNAVAILABLE')
  with tempfile.TemporaryDirectory(prefix='.import156-',dir=path.parent) as directory:
   staging=Path(directory)
   copy_mods(clientmods,staging/'client');copy_mods(Path(s['profile'])/'mods',staging/'server')
   initial=label();preview=publication83.first(project,root,staging/'client',staging/'server',initial,86400,game,endpoint)
   first=publication83.first(project,root,staging/'client',staging/'server',initial,86400,game,endpoint,preview['ticket'])
   seed=safe(Path(first['path'])/'public')
 name=label();prepared=publication84.run(project,root,'script-prepare',snapshot,side=side,fallback=seed)
 preview=publication84.run(project,root,'script-preview',Path(prepared['draft']),label=name,ttl=86400,fallback=seed)
 created=publication84.run(project,root,'script-author',Path(prepared['draft']),label=name,ttl=86400,ticket=preview['ticket'],fallback=seed)
 return store(root,key,job,dict(schema='156',run=s['run'],job=job,label=created['authored'],side=side,snapshot=str(safe(snapshot)),baseline=str(seed.parent),generation=view(root,key).get('generation',''),phase='READY',release=created.get('release',''),created=str(int(time.time()*1000))))

def launch(project,root,job,timeout=420,cert='',private_key='',bind='127.0.0.1'):
 from .inplace104 import lease
 root=safe(root);folder=job_path(root,job)
 if not (folder/'receipt156').is_file():raise ValueError('AUTO156_JOB_MISSING')
 with lease(folder/'operation156.lock'):
  return launch_locked(project,root,job,timeout,cert,private_key,bind)

def launch_locked(project,root,job,timeout=180,cert='',private_key='',bind='127.0.0.1'):
 from .dedicated75 import submit
 root=safe(root);s,key=base.settings(root);data=load(root,key,job)
 if data.get('schema')!='156' or data.get('run')!=s['run'] or data.get('job')!=job:raise ValueError('AUTO156_STALE_JOB')
 if data['phase']=='COMMITTED':return data
 if data['phase'] in {'COMMIT_PENDING','COMMIT_QUEUED'}:
  queued=data.get('request')
  if queued:
   current=view(root,key)
   failed=command_failure(root,key,s['run'],queued,current)
   if failed:
    result,code=failed
    control=json.loads(current.get('control','{}'))
    if result!='REJECTED' or control.get('phase')!='PREPARING' or control.get('release')!=data.get('release') or not data.get('stageRequest'):
     raise ValueError(code)
    # A new explicit Launch may retry a definitively rejected command in the same consent round.
    data['phase']='WAITING';data.pop('request',None);store(root,key,job,data)
   elif (root/'request74').is_file():
    request=read(root/'request74',key)
    if all(request.get(k)==v for k,v in dict(run=s['run'],request=queued,action='COMMIT',label=data['label']).items()):
     data['phase']='COMMIT_QUEUED';return store(root,key,job,data)
  if data['phase']=='COMMIT_QUEUED':return data
 if data['phase'] not in {'READY','WAITING'}:raise ValueError('AUTO156_REVIEW_INTERRUPTED_JOB')
 resume=data['phase']=='WAITING'
 _,_,tls=https78.load(root)
 if not tls:
  if bool(cert)!=bool(private_key):raise ValueError('HTTPS159_CERT_PAIR_REQUIRED')
  if not cert:
   info=https78.public_info(project,s['java'],root/'releases'/data['label']/'public')
   identity=publication83.command(project,root,'com.fixmer.genesis.technology.server.remote.TlsIdentity159',root/'publisher73',root/'tls159',info['endpoint'])
   cert=identity['cert'];private_key=identity['key']
  https78.save(project,root,root/'releases'/data['label']/'public',Path(cert),Path(private_key),bind,Path(s['java']))
 if tls and tls.get('enabled')=='false':raise ValueError('HTTPS78_DISTRIBUTION_DISABLED')
 deadline=time.monotonic()+90
 while True:
  status=https78.status(root)
  if status.get('state')=='RUNNING_TLS':break
  if status.get('state')=='FAILED':raise ValueError(status.get('code') or 'HTTPS159_LISTENER_FAILED')
  if time.monotonic()>=deadline:raise ValueError('HTTPS159_LISTENER_TIMEOUT')
  time.sleep(.5)
 # First baseline is created automatically but remains a real signed controller selection.
 if not (root/'publication75').is_file():
  baseline=Path(data['baseline']);request=submit(root,'PUBLISH_BASELINE',baseline.name)
  for state in wait_command(root,key,s['run'],request,min(timeout,90)):
   if (root/'publication75').is_file() and read(root/'publication75',key).get('label')==baseline.name:break
 data['phase']='OFFERING';store(root,key,job,data)
 try:
  if not resume:
   capability=json.loads(view(root,key).get('control','{}')).get('autoStage157') is True
   data['ownerAuto']='true' if capability else 'false'
  owner_auto=data.get('ownerAuto')=='true'
  request=data.get('stageRequest') if resume else submit(root,'STAGE_AUTO' if owner_auto else 'STAGE',data['label'])
  if request:
   data['stageRequest']=request;store(root,key,job,data)
  deadline=time.monotonic()+timeout
  while time.monotonic()<deadline:
   current=view(root,key);control=json.loads(current.get('control','{}'));phase=control.get('phase','')
   failed=command_failure(root,key,s['run'],request,current) if request else None
   if failed:
    data['phase']='READY';data.pop('stageRequest',None);store(root,key,job,data)
    raise ValueError(failed[1])
   if not owner_auto and phase=='PREPARING' and control.get('release')==data.get('release'):
    accepted=int(control.get('accepted',0))-int(control.get('aborted',0));prepared=int(control.get('prepared',0));pending=int(control.get('pending',0))
    if accepted==0 and pending==0:
     submit(root,'CANCEL',data['label'])
     data['phase']='CANCELLED';store(root,key,job,data)
     raise ValueError('AUTO156_NO_ACCEPTANCE')
    if accepted>0 and pending==0 and prepared==accepted:
     # Record before enqueue: interruption must not silently replay the commit.
     nonce=str(uuid.uuid4());data['phase']='COMMIT_PENDING';data['request']=nonce;store(root,key,job,data)
     # A crash after enqueue can be reconciled against the exact signed request.
     if submit(root,'COMMIT',data['label'],request=nonce)!=nonce:
      raise ValueError('AUTO156_COMMIT_NONCE')
     data['phase']='COMMIT_QUEUED';return store(root,key,job,data)
   if (root/'rollout118').is_file():
    rollout=read(root/'rollout118',key)
    if rollout.get('run')==s['run'] and rollout.get('label')==data['label']:
     outcome=rollout.get('phase')
     if outcome=='CANCELLED':
      data['phase']='CANCELLED';store(root,key,job,data)
      raise ValueError('AUTO156_ROUND_CANCELLED')
     if owner_auto and outcome in {'FAILED','RESTORED','INTERRUPTED'}:
      data['phase']=outcome;store(root,key,job,data)
      raise ValueError(rollout.get('reason') or 'AUTO156_ROUND_'+outcome)
     if owner_auto and outcome in {'COMMIT_SEEN','COMMITTED','STOPPED_SAVED','STARTING_TARGET','ACTIVE_UPDATED','CLOSED'}:
      data['phase']='COMMIT_QUEUED';return store(root,key,job,data)
   time.sleep(.5)
  raise ValueError('AUTO156_WAITING_FOR_CONSENT')
 except Exception:
  # Never auto-cancel a committed update. Record uncertainty for an explicit next operator action.
  if data['phase']=='OFFERING':data['phase']='WAITING';store(root,key,job,data)
  raise


def failure(root,job,error):
 # Diagnostics are not authority: never overwrite the signed phase around an uncertain commit.
 try:
  root=safe(root);_,key=base.settings(root);folder=job_path(root,job);folder.mkdir(parents=True,exist_ok=True)
  prior=load(root,key,job) if (folder/'receipt156').exists() else {}
  code=str(error);code=code if re.fullmatch('[A-Z][A-Z0-9_]{1,120}',code) else 'AUTO156_OPERATION_FAILED'
  json_write(folder/'error.json',dict(job=job,phase=prior.get('phase','PREPARING'),code=code,at=int(time.time()*1000)))
 except Exception:pass
