#!/usr/bin/env python3
"""Read-only Java inspection of evidence or a native launch intent. Never starts a server."""
from pathlib import Path
import argparse,subprocess,sys,os
from server_supervisor import java_home

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path);p.add_argument('action',choices=('intent','evidence','stop'));p.add_argument('target',type=Path);p.add_argument('--sha256');p.add_argument('--helper',type=Path);p.add_argument('--java-home');a=p.parse_args();project=a.project.resolve();_,java=java_home(a.java_home);classes=project/'technologies/core/build/classes/java/main'
    if not (classes/'com/fixmer/genesis/technology/server/process/ServerNativeReviewCli.class').is_file():raise ValueError('Build :genesis-core:classes first')
    target=a.target.expanduser().resolve();args=[a.action,str(target)]
    if a.action=='evidence':
        if not a.sha256 or len(a.sha256)!=64 or any(c not in '0123456789abcdef' for c in a.sha256) or a.helper is None:raise ValueError('evidence requires --sha256 and --helper')
        args.extend([a.sha256,str(a.helper.expanduser().resolve())])
    elif a.sha256 or a.helper:raise ValueError('intent/stop do not accept evidence options')
    packages=project/'technologies/core/build/resources/main'
    return subprocess.call([str(java),'-cp',os.pathsep.join((str(classes),str(packages))),'com.fixmer.genesis.technology.server.process.ServerNativeReviewCli',*args],cwd=project)
if __name__=='__main__':
    try:raise SystemExit(main())
    except (ValueError,OSError) as bad:raise SystemExit('Native review: '+str(bad))
