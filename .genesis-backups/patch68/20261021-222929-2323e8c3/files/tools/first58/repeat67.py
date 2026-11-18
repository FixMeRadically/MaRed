"""Repeated local apply owned by the SAME controller. No PID adoption or LIVE profile mutation."""
from pathlib import Path
import json,os,re,secrets,shutil,time,uuid
from . import bridge67
from .common import safe,atomic,json_read,json_write,inventory,clone,install_new_generation,sign,record,read_record,sha
from .setup import initialize,seal
from .wizard_model import replacement_inventory,changes,stage_replacement,mod_ids,properties
from .diagnostics63 import failure_data,PilotFailure

IDLE={'ACTIVE','CHECKED','FAILED','CANCELLED'}

def copy_files(source,target,excluded=(),checkpoint=lambda:None):
 source=safe(source);target=safe(target)
 if target.exists() or source==target or source in target.parents or target in source.parents:raise ValueError('New disjoint apply67 copy required')
 target.mkdir();count=total=nodes=0
 for folder,dirs,names in os.walk(source,followlinks=False):
  checkpoint();folder=safe(folder);rel=folder.relative_to(source)
  if len(rel.parts)>32:raise ValueError('Profile depth limit')
  dirs[:]=[n for n in dirs if n not in set(excluded)|{'logs','crash-reports','screenshots','backups','__pycache__','first58-temp'}]
  nodes+=len(dirs)+len(names)+1
  if nodes>400000:raise ValueError('Profile copy budget')
  for n in dirs:safe(folder/n);(target/rel/n).mkdir()
  for n in names:
   if n in {'first58.marker','session.lock','controller.lock'}:continue
   p=safe(folder/n)
   if not p.is_file():raise ValueError('Special profile file')
   count+=1;total+=p.stat().st_size
   if count>200000 or total>20*1024**3:raise ValueError('Profile copy budget')
   digest=sha(p,20*1024**3);dest=target/rel/n
   with p.open('rb') as src,dest.open('xb') as out:shutil.copyfileobj(src,out,1024*1024)
   if sha(p,20*1024**3)!=digest or sha(dest,20*1024**3)!=digest:raise ValueError('Apply files changed during staging')
 return target

def validate_request(data,instance,config):
 if set(data)!={'instance','run','account','nonce','action','jar','sha','side'}:raise ValueError('Apply67 request fields')
 if data['instance']!=instance or data['run']!=config['run'] or data['account']!=config['account']:raise ValueError('Apply67 stale request')
 if str(uuid.UUID(data['nonce']))!=data['nonce'] or data['action'] not in {'CHECK','PREPARE','ARM','CANCEL'} or data['side'] not in {'BOTH','SERVER','CLIENT'}:raise ValueError('Apply67 action')
 if len(data['jar'])>4096 or any(ord(c)<32 for c in data['jar']):raise ValueError('Apply67 artifact path')
 if bool(data['jar'])!=bool(data['sha']) or data['sha'] and not re.fullmatch('[a-f0-9]{64}',data['sha']):raise ValueError('Apply67 artifact hash')
 return data

