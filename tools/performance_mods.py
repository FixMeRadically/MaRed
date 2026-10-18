#!/usr/bin/env python3
"""Pinned NeoForge 1.21.1 candidates. Metadata/hash verification is not runtime certification. Python 3.11+."""
from pathlib import Path
from contextlib import contextmanager
import argparse,hashlib,json,os,re,sys,tempfile,urllib.request,urllib.parse,uuid,zipfile,tomllib,http.client
from startup_compat import game_directory,read,properties,safe
CATALOG=Path(__file__).with_name('performance_mods_catalog.json')
def digest(path):
 safe(path);value=hashlib.sha512()
 if not path.is_file() or path.stat().st_size>256*1024*1024:raise ValueError('Invalid or oversized JAR: '+path.name)
 with path.open('rb') as source:
  while chunk:=source.read(65536):value.update(chunk)
 return value.hexdigest()
def metadata(path):
 safe(path)
 if path.stat().st_size>256*1024*1024:raise ValueError('Oversized JAR: '+path.name)
 with zipfile.ZipFile(path) as jar:
  if len(jar.infolist())>50000:raise ValueError('Too many archive entries: '+path.name)
  names=jar.namelist();key=next((n for n in ['META-INF/neoforge.mods.toml','META-INF/mods.toml'] if n in names),None)
  if key is None:return {'ids':[],'loader':'fabric' if 'fabric.mod.json' in names else 'unknown'}
  info=jar.getinfo(key)
  if info.file_size>262144:raise ValueError('Oversized metadata: '+path.name)
  data=tomllib.loads(jar.read(info).decode('utf-8'));ids=[x['modId'] for x in data.get('mods',[])]
  if not all(isinstance(x,str) and re.fullmatch('[a-z][a-z0-9_]{1,63}',x) for x in ids):raise ValueError('Invalid mod ids: '+path.name)
  return {'ids':ids,'loader':'neoforge' if key.startswith('META-INF/neoforge') else 'forge_or_neoforge'}
def inventory(game):
 root=safe(game/'mods');out=[]
 if not root.exists():return out
 for index,path in enumerate(root.iterdir()):
  if index>4096:raise ValueError('Mod directory scan limit')
  if path.suffix.lower()!='.jar':continue
  safe(path)
  if not path.is_file():raise ValueError('Not a regular mod file: '+path.name)
  out.append({'file':path.name,'sha512':digest(path),**metadata(path)})
 return sorted(out,key=lambda item:item['file'].lower())
def problems(items,catalog):
 ids={};out=[]
 for item in items:
  if item['loader']=='fabric':out.append('Fabric-only JAR: '+item['file'])
  for key in item['ids']:ids.setdefault(key,[]).append(item['file'])
 for key,files in ids.items():
  if len(files)>1:out.append('Duplicate mod id '+key+': '+', '.join(files))
 for group in catalog['conflict_groups']:
  present=[key for key in group if key in ids]
  if len(present)>1:out.append('Alternative implementations installed together: '+', '.join(present))
 for left,right in catalog['incompatible_pairs']:
  if left in ids and right in ids:out.append('Known incompatible pair: '+left+', '+right)
 return out
def selected(catalog,profile):
 rows={m['slug']:m for m in catalog['mods']};by_id={m['project_id']:m for m in rows.values()};slugs=list(catalog['profiles'][profile])
 for slug in slugs:
  for key in rows[slug]['dependencies']:
   if key not in by_id:raise ValueError('Unresolved required dependency: '+slug)
   dep=by_id[key]['slug']
   if dep not in slugs:slugs.append(dep)
 return [rows[slug] for slug in slugs]
def catalog_load():
 data=json.loads(CATALOG.read_text(encoding='utf-8'))
 if data.get('schema')!=1:raise ValueError('Unknown catalog schema')
 for m in data['mods']:
  url=urllib.parse.urlsplit(m['url']);name=m['filename']
  if url.scheme!='https' or url.netloc!='cdn.modrinth.com' or url.username or url.query or url.fragment:raise ValueError('Invalid download URL')
  if not re.fullmatch(r'[A-Za-z0-9_.+ -]+\.jar',name) or '/' in name or '\\' in name:raise ValueError('Invalid catalog filename')
  if not re.fullmatch('[0-9a-f]{128}',m['sha512']) or not 0<m['size']<32*1024*1024:raise ValueError('Invalid pinned file')
 return data
def atomic(path,data):
 safe(path);path.parent.mkdir(parents=True,exist_ok=True);fd,name=tempfile.mkstemp(prefix='.performance-',dir=path.parent);temp=Path(name)
 try:
  with os.fdopen(fd,'wb') as out:out.write(data);out.flush();os.fsync(out.fileno())
  os.replace(temp,path)
 finally:temp.unlink(missing_ok=True)
