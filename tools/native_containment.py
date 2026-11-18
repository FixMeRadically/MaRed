#!/usr/bin/env python3
"""Build/run Genesis Windows Job diagnostics. Never launches Minecraft or enables LIVE install."""
from pathlib import Path
import argparse, hashlib, json, os, platform, re, shutil, subprocess, sys, tempfile, time, uuid
from contextlib import contextmanager
SCHEMA=43
SOURCE_NAMES=('record.h','record.c','job.h','job.c','main.c','owner.h','owner.c','owner_protocol.h','owner_protocol.c','capsule.h','capsule.c','java_owner.h','java_owner.c')
CASES=('suspended_assignment','membership_and_limits','descendant_kill_on_close','root_exit_keeps_descendant','breakaway_denied','owner_exit_kills_inner_job','owner_crash_kills_inner_job','failed_spawn_no_handle_leak')
MAX_REPORT=64*1024

def digest(path):
    if path.is_symlink() or not path.is_file():raise ValueError('Invalid native binary path')
    result=hashlib.sha256();total=0
    with path.open('rb') as source:
        while True:
            chunk=source.read(64*1024)
            if not chunk:break
            total+=len(chunk)
            if total>32*1024*1024:raise ValueError('Native binary exceeds 32 MiB')
            result.update(chunk)
    return result.hexdigest()
def finite_only(value):raise ValueError('Non-finite JSON value: '+value)
def hash_value(value):return isinstance(value,str) and re.fullmatch('[0-9a-f]{64}',value) is not None
def validate_build(data):
    if not isinstance(data,dict) or set(data)!={'schema','compiler','source_sha256','binary_sha256','host','install_authority'}:raise ValueError('Wrong build metadata shape')
    if type(data['schema']) is not int or data['schema']!=43 or data['compiler'] not in ('msvc','mingw') or data['install_authority'] is not False:raise ValueError('Wrong build metadata protocol')
    if not hash_value(data['source_sha256']) or not hash_value(data['binary_sha256']) or not isinstance(data['host'],str) or not 1<=len(data['host'])<=512:raise ValueError('Invalid build identity')
    return data
def no_duplicates(pairs):
    result={}
    for key,value in pairs:
        if key in result:raise ValueError('Duplicate JSON key: '+key)
        result[key]=value
    return result
def decode_json(data):
    if not isinstance(data,str):raise ValueError('JSON text required')
    depth=0;quoted=False;escaped=False
    for char in data:
        if quoted:
            if escaped:escaped=False
            elif char=='\\':escaped=True
            elif char=='"':quoted=False
        elif char=='"':quoted=True
        elif char in '{[':
            depth+=1
            if depth>32:raise ValueError('JSON nesting exceeds 32')
        elif char in '}]':depth-=1
    try:return json.loads(data,object_pairs_hook=no_duplicates,parse_constant=finite_only)
    except RecursionError as error:raise ValueError('JSON nesting exceeds parser limit') from error
def read_json(path):
    if path.is_symlink() or path.stat().st_size>MAX_REPORT:raise ValueError('Invalid diagnostic report path/size')
    return decode_json(path.read_text(encoding='utf-8'))
def source_fingerprint(project):
    root=project/'native/windows-job';sha=hashlib.sha256()
    for name in SOURCE_NAMES:
        path=root/name
        if path.is_symlink() or not path.is_file() or path.stat().st_size>256*1024:raise ValueError('Invalid native source: '+name)
        # Checkout CRLF does not stale a source build, binary SHA is always exact.
        sha.update(name.encode('ascii')+b'\0'+path.read_bytes().replace(b'\r\n',b'\n')+b'\0')
    return sha.hexdigest()
def paths(project):
    project=project.resolve()
    if not (project/'settings.gradle').is_file():raise ValueError('MaRed project root not found')
    for relative in ('native','native/windows-job','build','build/genesis-native43'):
        if (project/relative).is_symlink():raise ValueError('Symlink/reparse output refused: '+relative)
        # Python 3.12+ Windows junction check; older runtimes use resolved ancestry below.
        if getattr(project/relative,'is_junction',lambda:False)():raise ValueError('Junction refused: '+relative)
        if not (project/relative).resolve().is_relative_to(project):raise ValueError('Native path escapes project')
    return project,project/'build/genesis-native43'
