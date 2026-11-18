#!/usr/bin/env python3
"""Isolated SAME-PC Windows dedicated-server/client pilot. Never updates original profiles."""
from pathlib import Path
import argparse,contextlib,json,os,sys
from first58.common import safe,json_write,json_read
from first58.setup import initialize,doctor
from first58.controller import Pilot

@contextlib.contextmanager
def ownership(root):
 import msvcrt
 path=safe(root/'controller.lock')
 with path.open('a+b') as lease:
  if path.stat().st_size==0:lease.write(b'0');lease.flush()
  lease.seek(0);msvcrt.locking(lease.fileno(),msvcrt.LK_NBLCK,1)
  try:yield
  finally:lease.seek(0);msvcrt.locking(lease.fileno(),msvcrt.LK_UNLCK,1)

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('project',type=Path);sub=parser.add_subparsers(dest='action',required=True)
 p=sub.add_parser('example');p.add_argument('output',type=Path)
 p=sub.add_parser('capture-client');p.add_argument('pid',type=int);p.add_argument('output',type=Path)
 p=sub.add_parser('init');p.add_argument('root',type=Path);p.add_argument('spec',type=Path)
 for name in ('doctor','run','status'):
  p=sub.add_parser(name);p.add_argument('root',type=Path)
 a=parser.parse_args();project=safe(a.project)
 if a.action=='capture-client':
  from first58.capture import capture
  print(json.dumps(capture(project,a.pid,a.output),ensure_ascii=False));return 0
 if a.action=='example':
  sample={'serverTemplate':'C:\\GenesisTemplates\\server','clientTemplate':'C:\\GenesisTemplates\\client','nextServerMods':'C:\\GenesisTemplates\\next-server-mods','nextClientMods':'C:\\GenesisTemplates\\next-client-mods','serverCommand':['C:\\Program Files\\Java\\jdk-21.0.12\\bin\\java.exe','@user_jvm_args.txt','@libraries/net/neoforged/neoforge/21.1.REPLACE/win_args.txt','nogui'],'clientCommand':['C:\\Program Files\\Java\\jdk-21.0.12\\bin\\java.exe','REPLACE_WITH_COMPLETE_CLIENT_JAVA_ARGUMENTS','--gameDir','{profile}'],'account':'REPLACE_WITH_ONLINE_ACCOUNT_UUID','minecraft':'1.21.1','neoforge':'21.1.REPLACE','port':27690};json_write(safe(a.output),sample,new=True);print('Created config template. It is not runnable until real commands and versions are supplied.');return 0
 root=safe(a.root)
 if a.action=='init':print(json.dumps(initialize(project,root,a.spec),ensure_ascii=False));return 0
 if a.action=='status':print(json.dumps(json_read(root/'report.json'),ensure_ascii=False,indent=2));return 0
 if os.name!='nt':raise ValueError('Windows x64 required; no native simulation pass')
 with ownership(root):
  if a.action=='doctor':print(json.dumps(doctor(project,root),ensure_ascii=False));return 0
  if (root/'started.v58').exists():raise ValueError('Already-run root: no automatic recovery/replay. Preserve saved generations and use a NEW pilot root.')
  doctor(project,root) # Real current-host native descendant self-test is mandatory, not a saved boolean.
  from first58.ui import run_cover
  run_cover(lambda notify,cancel,ask:Pilot(project,root,notify,cancel,ask).run(),root/'generations/client-0')
 return 0
if __name__=='__main__':
 try:raise SystemExit(main())
 except (OSError,ValueError,RuntimeError,TimeoutError) as error:raise SystemExit('Genesis first58: '+str(error))
