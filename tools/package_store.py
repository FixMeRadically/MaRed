#!/usr/bin/env python3
"""Verified local staging, read-only status and NEW diagnostic profile demo. No live-game install."""
from pathlib import Path
import argparse,subprocess
from server_supervisor import java_home

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home');parser.add_argument('project',type=Path)
    parser.add_argument('command',choices=['stage','status','demo']);parser.add_argument('arguments',nargs=argparse.REMAINDER)
    args=parser.parse_args();project=args.project.resolve();_,java=java_home(args.java_home)
    classes=project/'technologies/core/build/classes/java/main'
    if not (classes/'com/fixmer/genesis/technology/server/packages/PackageStoreCli.class').is_file():raise ValueError('Store classes are missing. Run gradlew.bat :genesis-core:classes first.')
    return subprocess.call([str(java),'-cp',str(classes),'com.fixmer.genesis.technology.server.packages.PackageStoreCli',args.command,*args.arguments],cwd=project)

if __name__=='__main__':
    try:raise SystemExit(main())
    except (ValueError,OSError) as bad:raise SystemExit('Package store: '+str(bad))