def atomic_json(path,data):
    fd,name=tempfile.mkstemp(prefix='.native43-',suffix='.json',dir=path.parent)
    try:
        with os.fdopen(fd,'w',encoding='utf-8',newline='\n') as out:
            json.dump(data,out,ensure_ascii=False,indent=2);out.write('\n');out.flush();os.fsync(out.fileno())
        os.replace(name,path)
    finally:Path(name).unlink(missing_ok=True)
@contextmanager
def owner_lock(output):
    output.mkdir(parents=True,exist_ok=True);path=output/'operation.lock'
    if path.is_symlink():raise ValueError('Native operation lock is a symlink')
    with path.open('a+b') as lock:
        if path.stat().st_size==0:lock.write(b'0');lock.flush()
        lock.seek(0)
        if sys.platform=='win32':
            import msvcrt
            msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        else:
            import fcntl
            fcntl.flock(lock.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
        try:yield
        finally:
            lock.seek(0)
            if sys.platform=='win32':msvcrt.locking(lock.fileno(),msvcrt.LK_UNLCK,1)
            else:fcntl.flock(lock.fileno(),fcntl.LOCK_UN)

def validate_native(data):
    if not isinstance(data,dict) or set(data)!={'schema','mode','install_authority','cases','all_passed'}:raise ValueError('Wrong helper report shape')
    if type(data['schema']) is not int or data['schema']!=43 or data['mode']!='diagnostic_fixtures_only' or data['install_authority'] is not False:raise ValueError('Wrong helper protocol')
    rows=data['cases']
    if not isinstance(rows,list) or len(rows)!=len(CASES):raise ValueError('Incomplete native cases')
    for expected,row in zip(CASES,rows):
        if not isinstance(row,dict) or set(row)!={'case','pass','win32_error'} or row['case']!=expected:raise ValueError('Unknown/reordered native case')
        if type(row['pass']) is not bool or type(row['win32_error']) is not int or not 0<=row['win32_error']<=4294967295:raise ValueError('Invalid native case result')
        if row['pass'] and row['win32_error']!=0:raise ValueError('Successful case has an error')
    if type(data['all_passed']) is not bool or data['all_passed']!=all(row['pass'] for row in rows):raise ValueError('Inconsistent native summary')
    return data['all_passed']
def legacy_status(project,output):
    result={'schema':43,'mode':'diagnostic_fixtures_only','windows_host':sys.platform=='win32','install_authority':False,'supervisor_backend_activated':False,'historical_selftest':None,'minimum_os':'Windows 10 / Windows Server 2016 (64-bit)','compiler_available':{'msvc':shutil.which('cl.exe') is not None,'mingw':shutil.which('gcc.exe') is not None},'binary_present':(output/'genesis-job43.exe').is_file()}
    report=output/'selftest.json';attempt_path=output/'attempt.json';exe=output/'genesis-job43.exe';build=output/'build.json'
    if attempt_path.exists():
        try:
            attempt=read_json(attempt_path)
            if set(attempt)!={'schema','id','state'} or type(attempt['schema']) is not int or attempt['schema']!=43 or not isinstance(attempt['id'],str) or str(uuid.UUID(attempt['id']))!=attempt['id'] or attempt['state'] not in ('RUNNING','COMPLETED','FAILED'):raise ValueError('Invalid native attempt marker')
            result['latest_attempt_state']=attempt['state']
            if attempt['state']!='COMPLETED':
                result['report_error']='Latest selftest did not commit a complete result';return result
        except (OSError,ValueError,TypeError,KeyError) as error:
            result['report_error']=str(error);return result
    else:attempt=None
    if not report.exists():
        if attempt is not None:result['report_error']='Completed attempt has no report'
        return result
    try:
        data=read_json(report)
        if set(data)!={'schema','attempt','host','completed_unix_ms','source_sha256','binary_sha256','native'} or (type(data['schema']) is not int or data['schema']!=43):raise ValueError('Wrong stored report schema')
        if not isinstance(data['host'],str) or not 1<=len(data['host'])<=512 or type(data['completed_unix_ms']) is not int or not 0<data['completed_unix_ms']<2**63 or not hash_value(data['source_sha256']) or not hash_value(data['binary_sha256']):raise ValueError('Invalid stored result identity')
        if attempt is None or data['attempt']!=attempt['id']:raise ValueError('Report belongs to another attempt')
        passed=validate_native(data['native'])
        current=(exe.is_file() and not exe.is_symlink() and data['binary_sha256']==digest(exe) and data['source_sha256']==source_fingerprint(project))
        result['historical_selftest']={'all_passed':passed,'matches_current_source_and_binary':bool(current),'host':data['host'],'completed_unix_ms':data['completed_unix_ms'],'live_containment_proof':False}
        if not build.exists():result['historical_selftest']['build_metadata_missing']=True
    except (OSError,ValueError,TypeError,KeyError) as error:result['report_error']=str(error)
    return result

OWNER_JAVA_NAMES=('ServerFiles.java','ServerJson.java','ServerNativeOwnerProtocol.java','ServerNativeOwnerSession.java','ServerNativeOwnerCli.java')
def owner_java_sources(project):return [project/'technologies/core/src/main/java/com/fixmer/genesis/technology/server/process'/name for name in OWNER_JAVA_NAMES]
def owner_java_hash(project):
    sha=hashlib.sha256()
    for source in owner_java_sources(project):
        if source.is_symlink() or not source.is_file() or source.stat().st_size>256*1024:raise ValueError('Invalid native bridge source')
        sha.update(source.name.encode('ascii')+b'\0'+source.read_bytes().replace(b'\r\n',b'\n')+b'\0')
    return sha.hexdigest()
def validate_owner_result(result):
    if not isinstance(result,dict) or set(result)!={'schema','graceful','forced','unused','ownerPipeLoss','ownerCrash','all_passed','installAuthority','gameLaunched'}:raise ValueError('Wrong owner result shape')
    if type(result['schema']) is not int or result['schema']!=44 or any(result[name] is not True for name in ('graceful','forced','unused','ownerPipeLoss','ownerCrash','all_passed')) or result['installAuthority'] is not False or result['gameLaunched'] is not False:raise ValueError('Native owner demo failed')
def owner_status(project,output):
    marker=output/'owner-attempt.json';report=output/'owner-demo.json'
    if not marker.exists() and not report.exists():return None
    try:
        attempt=read_json(marker)
        if set(attempt)!={'schema','state','attempt'} or type(attempt['schema']) is not int or attempt['schema']!=44 or not isinstance(attempt['attempt'],str) or str(uuid.UUID(attempt['attempt']))!=attempt['attempt'] or attempt['state'] not in ('RUNNING','FAILED','COMPLETED'):raise ValueError('Invalid owner attempt')
        if attempt['state']!='COMPLETED':return {'state':attempt['state'],'all_passed':False,'installAuthority':False,'historical':True}
        data=read_json(report)
        if set(data)!={'schema','attempt','source_sha256','java_sha256','binary_sha256','result','installAuthority'} or type(data['schema']) is not int or data['schema']!=44 or data['attempt']!=attempt['attempt'] or data['installAuthority'] is not False:raise ValueError('Invalid owner report binding')
        validate_owner_result(data['result'])
        if not all(hash_value(data[k]) for k in ('source_sha256','java_sha256','binary_sha256')):raise ValueError('Invalid owner input identity')
        current=(data['source_sha256']==source_fingerprint(project) and data['java_sha256']==owner_java_hash(project) and data['binary_sha256']==digest(output/'genesis-job43.exe'))
        return {'state':'COMPLETED','all_passed':True,'matches_current_inputs':current,'historical':True,'installAuthority':False,'liveContainmentProof':False}
    except (OSError,ValueError,KeyError,TypeError) as error:return {'state':'UNKNOWN','error':str(error),'all_passed':False,'installAuthority':False}
def status(project,output):
    result=legacy_status(project,output);result['owner_diagnostic']=owner_status(project,output);result['java_diagnostic']=java_status(project,output);return result

def build_native(project,output,compiler):
    if sys.platform!='win32':raise ValueError('Native build requires Windows; this host cannot certify Windows containment')
    fingerprint=source_fingerprint(project)
    tool=shutil.which('cl.exe' if compiler=='msvc' else 'gcc.exe')
    if not tool:raise ValueError('Compiler unavailable: use x64 Native Tools Command Prompt for VS, or explicit --compiler mingw with 64-bit MinGW-w64')
    output.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='compile-',dir=output) as temp:
        temp=Path(temp);exe=temp/'genesis-job43.exe';src=project/'native/windows-job'
        sources=[str(src/n) for n in ('record.c','job.c','main.c','owner_protocol.c','owner.c','capsule.c','java_owner.c')]
        if compiler=='msvc':command=[tool,'/nologo','/W4','/WX','/std:c11','/O2','/DUNICODE','/D_UNICODE','/D_CRT_SECURE_NO_WARNINGS','/D_WIN32_WINNT=0x0A00',*sources,'/Fe:'+str(exe),'/link','bcrypt.lib']
        else:command=[tool,'-std=c11','-Wall','-Wextra','-Werror','-O2','-municode','-DUNICODE','-D_UNICODE','-D_WIN32_WINNT=0x0A00',*sources,'-o',str(exe),'-lbcrypt']
        subprocess.run(command,cwd=temp,check=True,timeout=120)
        if source_fingerprint(project)!=fingerprint:raise ValueError('Native sources changed during compilation; output not installed')
        probe=subprocess.run([str(exe),'--probe'],check=True,capture_output=True,timeout=10)
        if len(probe.stdout)>MAX_REPORT:raise ValueError('Oversized native probe')
        data=decode_json(probe.stdout.decode('utf-8'))
        if data!={'schema':43,'backend':'windows_job','mode':'diagnostic_fixtures_only','install_authority':False}:raise ValueError('Native probe mismatch')
        binary=digest(exe);os.replace(exe,output/'genesis-job43.exe')
        atomic_json(output/'build.json',{'schema':43,'compiler':compiler,'source_sha256':fingerprint,'binary_sha256':binary,'host':platform.platform(),'install_authority':False})
    return {'built':str(output/'genesis-job43.exe'),'native_runtime_verified':False,'install_authority':False}

