"""Real HTTPS, source JVM/auth handoff, Worker.prepare/build, child JVM and receipts.
Minecraft user/window/world callbacks are fixture inputs. Windows runs actual capture, retained HANDLE and Job.
"""
from pathlib import Path
from contextlib import ExitStack
import os,sys,subprocess,time,uuid,json,shutil,zipfile
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.repeat71 import RepeatingWorker71 as Worker
from first58.control73 import decode
from first58.capture import normalize_client_argv
from first58.admission82 import Combined82
from first58.install70 import files
from first58.common import inventory,json_write
from test_remote_install70 import mod

class PortableFixtureJob:
 def __init__(self,argv,cwd,env,log):
  self.out=Path(log).open('xb');self.p=subprocess.Popen(argv,cwd=cwd,env=env,stdout=self.out,stderr=subprocess.STDOUT)
 def status(self):
  code=self.p.poll();return dict(rootExited=code is not None,exitCode=code,logFailed=False,active=int(code is None))
 def empty(self):return self.p.poll()==0
 def close(self):
  if self.p.poll() is None:
   self.p.terminate()
   try:self.p.wait(timeout=5)
   except subprocess.TimeoutExpired:self.p.kill();self.p.wait(timeout=5)
  self.out.close()

class PortableSourceLease:
 def __init__(self,process):self.process=process
 def closed(self):return self.process.poll() is not None
 def normal(self):return self.process.poll()==0
 def close(self):pass

class PublicFixture:
 def check(self):return {'phase':'READY'}

