#!/usr/bin/env python3
"""Dedicated75 local operator: persistent owner, signed history and native-game panel backend."""
from pathlib import Path
import argparse,json,os,shutil,sys,uuid
from first58.common import safe,json_read
from first58 import dedicated74 as old
from first58.dedicated75 import submit,History75,independent_owner
from first58.dedicated76 import Owner76
from first58.dedicated77 import initialize,Owner77,catalog,recover
from first58.dedicated78 import Owner78
from first58 import https78,repair78

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path);sub=p.add_subparsers(dest='action',required=True)
 q=sub.add_parser('init');q.add_argument('server',type=Path);q.add_argument('--root',type=Path);q.add_argument('--java',type=Path)
 q=sub.add_parser('recover');q.add_argument('root',type=Path);q.add_argument('generation',type=int);q.add_argument('ticket');q.add_argument('--target',type=Path);q.add_argument('--java',type=Path);q.add_argument('--allow-older',action='store_true')
 for name in ('tls-save','tls-check'):
  q=sub.add_parser(name);q.add_argument('root',type=Path);q.add_argument('public',type=Path);q.add_argument('cert',type=Path);q.add_argument('key',type=Path);q.add_argument('--bind',default='127.0.0.1');q.add_argument('--java',type=Path)
 for name in ('run','status','history','catalog','repair','tls-status','tls-disable','tls-probe'):
  q=sub.add_parser(name);q.add_argument('root',type=Path)
 q=sub.add_parser('author');q.add_argument('root',type=Path);q.add_argument('public_export',type=Path);q.add_argument('next_server_mods',type=Path);q.add_argument('--label',default='r1')
 q=sub.add_parser('command');q.add_argument('root',type=Path);q.add_argument('command',choices=['local-test-on','local-test-off','stage','offer','status','commit','cancel','close','stop']);q.add_argument('--label',default='r1')
 a=p.parse_args();project=safe(a.project)
 if a.action in {'init','recover'}:
  java=a.java or (Path(os.environ['JAVA_HOME'])/'bin/java.exe' if os.environ.get('JAVA_HOME') else Path(shutil.which('java') or 'java'));root=a.root if a.action=='init' else a.target
  if root is None:
   local=os.environ.get('LOCALAPPDATA')
   if not local:raise ValueError('DEDICATED75_SET_ROOT')
   parent=safe(Path(local)/'Genesis/dedicated75');parent.mkdir(parents=True,exist_ok=True);root=parent/uuid.uuid4().hex
  if a.action=='init':initialize(project,safe(a.server),safe(root),safe(java));result={'root':str(safe(root))}
  else:
   try:result=recover(project,safe(a.root),a.generation,a.ticket,safe(root),safe(java),a.allow_older)
   except Exception:
    if safe(root).is_dir():print(json.dumps({'incompleteRoot':str(safe(root))},ensure_ascii=False))
    raise
  print(json.dumps(result,ensure_ascii=False));return
 root=safe(a.root);s,key=old.settings(root)
 if a.action=='catalog':
  result=catalog(root)
  try:result['repairPending']=repair78.inspect(root)
  except Exception:result['repairPending']=False
  print(json.dumps(result,ensure_ascii=False));return
 if a.action=='repair':print(json.dumps(repair78.repair(project,root,s['java']),ensure_ascii=False));return
 if a.action.startswith('tls-'):
  if a.action=='tls-status':result=https78.status(root)
  elif a.action=='tls-disable':result=https78.disable(root)
  elif a.action=='tls-probe':
   _,_,config=https78.load(root)
   if not config:raise ValueError('HTTPS78_NOT_CONFIGURED')
   result=https78.java_check(project,s['java'],'probe',config['endpoint'],root/'publisher73/public.der')
  elif a.action=='tls-check':result=https78.check(project,root,a.public,a.cert,a.key,a.bind,a.java or s['java']);result={k:result[k] for k in ('endpoint','fingerprint','expires','bind','port')}
  else:result=https78.save(project,root,a.public,a.cert,a.key,a.bind,a.java or s['java'])
  print(json.dumps(result,ensure_ascii=False));return
 if a.action=='status':
  from first58.bridge67 import read
  print(json.dumps(read(root/'view75',key) if (root/'view75').exists() else {'phase':'INITIALIZED','root':str(root)},ensure_ascii=False));return
 if a.action=='history':print(json.dumps(History75(root,key,s['run']).public(None),ensure_ascii=False,indent=2));return
 if a.action=='author':old.core(project,s['java'],'author',root/'publisher73',safe(a.public_export),safe(a.next_server_mods),root/'releases'/old.label(a.label));print(json.dumps({'authored':a.label}));return
 if a.action=='command':print(json.dumps({'queued':submit(root,{'stage':'STAGE','stop':'SHUTDOWN'}.get(a.command,a.command.upper().replace('-','_')),a.label)}));return
 if a.action=='run':
  if (root/'started74').exists():raise ValueError('DEDICATED77_USE_RECOVERY')
  if (root/'import77.pending').exists():raise ValueError('DEDICATED77_INCOMPLETE_RECOVERY')
  if os.name!='nt':raise ValueError('DEDICATED75_WINDOWS_X64_ONLY')
  independent_owner()
  from first_launch import ownership
  with ownership(root):(Owner78 if (root/'owner77').is_file() else Owner76)(project,root).run()
if __name__=='__main__':
 try:main()
 except Exception as e:
  code=e.args[0] if len(e.args)==1 and isinstance(e.args[0],str) and __import__('re').fullmatch('[A-Z0-9_]{1,100}',e.args[0]) else 'DEDICATED75_OPERATION_FAILED'
  print(code,file=sys.stderr);raise SystemExit(2)