def selftest(project,output):
    if sys.platform!='win32':raise ValueError('Native selftest requires Windows; simulated results are not accepted')
    exe=output/'genesis-job43.exe';metadata=validate_build(read_json(output/'build.json'));fingerprint=source_fingerprint(project)
    if exe.is_symlink() or not exe.is_file() or metadata.get('schema')!=43 or metadata.get('source_sha256')!=fingerprint or metadata.get('binary_sha256')!=digest(exe):raise ValueError('Build missing/stale: build the current source first')
    binary=digest(exe)
    # A job enclosing this helper is not installed by Python. Native scenarios own
    # their fixture jobs. On timeout Python terminates the helper, whose job handles
    # close, killing its attached fixtures. No unrelated PID scanning/kill.
    attempt={'schema':43,'id':str(uuid.uuid4()),'state':'RUNNING'}
    atomic_json(output/'attempt.json',attempt)
    try:
        completed=subprocess.run([str(exe),'--selftest'],capture_output=True,timeout=180)
        if len(completed.stdout)>MAX_REPORT or len(completed.stderr)>MAX_REPORT:raise ValueError('Oversized selftest output')
        native=decode_json(completed.stdout.decode('utf-8'));passed=validate_native(native)
        if completed.returncode!=(0 if passed else 1):raise ValueError('Helper exit code disagrees with native result')
        if fingerprint!=source_fingerprint(project) or binary!=digest(exe):raise ValueError('Source/binary changed during selftest; result not committed')
        atomic_json(output/'selftest.json',{'schema':43,'attempt':attempt['id'],'host':platform.platform(),'completed_unix_ms':int(time.time()*1000),'source_sha256':fingerprint,'binary_sha256':binary,'native':native})
        attempt['state']='COMPLETED';atomic_json(output/'attempt.json',attempt)
        result=status(project,output)
        if result.get('report_error') or not (result.get('historical_selftest') or {}).get('matches_current_source_and_binary',False):raise ValueError('Committed selftest cannot be revalidated')
    except BaseException:
        attempt['state']='FAILED'
        atomic_json(output/'attempt.json',attempt)
        raise
    return result,passed