def check_process_chain(root,project,runtime,java,cp,props,cache,dev=False,delay=0):
 root=Path(root);label='dev' if dev else 'installed'
 (root/'stop165').unlink(missing_ok=True)
 (root/'control73').rename(root/('control73-before165-'+label));(root/'control73.cfg').unlink()
 subprocess.run([str(java),'-m','jdk.compiler/com.sun.tools.javac.Main','-cp',cp,'-d',str(root/'harness'),str(project/'verification/server59/UpdateChainProcess165.java')],check=True,capture_output=True,timeout=30)
 subprocess.run([str(java),'-cp',cp,'UpdateChainProcess165','setup',str(root)],check=True,capture_output=True,timeout=30)
 pump=subprocess.Popen([str(java),'-cp',cp,'UpdateChainProcess165','pump',str(root)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 source=root/('source165-'+label);(source/'mods').mkdir(parents=True)
 if not dev:mod(source/'mods/mared.jar','mared',adapter=True)
 mod(source/'mods/old.jar','example','old')
 (source/'options.txt').write_text('saved user settings');(source/'saves/world').mkdir(parents=True);(source/'saves/world/level.dat').write_bytes(b'original world')
 before={str(p.relative_to(source)):p.read_bytes() for p in source.rglob('*') if p.is_file()}
 candidate=next((Path(cache)/'downloads').iterdir());folder=root/('worker165-'+label);folder.mkdir();(folder/'runtime').mkdir();shutil.copyfile(runtime/'runtime/core.jar',folder/'runtime/core.jar')
 fixturejar=folder/'fixture-runtime.jar'
 with zipfile.ZipFile(fixturejar,'w') as out:
  seen=set()
  for directory in map(Path,cp.split(os.pathsep)):
   for file in directory.rglob('*.class'):
    name=file.relative_to(directory).as_posix()
    if name not in seen:out.write(file,name);seen.add(name)
 devmods=''
 if dev:
  devroot=root/'compiled165';devroot.mkdir()
  for name in ('FirstLaunchClient58','RemoteClient70'):
   path=devroot/('com/fixmer/mared/technology/server/firstlaunch/'+name+'.class');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'explicit loaded-root fixture, not Minecraft bytecode')
  devmods='MaRed%%'+str(devroot)
 cfg=decode((root/'control73.cfg').read_bytes());identifier=str(uuid.uuid4());source_account=str(uuid.uuid4())
 argv=[str(java),'-Dfixture.delay165='+str(delay),'--class-path='+str(fixturejar),'UpdateChainProcess165','source','--gameDir='+str(source),'--uuid',source_account,'--username','Fixture165','--accessToken','0','--userType','legacy','--fml.neoForgeVersion','21.1.264','--fixture-owner',str(folder),'--fixture-run',identifier]
 if dev:argv+=['--launchTarget','forgeclientdev']
 source_log=(folder/'source165.log').open('xb');original=subprocess.Popen(argv,cwd=source,stdout=source_log,stderr=subprocess.STDOUT);w=None
 try:
  deadline=time.monotonic()+15
  while not (folder/'source-ready165').exists():
   if original.poll() is not None or time.monotonic()>deadline:raise AssertionError('source JVM fixture failed')
   time.sleep(.05)
  birth=int((folder/'source-ready165').read_text())
  r=dict(schema=70,auth89=True,request=identifier,pid=original.pid,birth=birth,profile=str(source),account=source_account,neoforge='21.1.264',cache=str(cache),candidate=str(candidate),authMode=cfg['authMode'])
  (folder/'request70.json').write_text(json.dumps(r));shutil.copyfile(root/'control73.cfg',folder/'control73.cfg')
  session=dict(schema=62,pid=original.pid,profile=str(source),account=source_account,name='Fixture165',accessToken='0',userType='legacy',issuedAt=int(time.time()*1000),devMods=devmods,cwd=str(source))
  (folder/'client-session61.json').write_text(json.dumps(session))
  w=Worker(folder)
  if os.name!='nt':w.lease_factory=lambda pid,birth:PortableSourceLease(original);w.job_factory=PortableFixtureJob
  with ExitStack() as stack:
   stack.enter_context(patch.dict(os.environ,{'JAVA_TOOL_OPTIONS':' '.join('"'+p+'"' for p in props)}))
   if os.name!='nt':
    stack.enter_context(patch('first58.remote70.read_client',side_effect=lambda pid,hint,cwd:normalize_client_argv(argv,hint)))
    stack.enter_context(patch('first58.remote70.native_probe'))
   w.prepare();assert w.phase=='READY' and w.gate73.committed and original.poll() is None
   gate=w.gate73;w.gate73=Combined82(PublicFixture(),gate)
   def no_independent_proof():raise ValueError('fixture legacy server')
   w.gate73.proof=no_independent_proof
   transitions=[];actual_state=w.state
   def state(phase):transitions.append(phase);actual_state(phase)
   w.state=state
   def commit(action,seconds):
    assert action=='COMMIT' and w.phase=='ARMED'
    json_write(folder/'command70.json',dict(schema=70,request=identifier,action='COMMIT'))
    (folder/'source-exit165').write_text('cooperative exit')
    assert original.wait(timeout=10)==0
    Worker.wait_action(w,action,seconds)
   w.wait_action=commit
   def no_questions(message):raise AssertionError('Unexpected question')
   w._activate(lambda message:None,lambda:False,no_questions)
   assert transitions[-2:]==['ACTIVE','COMPLETE'],transitions
   profile=w.root/'generations/client-1'
   assert (profile/'fixture-joins165').read_text()=='3'
   assert files(profile/'mods')==w.approved['after'] and inventory(profile)==w.config['inventory']
   assert not (profile/'saves').exists() and (profile/'options.txt').read_text()=='saved user settings'
   assert {str(p.relative_to(source)):p.read_bytes() for p in source.rglob('*') if p.is_file()}==before
   assert 'JOINED' in gate.status()['clients'] and w.job.empty() and original.poll()==0
   assert w.auth89['stage']=='OFFLINE'
   assert bool(w.spec.get('devSnapshot'))==dev
   print('Update165 '+label+': actual source JVM, fresh auth before PREPARED, signed HTTPS/COMMIT, Worker.prepare/final_check/build/start/join/watch, three JOIN attempts, original profile unchanged, normal exits: PASS')
   print('Modeled boundaries: Minecraft user/window/world callbacks and public readiness fallback. Native runner: '+('actual Windows capture/HANDLE/Job/probe' if os.name=='nt' else 'portable subprocess; Windows capture and Job probe not executed'))
 finally:
  if w:
   if w.job:w.job.close()
   if w.lease:w.lease.close()
   if getattr(w,'apply_lease76',None):w.apply_lease76.close()
  (folder/'source-exit165').write_text('cleanup')
  try:original.wait(timeout=5)
  except subprocess.TimeoutExpired:original.terminate();original.wait(timeout=5)
  source_log.close()
  if original.returncode:print('SOURCE FIXTURE ERROR', (folder/'source165.log').read_text(errors='replace')[-2000:])
  (root/'stop165').write_text('stop')
  try:out,err=pump.communicate(timeout=5)
  except subprocess.TimeoutExpired:pump.kill();out,err=pump.communicate(timeout=5)
  if pump.returncode!=0:raise AssertionError('fixture control pump failed: '+err.decode(errors='replace')[:1000])

def main():
 import argparse,tempfile,ssl,urllib.request,socket
 parser=argparse.ArgumentParser();parser.add_argument('--project',type=Path,required=True);parser.add_argument('--java',type=Path,required=True);parser.add_argument('--classes',type=Path,required=True);parser.add_argument('--delay-ms',type=int,default=0)
 args=parser.parse_args();project=args.project.resolve();java=args.java.resolve();classes=args.classes.resolve()
 def run(argv):return subprocess.run(list(map(str,argv)),check=True,capture_output=True,text=True,timeout=90)
 with tempfile.TemporaryDirectory(prefix='update165-') as temporary:
  root=Path(temporary);harness=root/'harness';harness.mkdir()
  run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-cp',classes,'-d',harness,project/'verification/server59/RemoteChecks69.java'])
  cp=str(harness)+os.pathsep+str(classes)
  cert=root/'server.crt';key=root/'server.key';trust=root/'trust.p12'
  run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',key,'-out',cert,'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'])
  run([java.with_name('keytool.exe' if os.name=='nt' else 'keytool'),'-importcert','-noprompt','-alias','fixture','-file',cert,'-keystore',trust,'-storetype','PKCS12','-storepass','fixture-only165'])
  with socket.socket() as probe:probe.bind(('127.0.0.1',0));port=probe.getsockname()[1]
  endpoint=f'https://localhost:{port}/genesis/';props=['-Djavax.net.ssl.trustStore='+str(trust),'-Djavax.net.ssl.trustStorePassword=fixture-only165']
  run([java,'-cp',cp,'com.fixmer.genesis.technology.server.remote.RemoteChecks69','fixture',root,endpoint])
  runtime=root/'runtime';(runtime/'runtime').mkdir(parents=True)
  with zipfile.ZipFile(runtime/'runtime/core.jar','w',zipfile.ZIP_DEFLATED) as archive:
   for file in classes.rglob('*.class'):archive.write(file,file.relative_to(classes))
  (root/'control73').mkdir();(root/'control73.cfg').write_bytes(b'')
  host=subprocess.Popen([sys.executable,str(project/'tools/remote_update.py'),str(runtime),'--java',str(java),'serve',str(root/'public2'),'--cert',str(cert),'--key',str(key),'--port',str(port),'--control-root',str(root/'control73')],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
  try:
   tls=ssl.create_default_context(cafile=str(cert));deadline=time.monotonic()+15
   while True:
    if host.poll() is not None:raise AssertionError('fixture HTTPS startup failed')
    try:
     with urllib.request.urlopen(endpoint+'offer.bin',context=tls,timeout=1) as response:assert response.status==200
     break
    except OSError:
     if time.monotonic()>deadline:raise
     time.sleep(.1)
   cache=root/'cache';run([java,*props,'-cp',cp,'com.fixmer.genesis.technology.server.remote.RemoteChecks69','network',cache,endpoint])
   for dev in (False,True):check_process_chain(root,project,runtime,java,cp,props,cache,dev=dev,delay=args.delay_ms)
  finally:
   host.terminate()
   try:host.communicate(timeout=5)
   except subprocess.TimeoutExpired:host.kill();host.communicate(timeout=5)
if __name__=='__main__':main()
