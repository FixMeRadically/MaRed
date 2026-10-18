#!/usr/bin/env python3
"""Toggle the opt-in Windows FML root comparison cache for the next direct-cache launch."""
from pathlib import Path
import argparse,json,os,re,tempfile

KEY='pathFilterCache.enabled'
def safe(path):
 for part in [path,*path.parents]:
  if part.is_symlink():raise ValueError('Linked settings path is unsupported')
 return path
def game_directory(path):
 root=Path(path).resolve()
 if (root/'settings.gradle').is_file():return root/'run/client'
 if (root/'config/mared/dev').is_dir():return root
 raise ValueError('Specify the MaRed project root or game directory')
def read(path):
 safe(path)
 if not path.exists():return ''
 if not path.is_file() or path.stat().st_size>65536:raise ValueError('Settings exceed limit or are not a regular file')
 with path.open('r',encoding='utf-8',newline='') as source:return source.read()
def enabled(text):
 # Only canonical lines written by this tool; unknown existing properties are preserved.
 values=re.findall(r'^pathFilterCache\.enabled[ \t]*[=:][ \t]*((?i:true|false))\r?$',text,re.M)
 return values[-1].lower()=='true' if values else False
def configure(game,value):
 path=game/'config/mared/dev/startup.properties';old=read(path)
 # Append so unrelated options, comments and line endings are never rewritten.
 data=old+'\n\n'+KEY+'='+('true' if value else 'false')+'\n'
 if len(data.encode('utf-8'))>65536:raise ValueError('Settings exceed limit; no change made')
 path.parent.mkdir(parents=True,exist_ok=True)
 fd,name=tempfile.mkstemp(prefix='.startup-',dir=path.parent);temporary=Path(name)
 try:
  with os.fdopen(fd,'w',encoding='utf-8',newline='') as output:output.write(data);output.flush();os.fsync(output.fileno())
  if read(path)!=old:raise ValueError('Settings changed concurrently; retry')
  os.replace(temporary,path)
 finally:temporary.unlink(missing_ok=True)
def status(game):
 out={'path_cache_requested_next_launch':enabled(read(game/'config/mared/dev/startup.properties')),'last_agent_report':None}
 path=game/'config/mared/dev/path-cache-last.properties';text=read(path)
 values={}
 for line in text.splitlines():
  if '=' in line:
   key,value=line.split('=',1);values[key]=value
 if values.get('schema')=='1':
  known={'waiting_for_loader','unsupported_platform','agent_unavailable','unsupported_loader','applied_fml_4_0_42','patch_declined'}
  entry={'status':values.get('status') if values.get('status') in known else 'unknown'}
  for key in ['hits','misses']:
   if re.fullmatch(r'[0-9]{1,19}',values.get(key,'')):entry[key]=int(values[key])
  if re.fullmatch(r'[0-9a-f-]{36}',values.get('launch','')):entry['launch']=values['launch']
  out['last_agent_report']=entry
 return out
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('path',nargs='?',default='.');parser.add_argument('--path-cache',choices=['on','off']);args=parser.parse_args()
 try:
  game=game_directory(args.path)
  if args.path_cache is not None:configure(game,args.path_cache=='on')
  print(json.dumps(status(game),indent=2))
 except (OSError,ValueError) as error:raise SystemExit(str(error))
if __name__=='__main__':main()
