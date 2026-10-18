#!/usr/bin/env python3
"""Toggle and inspect opt-in font set presizing for the next supervised direct-cache launch."""
from pathlib import Path
import argparse,json,os,re,tempfile

KEY='fontSizing.enabled'
def safe(path):
 for part in [path,*path.parents]:
  if part.is_symlink():raise ValueError('Linked settings/report path is unsupported')
 return path
def game_directory(path):
 root=Path(path).resolve()
 if (root/'settings.gradle').is_file():return root/'run/client'
 if (root/'config/mared/dev').is_dir():return root
 raise ValueError('Specify the MaRed project root or its game directory')
def read(path):
 safe(path)
 if not path.exists():return ''
 if not path.is_file() or path.stat().st_size>65536:raise ValueError('Settings/report exceed limit or are not a regular file')
 with path.open('r',encoding='utf-8',newline='') as source:return source.read()
def requested(text):
 values=re.findall(r'^fontSizing\.enabled[ \t]*[=:][ \t]*((?i:true|false))\r?$',text,re.M)
 return bool(values and values[-1].lower()=='true')
def configure(game,value):
 path=game/'config/mared/dev/startup.properties';old=read(path)
 data=old+'\n\n'+KEY+'='+('true' if value else 'false')+'\n'
 if len(data.encode('utf-8'))>65536:raise ValueError('Settings exceed limit; no change made')
 path.parent.mkdir(parents=True,exist_ok=True);fd,name=tempfile.mkstemp(prefix='.font-sizing-',dir=path.parent);temporary=Path(name)
 try:
  with os.fdopen(fd,'w',encoding='utf-8',newline='') as output:output.write(data);output.flush();os.fsync(output.fileno())
  if read(path)!=old:raise ValueError('Settings changed concurrently; retry')
  os.replace(temporary,path)
 finally:temporary.unlink(missing_ok=True)
def status(game):
 out={'font_sizing_requested_next_launch':requested(read(game/'config/mared/dev/startup.properties')),'last_agent_report':None}
 text=read(game/'config/mared/dev/path-cache-last.properties');values={}
 for line in text.splitlines():
  if '=' in line:
   key,value=line.split('=',1);values[key]=value
 if values.get('schema')=='1':
  known={'disabled','waiting_for_codepoint_map','applied_layout_v1','unsupported_font_layout','patch_declined','agent_unavailable'}
  entry={'status':values['font_status'] if values.get('font_status') in known else 'not_recorded'}
  for source,target in [('font_calls','calls'),('font_large_calls','large_calls'),('font_expected_symbols','expected_symbols'),('font_capped_calls','capped_calls'),('font_max_expected','max_expected')]:
   if re.fullmatch(r'[0-9]{1,19}',values.get(source,'')):entry[target]=int(values[source])
  if re.fullmatch(r'[0-9a-f-]{36}',values.get('launch','')):entry['launch']=values['launch']
  out['last_agent_report']=entry
 return out
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('path',nargs='?',default='.');parser.add_argument('--font-sizing',choices=['on','off']);args=parser.parse_args()
 try:
  game=game_directory(args.path)
  if args.font_sizing is not None:configure(game,args.font_sizing=='on')
  print(json.dumps(status(game),indent=2))
 except (OSError,ValueError) as error:raise SystemExit(str(error))
if __name__=='__main__':main()
