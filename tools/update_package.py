#!/usr/bin/env python3
"""Local package authoring, verification and read-only planning. Never installs a release."""
from pathlib import Path
import argparse, os, subprocess, sys
from server_supervisor import java_home

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',help='JDK21 used to build Genesis')
    parser.add_argument('project',type=Path)
    parser.add_argument('command',choices=['keygen','create','verify','plan','demo'])
    parser.add_argument('arguments',nargs=argparse.REMAINDER)
    args=parser.parse_args();project=args.project.resolve();_,java=java_home(args.java_home)
    classes=project/'technologies/core/build/classes/java/main'
    if not (classes/'com/fixmer/genesis/technology/server/packages/PackageCli.class').is_file():raise ValueError('Package classes are missing. Run gradlew.bat :genesis-core:classes first.')
    return subprocess.call([str(java),'-cp',str(classes),'com.fixmer.genesis.technology.server.packages.PackageCli',args.command,*args.arguments],cwd=project)

if __name__=='__main__':
    try:raise SystemExit(main())
    except (ValueError,OSError) as bad:raise SystemExit('Update package: '+str(bad))
