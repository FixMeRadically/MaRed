"""Actual owner-thread HTTPS/key/SAN/identity/connection shutdown; Minecraft/Windows are not started."""
from pathlib import Path
from unittest.mock import patch
import json,os,shutil,socket,ssl,subprocess,sys,time,urllib.request,urllib.error,uuid


def check_repair(runtime,old_root,target,java):
 from first58.repair78 import repair
 before=(target/'generations/server-2/world/level.dat').read_bytes();(target/'import77.pending').write_text('77');(target/'recovery77.ready').unlink()
 result=repair(runtime,old_root,java);assert result['repairedRegistration'] and not result['started'];assert (target/'generations/server-2/world/level.dat').read_bytes()==before;assert not (target/'import77.pending').exists();assert not (target/'control73').exists()
 print('Recovery78 actual signed-pair/final-registration checks passed: 4; no process start or world recopy.')


def check_https(root,project,runtime,java,cp,props,cert,tls_key):
 from first58 import dedicated74 as base,https78 as h
 from first58.common import sha,inventory,json_write
 from first58.bridge67 import write,read
 from first58.runtime import core_classpath
 def run(args,ok=True):
  result=subprocess.run(list(map(str,args)),capture_output=True,text=True,timeout=120)
  assert (result.returncode==0)==ok,(result.stdout,result.stderr);return result
 sock=socket.socket();sock.bind(('127.0.0.1',0));port=sock.getsockname()[1];sock.close();endpoint=f'https://localhost:{port}/genesis/';identity=root/'identity78';public=root/'public78';owner=root/'owner78';profile=owner/'generations/server-0';shutil.copytree(root/'owner75/generations/server-2',profile);(owner/'publisher73').mkdir();(owner/'releases').mkdir();(owner/'stages').mkdir()
 cli=[java,'-cp',str(core_classpath(runtime)),'com.fixmer.genesis.technology.server.remote.RemoteUpdateCli69'];tlscli=[java,'-cp',str(core_classpath(runtime)),'com.fixmer.genesis.technology.server.remote.Tls78'];run([*cli,'init',identity,'localhost:25565',endpoint]);run([*cli,'publish',identity,root/'packages',public,'3600'])
 for name in ('public.der','private.der'):(owner/'publisher73'/name).write_bytes((identity/name).read_bytes())
 s=dict(schema=74,run=str(uuid.uuid4()),source=str(root/'source78'),java=str(java),argv=[str(java)],minecraft='1.21.1',neoforge='21.1.264',auth='false',bind='127.0.0.1',port='25565',level='world',initialInventory=inventory(profile));key=b'k'*32;(owner/'owner74.key').write_bytes(key);json_write(owner/'settings.json',s);write(owner/'settings74',key,dict(schema='74',sha=sha(owner/'settings.json')));base.session(owner,s,key,0,s['initialInventory'])
 for v in (75,76,77,78):write(owner/f'owner{v}',key,dict(schema=str(v),run=s['run']))
 original_cert=cert;cert=root/'owner78-cert.pem';cert.write_bytes(original_cert.read_bytes());original_key=tls_key;tls_key=root/'owner78-key.pem';tls_key.write_bytes(original_key.read_bytes())
 checked=h.check(runtime,owner,public,cert,tls_key,'127.0.0.1',java);assert checked['endpoint']==endpoint and checked['fingerprint'];count=1
 denied=run([*tlscli,'inspect',cert,f'https://127.0.0.1:{port}/genesis/'],False);assert 'HTTPS78_HOSTNAME' in denied.stderr;count+=1
 bad_key=root/'mismatch78.key';run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:2048','-out',bad_key])
 try:h.check(runtime,owner,public,cert,bad_key,'127.0.0.1',java);raise AssertionError('Mismatched key accepted')
 except ValueError as bad:assert str(bad)=='HTTPS78_KEY_PAIR';count+=1
 h.save(runtime,owner,public,cert,tls_key,'127.0.0.1',java);before=(owner/'https78.cfg').read_bytes()
 try:h.save(runtime,owner,public,cert,bad_key,'127.0.0.1',java);raise AssertionError('Bad config saved')
 except ValueError:assert before==(owner/'https78.cfg').read_bytes();count+=1
 # An occupied port reports a failure; the unrelated listener remains owned by this fixture.
 blocker=socket.socket();blocker.bind(('127.0.0.1',port));blocker.listen(1);manager=h.Manager78(runtime,owner,java)
 try:
  manager.tick(True);assert manager.state=='FAILED' and manager.reason=='HTTPS78_LISTEN_FAILED';assert blocker.fileno()>=0;count+=1;blocker.close()
  h.save(runtime,owner,public,cert,tls_key,'127.0.0.1',java);manager.tick(True);assert manager.state=='RUNNING_TLS';count+=1
  context=ssl.create_default_context(cafile=str(cert))
  with urllib.request.urlopen(endpoint+'offer.bin',context=context,timeout=5) as reply:assert reply.read()==(public/'offer.bin').read_bytes();count+=1
  default=run([*tlscli,'probe',endpoint,owner/'publisher73/public.der'],False);assert 'HTTPS78_TLS_TRUST' in default.stderr;count+=1
  trusted=run([java,*props,'-cp',str(core_classpath(runtime)),'com.fixmer.genesis.technology.server.remote.Tls78','probe',endpoint,owner/'publisher73/public.der']);assert json.loads(trusted.stdout)['clientTrust']=='VERIFIED_THIS_JDK';count+=1
  wrong=root/'different78.der';wrong.write_bytes((root/'identity/public.der').read_bytes());denied=run([java,*props,'-cp',str(core_classpath(runtime)),'com.fixmer.genesis.technology.server.remote.Tls78','probe',endpoint,wrong],False);assert 'HTTPS78_ENDPOINT_IDENTITY' in denied.stderr;count+=1
  for name in ('private.der','https78.cfg','owner74.key','../settings.json','offer.bin?token=private'):
   try:urllib.request.urlopen(endpoint+name,context=context,timeout=5);raise AssertionError('Private/query path served')
   except urllib.error.HTTPError as bad:assert bad.code==404;count+=1
  assert h.status(owner)['state']=='RUNNING_TLS';count+=1
  try:h.check(runtime,owner,public,cert,tls_key,'0.0.0.0',java);raise AssertionError('Public offline listener accepted')
  except ValueError as bad:assert str(bad)=='HTTPS78_OFFLINE_LOOPBACK';count+=1
  write(owner/'view75',key,dict(schema='75',run=s['run'],phase='WAITING_ALL_INSTALLED',busy='false',updated=str(int(time.time()*1000)),control=json.dumps(dict(phase='COMMITTED'))))
  saved_cert=cert.read_bytes();cert.write_bytes(b'changed while active');manager.tick(True);assert manager.state=='FAILED' and manager.reason=='HTTPS78_CERTIFICATE_CHANGED';count+=1
  try:h.disable(owner);raise AssertionError('HTTPS disabled during committed repair')
  except ValueError as bad:assert str(bad)=='HTTPS78_UPDATE_BUSY';count+=1
  cert.write_bytes(saved_cert);manager.tick(True);assert manager.server is None;count+=1
  write(owner/'view75',key,dict(schema='75',run=s['run'],phase='WAITING_ALL_INSTALLED',busy='false',updated=str(int(time.time()*1000)),control=json.dumps(dict(phase='COMMITTED'))))
  h.save(runtime,owner,public,cert,tls_key,'127.0.0.1',java);manager.tick(True);assert manager.state=='RUNNING_TLS';count+=1
  (owner/'view75').unlink()
  stalled=socket.socket();stalled.settimeout(2);stalled.connect(('127.0.0.1',port))
  transport=getattr(manager.server,'primary',manager.server) # both loopback sockets share this registry
  for _ in range(50):
   with transport.connections_lock:waiting=bool(transport.connections)
   if waiting:break
   time.sleep(.02)
  assert waiting;count+=1
  h.disable(owner);manager.tick(True);assert manager.state=='DISABLED';count+=1
  try:assert stalled.recv(1)==b''
  except ConnectionResetError:pass
  finally:stalled.close()
  count+=1
  try:urllib.request.urlopen(endpoint+'offer.bin',context=context,timeout=2);raise AssertionError('Disabled listener still serves')
  except urllib.error.URLError:count+=1
  # In-process import closure: no detached helper or PID adoption was used.
 finally:manager.close();blocker.close()
 assert h.status(owner)['state']=='STOPPED';count+=1
 print('HTTPS78 real certificate/SAN/key/TLS/trust/identity/port/owner-thread checks passed:',count,'; no Minecraft or Windows process was started.')