def owner_demo(project,output):
    if sys.platform!='win32':raise ValueError('Native owner demo requires Windows')
    exe=output/'genesis-job43.exe';metadata=validate_build(read_json(output/'build.json'))
    fingerprint=source_fingerprint(project)
    if metadata['source_sha256']!=fingerprint or metadata['binary_sha256']!=digest(exe):raise ValueError('Native helper build missing/stale; rebuild first')
    java=shutil.which('java.exe')
    if not java:raise ValueError('JDK17+ required on PATH for diagnostic-only Java bridge')
    sources=owner_java_sources(project)
    java_hash=owner_java_hash(project)
    # Compile standalone bridge only, no Gradle/Minecraft/world/config access.
    # Pending marker suppresses an older success if Java compile/demo fails.
    marker={'schema':44,'state':'RUNNING','attempt':str(uuid.uuid4())}
    atomic_json(output/'owner-attempt.json',marker)
    try:
        with tempfile.TemporaryDirectory(prefix='owner-java-',dir=output) as temporary:
            subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',temporary,*map(str,sources)],check=True,timeout=60)
            completed=subprocess.run([java,'-cp',temporary,'com.fixmer.genesis.technology.server.process.ServerNativeOwnerCli',str(exe)],capture_output=True,check=True,timeout=120)
            if len(completed.stdout)>MAX_REPORT:raise ValueError('Oversized owner demo report')
            result=decode_json(completed.stdout.decode('utf-8'))
            validate_owner_result(result)
            if source_fingerprint(project)!=fingerprint or digest(exe)!=metadata['binary_sha256'] or java_hash!=owner_java_hash(project):raise ValueError('Native owner demo inputs changed')
            atomic_json(output/'owner-demo.json',{'schema':44,'attempt':marker['attempt'],'source_sha256':fingerprint,'java_sha256':java_hash,'binary_sha256':metadata['binary_sha256'],'result':result,'installAuthority':False})
        marker['state']='COMPLETED';atomic_json(output/'owner-attempt.json',marker)
    except BaseException:
        marker['state']='FAILED';atomic_json(output/'owner-attempt.json',marker);raise
    return result