class Coordinator:
 def __init__(self,pilot,source_workspace=None):
  self.initial=pilot;self.active=pilot;self.generation=1;self.root=safe(pilot.root/'repeat67');self.root.mkdir();self.key=secrets.token_bytes(32);self.instance=str(uuid.uuid4());self.phase='STARTING';self.nonce='-';self.delta={};self.pending=None;self.last_request=None;self.last_publish=0
  atomic(self.root/'bridge.key',self.key,new=True);bridge67.write(self.root/'meta67',self.key,{'instance':self.instance,'account':pilot.config['account']})
  (self.root/'requests').mkdir()
  source=safe(source_workspace) if source_workspace else pilot.root/'generations/client-0/config/mared/script_projects'
  workspace=self.root/'workspace'
  if source.is_dir():copy_files(source,workspace,{'build','.gradle'})
  else:workspace.mkdir()
  json_write(self.root/'workspace-origin67.json',{'schema':67,'source':str(source),'copy':str(workspace),'sourceModified':False,'buildCachesCopied':False},new=True)
  self.publish('STARTING')
 def environment(self):return {'GENESIS_PANEL67_ROOT':str(self.root),'GENESIS_PANEL67_INSTANCE':self.instance}
 def publish(self,phase=None):
  if phase is not None:self.phase=phase
  bridge67.write(self.root/'state67',self.key,{'instance':self.instance,'account':self.active.config['account'],'run':self.active.config['run'],'nonce':self.nonce,'phase':self.phase,'directory':str(self.pending['pilot'].root if self.pending else self.active.root),'delta':json.dumps(self.delta,separators=(',',':')),'updated':str(int(time.time()*1000))})
  self.last_publish=time.monotonic()
 def receive(self):
  p=self.root/'request67'
  if not p.exists():return None
  if p.stat().st_size>131072:raise ValueError('Apply67 message limit')
  raw=p.read_bytes()
  if raw==self.last_request:return None
  self.last_request=raw
  data=bridge67.verify(self.key,raw);validate_request(data,self.instance,self.active.config);return data
 def cancelled(self):
  p=self.root/'request67'
  if not p.exists():return False
  data=bridge67.read(p,self.key);validate_request(data,self.instance,self.active.config)
  return data['nonce']==self.nonce and data['action']=='CANCEL'
 def checkpoint(self):
  self.active.check_jobs()
  if self.active.jobs['client',self.generation].status()['rootExited']:raise InterruptedError('Client closed during apply staging')
  if self.cancelled() or self.active.cancel():raise InterruptedError('Pilot cancelled')
 def preview(self,data):
  self.checkpoint();path=safe(data['jar']) if data['jar'] else None
  if path:
   if sha(path,64*1024*1024)!=data['sha']:raise ValueError('Apply artifact changed before staging')
   if 'mared' in mod_ids(path):raise ValueError('MaRed itself cannot be replaced in a dev snapshot')
  self.delta={};sides=('server','client') if data['side']=='BOTH' else (data['side'].lower(),)
  for role in ('server','client'):
   profile=self.active.root/'generations'/f'{role}-{self.generation}'
   if inventory(profile)!=self.active.config[role+str(self.generation)]:raise ValueError('Active inventory changed before apply')
   if path and role in sides:
    before,after=replacement_inventory(profile/'mods',path);self.delta[role]=changes(before,after)
   else:self.delta[role]={'added':[],'changed':[],'removed':[]}
  return path,sides
 def prepare(self,data):
  self.nonce=data['nonce'];request=self.root/'requests'/self.nonce
  if request.exists():raise ValueError('Apply request was already used')
  request.mkdir();self.publish('VALIDATING');path,sides=self.preview(data)
  if data['action']=='CHECK':self.publish('CHECKED');return
  self.publish('PREPARING');spec=dict(self.active.spec)
  source_server=self.active.root/'generations'/f'server-{self.generation}';source_client=self.active.root/'generations'/f'client-{self.generation}'
  level=properties(source_server).get('level-name','world')
  if not re.fullmatch('[A-Za-z0-9_-]{1,80}',level):raise ValueError('Simple world directory required')
  server=copy_files(source_server,request/'server-template',{level,level+'_nether',level+'_the_end'},self.checkpoint)
  client=copy_files(source_client,request/'client-template',{'saves','.genesis-dev62'},self.checkpoint)
  spec.update({'serverTemplate':str(server),'clientTemplate':str(client)})
  for role,baseline in [('server',server),('client',client)]:
   if inventory(baseline)!=self.active.config[role+str(self.generation)]:raise ValueError('Active inventory changed during apply')
   target=request/('next-'+role)
   if path and role in sides:stage_replacement(baseline/'mods',path,target)
   else:copy_files(baseline/'mods',target,checkpoint=self.checkpoint)
   spec['next'+role.title()+'Mods']=str(target)
  if path and sha(path,64*1024*1024)!=data['sha']:raise ValueError('Apply artifact changed during staging')
  self.checkpoint();settings=request/'settings.json';json_write(settings,spec,new=True);next_root=request/'run'
  initialize(self.active.project,next_root,settings)
  # Reserve this one-use root for this owner BEFORE exposing READY. The legacy
  # CLI must never replay baseline gen0 alongside a repeated generation1.
  atomic(next_root/'started.v58',('repeat67:'+self.instance).encode(),new=True)
  from .controller import Pilot
  new=Pilot(self.active.project,next_root,self.active.notify,self.active.cancel,self.active.ask);new.live67=self
  self.pending={'pilot':new,'request':data,'source':self.active.root,'generation':self.generation}
  self.checkpoint();self.publish('READY')
 def consent(self):
  old=self.active;new=self.pending['pilot'];nonce=self.nonce
  atomic(old.root/'offer67',sign(old.key,{'run':old.config['run'],'account':old.config['account'],'update':new.config['update'],'serverPackage':new.config['serverPackage'],'clientPackage':new.config['clientPackage'],'request':nonce,'expires':str(int(time.time()*1000)+45000)}))
  cmd=old.command('server',self.generation,'OFFER67',request=nonce);self.publish('CONSENT');end=time.monotonic()+45;offer=None
  while time.monotonic()<end:
   self.checkpoint();m=read_record(old.root,old.key,'consent67.status',old.config,'server',self.generation)
   if m and m['sequence']==str(cmd['sequence']):
    if m['inventory']!=new.config['serverPackage']:raise ValueError('Consent package mismatch')
    if offer is None:offer=m['request']
    if m['request']!=offer:raise ValueError('Consent offer changed')
    if m['phase']=='ACCEPTED':return True
    if m['phase'] in {'DECLINED','EXPIRED','UNAVAILABLE','CANCELLED'}:return False
   time.sleep(.1)
  return False
 def publish_pair(self,old,generation,new):
  # stop() has already verified matching save receipt, normal exit and empty OS Job.
  if old.jobs:raise ValueError('Old jobs still active; world copy refused')
  for role,phase in [('server','STOPPED_SAVED'),('client','STOP_REQUESTED')]:
   cmd=read_record(old.root,old.key,f'{role}.{generation}.command',old.config,role,generation)
   status=read_record(old.root,old.key,f'{role}.{generation}.status',old.config,role,generation)
   if not cmd or not status or cmd['phase']!='STOP' or status['phase']!=phase or status['inventory']!=old.config[role+str(generation)] or cmd['sequence']!=status['sequence'] or cmd['request']!=status['request'] or int(cmd['sequence'])!=old.seq[role,generation]:raise ValueError('Saved source receipt mismatch')
  for role in ('server','client'):
   temporary=new.root/'generations'/f'{role}-1.building';target=temporary.with_name(f'{role}-1')
   clone(old.root/'generations'/f'{role}-{generation}',temporary)
   install_new_generation(temporary,new.root/'packages'/f'{role}-1.zip',new.artifacts[role+'-0'],new.artifacts[role+'-1'])
   atomic(temporary/'first58.marker',sign(new.key,record(new.config,role,1,'SEALED')),new=True);temporary.rename(target)
  json_write(new.root/'saved-source67.json',{'schema':67,'sourceRun':old.config['run'],'sourceGeneration':generation,'source':str(old.root),'worldCopiedAfterConfirmedStop':True},new=True)
 def verify_pending(self,verify_signatures=False):
  new=self.pending['pilot']
  if json_read(new.root/'launch.json',512*1024)!=new.spec or json_read(new.root/'artifacts.json',512*1024)!=new.artifacts:raise ValueError('Prepared launch settings changed')
  from .common import verify
  if verify(new.key,(new.root/'session.v58').read_bytes())!=new.config:raise ValueError('Prepared session changed')
  for role in ('server','client'):
   archive=new.root/'packages'/f'{role}-1.zip';expected=new.artifacts[role+'-1']
   if sha(archive,300*1024*1024)!=expected['archiveSha256']:raise ValueError('Prepared package changed')
   if verify_signatures and new.java('verify',archive,new.root/'packages/public.der',new.artifacts['distribution'],role.upper(),new.config['minecraft'],new.config['neoforge'],'1')!=expected:raise ValueError('Prepared package signature changed')
   if inventory(self.active.root/'generations'/f'{role}-{self.generation}')!=self.active.config[role+str(self.generation)]:raise ValueError('Active inventory changed before restart')
 def activate(self):
  old=self.active;generation=self.generation;new=self.pending['pilot'];shutdown_started=False
  try:
   self.verify_pending(True)
   if not self.consent():
    old.command('server',generation,'CANCEL67');self.pending=None;self.publish('CANCELLED');return
   self.checkpoint();self.verify_pending()
   if set(old.jobs)!={('server',generation),('client',generation)}:raise ValueError('Missing owner jobs before apply')
   self.publish('RESTARTING');old.notify('COVER');shutdown_started=True
   old.stop('client',generation);old.report('SAVING_SERVER');old.stop('server',generation)
   new.report('PREPARING_GENERATION');self.publish_pair(old,generation,new)
   # Switch context before spawning, so the new client sees its own authenticated run.
   self.active=new;self.generation=1;self.publish('STARTING')
   new.report('STARTING_UPDATED_SERVER');new.start('server',1);new.ready('server',1)
   new.report('STARTING_UPDATED_CLIENT');new.start('client',1);new.ready('client',1)
   if not new.join(1):raise InterruptedError('Update connection cancelled')
   new.report('WORLD_READY',actualGameAcknowledged=True,actualFrameAcknowledged=True);new.command('client',1,'FOCUS');self.pending=None;self.publish('ACTIVE')
  except Exception as error:
   if shutdown_started:
    # Preserve the activation failure even if a secondary cleanup also fails.
    for owner in (old,new):
     try:owner.cleanup()
     except Exception:pass
    raise new.diagnostics.failure(error,new.config) from None
   try:old.command('server',generation,'CANCEL67')
   except Exception:pass
   raise

 def watch(self,pilot,generation):
  self.active=pilot;self.generation=generation;self.publish('ACTIVE')
  try:
   while not self.active.jobs['client',self.generation].status()['rootExited']:
    self.active.check_jobs()
    if self.active.cancel():self.active.stop('client',self.generation);break
    try:
     data=self.receive()
     if data:
      if data['action'] in {'CHECK','PREPARE'} and self.phase in IDLE:self.prepare(data)
      elif data['nonce']==self.nonce and data['action']=='CANCEL' and self.pending:
       self.pending=None;self.publish('CANCELLED')
      elif data['nonce']==self.nonce and data['action']=='ARM' and self.phase=='READY':self.activate()
    except PilotFailure:raise
    except InterruptedError:
     if not self.active.jobs or self.active.jobs['client',self.generation].status()['rootExited']:break
     self.pending=None;self.publish('CANCELLED')
    except Exception as error:
     if not self.active.jobs:raise
     data=failure_data(error,{'phase':self.phase,'action':'run'});json_write(self.root/'apply-report67.json',{'schema':67,'nonce':self.nonce,**data});json_write(self.active.root/'apply-report67.json',{'schema':67,'nonce':self.nonce,**data});self.pending=None;self.publish('FAILED')
    if time.monotonic()-self.last_publish>1:self.publish()
    time.sleep(.1)
   current=self.active;client=current.jobs.pop(('client',self.generation),None)
   if client:
    try:current.wait(lambda:client.empty(),15,'Client descendants did not exit',False)
    finally:client.close()
   current.stop('server',self.generation);current.report('COMPLETE',actualGameAcknowledged=True,actualFrameAcknowledged=True);self.publish('COMPLETE')
  finally:
   self.active.cleanup()
