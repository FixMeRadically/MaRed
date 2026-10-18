#!/usr/bin/env python3
"""Set next-launch compatibility policy and inspect the last supervised launch."""
from pathlib import Path
import argparse,json,os,re,tempfile

def safe(path):
 for part in [path,*path.parents]:
  if part.is_symlink():raise ValueError('Linked settings/report paths are unsupported')
 return path
def read(path):
 safe(path)
 if not path.exists():return ''
 if not path.is_file() or path.stat().st_size>65536:raise ValueError('Settings/report exceed limit or are not a regular file')
 with path.open('r',encoding='utf-8',newline='') as source:return source.read()
def properties(text):
 # Match java.util.Properties.load(Reader), including continuation, escapes and duplicate keys.
 def decode(value):
  out=[];i=0
  while i<len(value):
   if value[i]!='\\':out.append(value[i]);i+=1;continue
   i+=1
   if i==len(value):break
   c=value[i];i+=1
   if c=='u':
    digits=value[i:i+4]
    if not re.fullmatch('[0-9a-fA-F]{4}',digits):raise ValueError('Malformed Unicode escape in properties')
    out.append(chr(int(digits,16)));i+=4
   else:out.append({'t':'\t','r':'\r','n':'\n','f':'\f'}.get(c,c))
  return ''.join(out)
 result={};pending='';continued=False
 for line in re.split(r'\r\n|\r|\n',text):
  line=line.lstrip(' \t\f')
  if not continued and (not line or line[0] in '#!'):continue
  line=pending+line
  if (len(line)-len(line.rstrip('\\')))%2:pending=line[:-1];continued=True;continue
  pending='';continued=False;escaped=False;end=len(line);value_at=end
  for i,c in enumerate(line):
   if not escaped and c in '=:\t \f':
    end=i;j=i
    while j<len(line) and line[j] in ' \t\f':j+=1
    if j<len(line) and line[j] in '=:':j+=1
    while j<len(line) and line[j] in ' \t\f':j+=1
    value_at=j;break
   escaped=(not escaped) if c=='\\' else False
  result[decode(line[:end])]=decode(line[value_at:])
 if pending:result.update(properties(pending+'\n'))
 return result
def game_directory(path):
 root=Path(path).resolve()
 if (root/'settings.gradle').is_file():return root/'run/client'
 if (root/'config/mared/dev').is_dir():return root
 raise ValueError('Specify the MaRed project root or its game directory')
def configure(game,mode):
 path=game/'config/mared/dev/startup.properties';old=read(path)
 data=old+'\n\nstartup.mode='+mode+'\n'
 if len(data.encode('utf-8'))>65536:raise ValueError('Settings exceed limit; no change made')
 path.parent.mkdir(parents=True,exist_ok=True);fd,name=tempfile.mkstemp(prefix='.startup-compat-',dir=path.parent);temporary=Path(name)
 try:
  with os.fdopen(fd,'w',encoding='utf-8',newline='') as out:out.write(data);out.flush();os.fsync(out.fileno())
  if read(path)!=old:raise ValueError('Settings changed concurrently; retry')
  os.replace(temporary,path)
 finally:temporary.unlink(missing_ok=True)
def status(game):
 settings=properties(read(game/'config/mared/dev/startup.properties'))
 mode='experimental' if settings.get('startup.mode')=='experimental' else 'compatible'
 result={'mode_requested_next_launch':mode,'genesis_experiments_allowed_next_launch':mode=='experimental','last_launch':None}
 data=properties(read(game/'config/mared/dev/startup-compat-last.properties'))
 if data.get('schema')=='1' and data.get('mode') in ['compatible','experimental']:
  report={'mode':data['mode']}
  if re.fullmatch('[0-9a-f-]{36}',data.get('launch','')):report['launch']=data['launch']
  if data.get('launch_mode') in ['direct_cache','gradle','direct_config','configured']:report['launch_mode']=data['launch_mode']
  for key in ['startup_agent_applied','dynamic_cds_applied','world_distance_tuning_allowed']:
   if data.get(key) in ['true','false']:report[key]=data[key]=='true'
  result['last_launch']=report
 return result
def mod_files(game):
 folder=safe(game/'mods')
 if not folder.exists():return []
 out=[];seen=0
 for path in folder.iterdir():
  seen+=1
  if seen>4096:raise ValueError('Mod directory scan limit')
  if path.suffix.lower()!='.jar':continue
  safe(path)
  if path.is_file():out.append({'file':path.name,'bytes':path.stat().st_size})
 return sorted(out,key=lambda row:row['file'].lower())
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('path',nargs='?',default='.');parser.add_argument('--mode',choices=['compatible','experimental']);parser.add_argument('--list-mods',action='store_true');args=parser.parse_args()
 try:
  game=game_directory(args.path)
  if args.mode:configure(game,args.mode)
  result=status(game)
  if args.list_mods:result['mod_files']=mod_files(game)
  print(json.dumps(result,indent=2,ensure_ascii=True))
 except (OSError,ValueError) as error:raise SystemExit(str(error))
if __name__=='__main__':main()
