#!/usr/bin/env python3
"""Reanalyze an existing startup JFR by thread and separate Java/native stacks; --arm remains optional."""
from pathlib import Path
import argparse,json,os,re,subprocess,itertools
MARKER=b'genesis-startup-profile-v1\n'

def safe(path):
 for part in [path,*path.parents]:
  if part.is_symlink():raise ValueError('Linked profile path is unsupported')
 return path

def game_directory(path):
 root=Path(path).resolve()
 if (root/'settings.gradle').is_file():return root/'run/client'
 if (root/'config/mared/dev').is_dir():return root
 raise ValueError('Specify the MaRed project root or its game directory')

def milestones(game,launch):
 out={}
 for name in ['timings.previous.jsonl','timings.jsonl']:
  path=game/'config/mared/dev/logs'/name
  if not path.is_file():continue
  safe(path)
  if path.stat().st_size>8*1024*1024:raise ValueError('Timings exceed report limit')
  for line in path.read_text(encoding='utf-8').splitlines():
   try:
    event=json.loads(line)
    if event.get('launch')!=launch:continue
    phase=event.get('phase');value=event.get('valueMs')
    if phase in ['mod_loaded','resources_ready','world_ready'] and isinstance(value,(int,float)) and not isinstance(value,bool) and 0<=value<=900000:out.setdefault(phase,int(value))
   except (ValueError,TypeError,AttributeError):continue
 if not all(p in out for p in ['mod_loaded','resources_ready','world_ready']):raise ValueError('This profiled launch has no complete world-entry milestones yet')
 if not 0<out['mod_loaded']<=out['resources_ready']<=out['world_ready']:raise ValueError('Profile milestones are inconsistent')
 return out

def report(game,all_phases=False):
 directory=safe(game/'config/mared/dev/profiles')
 files=list(itertools.islice(directory.glob('startup-*.json'),33))
 if not files:raise ValueError('No profile found. Arm once, then use a supervisor restart with direct_cache/hit')
 if len(files)>32:raise ValueError('Too many profile entries')
 info_path=safe(max(files,key=lambda p:p.stat().st_mtime_ns))
 if info_path.stat().st_size>65536:raise ValueError('Profile metadata exceeds limit')
 info=json.loads(info_path.read_text(encoding='utf-8'))
 launch=info.get('launch','')
 if info.get('schema')!=1 or not re.fullmatch(r'[0-9a-f-]{36}',launch) or info_path.name!='startup-'+launch+'.json' or info.get('recording')!='startup-'+launch+'.jfr':raise ValueError('Unsupported profile metadata')
 recording=safe(directory/info['recording'])
 if not recording.is_file() or recording.stat().st_size<32:raise ValueError('Recording is not ready. Wait until 30 seconds after the JVM started; do not restart again')
 if recording.stat().st_size>128*1024*1024:raise ValueError('Recording exceeds 128 MiB report limit')
 java=safe(Path(info['java']));helper=safe(Path(__file__).resolve().with_name('StartupProfileDetails.java'))
 if not java.is_absolute() or not java.is_file() or java.name.lower() not in ['java','java.exe','javaw.exe'] or not helper.is_file():raise ValueError('The recorded JDK or source report helper is unavailable')
 marks=milestones(game,launch)
 command=[str(java),'--add-modules=jdk.jfr','--source','17',str(helper),str(recording),str(marks['mod_loaded']),str(marks['resources_ready']),str(marks['world_ready']),'all' if all_phases else 'startup']
 try:result=subprocess.run(command,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=45)
 except subprocess.TimeoutExpired:raise ValueError('Profile analysis timed out') from None
 if result.returncode:raise ValueError('JFR analysis failed or recording is unfinished. Wait a little and try the report again; no new restart is needed')
 if len(result.stdout)>1024*1024:raise ValueError('Profile summary exceeds output limit')
 out=json.loads(result.stdout);out['launch']=launch;out['profiled_launch']=True
 return out

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('path',nargs='?',default='.');p.add_argument('--all-phases',action='store_true',help='Include resource/world phases; default focuses on JVM to mod_loaded')
 options=p.add_mutually_exclusive_group();options.add_argument('--arm',action='store_true');options.add_argument('--cancel',action='store_true');a=p.parse_args()
 try:
  game=game_directory(a.path);directory=safe(game/'config/mared/dev');request=directory/'startup-profile.request';safe(request)
  if a.arm:
   directory.mkdir(parents=True,exist_ok=True)
   if len(list(itertools.islice((directory/'profiles').glob('startup-*.json'),8)))>=8:raise ValueError('Eight profiles already retained. Archive old profiles before arming another')
   try:
    descriptor=os.open(request,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(descriptor,'wb') as output:output.write(MARKER)
   except FileExistsError:
    if request.stat().st_size>128 or request.read_bytes()!=MARKER:raise ValueError('Unknown request file preserved')
   print('Armed: the next eligible direct-cache game launch will record once for 30 seconds. Gradle launches leave the request pending.')
  elif a.cancel:
   if request.exists():
    if request.stat().st_size>128 or request.read_bytes()!=MARKER:raise ValueError('Unknown request file preserved')
    request.unlink()
   print('Pending profile request cancelled. A recording already started stops automatically.')
  else:print(json.dumps(report(game,a.all_phases),ensure_ascii=False,indent=2))
 except (OSError,ValueError,KeyError,TypeError) as error:raise SystemExit(str(error))
if __name__=='__main__':main()