@contextmanager
def file_lock(path,initialize=True):
 safe(path);path.parent.mkdir(parents=True,exist_ok=True)
 with path.open('a+b' if initialize else 'r+b') as f:
  if initialize and f.seek(0,2)==0:f.write(b'0');f.flush()
  f.seek(0)
  try:
   if os.name=='nt':
    import msvcrt
    msvcrt.locking(f.fileno(),msvcrt.LK_NBLCK,1)
   else:
    import fcntl
    fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB,1)
  except OSError:raise ValueError('File is locked by a running game or another installer; close it first')
  try:yield
  finally:
   f.seek(0)
   if os.name=='nt':msvcrt.locking(f.fileno(),msvcrt.LK_UNLCK,1)
   else:fcntl.lockf(f,fcntl.LOCK_UN,1)
def ensure_closed(game):
 runtime=safe(game/'config/mared/dev/runtime')
 if not runtime.exists():return
 for i,directory in enumerate(runtime.iterdir()):
  if i>512:raise ValueError('Runtime directory scan limit')
  safe(directory)
  lease=directory/'game.lease'
  if lease.exists():
   with file_lock(lease,False):pass
 # Unsupservised/starting JVMs may not yet own a lease: the user must close the game and supervisor.
def download(row,root):
 file=safe(root/row['filename'])
 if file.exists() and file.stat().st_size==row['size'] and digest(file)==row['sha512']:return file
 root.mkdir(parents=True,exist_ok=True)
 print('Downloading '+row['slug']+' '+row['version'],file=sys.stderr,flush=True)
 request=urllib.request.Request(row['url'],headers={'User-Agent':'MaRed-PerformanceMods/25'})
 with urllib.request.urlopen(request,timeout=45) as response:
  if urllib.parse.urlsplit(response.geturl()).netloc!='cdn.modrinth.com':raise ValueError('Unexpected download redirect')
  data=response.read(row['size']+1)
 if len(data)!=row['size'] or hashlib.sha512(data).hexdigest()!=row['sha512']:raise ValueError('Download hash/size mismatch: '+row['slug'])
 atomic(file,data)
 if set(metadata(file)['ids'])!=set(row['mod_ids']):raise ValueError('Downloaded mod ids differ: '+row['slug'])
 return file
def admit(game,catalog,rows):
 current=inventory(game);merged=list(current);add=[]
 for row in rows:
  matches=[item for item in current if set(item['ids'])&set(row['mod_ids']) or item['file'].lower()==row['filename'].lower()]
  if matches:
   if len(matches)!=1 or matches[0]['file']!=row['filename'] or matches[0]['sha512']!=row['sha512']:raise ValueError('Existing different version/name of '+row['slug']+'; preserved. Resolve it manually first.')
  else:add.append(row);merged.append({'file':row['filename'],'sha512':row['sha512'],'ids':row['mod_ids'],'loader':'neoforge'})
 errors=problems(merged,catalog)
 if errors:raise ValueError('; '.join(errors))
 return add
def rollback(game,directory):
 directory=directory.resolve();root=(game/'config/mared/dev/performance-mods/transactions').resolve()
 if directory.parent!=root:raise ValueError('Rollback must name a transaction directory for this game')
 safe(directory);journal=directory/'transaction.json'
 if journal.stat().st_size>65536:raise ValueError('Oversized transaction')
 data=json.loads(journal.read_text());items=data['added'];paths=[]
 if data.get('status')=='rolled_back':return 0
 if data.get('schema')!=1 or data.get('game')!=str(game.resolve()) or len(items)>32:raise ValueError('Invalid transaction')
 for item in items:
  name=item['file']
  if not re.fullmatch(r'[A-Za-z0-9_.+ -]+\.jar',name) or Path(name).name!=name or '/' in name or '\\' in name or not re.fullmatch('[0-9a-f]{128}',item['sha512']):raise ValueError('Invalid transaction entry')
  file=safe(game/'mods'/name)
  if file.exists():
   if digest(file)!=item['sha512']:raise ValueError('Installed file changed; rollback preserved it: '+name)
   paths.append(file)
 ensure_closed(game)
 for file in paths:file.unlink()
 data['status']='rolled_back';atomic(journal,(json.dumps(data,indent=2)+'\n').encode());return len(paths)
