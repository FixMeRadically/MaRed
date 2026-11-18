#!/usr/bin/env python3
"""Explicit cold recovery entry. No PID adoption, port scanning, shell, automatic restore or acceptance."""
import argparse,re,subprocess
import recover_local92 as local

def command(project,profile=None,java=None,check=False):
 return [str(local.java_path(java)),'-cp',str(local.capsule(project)),
         'com.fixmer.genesis.supervisor.ColdOwner94' if check else 'com.fixmer.genesis.supervisor.Supervisor',
         '--config='+str(local.config_path(project,profile)), '--check' if check else '--recover-cold94']

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',nargs='?',default='.');p.add_argument('--profile');p.add_argument('--java');p.add_argument('--check',action='store_true');a=p.parse_args()
 try:
  args=command(a.project,a.profile,a.java,a.check);v=subprocess.run([args[0],'-version'],capture_output=True,text=True,timeout=5);m=re.search(r'version\s+"?(\d+)',v.stdout+v.stderr)
  if v.returncode or not m or int(m[1])<21:raise ValueError('Нужен JDK21 или новее. Укажи --java с полным путём к java.exe.')
  return subprocess.run(args,check=False).returncode
 except (OSError,ValueError,subprocess.TimeoutExpired) as e:print(str(e));return 2
if __name__=='__main__':raise SystemExit(main())
