#!/usr/bin/env python3
"""Dedicated75 local operator: persistent owner, signed history and native-game panel backend."""
from pathlib import Path
import argparse,json,os,shutil,sys,uuid
from first58.common import safe,json_read
from first58 import dedicated74 as old
from first58.dedicated75 import submit,History75,independent_owner
from first58.dedicated76 import initialize,Owner76

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path);sub=p.add_subparsers(dest='action',required=True)
 q=sub.add_parser('init');q.add_argument('server',type=Path);q.add_argument('--root',type=Path);q.add_argument('--java',type=Path)
 for name in ('run','status','history'):
  q=sub.add_parser(name);q.add_argument('root',type=Path)
 q=sub.add_parser('author');q.add_argument('root',type=Path);q.add_argument('public_export',type=Path);q.add_argument('next_server_mods',type=Path);q.add_argument('--label',default='r1')
 q=sub.add_parser('command');q.add_argument('root',type=Path);q.add_argument('command',choices=['local-test-on','local-test-off','stage','offer','status','commit','cancel','close','stop']);q.add_argument('--label',default='r1')
 a=p.parse_args();project=safe(a.project)
 if a.action=='init':
  java=a.java or (Path(os.environ['JAVA_HOME'])/'bin/java.exe' if os.environ.get('JAVA_HOME') else Path(shutil.which('java') or 'java'));root=a.root
  if root is None:
   local=os.environ.get('LOCALAPPDATA')
   if not local:raise ValueError('DEDICATED75_SET_ROOT')
   parent=safe(Path(local)/'Genesis/dedicated75');parent.mkdir(parents=True,exist_ok=True);root=parent/uuid.uuid4().hex
  initialize(project,safe(a.server),safe(root),safe(java));print(json.dumps({'root':str(safe(root))},ensure_ascii=False));return
 root=safe(a.root);s,key=old.settings(root)
 if a.action=='status':
  from first58.bridge67 import read
  print(json.dumps(read(root/'view75',key) if (root/'view75').exists() else {'phase':'INITIALIZED','root':str(root)},ensure_ascii=False));return
 if a.action=='history':print(json.dumps(History75(root,key,s['run']).public(None),ensure_ascii=False,indent=2));return
 if a.action=='author':old.core(project,s['java'],'author',root/'publisher73',safe(a.public_export),safe(a.next_server_mods),root/'releases'/old.label(a.label));print(json.dumps({'authored':a.label}));return
 if a.action=='command':print(json.dumps({'queued':submit(root,{'stage':'STAGE','stop':'SHUTDOWN'}.get(a.command,a.command.upper().replace('-','_')),a.label)}));return
 if a.action=='run':
  if os.name!='nt':raise ValueError('DEDICATED75_WINDOWS_X64_ONLY')
  independent_owner()
  from first_launch import ownership
  with ownership(root):Owner76(project,root).run()
if __name__=='__main__':
 try:main()
 except Exception as e:
  code=e.args[0] if len(e.args)==1 and isinstance(e.args[0],str) and __import__('re').fullmatch('[A-Z0-9_]{1,100}',e.args[0]) else 'DEDICATED75_OPERATION_FAILED'
  print(code,file=sys.stderr);raise SystemExit(2)
