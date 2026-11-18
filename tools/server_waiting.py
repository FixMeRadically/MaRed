#!/usr/bin/env python3
"""Run the local external waiting/consent service; no game update or remote listener."""
from pathlib import Path
import argparse,os,shutil,subprocess

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('project',type=Path)
    p.add_argument('--java-home',type=Path)
    p.add_argument('action',choices=('init-prepared','init','serve','status','action','issue','revoke','stop','player-status','decision','retry-decision'))
    p.add_argument('arguments',nargs=argparse.REMAINDER)
    a=p.parse_args();project=a.project.resolve();classes=project/'technologies/core/build/classes/java/main'
    if not (classes/'com/fixmer/genesis/technology/server/waiting/WaitingCli.class').is_file():raise ValueError('Build first: gradlew.bat :genesis-core:classes')
    java=str(a.java_home.resolve()/'bin'/('java.exe' if os.name=='nt' else 'java')) if a.java_home else shutil.which('java')
    if not java:raise ValueError('JDK21 required on PATH or --java-home')
    return subprocess.call([java,'-cp',str(classes),'com.fixmer.genesis.technology.server.waiting.WaitingCli',a.action,*a.arguments],cwd=project)
if __name__=='__main__':
    try:raise SystemExit(main())
    except (ValueError,OSError) as error:raise SystemExit('Waiting service: '+str(error))
