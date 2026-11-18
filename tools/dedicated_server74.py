#!/usr/bin/env python3
"""Dedicated74: import one stopped server, own its copies, stage and perform one checked update."""
from pathlib import Path
import argparse,json,os,shutil,sys,uuid
from first58.common import safe,json_read
from first58.dedicated74 import initialize,settings,core,submit,Owner74,label

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path);sub=p.add_subparsers(dest='action',required=True)
 q=sub.add_parser('init');q.add_argument('server',type=Path);q.add_argument('--root',type=Path);q.add_argument('--java',type=Path)
 for name in ('run','status'):
  q=sub.add_parser(name);q.add_argument('root',type=Path)
 q=sub.add_parser('author');q.add_argument('root',type=Path);q.add_argument('public_export',type=Path);q.add_argument('next_server_mods',type=Path);q.add_argument('--label',default='r1')
 q=sub.add_parser('command');q.add_argument('root',type=Path);q.add_argument('command',choices=['local-test-on','local-test-off','stage','offer','status','commit','cancel','close','stop']);q.add_argument('--label',default='r1')
 a=p.parse_args();project=safe(a.project)
 if a.action=='init':
  java=a.java or (Path(os.environ['JAVA_HOME'])/'bin/java.exe' if os.environ.get('JAVA_HOME') else Path(shutil.which('java') or 'java'))
  root=a.root
  if root is None:
   local=os.environ.get('LOCALAPPDATA')
   if not local:raise ValueError('DEDICATED74_SET_ROOT_OR_LOCALAPPDATA')
   parent=safe(Path(local)/'Genesis/dedicated74');parent.mkdir(parents=True,exist_ok=True);root=parent/uuid.uuid4().hex
  root=safe(root);existed=root.exists()
  try:initialize(project,a.server,root,java)
  except Exception:
   if not existed and root.exists():
    from first58.common import json_write
    json_write(root/'report.json',{'schema':74,'phase':'INITIALIZATION_FAILED','sourceModified':False,'root':str(root)});print('Incomplete private root; inspect locally:',root,file=sys.stderr)
   raise
  print(json.dumps({'root':str(safe(root)),'controlRoot':str(safe(root)/'control73'),'next':'run'},ensure_ascii=False,indent=2));return
 root=safe(a.root)
 if a.action=='status':print(json.dumps(json_read(root/'report.json'),ensure_ascii=False,indent=2));return
 s,key=settings(root)
 if a.action=='author':
  destination=root/'releases'/label(a.label);core(project,s['java'],'author',root/'publisher73',safe(a.public_export),safe(a.next_server_mods),destination);print('Paired release authored:',a.label);return
 if a.action=='command':
  action={'stage':'STAGE','stop':'SHUTDOWN'}.get(a.command,a.command.upper().replace('-','_'));print('Queued:',submit(root,action,a.label));return
 if a.action=='run':
  if os.name!='nt':raise ValueError('DEDICATED74_WINDOWS_X64_ONLY')
  from first_launch import ownership
  with ownership(root):Owner74(project,root).run()

if __name__=='__main__':
 try:main()
 except Exception:print('Dedicated74: operation refused/stopped. Check paths, base74 build and private report.json/logs. Original server profile was not modified.',file=sys.stderr);raise SystemExit(2)
