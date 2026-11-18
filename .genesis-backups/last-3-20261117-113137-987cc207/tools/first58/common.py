"""Private bounded files for the isolated one-PC pilot. No shell or in-place installer."""
from pathlib import Path
import hashlib,hmac,json,os,re,secrets,shutil,stat,tempfile,time,uuid,zipfile
MAX_ARCHIVE=300*1024*1024
FIELDS={'run','role','generation','account','phase','sequence','request','inventory'}
def safe(path):
 p=Path(os.path.abspath(path))
 for x in (p,*p.parents):
  if x.exists() or x.is_symlink():
   st=x.lstat()
   if x.is_symlink() or getattr(st,'st_file_attributes',0)&0x400 or os.path.normcase(str(x.resolve()))!=os.path.normcase(str(x)):raise ValueError('Linked/aliased path refused')
 return p

def sha(path,max_bytes=MAX_ARCHIVE):
 p=safe(path)
 if not p.is_file() or p.stat().st_size>max_bytes:raise ValueError('File type/size refused')
 d=hashlib.sha256();total=0
 with p.open('rb') as stream:
  while b:=stream.read(65536):
   total+=len(b)
   if total>max_bytes:raise ValueError('File grew beyond limit')
   d.update(b)
 return d.hexdigest()

def atomic(path,data,new=False):
 p=safe(path)
 if new:
  out=p.open('xb')
  try:
   with out:
    out.write(data);out.flush();os.fsync(out.fileno())
  except BaseException as error:
   try:p.unlink(missing_ok=True)
   except OSError as cleanup:error.add_note('Could not remove incomplete private file: '+type(cleanup).__name__)
   raise
  return
 fd,name=tempfile.mkstemp(prefix='.first58-',dir=p.parent)
 try:
  with os.fdopen(fd,'wb') as out:
   out.write(data);out.flush();os.fsync(out.fileno())
  # Retry transient Windows file replacement failures.
  for attempt in range(10):
   try:
    os.replace(name,p)
    break
   except PermissionError as error:
    if os.name!='nt' or getattr(error,'winerror',None) not in {5,32,33} or attempt==9:
     raise
    time.sleep(.1)
 finally:
  Path(name).unlink(missing_ok=True)

