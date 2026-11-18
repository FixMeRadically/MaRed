from pathlib import Path
import json,os,re,shutil,subprocess,time,uuid
from .common import *
from .windows_job import WindowsJob
from .runtime import core_classpath
REQUIRED={'serverTemplate','clientTemplate','nextServerMods','nextClientMods','serverCommand','clientCommand','account','minecraft','neoforge','port'}

def java(project,executable,*args):
 result=subprocess.run([str(executable),'-cp',str(core_classpath(project)),'com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchArtifacts',*map(str,args)],capture_output=True,text=True,timeout=120,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
 if result.returncode:raise ValueError('Artifact operation failed. Build :genesis-core:classes first; check paths and package limits.')
 return json.loads(result.stdout)

def validate_spec(spec):
 if not REQUIRED<=set(spec) or set(spec)-REQUIRED-{'authMode','devModClasses','devSnapshot'}:raise ValueError('Unexpected/missing pilot config fields')
 mode=spec.get('authMode','ONLINE')
 if mode not in {'ONLINE','LOCAL_OFFLINE'}:raise ValueError('Unknown authentication mode')
 if mode=='LOCAL_OFFLINE':
  from .session61 import offline_uuid
  argv=spec['clientCommand']
  if argv.count('--username')!=1 or argv.index('--username')+1>=len(argv) or offline_uuid(argv[argv.index('--username')+1])!=spec['account']:raise ValueError('Offline player name/UUID mismatch')
 if spec.get('devModClasses'):
  from .launch62 import dev_groups
  dev_groups(spec['devModClasses'])
 if 'devSnapshot' in spec:
  frozen=spec['devSnapshot']
  if not isinstance(frozen,dict) or set(frozen)!={'source','hash','roots','legacy'} or not re.fullmatch('[a-f0-9]{64}',frozen['hash']):raise ValueError('Dev snapshot format')
  from .launch62 import ROOT
  if not safe(frozen['source']).is_dir():raise ValueError('Dev snapshot missing')
  if not isinstance(frozen['roots'],list) or not 1<=len(frozen['roots'])<=16:raise ValueError('Dev roots limit')
  for group,relative in frozen['roots']:
   if not re.fullmatch('[A-Za-z][A-Za-z0-9_.-]{0,60}',group) or relative!='mods/'+group:raise ValueError('Dev root path')
  if frozen['legacy'] is not None and (not isinstance(frozen['legacy'],list) or any(not isinstance(v,str) for v in frozen['legacy'])):raise ValueError('Dev classpath format')
 uuid.UUID(spec['account'])
 if str(uuid.UUID(spec['account']))!=spec['account'] or uuid.UUID(spec['account']).int==0:raise ValueError('Canonical online account UUID required')
 if spec['minecraft']!='1.21.1' or not re.fullmatch(r'21\.1\.[0-9]+',spec['neoforge']):raise ValueError('Minecraft1.21.1 / exact NeoForge21.1.x required')
 if type(spec['port']) is not int or not 1024<=spec['port']<=65535:raise ValueError('Loopback port')
 for field in ('serverTemplate','clientTemplate','nextServerMods','nextClientMods'):
  if not safe(spec[field]).is_dir():raise ValueError('Template/mod directory missing: '+field)
 for role in ('server','client'):
  argv=spec[role+'Command']
  if not isinstance(argv,list) or not 2<=len(argv)<=256 or any(not isinstance(a,str) or any(c in a for c in '\x00\r\n') for a in argv):raise ValueError('Direct Java argv array required')
  exe=safe(argv[0])
  if not exe.is_file() or exe.name.lower()!='java.exe':raise ValueError('Explicit java.exe required')
  if any(a.startswith(('-javaagent','-agentlib','-agentpath','-Dfml.modFolders','-Dminecraft.gameDir')) for a in argv):raise ValueError('Pilot does not support agents or external mod-folder overrides')
  if role=='client':
   if argv.count('--gameDir')!=1 or argv.index('--gameDir')+1>=len(argv) or argv[argv.index('--gameDir')+1]!='{profile}':raise ValueError('Client requires exactly --gameDir {profile}')
   if any(a in {'--server','--port','--quickPlayMultiplayer','--quickPlaySingleplayer','--quickPlayRealms'} for a in argv):raise ValueError('Remove pre-existing quick-play target')
  for a in argv:
   if '{' in a.replace('{profile}','') or '}' in a.replace('{profile}',''):raise ValueError('Unknown command placeholder')
 return spec

def seal(root,key,config,role,generation):atomic(root/'generations'/f'{role}-{generation}'/'first58.marker',sign(key,record(config,role,generation,'SEALED')),new=True)

def initialize(project,root,spec_file):
 if os.name!='nt':raise ValueError('Preparing real profiles requires Windows; Linux only runs explicit fixtures')
 project=safe(project);root=safe(root);spec=validate_spec(json_read(spec_file,512*1024))
 if root.exists() or not root.parent.is_dir():raise ValueError('NEW pilot root and existing parent required')
 for field in ('serverTemplate','clientTemplate','nextServerMods','nextClientMods'):
  source=safe(spec[field])
  if root in source.parents or source in root.parents:raise ValueError('Pilot root must be disjoint from templates/mod directories')
 root.mkdir();java(project,spec['serverCommand'][0],'private',root)
 key=secrets.token_bytes(32);atomic(root/'channel.key',key,new=True);(root/'generations').mkdir();(root/'downloads').mkdir()
 json_write(root/'launch.json',spec,new=True)
 artifacts=java(project,spec['serverCommand'][0],'author',root/'packages',Path(spec['serverTemplate'])/'mods',Path(spec['clientTemplate'])/'mods',spec['nextServerMods'],spec['nextClientMods'],spec['minecraft'],spec['neoforge'])
 json_write(root/'artifacts.json',artifacts,new=True)
 config={'authMode':spec.get('authMode','ONLINE'),'run':str(uuid.uuid4()),'account':spec['account'],'update':artifacts['next'],'serverPackage':artifacts['server-1']['archiveSha256'],'clientPackage':artifacts['client-1']['archiveSha256'],'minecraft':spec['minecraft'],'neoforge':spec['neoforge'],'port':str(spec['port'])}
 if spec.get('devSnapshot'):config['devHash']=spec['devSnapshot']['hash']
 for role in ('server','client'):
  for gen in (0,1):config[role+str(gen)]=artifacts[f'{role}-{gen}']['inventorySha256']
 atomic(root/'session.v58',sign(key,config),new=True)
 for role in ('server','client'):
  target=root/'generations'/f'{role}-0';clone(Path(spec[role+'Template']),target)
  if inventory(target)!=config[role+'0']:raise ValueError('Template changed during copy')
  if role=='client' and spec.get('devSnapshot'):
   from .launch62 import ROOT,tree_hash
   frozen=spec['devSnapshot'];clone(Path(frozen['source']),target/ROOT)
   if tree_hash(target/ROOT)!=config['devHash']:raise ValueError('Dev snapshot changed during preparation')
  if role=='server':
   p=target/'server.properties';lines=p.read_text(encoding='utf-8').splitlines() if p.exists() else [];properties={}
   for line in lines:
    if line and not line.lstrip().startswith(('#','!')) and '=' in line:
     k,v=line.split('=',1)
     if k in properties:raise ValueError('Duplicate server property')
     properties[k.strip()]=v.strip()
   level=properties.get('level-name','world')
   if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}',level):raise ValueError('Pilot requires a local simple world directory name')
   properties.update({'server-ip':'127.0.0.1','server-port':str(spec['port']),'online-mode':'false' if config['authMode']=='LOCAL_OFFLINE' else 'true','enable-rcon':'false','enable-query':'false'})
   atomic(p,('\n'.join(k+'='+v for k,v in properties.items())+'\n').encode())
   if not (target/'eula.txt').exists() or not re.search(r'(?m)^eula=true\s*$',(target/'eula.txt').read_text()):raise ValueError('Server template must already have an accepted EULA; tool will not accept it for you')
  seal(root,key,config,role,0)
 json_write(root/'report.json',{'schema':58,'phase':'PREPARED','scope':'ISOLATED_ONE_PC_PILOT','run':config['run'],'remoteUpdater':False,'liveInstallAuthority':False},new=True)
 # Seal local launch configuration too; never silently accept edited auth/launch arguments on replay.
 atomic(root/'config.sha256',hashlib.sha256(encode(config)+json.dumps(spec,sort_keys=True).encode()+json.dumps(artifacts,sort_keys=True).encode()).hexdigest().encode(),new=True)
 return {'phase':'PREPARED','run':config['run'],'scope':'ISOLATED_ONE_PC_PILOT','originalProfilesModified':False}