JAVA46_NAMES=('ServerFiles.java','ServerJson.java','ServerProcessConfig.java','ServerNativeOwnerProtocol.java','ServerNativeLaunchCapsule.java','ServerNativeJavaProtocol.java','ServerNativeJavaSession.java','ServerNativeJavaProbe.java','ServerNativeJavaCli.java')
JAVA46_CASES=('graceful','forced','orphanGraceful','orphanForce','flood','rootExitDescendant')
def java46_sources(project):return [project/'technologies/core/src/main/java/com/fixmer/genesis/technology/server/process'/n for n in JAVA46_NAMES]
def java46_hash(project):
    sha=hashlib.sha256()
    for file in java46_sources(project):
        if file.is_symlink() or not file.is_file() or file.stat().st_size>256*1024:raise ValueError('Invalid Java46 source')
        sha.update(file.name.encode()+b'\0'+file.read_bytes().replace(b'\r\n',b'\n')+b'\0')
    return sha.hexdigest()
def validate_java46(data):
    if not isinstance(data,dict) or set(data)!={'schema',*JAVA46_CASES,'all_passed','gameLaunched','installAuthority'} or type(data['schema']) is not int or data['schema']!=46 or any(data[k] is not True for k in (*JAVA46_CASES,'all_passed')) or data['gameLaunched'] is not False or data['installAuthority'] is not False:raise ValueError('Invalid Java46 diagnostic result')
