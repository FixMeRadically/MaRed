#!/usr/bin/env python3
"""Prepare/recheck signed server updates locally; no server stop or game installation."""
from pathlib import Path
import argparse,os,shutil,subprocess,sys

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path);p.add_argument('action',choices=('prepare','status','recheck','resume','cancel','demo'));p.add_argument('target',type=Path);p.add_argument('--output',type=Path);p.add_argument('--intent-sha256');p.add_argument('--confirm',action='store_true');p.add_argument('--java-home',type=Path);a=p.parse_args()
    if a.action=='prepare' and a.output is None:p.error('prepare requires --output NEW_DIRECTORY')
    if a.action in ('resume','recheck','cancel') and (a.intent_sha256 is None or len(a.intent_sha256)!=64 or any(c not in '0123456789abcdef' for c in a.intent_sha256)):p.error('Expected --intent-sha256 from the preparation report')
    if a.action=='cancel' and not a.confirm:p.error('cancel requires --confirm')
    if a.action!='prepare' and a.output is not None:p.error('--output is only for prepare')
    if a.action not in ('resume','recheck','cancel') and a.intent_sha256 is not None:p.error('--intent-sha256 is not valid for this action')
    if a.action!='cancel' and a.confirm:p.error('--confirm is only for cancel')
    project=a.project.resolve();classes=project/'technologies/core/build/classes/java/main'
    if not (classes/'com/fixmer/genesis/technology/server/preparation/PreparationCli.class').is_file():raise ValueError('Build genesis-core first: gradlew.bat :genesis-core:classes')
    java=(a.java_home.expanduser().resolve()/'bin'/('java.exe' if os.name=='nt' else 'java')) if a.java_home else shutil.which('java')
    if not java:raise ValueError('JDK21 required on PATH or --java-home')
    command=[str(java),'-cp',str(classes),'com.fixmer.genesis.technology.server.preparation.PreparationCli',a.action,str(a.target.absolute())]
    if a.action=='prepare':command.append(str(a.output.absolute()))
    if a.intent_sha256:command.append(a.intent_sha256)
    if a.confirm:command.append('--confirm')
    return subprocess.call(command,cwd=project)
if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError) as error:raise SystemExit('Server update preparation: '+str(error))
