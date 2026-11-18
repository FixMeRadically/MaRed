#!/usr/bin/env python3
"""Collect local server restart observations; optional exercise requests exactly one confirmed restart."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,tempfile,time,uuid,zipfile
from server_supervisor import java_home
ADAPTER='com/fixmer/mared/technology/server/GenesisServerLifecycleHooks.class'
PROOFS=('nativeContainmentVerified','worldSaveVerified','actualMinecraftVerified','runtimeCompatibilityVerified','authorizationVerified','installAuthority','productionLaunchAllowed')

def bounded(path,maximum=512*1024):
    if not path.is_file() or path.is_symlink():raise ValueError('Regular unlinked file required')
    with path.open('rb') as stream:data=stream.read(maximum+1)
    if len(data)>maximum:raise ValueError('Evidence input size limit')
    return data

def adapter_hash(project,jar=None):
    if jar is None:return hashlib.sha256(bounded(project/'build/classes/java/main'/ADAPTER)).hexdigest()
    if not jar.is_file() or jar.is_symlink() or jar.stat().st_size>512*1024*1024:raise ValueError('Invalid adapter JAR')
    with zipfile.ZipFile(jar) as z:
        entries=[e for e in z.infolist() if e.filename==ADAPTER]
        if len(entries)!=1 or entries[0].file_size>512*1024 or entries[0].flag_bits&1:raise ValueError('Expected one bounded adapter class in the selected JAR')
        with z.open(entries[0]) as stream:data=stream.read(512*1024+1)
        if len(data)>512*1024:raise ValueError('Adapter class size limit')
    return hashlib.sha256(data).hexdigest()

def atomic(path,value):
    fd,name=tempfile.mkstemp(prefix='.evidence54-',suffix='.tmp',dir=path.parent)
    try:
        with os.fdopen(fd,'w',encoding='utf-8') as out:json.dump(value,out,ensure_ascii=False,sort_keys=True);out.write('\n');out.flush();os.fsync(out.fileno())
        os.replace(name,path)
    finally:Path(name).unlink(missing_ok=True)

def java_json(java,classpath,main,*args,timeout=15):
    # Known entrypoints only, no shell or user-provided Java main class.
    if main not in ('ServerEvidenceCli','ServerSupervisorCli'):raise ValueError('Invalid fixed evidence entrypoint')
    with tempfile.TemporaryFile() as out,tempfile.TemporaryFile() as err:
        result=subprocess.run([str(java),'-cp',classpath,'com.fixmer.genesis.technology.server.process.'+main,*map(str,args)],stdout=out,stderr=err,timeout=timeout)
        out.seek(0);data=out.read(128*1024+1);err.seek(0);error=err.read(128*1024+1)
        if len(data)>128*1024 or len(error)>128*1024:raise ValueError('Evidence command output limit')
        if result.returncode:raise ValueError('Evidence command refused: '+error.decode('utf-8',errors='replace')[-2048:])
    value=json.loads(data)
    if not isinstance(value,dict):raise ValueError('Evidence response must be an object')
    return value

def valid_uuid(value):
    try:return str(uuid.UUID(value))==value and uuid.UUID(value).int!=0
    except (ValueError,TypeError,AttributeError):return False

def exercise(call,config,adapter,output,timeout,clock=time.monotonic,pause=time.sleep):
    # begin performs live progress preflight and creates the private directory, without a process command.
    before=call('ServerEvidenceCli','begin',config,adapter,output)
    attempt={'schema':54,'scope':'SINGLE_OPERATOR_RESTART_EXERCISE_NO_INSTALL','stage':'PRECHECKED','sourceRun':before['run'],'owner':before['owner'],'adapterSha256':adapter,'processCommandMayHaveBeenSent':False}
    path=output/'attempt.json';atomic(path,attempt)
    try:
        attempt.update(stage='COMMAND_INTENT',processCommandMayHaveBeenSent=True);atomic(path,attempt)
        reply=call('ServerSupervisorCli','prepare-restart-for',config,before['run'],before['owner'],'--confirm')
        if reply.get('accepted') is not True:raise ValueError('Prepared restart was not accepted; inspect supervisor before another attempt')
        attempt['stage']='WAITING_FOR_COMPLETION';atomic(path,attempt);deadline=clock()+timeout
        request=None
        while clock()<deadline:
            state=call('ServerEvidenceCli','inspect',config)
            if state.get('preparedRestartSource')!=before['run']:raise ValueError('Another restart source was selected; no further process commands sent')
            selected=state.get('request')
            if not valid_uuid(selected) or request is not None and selected!=request:raise ValueError('Restart request changed')
            request=selected;attempt['request']=request
            phase=state.get('preparedRestartState')
            if phase in ('CANCELLED','FAILED'):raise ValueError('Restart ended as '+phase+'; it was not retried')
            if phase=='COMPLETE':
                report=call('ServerEvidenceCli','capture',config,adapter,request,output/'bundle')
                if report.get('localLifecycleEvidenceComplete') is not True or any(report.get(k) is not False for k in PROOFS):raise ValueError('Invalid evidence safety flags')
                attempt['stage']='COMPLETED';atomic(path,attempt);return report
            atomic(path,attempt);pause(.25)
        raise TimeoutError('Restart evidence deadline exceeded; the supervisor operation may still continue')
    except BaseException:
        attempt['stage']='OUTCOME_REVIEW_REQUIRED'
        try:atomic(path,attempt)
        except OSError:pass
        raise

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('project',type=Path);p.add_argument('action',choices=('inspect','probe','capture','status','exercise'));p.add_argument('target',type=Path);p.add_argument('--output',type=Path);p.add_argument('--request');p.add_argument('--confirm',action='store_true');p.add_argument('--timeout',type=int);p.add_argument('--java-home');p.add_argument('--adapter-jar',type=Path);a=p.parse_args()
    if a.action in ('capture','exercise') and a.output is None:p.error('--output NEW_DIRECTORY is required')
    if a.action=='capture' and not valid_uuid(a.request):p.error('capture requires --request UUID from inspect')
    if a.action=='exercise' and not a.confirm:p.error('exercise stops/restarts the configured running server; requires --confirm')
    if a.action!='exercise' and a.confirm:p.error('--confirm is only for exercise')
    if a.action!='capture' and a.request:p.error('--request is only for capture')
    if a.action not in ('capture','exercise') and a.output:p.error('--output is only for capture/exercise')
    if a.action!='exercise' and a.timeout is not None:p.error('--timeout is only for exercise')
    if a.action in ('inspect','status') and a.adapter_jar:p.error('--adapter-jar is only for probe/capture/exercise')
    if a.timeout is None:a.timeout=180
    if not 10<=a.timeout<=600:p.error('--timeout must be 10..600 seconds')
    project=a.project.resolve();classes=project/'technologies/core/build/classes/java/main';resources=project/'technologies/core/src/main/resources'
    if not (classes/'com/fixmer/genesis/technology/server/process/ServerEvidenceCli.class').is_file():raise ValueError('Build genesis-core first')
    _,java=java_home(a.java_home);classpath=os.pathsep.join((str(classes),str(resources)))
    def call(main,*args):return java_json(java,classpath,main,*args)
    target=a.target.absolute()
    if a.action in ('inspect','status'):report=call('ServerEvidenceCli',a.action,target)
    else:
        adapter=adapter_hash(project,a.adapter_jar)
        if a.action=='probe':report=call('ServerEvidenceCli','probe',target,adapter)
        elif a.action=='capture':report=call('ServerEvidenceCli','capture',target,adapter,a.request,a.output.absolute())
        else:report=exercise(call,target,adapter,a.output.absolute(),a.timeout)
    print(json.dumps(report,ensure_ascii=False,indent=2));return 0
if __name__=='__main__':
    try:raise SystemExit(main())
    except KeyboardInterrupt:raise SystemExit('Observation interrupted. A requested restart may continue. Inspect supervisor; do not blindly repeat exercise.')
    except (OSError,ValueError,TimeoutError,subprocess.SubprocessError) as error:raise SystemExit('Lifecycle evidence: '+str(error)+'\nNo automatic retry, force-stop or installation was performed.')
