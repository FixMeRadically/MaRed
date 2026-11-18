#!/usr/bin/env python3
"""Profile coordination model demo and read-only status. No live installation grant."""
from pathlib import Path
import argparse,subprocess
from server_supervisor import java_home

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java-home');p.add_argument('project',type=Path);p.add_argument('command',choices=['demo','status']);p.add_argument('target',type=Path);args=p.parse_args();project=args.project.resolve();_,java=java_home(args.java_home)
    classes=project/'technologies/core/build/classes/java/main'
    if not (classes/'com/fixmer/genesis/technology/server/packages/AdmissionCli.class').is_file():raise ValueError('Admission classes are missing. Run gradlew.bat :genesis-core:classes first.')
    return subprocess.call([str(java),'-cp',str(classes),'com.fixmer.genesis.technology.server.packages.AdmissionCli',args.command,str(args.target.resolve())],cwd=project)

if __name__=='__main__':
    try:raise SystemExit(main())
    except (ValueError,OSError) as bad:raise SystemExit('Profile admission: '+str(bad))
