"""Install only into an unpublished NEW client copy; preserve the immutable MaRed bootstrap.
The signed package is the managed mod inventory, not a Minecraft/launcher runtime.
"""
from pathlib import Path
import re,zipfile
from .common import safe,sha,inventory
from .wizard_model import mod_ids,changes

ADAPTER='com/fixmer/mared/technology/server/firstlaunch/RemoteClient70.class'

def files(mods):
 result={}
 for p in safe(mods).iterdir():
  p=safe(p)
  if p.is_dir():raise ValueError('REMOTE70_NESTED_MODS')
  if p.name.lower().endswith('.jar'):
   if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]{0,119}\.jar',p.name) or '..' in p.name:raise ValueError('REMOTE70_MOD_NAME')
   if p.name.lower() in {v.lower() for v in result}:raise ValueError('REMOTE70_MOD_NAME')
   if len(result)>=256:raise ValueError('REMOTE70_MOD_LIMIT')
   result[p.name]=sha(safe(p),64*1024*1024)
 return result

def policy(mods,archive,info,dev=False):
 before=files(mods);boot={};source_ids={};next_ids={};expected={}
 with zipfile.ZipFile(safe(archive)) as z:
  for entry in info['files']:
   name=entry['path']
   if not re.fullmatch(r'mods/[A-Za-z0-9][A-Za-z0-9._+-]{0,119}\.jar',name) or '..' in name or name[5:].lower() in {v.lower() for v in expected}:raise ValueError('REMOTE70_PACKAGE_PATH')
   expected[name[5:]]=entry['sha256']
  if len(expected)>256:raise ValueError('REMOTE70_MOD_LIMIT')
  for name,digest in before.items():
   path=safe(Path(mods)/name);ids=mod_ids(path);source_ids[name]=ids
   with zipfile.ZipFile(path) as jar:has_adapter=ADAPTER in jar.namelist()
   if 'mared' in ids:
    if not has_adapter or boot:raise ValueError('REMOTE70_BOOTSTRAP')
    boot[name]=digest
  if not dev and len(boot)!=1:raise ValueError('REMOTE70_BOOTSTRAP')
  # Inspect bounded JAR bytes, not install scripts or any archive-supplied filesystem path.
  import io,tomllib
  for name,digest in expected.items():
   entry=next(v for v in info['files'] if v['path']=='mods/'+name)
   if type(entry['bytes']) is not int or not 1<=entry['bytes']<=64*1024*1024:raise ValueError('REMOTE70_PACKAGE_CHANGED')
   with z.open('files/mods/'+name) as raw:data=raw.read(entry['bytes']+1)
   if len(data)!=entry['bytes'] or __import__('hashlib').sha256(data).hexdigest()!=digest:raise ValueError('REMOTE70_PACKAGE_CHANGED')
   with zipfile.ZipFile(io.BytesIO(data)) as jar:
    meta=next((n for n in ('META-INF/neoforge.mods.toml','META-INF/mods.toml') if n in jar.namelist()),None)
    if meta is None:
     if before.get(name)==digest and not source_ids.get(name):next_ids[name]=frozenset();continue
     raise ValueError('REMOTE70_MOD_METADATA')
    if jar.getinfo(meta).file_size>1024*1024:raise ValueError('REMOTE70_MOD_METADATA')
    raw_ids=[v.get('modId') for v in tomllib.loads(jar.read(meta).decode('utf-8-sig')).get('mods',[])]
    ids=frozenset(raw_ids)
    if len(ids)!=len(raw_ids):raise ValueError('REMOTE70_MOD_METADATA')
   if not ids or any(not isinstance(i,str) or not re.fullmatch('[a-z][a-z0-9_]{1,63}',i) for i in ids) or any(ids&known for known in next_ids.values()):raise ValueError('REMOTE70_MOD_METADATA')
   next_ids[name]=ids
   if ids&{'minecraft','neoforge'}:raise ValueError('REMOTE70_LOADER_CHANGE')
   if 'mared' in ids and (name not in boot or digest!=boot[name]):raise ValueError('REMOTE70_BOOTSTRAP_CHANGE')
  for name,digest in boot.items():
   if name in expected and expected[name]!=digest or name.lower() in {v.lower() for v in expected} and name not in expected:raise ValueError('REMOTE70_BOOTSTRAP_CHANGE')
   expected[name]=digest
 if len(expected)>256:raise ValueError('REMOTE70_MOD_LIMIT')
 return {'before':before,'after':expected,'bootstrap':boot,'delta':changes(before,expected)}

def install(profile,archive,info,approved,dev=False):
 profile=safe(profile)
 if not profile.name.endswith('.building') or not profile.is_dir():raise ValueError('REMOTE70_NEW_PROFILE')
 if files(profile/'mods')!=approved['before'] or policy(profile/'mods',archive,info,dev)!=approved:raise ValueError('REMOTE70_SOURCE_CHANGED')
 old=profile/'mods';target=profile/'mods.remote70';target.mkdir()
 with zipfile.ZipFile(safe(archive)) as z:
  remote={e['path'][5:]:e for e in info['files']}
  for name,digest in approved['after'].items():
   destination=target/name
   with destination.open('xb') as out:
    if name in remote:
     entry=remote[name]
     if type(entry['bytes']) is not int or not 1<=entry['bytes']<=64*1024*1024:raise ValueError('REMOTE70_PACKAGE_CHANGED')
     with z.open('files/mods/'+name) as src:
      total=0
      while chunk:=src.read(65536):
       total+=len(chunk)
       if total>entry['bytes']:raise ValueError('REMOTE70_PACKAGE_CHANGED')
       out.write(chunk)
     if total!=entry['bytes']:raise ValueError('REMOTE70_PACKAGE_CHANGED')
    else:
     with (old/name).open('rb') as src:__import__('shutil').copyfileobj(src,out,65536)
   if sha(destination,64*1024*1024)!=digest:raise ValueError('REMOTE70_PACKAGE_CHANGED')
 if files(target)!=approved['after']:raise ValueError('REMOTE70_PACKAGE_CHANGED')
 # Only this NEW, unlaunched copy. No active or original mods directory is modified.
 old.rename(profile/'mods.before70');target.rename(old)
 return inventory(profile)
