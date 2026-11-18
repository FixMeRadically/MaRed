"""Install only into an unpublished NEW profile copy; preserve the immutable MaRed bootstrap.
The signed package is the managed mod inventory, not a Minecraft/launcher runtime.
"""
from pathlib import Path
import hashlib,re,shutil,zipfile
from .common import safe,sha,inventory,valid_mod_name
from .wizard_model import mod_ids,changes

ADAPTER='com/fixmer/mared/technology/server/firstlaunch/RemoteClient70.class'

def files(mods):
 result={}
 for p in safe(mods).iterdir():
  p=safe(p)
  if p.is_dir():raise ValueError('REMOTE70_NESTED_MODS')
  if p.name.lower().endswith('.jar'):
   if not valid_mod_name(p.name) or not p.is_file() or p.stat().st_size==0:raise ValueError('REMOTE70_MOD_NAME')
   if p.name.lower() in {v.lower() for v in result}:raise ValueError('REMOTE70_MOD_NAME')
   if len(result)>=256:raise ValueError('REMOTE70_MOD_LIMIT')
   result[p.name]=sha(safe(p),64*1024*1024)
 return result

def policy(mods,archive,info,dev=False):
 before=files(mods);boot={};source_ids={};next_ids={};expected={}
 with zipfile.ZipFile(safe(archive)) as z:
  for entry in info['files']:
   name=entry['path']
   if not name.startswith('mods/') or not valid_mod_name(name[5:]) or name[5:].lower() in {v.lower() for v in expected}:raise ValueError('REMOTE70_PACKAGE_PATH')
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

def approval_seal120(info,approved):
 return __import__('hashlib').sha256(__import__('json').dumps(dict(archive=info['archiveSha256'],before=approved['before'],after=approved['after']),sort_keys=True,separators=(',',':')).encode()).hexdigest()

def write_verified_mods(old,target,archive,info,approved,cancel=lambda:False):
 from .preflight87 import check
 old=safe(old);target=safe(target)
 target.mkdir()
 try:
  with zipfile.ZipFile(safe(archive)) as z:
   remote={e['path'][5:]:e for e in info['files']}
   for name,digest in approved['after'].items():
    check(cancel)
    entry=remote.get(name)
    changed=entry is not None and approved['before'].get(name)!=digest
    expected_size=entry['bytes'] if changed else safe(old/name).stat().st_size
    if type(expected_size) is not int or not 1<=expected_size<=64*1024*1024:
     raise ValueError('REMOTE70_PACKAGE_CHANGED')
    source=z.open('files/mods/'+name) if changed else safe(old/name).open('rb')
    with source as src,(target/name).open('xb') as out:
     total=0
     while chunk:=src.read(1024*1024):
      check(cancel);total+=len(chunk)
      if total>expected_size:raise ValueError('REMOTE70_PACKAGE_CHANGED')
      out.write(chunk)
     if total!=expected_size:raise ValueError('REMOTE70_PACKAGE_CHANGED')
  verify_staged_mods(target,approved['after'])
 except BaseException:
  shutil.rmtree(target,ignore_errors=True)
  raise

def verify_staged_mods(staged,expected):
 staged=safe(staged)
 if {p.name for p in staged.iterdir()}!=set(expected) or files(staged)!=expected:
  raise ValueError('REMOTE70_PACKAGE_CHANGED')

def promote_remote_mods(profile,staged,info,approved,transaction):
 profile=safe(profile);staged=safe(staged)
 root=profile.parent.parent
 if not profile.name.endswith('.building') or not profile.is_dir() or profile.parent.name!='generations' or staged!=root/'prepared-mods70':
  raise ValueError('APPROVAL120_SCOPE')
 from .approval120 import verify
 verify(root,'REMOTE',transaction,approval_seal120(info,approved))
 verify_staged_mods(staged,approved['after'])
 target=profile/'mods'
 if target.exists():raise ValueError('REMOTE70_NEW_PROFILE')
 staged.rename(target)
 rows=''.join('mods/'+name+'='+digest+'\n' for name,digest in sorted(approved['after'].items()))
 return hashlib.sha256(rows.encode('ascii')).hexdigest()

def install(profile,archive,info,approved,dev=False,approval=None,*,purpose="REMOTE"):
 profile=safe(profile)
 if not profile.name.endswith('.building') or not profile.is_dir():raise ValueError('REMOTE70_NEW_PROFILE')
 if policy(profile/'mods',archive,info,dev)!=approved:raise ValueError('REMOTE70_SOURCE_CHANGED')
 if sha(archive,300*1024*1024)!=info.get('archiveSha256'):raise ValueError('REMOTE70_PACKAGE_CHANGED')
 if not approval or set(approval)!={'scope','transaction'}:raise ValueError('APPROVAL120_EXPLICIT_REVIEW_REQUIRED')
 parent={'REMOTE':'generations','SERVER_STAGE':'stages'}.get(purpose)
 if parent is None or profile.parent.name!=parent or safe(Path(approval['scope']))!=profile.parent.parent:raise ValueError('APPROVAL120_SCOPE')
 from .approval120 import verify
 verify(safe(Path(approval['scope'])),purpose,approval['transaction'],approval_seal120(info,approved))
 old=profile/'mods';target=profile/'mods.remote70'
 write_verified_mods(old,target,archive,info,approved)
 # Only this NEW, unlaunched copy. No active or original mods directory is modified.
 old.rename(profile/'mods.before70');target.rename(old)
 return inventory(profile)
