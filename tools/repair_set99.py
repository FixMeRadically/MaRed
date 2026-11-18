#!/usr/bin/env python3
"""Inspect or explicitly restore a Genesis99 mod set after its owner stopped; never launches Minecraft."""
import argparse,re,subprocess,zipfile
from recover_local92 import capsule,config_path,java_path
REQUIRED={'com/fixmer/genesis/supervisor/SetRecovery99.class','com/fixmer/genesis/supervisor/ModSet99.class','com/fixmer/genesis/supervisor/ModSetMetadata99.class'}
def command(project,profile=None,java=None,action='inspect',revision=''):
    if action not in {'inspect','restore','cancel-pending'}:raise ValueError('Неизвестное действие.')
    if action!='inspect' and not re.fullmatch('[a-f0-9]{64}',revision):raise ValueError('Нужен revision из свежего просмотра --inspect.')
    jar=capsule(project)
    with zipfile.ZipFile(jar) as archive:
        if not REQUIRED<=set(archive.namelist()):raise ValueError('Сначала собери патч99: gradlew.bat build.')
    result=[str(java_path(java)),'-cp',str(jar),'com.fixmer.genesis.supervisor.SetRecovery99','--config='+str(config_path(project,profile)),'--'+action]
    if action!='inspect':result+=['--revision='+revision]
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',nargs='?',default='.');p.add_argument('--profile');p.add_argument('--java');g=p.add_mutually_exclusive_group();g.add_argument('--inspect',action='store_true');g.add_argument('--restore',action='store_true');g.add_argument('--cancel-pending',action='store_true');p.add_argument('--revision',default='');a=p.parse_args()
    try:
        action='restore' if a.restore else 'cancel-pending' if a.cancel_pending else 'inspect';args=command(a.project,a.profile,a.java,action,a.revision)
        version=subprocess.run([args[0],'-version'],capture_output=True,text=True,timeout=5);match=re.search(r'version\s+"?(\d+)',version.stdout+version.stderr)
        if version.returncode or not match or int(match[1])<21:raise ValueError('Нужен JDK21. Укажи --java с путём к java.exe.')
        return subprocess.run(args,check=False).returncode
    except (ValueError,OSError,zipfile.BadZipFile,subprocess.TimeoutExpired) as e:print(str(e));return 2
if __name__=='__main__':raise SystemExit(main())