def json_write(path,value,new=False):atomic(path,(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8'),new)
def json_read(path,max_bytes=65536):
 p=safe(path)
 if not p.is_file() or p.stat().st_size>max_bytes:raise ValueError('JSON file limit')
 def strict(pairs):
  out={}
  for k,v in pairs:
   if k in out:raise ValueError('Duplicate JSON field')
   out[k]=v
  return out
 with p.open('rb') as stream:data=stream.read(max_bytes+1)
 if len(data)>max_bytes:raise ValueError('JSON file limit')
 return json.loads(data.decode('utf-8-sig'),object_pairs_hook=strict)

def encode(values):
 if len(values)>40:raise ValueError('Record field limit')
 for k,v in values.items():
  if not isinstance(k,str) or not isinstance(v,str) or not re.fullmatch(r'[a-zA-Z][a-zA-Z0-9.]{0,39}',k) or not re.fullmatch(r'[A-Za-z0-9_./:+-]{1,160}',v):raise ValueError('Record field')
 data=''.join(k+'='+values[k]+'\n' for k in sorted(values)).encode('ascii')
 if len(data)>8192:raise ValueError('Record size')
 return data

def sign(key,values):
 if 'mac' in values:raise ValueError('Reserved field')
 return encode({**values,'mac':hmac.new(key,encode(values),hashlib.sha256).hexdigest()})
def verify(key,data):
 if len(data)>8192:raise ValueError('Record size')
 out={}
 for line in data.decode('ascii').splitlines():
  k,v=line.split('=',1)
  if k in out:raise ValueError('Duplicate record field')
  out[k]=v
 if encode(out)!=data:raise ValueError('Record canonical form')
 signature=out.pop('mac',None)
 if not isinstance(signature,str) or not hmac.compare_digest(signature,hmac.new(key,encode(out),hashlib.sha256).hexdigest()):raise ValueError('Record authentication')
 return out

def record(config,role,generation,phase,sequence=0,request=None,inventory=None):return {'run':config['run'],'role':role,'generation':str(generation),'account':config['account'],'phase':phase,'sequence':str(sequence),'request':request or str(uuid.UUID(int=0)),'inventory':inventory or config[role+str(generation)]}
def read_record(root,key,name,config,role,generation):
 p=safe(root/name)
 if not p.exists():return None
 if p.stat().st_size>8192:raise ValueError('Record size')
 m=verify(key,p.read_bytes())
 if set(m)!=FIELDS or any(m[k]!=v for k,v in {'run':config['run'],'role':role,'generation':str(generation),'account':config['account']}.items()):raise ValueError('Stale/wrong-role record')
 if not re.fullmatch(r'0|[1-9][0-9]{0,17}',m['sequence']):raise ValueError('Record sequence')
 uuid.UUID(m['request']);return m

def valid_mod_name(name):
 return bool(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]{0,119}\.jar',name)
  and '..' not in name and not re.fullmatch(r'CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]',name.split('.',1)[0].upper()))

def inventory(profile):
 mods=safe(Path(profile)/'mods');entries={};names=set()
 for p in mods.iterdir():
  if p.suffix.lower()!='.jar':continue
  name='mods/'+p.name
  if not valid_mod_name(p.name):raise ValueError('Unsafe mod name')
  if p.name.lower() in names:raise ValueError('Mod filename case collision')
  if len(entries)>=256:raise ValueError('Mod inventory limit')
  p=safe(p)
  if not p.is_file() or p.stat().st_size==0:raise ValueError('Invalid mod file')
  names.add(p.name.lower());entries[name]=sha(p,64*1024*1024)
 return hashlib.sha256(''.join(k+'='+v+'\n' for k,v in sorted(entries.items())).encode('ascii')).hexdigest()

def clone(source,target):
 source=safe(source);target=safe(target)
 if target.exists() or source==target or source in target.parents or target in source.parents:raise ValueError('NEW disjoint generation required')
 target.mkdir();count=total=0
 for folder,dirs,files in os.walk(source,followlinks=False):
  directory=safe(folder);rel=directory.relative_to(source)
  if len(rel.parts)>32:raise ValueError('Profile depth limit')
  dirs[:]=[name for name in dirs if name not in {'logs','crash-reports','screenshots','backups','__pycache__'}]
  for name in dirs:safe(directory/name);(target/rel/name).mkdir()
  for name in files:
   if name in {'first58.marker','session.lock'}:continue
   p=safe(directory/name)
   if not p.is_file():raise ValueError('Special profile file')
   count+=1;total+=p.stat().st_size
   if count>200000 or total>20*1024**3:raise ValueError('Profile copy budget')
   dest=target/rel/name
   with p.open('rb') as src,dest.open('xb') as out:shutil.copyfileobj(src,out,1024*1024)
 return {'files':count,'bytes':total}

def install_new_generation(profile,archive,previous,next_manifest):
 """Only a NEW unlaunched .building directory; caller publishes by atomic rename after seal."""
 profile=safe(profile)
 if not profile.name.endswith('.building') or not profile.is_dir():raise ValueError('Unpublished generation required')
 before={e['path']:e for e in previous['files']};after={e['path']:e for e in next_manifest['files']}
 for name,entry in before.items():
  if sha(profile/name,64*1024*1024)!=entry['sha256']:raise ValueError('Baseline mod conflict')
 for name in after:
  if name not in before and (profile/name).exists():raise ValueError('Unmanaged mod collision')
 # Inventory must be complete for this pilot, including every top-level mod.
 if inventory(profile)!=previous['inventorySha256']:raise ValueError('Unknown mod inventory')
 with zipfile.ZipFile(safe(archive)) as z:
  expected={'genesis/manifest.bin','genesis/signature.ed25519',*('files/'+n for n in after)}
  names=z.namelist()
  if set(names)!=expected or len(names)!=len(expected):raise ValueError('Archive inventory/duplicates')
  for name,entry in after.items():
   if not re.fullmatch(r'mods/[A-Za-z0-9][A-Za-z0-9._+-]{0,119}\.jar',name) or '..' in name:raise ValueError('Archive path')
   if entry['bytes']<1 or entry['bytes']>64*1024*1024:raise ValueError('Payload limit')
   destination=safe(profile/name);temporary=destination.with_name(destination.name+'.first58.tmp')
   d=hashlib.sha256();count=0
   try:
    with z.open('files/'+name) as src,temporary.open('xb') as out:
     while b:=src.read(65536):
      count+=len(b)
      if count>entry['bytes']:raise ValueError('Payload grew')
      out.write(b);d.update(b)
     out.flush();os.fsync(out.fileno())
    if count!=entry['bytes'] or d.hexdigest()!=entry['sha256']:raise ValueError('Payload hash')
    os.replace(temporary,destination)
   finally:temporary.unlink(missing_ok=True)
 for name in before.keys()-after.keys():(profile/name).unlink()
 if inventory(profile)!=next_manifest['inventorySha256']:raise ValueError('Installed inventory')
