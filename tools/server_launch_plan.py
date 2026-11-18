#!/usr/bin/env python3
"""Prepare/verify an offline Genesis server launch plan. Never starts a server."""
from pathlib import Path
import argparse, subprocess, sys
from server_supervisor import java_home

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('project',type=Path)
    parser.add_argument('command',choices=['prepare','verify'])
    parser.add_argument('target',type=Path,help='Supervisor config (prepare) or launch.bin (verify)')
    parser.add_argument('value',help='NEW directory (prepare) or exact expected SHA256 (verify)')
    parser.add_argument('--java-home')
    args=parser.parse_args();project=args.project.expanduser().resolve();_,java=java_home(args.java_home)
    classes=project/'technologies/core/build/classes/java/main'
    if not (classes/'com/fixmer/genesis/technology/server/process/ServerNativeLaunchCli.class').is_file():
        raise ValueError('Launch plan classes are missing. Run gradlew.bat :genesis-core:classes first.')
    target=args.target.expanduser().resolve()
    value=str(Path(args.value).expanduser().resolve()) if args.command=='prepare' else args.value
    if args.command=='verify' and (len(value)!=64 or any(c not in '0123456789abcdef' for c in value)):
        raise ValueError('Expected an exact lowercase SHA256, 64 characters.')
    return subprocess.call([str(java),'-cp',str(classes),'com.fixmer.genesis.technology.server.process.ServerNativeLaunchCli',args.command,str(target),value],cwd=project)

if __name__=='__main__':
    try:raise SystemExit(main())
    except (ValueError,OSError) as bad:raise SystemExit('Server launch plan: '+str(bad))