def doctor(project,root,native=True):
 from .controller import Pilot
 p=Pilot(project,root);validate_spec(p.spec)
 expected=hashlib.sha256(encode(p.config)+json.dumps(p.spec,sort_keys=True).encode()+json.dumps(p.artifacts,sort_keys=True).encode()).hexdigest()
 if (p.root/'config.sha256').read_text()!=expected:raise ValueError('Prepared config changed; make a NEW root')
 for role in ('server','client'):
  executable=p.spec[role+'Command'][0];r=subprocess.run([executable,'-version'],capture_output=True,text=True,timeout=15,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
  if r.returncode or not re.search(r'version "21(?:\.|\")',r.stderr+r.stdout):raise ValueError('Both game commands require JDK21')
  for gen in (0,1):
   name=f'{role}-{gen}';verified=p.java('verify',p.root/'packages'/f'{name}.zip',p.root/'packages/public.der',p.artifacts['distribution'],role.upper(),p.config['minecraft'],p.config['neoforge'],'1')
   if verified!=p.artifacts[name]:raise ValueError('Signed package changed')
  p.argv(role,0) # validate profile-affecting switches inside copied @argfiles too
  profile=p.root/'generations'/f'{role}-0'
  if inventory(profile)!=p.config[role+'0']:raise ValueError('Baseline generation changed')
  if role=='client' and p.spec.get('devSnapshot'):
   from .launch62 import verify_profile
   verify_profile(profile,p.config['devHash'])
  if role=='server':
   from .wizard_model import properties
   props=properties(profile)
   expected='false' if p.config.get('authMode','ONLINE')=='LOCAL_OFFLINE' else 'true'
   if props.get('server-ip')!='127.0.0.1' or props.get('online-mode')!=expected or props.get('server-port')!=p.config['port']:raise ValueError('Prepared server binding/authentication changed')
 if native:
  if os.name!='nt':raise ValueError('Windows native selftest is required; no simulated pass')
  probe=p.root/('native-probe-'+uuid.uuid4().hex);probe.mkdir();leaf=probe/'leaf.pid';classes=core_classpath(p.project);env=dict(os.environ)
  # Root exits leaving a real descendant: root exit must NOT be classified as Job empty.
  job=WindowsJob([p.spec['serverCommand'][0],'-cp',str(classes),'com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchProbe','tree',str(leaf)],probe,env,probe/'output.log')
  child_handle=None
  try:
   deadline=time.monotonic()+15
   while not leaf.exists() or not job.status()['rootExited']:
    if time.monotonic()>deadline:raise ValueError('Native selftest child did not become ready')
    time.sleep(.05)
   if job.status()['active']<1 or job.empty():raise ValueError('Job selftest lost descendant')
   from ctypes import wintypes as W
   import ctypes as C
   job.k.OpenProcess.argtypes=[W.DWORD,W.BOOL,W.DWORD];job.k.OpenProcess.restype=W.HANDLE
   child_handle=job.k.OpenProcess(0x100000,False,int(leaf.read_text()))
   if not child_handle:raise ValueError('Could not retain native selftest descendant handle')
   job.close()
   if job.k.WaitForSingleObject(child_handle,10000)!=0:raise ValueError('Closing owner Job did not stop descendant')
  finally:
   job.close()
   if child_handle:job.k.CloseHandle(child_handle)
 json_write(p.root/'doctor.json',{'schema':58,'run':p.config['run'],'signedPackagesVerified':True,'nativeJobSelftest':native,'gameRuntimeVerified':False,'remoteUpdater':False,'liveInstallAuthority':False})
 return {'signedPackagesVerified':True,'nativeJobSelftest':native,'gameRuntimeVerified':False}