def java_status(project,output):
    if not (output/'java-attempt.json').exists() and not (output/'java-demo.json').exists():return None
    try:
        marker=read_json(output/'java-attempt.json')
        if set(marker)!={'schema','state','attempt'} or type(marker['schema']) is not int or marker['schema']!=46 or not isinstance(marker['attempt'],str) or str(uuid.UUID(marker['attempt']))!=marker['attempt'] or marker['state'] not in ('RUNNING','FAILED','COMPLETED'):raise ValueError('Invalid Java46 attempt')
        if marker['state']!='COMPLETED':return {'state':marker['state'],'all_passed':False,'installAuthority':False,'historical':True}
        data=read_json(output/'java-demo.json')
        if set(data)!={'schema','attempt','source_sha256','java_sha256','binary_sha256','result','installAuthority'} or type(data['schema']) is not int or data['schema']!=46 or data['attempt']!=marker['attempt'] or data['installAuthority'] is not False or not all(hash_value(data[k]) for k in ('source_sha256','java_sha256','binary_sha256')):raise ValueError('Invalid Java46 history')
        validate_java46(data['result'])
        current=data['source_sha256']==source_fingerprint(project) and data['java_sha256']==java46_hash(project) and data['binary_sha256']==digest(output/'genesis-job43.exe')
        return {'state':'COMPLETED','all_passed':True,'matches_current_inputs':current,'historical':True,'installAuthority':False,'liveContainmentProof':False}
    except (OSError,ValueError,KeyError,TypeError) as bad:return {'state':'UNKNOWN','all_passed':False,'error':str(bad),'installAuthority':False}
def java_demo(project,output):
    if sys.platform!='win32':raise ValueError('Native Java demo requires Windows')
    exe=output/'genesis-job43.exe';build=validate_build(read_json(output/'build.json'));fingerprint=source_fingerprint(project);java_hash=java46_hash(project)
    if build['source_sha256']!=fingerprint or build['binary_sha256']!=digest(exe):raise ValueError('Native helper stale; rebuild first')
    java=shutil.which('java.exe')
    if not java:raise ValueError('JDK17+ required on PATH')
    marker={'schema':46,'state':'RUNNING','attempt':str(uuid.uuid4())};atomic_json(output/'java-attempt.json',marker)
    try:
        with tempfile.TemporaryDirectory(prefix='java46-classes-',dir=output) as classes:
            subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',classes,*map(str,java46_sources(project))],check=True,timeout=60)
            newroot=output/('java-probes-'+marker['attempt'])
            completed=subprocess.run([java,'-cp',classes,'com.fixmer.genesis.technology.server.process.ServerNativeJavaCli',str(exe),str(newroot)],check=True,capture_output=True,timeout=150)
            if len(completed.stdout)>MAX_REPORT or len(completed.stderr)>MAX_REPORT:raise ValueError('Oversized Java46 output')
            result=decode_json(completed.stdout.decode('utf-8'));validate_java46(result)
            if fingerprint!=source_fingerprint(project) or java_hash!=java46_hash(project) or build['binary_sha256']!=digest(exe):raise ValueError('Java46 diagnostic inputs changed')
            atomic_json(output/'java-demo.json',{'schema':46,'attempt':marker['attempt'],'source_sha256':fingerprint,'java_sha256':java_hash,'binary_sha256':build['binary_sha256'],'result':result,'installAuthority':False})
        marker['state']='COMPLETED';atomic_json(output/'java-attempt.json',marker)
    except BaseException:
        marker['state']='FAILED';atomic_json(output/'java-attempt.json',marker);raise
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('project',type=Path,nargs='?',default=Path('.'));parser.add_argument('action',choices=('status','build','selftest','owner-demo','java-demo'),nargs='?',default='status');parser.add_argument('--compiler',choices=('msvc','mingw'),default='msvc');args=parser.parse_args()
    project,output=paths(args.project)
    if args.action!='status' and sys.platform!='win32':raise ValueError('Native build/selftest requires Windows; no files changed')
    if args.action=='status':result=status(project,output);passed=True
    elif args.action=='build':
        with owner_lock(output):result=build_native(project,output,args.compiler)
        passed=True
    elif args.action=='selftest':
        with owner_lock(output):result,passed=selftest(project,output)
    else:
        with owner_lock(output):result=java_demo(project,output) if args.action=='java-demo' else owner_demo(project,output)
        passed=True
    print(json.dumps(result,ensure_ascii=False,indent=2));return 0 if passed else 1
if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError,subprocess.SubprocessError) as error:raise SystemExit('Native diagnostics failed: '+str(error))
