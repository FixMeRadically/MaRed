#!/usr/bin/env python3
"""Exercise sequential signed updates in NEW diagnostic profiles; never installs into an existing game."""
from pathlib import Path
import argparse,os,shutil,subprocess,sys

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path);p.add_argument('action',choices=('execute','status','recheck','recover','demo'));p.add_argument('target',type=Path);p.add_argument('--output',type=Path);p.add_argument('--cycle-sha256');p.add_argument('--confirm',action='store_true');p.add_argument('--java-home',type=Path);a=p.parse_args()
    if a.action=='execute' and a.output is None:p.error('execute requires --output NEW_DIRECTORY')
    if a.action in ('recover','recheck') and (a.cycle_sha256 is None or len(a.cycle_sha256)!=64 or any(c not in '0123456789abcdef' for c in a.cycle_sha256)):p.error('Expected --cycle-sha256 from the diagnostic cycle report')
    if a.action=='recover' and not a.confirm:p.error('recover requires --confirm')
    if a.action!='execute' and a.output is not None:p.error('--output is only for execute')
    if a.action not in ('recover','recheck') and a.cycle_sha256 is not None:p.error('--cycle-sha256 is not valid for this action')
    if a.action!='recover' and a.confirm:p.error('--confirm is only for recover')
    project=a.project.resolve();classes=project/'technologies/core/build/classes/java/main'
    if not (classes/'com/fixmer/genesis/technology/server/preparation/DiagnosticUpdateCli.class').is_file():raise ValueError('Build genesis-core first: gradlew.bat :genesis-core:classes')
    java=(a.java_home.expanduser().resolve()/'bin'/('java.exe' if os.name=='nt' else 'java')) if a.java_home else shutil.which('java')
    if not java:raise ValueError('JDK21 required on PATH or --java-home')
    command=[str(java),'-cp',str(classes),'com.fixmer.genesis.technology.server.preparation.DiagnosticUpdateCli',a.action,str(a.target.absolute())]
    if a.action=='execute':command.append(str(a.output.absolute()))
    if a.cycle_sha256:command.append(a.cycle_sha256)
    if a.confirm:command.append('--confirm')
    return subprocess.call(command,cwd=project)
if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError) as error:raise SystemExit('Server update diagnostic: '+str(error))
