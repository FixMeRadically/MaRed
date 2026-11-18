#!/usr/bin/env python3
"""Genesis emergency client. Talks only to the existing owner; no offline JAR replacement or game launch."""
from pathlib import Path
import argparse,os,re,shutil,subprocess,zipfile,uuid
REQUIRED={'com/fixmer/genesis/admission/ProfileAgent93.class','com/fixmer/genesis/supervisor/ProfileLaunch93.class','com/fixmer/genesis/supervisor/RecoveryBootstrap92.class','com/fixmer/genesis/supervisor/RecoveryConnection92.class','com/fixmer/genesis/supervisor/RecoveryControl92.class','com/fixmer/genesis/supervisor/RecoveryAuth92.class','com/fixmer/genesis/supervisor/ProfileLease92.class'}

def config_path(project,profile=None):
 project=Path(project).resolve()
 if profile is not None:
  p=Path(profile).resolve()/'config/mared/dev/supervisor.properties'
  if not p.is_file():raise ValueError('В указанном профиле нет настроенного supervisor.')
  return p
 candidates=[project/'config/mared/dev/supervisor.properties',project/'run/client/config/mared/dev/supervisor.properties'];found=[p for p in candidates if p.is_file()]
 if len(found)!=1:raise ValueError('Нужен ровно один профиль. Укажи --profile с папкой игры; владелец должен оставаться доступен.')
 return found[0]

def capsule(project):
 root=Path(project).resolve();paths=[root/'build/supervisor/resource/mared/dev/supervisor-bootstrap.jar',root/'build/resources/main/mared/dev/supervisor-bootstrap.jar']
 for path in paths:
  if path.is_symlink() or not path.is_file() or path.stat().st_size>32*1024*1024:continue
  try:
   with zipfile.ZipFile(path) as z:
    if REQUIRED<=set(z.namelist()):return path
  except (OSError,zipfile.BadZipFile):pass
 raise ValueError('Сначала собери текущий патч93: gradlew.bat build. Нужен bootstrap именно этой сборки.')

def java_path(explicit=None):
 if explicit:
  p=Path(explicit).resolve()
  if not p.is_file():raise ValueError('Указанный java.exe не найден.')
  return p
 home=os.environ.get('JAVA_HOME');name='java.exe' if os.name=='nt' else 'java'
 if home and (Path(home)/'bin'/name).is_file():return Path(home)/'bin'/name
 found=shutil.which(name)
 if found:return Path(found)
 raise ValueError('Нужен JDK21. Укажи --java с полным путём к java.exe.')

def command(project,profile=None,java=None,status=False,action=None,ticket='',revision=''):
 if action is not None:
  try:
   if str(uuid.UUID(ticket))!=ticket:raise ValueError()
  except (ValueError,AttributeError):raise ValueError('Нужен ticket из свежего --status.') from None
  if action not in {'RESTORE','RETRY','REFRESH','CLOSE'} or not re.fullmatch('[a-f0-9-]{36}',ticket) or revision and not re.fullmatch('[a-f0-9]{64}',revision):raise ValueError('Сначала получи свежий --status; действие требует ticket и revision этого просмотра.')
 args=[str(java_path(java)),'-cp',str(capsule(project)),'com.fixmer.genesis.supervisor.RecoveryBootstrap92','--config='+str(config_path(project,profile))]
 if action:args+=['--action='+action,'--ticket='+ticket,'--revision='+revision]
 elif status:args+=['--status']
 return args

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',nargs='?',default='.');p.add_argument('--profile');p.add_argument('--java');p.add_argument('--status',action='store_true');p.add_argument('--action',choices=['RESTORE','RETRY','REFRESH','CLOSE']);p.add_argument('--ticket',default='');p.add_argument('--revision',default='');a=p.parse_args()
 try:
  args=command(a.project,a.profile,a.java,a.status,a.action,a.ticket,a.revision)
  check=subprocess.run([args[0],'-version'],capture_output=True,text=True,timeout=5);m=re.search(r'version\s+"?(\d+)',check.stdout+check.stderr)
  if check.returncode or not m or int(m[1])<21:raise ValueError('Нужен JDK21 или новее. Укажи --java с путём к JDK21\\bin\\java.exe.')
  return subprocess.run(args,check=False).returncode
 except (OSError,ValueError,subprocess.TimeoutExpired) as e:print(str(e));return 2
if __name__=='__main__':raise SystemExit(main())