def publish(source,target,expected):
 safe(target)
 fd,name=tempfile.mkstemp(prefix='.mared-performance-',suffix='.tmp',dir=target.parent);temporary=Path(name)
 try:
  with os.fdopen(fd,'wb') as output,source.open('rb') as input:
   while chunk:=input.read(65536):output.write(chunk)
   output.flush();os.fsync(output.fileno())
  if digest(temporary)!=expected:raise ValueError('Staged file changed before publication')
  if os.name=='nt':os.rename(temporary,target) # Windows rename fails if target already exists.
  else:os.link(temporary,target) # Atomic no-replace publication; decline unsupported file systems.
 finally:temporary.unlink(missing_ok=True)
def install(game,project,catalog,rows):
 if not (project/'settings.gradle').is_file():raise ValueError('Install requires the MaRed project root')
 props=properties(read(project/'gradle.properties'))
 neo=props.get('neo_version','');required=catalog['neoforge_min']
 def numeric(s):
  if not re.fullmatch(r'[0-9]+(?:\.[0-9]+)*',s):raise ValueError('Cannot verify numeric NeoForge version')
  return tuple(int(x) for x in s.split('.'))
 if props.get('minecraft_version')!=catalog['minecraft'] or numeric(neo)<numeric(required):raise ValueError('This catalog requires Minecraft 1.21.1 and NeoForge '+required+' or newer')
 if properties(read(game/'config/mared/dev/startup.properties')).get('startup.mode','compatible')!='compatible':raise ValueError('Set startup_compat.py . --mode compatible before installing')
 ensure_closed(game);root=safe(game/'config/mared/dev/performance-mods');root.mkdir(parents=True,exist_ok=True)
 unfinished=list((root/'transactions').glob('*/transaction.json')) if (root/'transactions').exists() else []
 for journal in unfinished:
  safe(journal)
  if json.loads(journal.read_text()).get('status')=='pending':raise ValueError('Interrupted installation; rollback this transaction first: '+str(journal.parent))
 add=admit(game,catalog,rows)
 if not add:return {'status':'already_installed','added':[]}
 staged={row['slug']:download(row,root/'downloads') for row in add}
 ensure_closed(game);add=admit(game,catalog,rows)
 transaction=root/'transactions'/str(uuid.uuid4());transaction.mkdir(parents=True)
 journal=transaction/'transaction.json';data={'schema':1,'game':str(game.resolve()),'status':'pending','added':[{'file':r['filename'],'sha512':r['sha512']} for r in add]}
 atomic(journal,(json.dumps(data,indent=2)+'\n').encode())
 published=[]
 try:
  mods=safe(game/'mods');mods.mkdir(exist_ok=True)
  for row in add:
   target=safe(mods/row['filename'])
   publish(staged[row['slug']],target,row['sha512'])
   published.append(row)
  data['status']='installed';atomic(journal,(json.dumps(data,indent=2)+'\n').encode())
 except BaseException:
  # Interrupted/power-loss transactions remain pending and are recoverable with --rollback.
  # Preserve files whose current contents cannot be proven to match our pinned additions.
  for row in reversed(published):
   target=safe(game/'mods'/row['filename'])
   if target.exists() and digest(target)==row['sha512']:target.unlink()
  data['status']='rolled_back';atomic(journal,(json.dumps(data,indent=2)+'\n').encode());raise
 return {'status':'installed','added':[r['slug'] for r in add],'rollback':str(transaction),'runtime_verified':False}
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('path',nargs='?',default='.');parser.add_argument('--profile',choices=['core','render','world','extended','all'],default='core');action=parser.add_mutually_exclusive_group();action.add_argument('--install',action='store_true');action.add_argument('--rollback',type=Path);action.add_argument('--audit',action='store_true');args=parser.parse_args()
 try:
  catalog=catalog_load();project=Path(args.path).resolve();game=game_directory(args.path);root=safe(game/'config/mared/dev/performance-mods')
  if args.install or args.rollback:
   with file_lock(root/'installer.lock'):result=install(game,project,catalog,selected(catalog,args.profile)) if args.install else {'status':'rolled_back','removed':rollback(game,args.rollback)}
  elif args.audit:
   items=inventory(game);pins={m['sha512']:m for m in catalog['mods']}
   result={'metadata_problems':problems(items,catalog),'installed':[{'file':i['file'],'mod_ids':i['ids'],'pinned_version':pins.get(i['sha512'],{}).get('version'),'runtime_verified':False} for i in items]}
  else:result={'profile':args.profile,'minecraft':catalog['minecraft'],'neoforge_min':catalog['neoforge_min'],'candidates':[{'mod':m['title'],'version':m['version'],'role':m['role'],'runtime_verified':False} for m in selected(catalog,args.profile)],'note':'Preview only. Close game and supervisor before --install.'}
  print(json.dumps(result,indent=2,ensure_ascii=True))
 except (OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile,tomllib.TOMLDecodeError,http.client.HTTPException) as error:raise SystemExit(str(error))
if __name__=='__main__':main()
