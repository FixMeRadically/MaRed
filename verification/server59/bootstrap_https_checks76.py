"""Actual HTTPS pre-login download and same-capability recovery. Server READY/Jobs remain explicit inputs."""
import json,os,socket,ssl,subprocess,sys,threading,time,urllib.request,urllib.error,uuid,shutil
from pathlib import Path
from unittest.mock import Mock

def check_bootstrap(root,project,runtime,java,cp,props,cert,tls_key):
 sys.path.insert(0,str(project/'tools'))
 from first58 import dedicated74 as old
 from first58.bridge67 import write,read
 from first58.common import json_write,sha,inventory
 from first58.runtime import core_classpath
 from first58.bootstrap76 import Gate76
 def run(args):return subprocess.run(list(map(str,args)),capture_output=True,text=True,timeout=120,check=True)
 sock=socket.socket();sock.bind(('127.0.0.1',0));port=sock.getsockname()[1];sock.close();endpoint=f'https://localhost:{port}/genesis/';identity=root/'identity76';public=root/'public76';owner=root/'owner76';owner.mkdir();(owner/'releases').mkdir();(owner/'publisher73').mkdir()
 cli=[java,'-cp',str(core_classpath(runtime)),'com.fixmer.genesis.technology.server.remote.RemoteUpdateCli69']
 run([*cli,'init',identity,'localhost:25565',endpoint]);run([*cli,'publish',identity,root/'packages',public,'3600'])
 for name in ('public.der','private.der'):(owner/'publisher73'/name).write_bytes((identity/name).read_bytes())
 source=owner/'generations/server-0';shutil.copytree(root/'owner75/generations/server-2',source);profile=owner/'generations/server-1';shutil.copytree(source,profile)
 s=dict(schema=74,run=str(uuid.uuid4()),source=str(root/'original76'),java=str(java),argv=[str(java)],minecraft='1.21.1',neoforge='21.1.264',auth='false',bind='127.0.0.1',port='25565',level='world',initialInventory=inventory(source));key=b'v'*32;(owner/'owner74.key').write_bytes(key);json_write(owner/'settings.json',s);write(owner/'settings74',key,{'schema':'74','sha':sha(owner/'settings.json',65536)})
 for version in (75,76):write(owner/f'owner{version}',key,{'schema':str(version),'run':s['run']})
 old.session(owner,s,key,0,inventory(source));old.session(owner,s,key,1,inventory(profile));session=read(owner/'server-1.session',key);receipt={k:session[k] for k in ('schema','run','generation','inventory')};receipt.update(sequence='0',request=str(uuid.UUID(int=0)),phase='READY',round='none',at='1');write(owner/'server-1.status',key,receipt)
 # Pair a real signed CLIENT export; no Minecraft process was used to create the READY input.
 old.core(runtime,java,'author',owner/'publisher73',public,root/'server-mods75-1',owner/'releases/r1');info=old.core(runtime,java,'verify',owner/'releases/r1',owner/'publisher73/public.der')
 def heartbeat(phase,serial):write(owner/'bootstrap76.local',key,dict(schema='76',run=s['run'],at=str(int(time.time()*1000)),serial=str(serial),phase=phase,label='r1',generation='1',release=info['release']))
 run([java,'-cp',cp,'com.fixmer.mared.BootstrapChecks76','round',owner,owner/'releases/r1/public','prepare'])
 heartbeat('TRANSITION',1)
 diagnostic=run([java,'-cp',cp,'com.fixmer.mared.BootstrapChecks76','publisher',owner]);assert diagnostic.stdout.strip()=='PUBLISHED'
 signer=subprocess.Popen([str(java),'-cp',str(core_classpath(runtime)),'com.fixmer.genesis.technology.server.remote.BootstrapPublisher76','serve',str(owner)],stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 process=subprocess.Popen([sys.executable,str(project/'tools/remote_update.py'),str(runtime),'--java',str(java),'serve',str(public),'--cert',str(cert),'--key',str(tls_key),'--port',str(port),'--owner75',str(owner)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 stop=threading.Event();phase=['TRANSITION'];serial=[0];errors=[]
 def pulse():
  try:
   while not stop.is_set():
    serial[0]+=1;heartbeat(phase[0],serial[0]);old.core(runtime,java,'pulse',owner);stop.wait(.8)
  except Exception as e:errors.append(type(e).__name__)
 thread=threading.Thread(target=pulse,daemon=True);thread.start();checks=0
 try:
  context=ssl.create_default_context(cafile=str(cert))
  def get(name):
   with urllib.request.urlopen(endpoint+name,context=context,timeout=3) as response:return response.read()
  for _ in range(100):
   if process.poll() is not None:raise AssertionError(process.communicate())
   if signer.poll() is not None:raise AssertionError('Signer stopped')
   try:data=get('bootstrap76/state');break
   except (urllib.error.URLError,TimeoutError):time.sleep(.1)
  else:
   from first58.https_bootstrap76 import Routes76
   from first58.publication75 import Publication75
   def verify_public(p):return json.loads(run([*cli,'verify-public',p]).stdout)
   route=Routes76(owner,'/genesis/',Publication75(owner,public,verify_public),verify_public);diagnostic=route.local();raise AssertionError(('Bootstrap76 did not start',diagnostic,(owner/'bootstrap76.state').exists()))
  result=run([java,*props,'-cp',cp,'com.fixmer.mared.BootstrapChecks76',owner,'localhost:25565',endpoint]);print(result.stdout.strip());checks+=1
  state=old.core(runtime,java,'inspect',owner);assert state['total']==2 and state['installed']==1 and not state['admission'];checks+=1
  for name in ['bootstrap76/'+info['release']+'/private.der','bootstrap76/'+str(uuid.uuid4())+'/offer.bin','bootstrap76/state?private=true','bootstrap76/../../owner74.key']:
   try:get(name);raise AssertionError('Private/unknown route was served')
   except urllib.error.HTTPError as error:assert error.code==404;checks+=1
  # Finish the other existing member; recovery did not add a new cohort member.
  run([java,'-cp',cp,'com.fixmer.mared.BootstrapChecks76','round',owner,owner/'releases/r1/public','finish']);phase[0]='READY'
  w=Mock();w.folder=runtime;w.spec={'clientCommand':[str(java)]};w.r={'candidate':(owner/'bootstrap-candidate76.txt').read_text(),'neoforge':'21.1.264'};w.info={k:info[j] for k,j in {'release':'release','archiveSha256':'client','distribution':'distribution','game':'game','endpoint':'endpoint'}.items()};w.cancelled.return_value=False;w.job.status.return_value={'rootExited':False,'logFailed':False};gate=Gate76(w)
  # These JDK calls need the fixture CA only; never alter the OS/JDK trust configuration.
  for _ in range(30):
   result=subprocess.run([str(java),*props,'-cp',str(core_classpath(runtime)),'com.fixmer.genesis.technology.server.remote.BootstrapGateCli76',w.r['candidate'],'1.21.1','21.1.264'],capture_output=True,text=True,timeout=30)
   if result.returncode==0:
    import base64
    from first58.bootstrap_wire76 import decode
    state=decode(base64.b64decode(result.stdout.strip()))
    if state['phase']=='READY':break
   time.sleep(.2)
  else:raise AssertionError('No READY gate')
  from unittest.mock import patch
  with patch.dict(os.environ,{'JAVA_TOOL_OPTIONS':' '.join(props)}):assert gate.check()['phase']=='READY'
  checks+=1
  assert old.core(runtime,java,'inspect',owner)['total']==2;checks+=1
  # EOF proves signer lifetime follows its pipe; it must not leave a fresh state behind.
  signer.stdin.close();signer.wait(timeout=5);assert not (owner/'bootstrap76.state').exists();checks+=1
  try:get('bootstrap76/state');raise AssertionError('Stopped signer still served readiness')
  except urllib.error.HTTPError as error:assert error.code==503;checks+=1
  assert not errors,errors
 finally:
  stop.set();thread.join(timeout=10)
  if signer.poll() is None:
   signer.stdin.close()
   try:signer.wait(timeout=5)
   except subprocess.TimeoutExpired:signer.kill();signer.wait(timeout=5)
  process.terminate()
  try:process.communicate(timeout=10)
  except subprocess.TimeoutExpired:process.kill();process.communicate()
 print('Bootstrap76 actual owner/HTTPS/recovery/EOF checks passed:',checks,'; server runtime READY was an explicit HMAC model, not Minecraft.')
