#!/usr/bin/env python3
"""Inspect or rehearse player coordination in a NEW directory; never updates a running server or client."""
from pathlib import Path
import argparse,os,shutil,subprocess

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('project',type=Path)
    parser.add_argument('action',choices=('status','demo'))
    parser.add_argument('directory',type=Path)
    parser.add_argument('--java-home',type=Path)
    args=parser.parse_args()
    project=args.project.resolve()
    classes=project/'technologies/core/build/classes/java/main'
    if not (classes/'com/fixmer/genesis/technology/server/players/PlayerUpdateCli.class').is_file():
        raise ValueError('Build first: gradlew.bat :genesis-core:classes')
    java=str(args.java_home.expanduser().resolve()/'bin'/('java.exe' if os.name=='nt' else 'java')) if args.java_home else shutil.which('java')
    if not java:raise ValueError('JDK21 is required on PATH or --java-home')
    return subprocess.call([java,'-cp',str(classes),'com.fixmer.genesis.technology.server.players.PlayerUpdateCli',args.action,str(args.directory.absolute())],cwd=project)

if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError) as error:raise SystemExit('Player coordination: '+str(error))
