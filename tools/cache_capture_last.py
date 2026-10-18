#!/usr/bin/env python3
"""Read the small allowlisted launch-cache capture report; never open the private launch cache."""
from pathlib import Path
import argparse,json,re

def report(path):
 root=Path(path).resolve()
 game=root/'run/client' if (root/'settings.gradle').is_file() else root
 target=game/'config/mared/dev/cache-capture-last.json'
 for part in [target,*target.parents]:
  if part.is_symlink():raise ValueError('Linked report path is unsupported')
 if not target.is_file():raise ValueError('No capture report yet. Start the rebuilt game and wait for startup maintenance to finish.')
 if target.stat().st_size>32768:raise ValueError('Report exceeds size limit')
 data=json.loads(target.read_text(encoding='utf-8'))
 if not isinstance(data,dict) or data.get('schema')!=1:raise ValueError('Unsupported report')
 status=data.get('status');launch=data.get('launch');changes=data.get('changed_inputs')
 if not isinstance(status,str) or not re.fullmatch(r'[a-z_]{1,80}',status):raise ValueError('Invalid report status')
 if not isinstance(launch,str) or launch!='unrecorded' and not re.fullmatch(r'[0-9a-f-]{36}',launch):raise ValueError('Invalid launch identifier')
 if not isinstance(changes,list) or len(changes)>8:raise ValueError('Invalid change list')
 for label in changes:
  if not isinstance(label,str) or not 0<len(label)<=230 or '\\' in label or any(ord(c)<32 or ord(c)==127 for c in label):raise ValueError('Invalid input label')
  value=label.removeprefix('native: ')
  if value.startswith(('/','\\')) or re.match(r'^[A-Za-z]:',value) or '..' in value.split('/'):raise ValueError('Absolute or escaping input label rejected')
 return {'schema':1,'launch':launch,'status':status,'changed_inputs':changes}
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('path',nargs='?',default='.');args=parser.parse_args()
 try:print(json.dumps(report(args.path),ensure_ascii=False,indent=2))
 except (OSError,ValueError,TypeError) as error:raise SystemExit(str(error))
if __name__=='__main__':main()
