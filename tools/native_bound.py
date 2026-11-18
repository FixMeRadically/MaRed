#!/usr/bin/env python3
"""Run a disposable bound native diagnostic; never a Minecraft server or installer."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,sys,tempfile,uuid
import native_evidence as evidence
n=evidence.native
CASES=('graceful','cancelBeforeStart','forced')

def sources(project):
    root=project/'technologies/core/src/main/java/com/fixmer/genesis/technology/server'
    files=sorted((root/'process').glob('*.java'))+sorted((root/'packages').glob('*.java'))
    if len(files)!=49:raise ValueError('Expected the complete patch54 headless source set')
    for path in files:
        if path.is_symlink() or not path.is_file() or path.stat().st_size>256*1024:raise ValueError('Invalid headless source')
    return files

def fingerprint(project):
    sha=hashlib.sha256()
    for path in [*sources(project),project/'tools/native_bound.py']:
        if path.is_symlink():raise ValueError('Linked binding source refused')
        with path.open('rb') as source:raw=source.read(256*1024+1)
        if len(raw)>256*1024:raise ValueError('Oversized binding source')
        sha.update(path.relative_to(project).as_posix().encode('utf-8')+b'\0'+raw.replace(b'\r\n',b'\n')+b'\0')
    return sha.hexdigest()

def validate_result(result):
    if not isinstance(result,dict) or set(result)!={'schema',*CASES,'all_passed','singleJvmHandoffFixture','gameLaunched','productionLaunchAllowed','installAuthority'} or type(result['schema']) is not int or result['schema']!=48 or any(result[k] is not True for k in (*CASES,'all_passed','singleJvmHandoffFixture')) or any(result[k] is not False for k in ('gameLaunched','productionLaunchAllowed','installAuthority')):raise ValueError('Invalid bound48 diagnostic result')

def inputs(project,output,bundle,sha):
    if not n.hash_value(sha):raise ValueError('Expected evidence properties SHA256')
    current,raw=evidence.current(project,output)
    if evidence.sha(evidence.bounded(bundle/'evidence.properties'))!=sha or evidence.bounded(bundle/'evidence.json')!=evidence.canonical(current) or any(evidence.bounded(bundle/'raw'/name)!=raw[name] for name in evidence.FILES):raise ValueError('Evidence does not match current runtime reports')
    return {'binding_sha256':fingerprint(project),'source_sha256':current['source_sha256'],'binary_sha256':current['binary_sha256'],'runtime_java_sha256':current['java_sha256'],'evidence_sha256':sha}

def status(project,output):
    result={'schema':48,'state':'NOT_RUN','historical':True,'productionLaunchAllowed':False,'installAuthority':False}
    try:
        if not (output/'bound-attempt.json').exists() and not (output/'bound-demo.json').exists():return result
        marker_raw=evidence.bounded(output/'bound-attempt.json');marker=n.decode_json(marker_raw.decode('utf-8'))
        if set(marker)!={'schema','state','attempt'} or type(marker['schema']) is not int or marker['schema']!=48 or not evidence.identity(marker['attempt']) or marker['state'] not in ('RUNNING','FAILED','COMPLETED'):raise ValueError('Invalid bound48 attempt')
        result['state']=marker['state']
        if marker['state']!='COMPLETED':return result
        report_raw=evidence.bounded(output/'bound-demo.json');report=n.decode_json(report_raw.decode('utf-8'))
        expected={'schema','attempt','binding_sha256','source_sha256','binary_sha256','runtime_java_sha256','evidence_sha256','result','installAuthority'}
        if set(report)!=expected or type(report['schema']) is not int or report['schema']!=48 or report['attempt']!=marker['attempt'] or report['installAuthority'] is not False or any(not n.hash_value(report[k]) for k in expected if k.endswith('_sha256')):raise ValueError('Invalid bound48 report')
        validate_result(report['result'])
        result.update(all_passed=True,matches_current_inputs=report['binding_sha256']==fingerprint(project) and report['source_sha256']==n.source_fingerprint(project) and report['runtime_java_sha256']==n.java46_hash(project) and report['binary_sha256']==n.digest(output/'genesis-job43.exe'))
        if marker_raw!=evidence.bounded(output/'bound-attempt.json') or report_raw!=evidence.bounded(output/'bound-demo.json'):raise ValueError('Diagnostic history changed during status snapshot')
    except (OSError,ValueError,KeyError,TypeError) as bad:
        result.pop('all_passed',None);result.pop('matches_current_inputs',None);result.update(state='UNKNOWN',error=str(bad))
    return result

def demo(project,output,bundle,sha,destination=None):
    if sys.platform!='win32':raise ValueError('Bound native diagnostics require Windows; no files changed')
    before=inputs(project,output,bundle,sha);java=shutil.which('java.exe')
    if not java:raise ValueError('JDK17+ required on PATH')
    marker={'schema':48,'state':'RUNNING','attempt':str(uuid.uuid4())}
    destination=evidence.safe_new(destination if destination is not None else output/('bound probes48-\u03a9-'+marker['attempt']))
    n.atomic_json(output/'bound-attempt.json',marker)
    try:
        with tempfile.TemporaryDirectory(prefix='bound48-classes-',dir=output) as classes:
            subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',classes,*map(str,sources(project))],check=True,timeout=60)
            # Fixed diagnostic entrypoint and generated fixed Probe; no arbitrary launch arguments.
            run=subprocess.run([java,'-cp',classes,'com.fixmer.genesis.technology.server.process.ServerNativeBoundCli',str(output/'genesis-job43.exe'),str(bundle),sha,str(destination)],check=True,capture_output=True,timeout=120)
            if len(run.stdout)>n.MAX_REPORT or len(run.stderr)>n.MAX_REPORT:raise ValueError('Oversized bound48 output')
            result=n.decode_json(run.stdout.decode('utf-8'));validate_result(result)
            if inputs(project,output,bundle,sha)!=before:raise ValueError('Binding inputs changed during diagnostic')
            n.atomic_json(output/'bound-demo.json',{'schema':48,'attempt':marker['attempt'],**before,'result':result,'installAuthority':False})
        marker['state']='COMPLETED';n.atomic_json(output/'bound-attempt.json',marker)
    except BaseException:
        marker['state']='FAILED';n.atomic_json(output/'bound-attempt.json',marker);raise
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path);p.add_argument('action',choices=('status','demo'),nargs='?',default='status');p.add_argument('--evidence',type=Path);p.add_argument('--sha256');p.add_argument('--output',type=Path);a=p.parse_args();project,output=n.paths(a.project)
    if a.action=='status':
        if a.evidence or a.sha256 or a.output:raise ValueError('Status does not accept execution options')
        result=status(project,output)
    else:
        if sys.platform!='win32':raise ValueError('Bound native diagnostics require Windows; no files changed')
        if a.evidence is None:raise ValueError('demo requires --evidence and --sha256')
        with n.owner_lock(output):result=demo(project,output,a.evidence.absolute(),a.sha256,a.output)
    print(json.dumps(result,ensure_ascii=False,indent=2));return 2 if result.get('state')=='UNKNOWN' else 0
if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError,KeyError,TypeError,subprocess.SubprocessError) as bad:raise SystemExit('Bound native diagnostic: '+str(bad))
