"""Two real signed releases and actual generation copies. Minecraft READY/save/OS Jobs are explicit models."""
import sys,uuid,zipfile
from pathlib import Path
from unittest.mock import Mock

def check_repeat(root,project,runtime,java,cert=None,tls_key=None):
 sys.path.insert(0,str(project/'tools'));sys.path.insert(0,str(project/'verification/server59'))
 from first58 import dedicated74 as old
 from first58.dedicated75 import Owner75,History75
 from first58.bridge67 import write,read
 from first58.common import inventory,json_write,sha
 from test_remote_install70 import mod
 owner_root=root/'owner75';source=owner_root/'generations/server-0';(source/'mods').mkdir(parents=True);(owner_root/'releases').mkdir();(owner_root/'stages').mkdir();(owner_root/'publisher73').mkdir()
 for name in ('public.der','private.der'):(owner_root/'publisher73'/name).write_bytes((root/'identity'/name).read_bytes())
 mod(source/'mods/mared.jar','mared',adapter=True);mod(source/'mods/old.jar','oldmod','old');(source/'world').mkdir();(source/'world/level.dat').write_bytes(b'initial world');(source/'config').mkdir();(source/'config/latest.txt').write_text('initial config');(source/'server.properties').write_text('level-name=world\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25565\n');(source/'eula.txt').write_text('eula=true\n')
 key=b'r'*32;(owner_root/'owner74.key').write_bytes(key);s=dict(schema=74,run=str(uuid.uuid4()),source=str(root/'untouched-original'),java=str(java),argv=[str(java)],minecraft='1.21.1',neoforge='21.1.264',auth='false',bind='127.0.0.1',port='25565',level='world',initialInventory=inventory(source));json_write(owner_root/'settings.json',s);write(owner_root/'settings74',key,{'schema':'74','sha':sha(owner_root/'settings.json',65536)});write(owner_root/'owner75',key,{'schema':'75','run':s['run']});old.session(owner_root,s,key,0,s['initialInventory']);owner=Owner75(runtime,owner_root);first=None;count=0
 from first58.publication75 import Publication75
 import json
 def verified(folder):
  from first58.runtime import core_classpath
  import subprocess
  r=subprocess.run([str(java),'-cp',str(core_classpath(runtime)),'com.fixmer.genesis.technology.server.remote.RemoteUpdateCli69','verify-public',str(folder)],capture_output=True,text=True,check=True);return json.loads(r.stdout)
 publication=Publication75(owner_root,root/'public1',verified)
 assert publication.snapshot()[0]==root/'public1'
 count+=1
 for target in (1,2):
  public=root/('public'+str(target));mods=root/('server-mods75-'+str(target));mods.mkdir()
  with zipfile.ZipFile(next(public.glob('client-*.zip'))) as z:(mods/'example.jar').write_bytes(z.read('files/mods/example.jar'))
  old.core(runtime,java,'author',owner_root/'publisher73',public,mods,owner_root/'releases'/('r'+str(target)))
 def native_ready(argv,cwd,env,log):
  gen=int(env['GENESIS_DEDICATED74_GENERATION'])
  assert env['GENESIS_DEDICATED75_VERSION']=='75' and gen in (1,2);c=read(owner_root/f'server-{gen}.session',key);d={k:c[k] for k in ('schema','run','generation','inventory')};d.update(sequence='0',request=str(uuid.UUID(int=0)),phase='READY',round='none',at='1');write(owner_root/f'server-{gen}.status',key,d);job=Mock();job.status.return_value={'rootExited':False,'exitCode':None,'logFailed':False};job.empty.return_value=False;return job
 owner.job_factory=native_ready;job=Mock();job.status.return_value={'rootExited':False,'exitCode':None,'logFailed':False};job.empty.return_value=False;owner.jobs={0:job}
 for target in (1,2):
  gen=target-1;current=owner_root/f'generations/server-{gen}';(current/'world/level.dat').write_bytes(('latest world '+str(target)).encode());(current/'config/latest.txt').write_text('latest config '+str(target));before=old.tree(current/'world','unused',raw=True);owner.report('ACTIVE_BASELINE' if gen==0 else 'ACTIVE_UPDATED');owner.prepare('r'+str(target))
  assert owner.pending['sourceGen']==gen and owner.pending['targetGen']==target
  assert not (owner.pending['path']/'world').exists();count+=2
  folder,allowed=publication.snapshot();assert folder==owner_root/'releases'/('r'+str(target))/'public' and len(allowed)==4;count+=1
  cmd=owner.command(gen,'SAVE_STOP',str(uuid.uuid4()));c=read(owner_root/f'server-{gen}.session',key);d={k:c[k] for k in ('schema','run','generation','inventory')};d.update(sequence=cmd['sequence'],request=cmd['request'],phase='STOPPED_SAVED',round=cmd['round'],at='1');write(owner_root/f'server-{gen}.status',key,d);job=owner.jobs[gen];job.status.return_value={'rootExited':True,'exitCode':0,'logFailed':False};job.empty.return_value=True;owner.copy_saved();next_profile=owner_root/f'generations/server-{target}'
  assert (next_profile/'world/level.dat').read_bytes()==('latest world '+str(target)).encode()
  assert (next_profile/'config/latest.txt').read_text()=='latest config '+str(target)
  assert old.tree(current/'world','unused',raw=True)==before
  assert sha(next_profile/'mods/mared.jar')==sha(current/'mods/mared.jar')
  assert sha(next_profile/'mods/example.jar')==sha(root/('server-mods75-'+str(target))/'example.jar');count+=5
  job.close.assert_not_called();job.close();del owner.jobs[gen];info=owner.pending['info'];owner.current_inventory=read(owner_root/f'server-{target}.session',key)['inventory'];owner.start(target);owner.previous_info=info;owner.event('SERVER_READY')
  assert owner.current==target and owner.ready_generation1;count+=2
  if first is None:first=info
  else:assert info['parent']==first['release'] and int(info['sequence'])>int(first['sequence']);count+=1
 assert History75(owner_root,key,s['run']).rows
 assert (owner_root/'generations/server-0/world/level.dat').read_bytes()==b'latest world 1'
 assert (owner_root/'generations/server-2/world/level.dat').read_bytes()==b'latest world 2';count+=3
 # A previously applied signed release is rejected by the actual parent/sequence check before a third copy.
 owner.pending=None
 try:owner.prepare('r1');raise AssertionError('Downgrade accepted')
 except ValueError as error:assert 'RELEASE_CHAIN' in str(error);count+=1
 # Real shipped HTTPS helper follows the same authenticated pointer across exports.
 if cert is not None:
  import socket,subprocess,ssl,urllib.request,urllib.error,time
  sock=socket.socket();sock.bind(('127.0.0.1',0));port=sock.getsockname()[1];sock.close()
  process=subprocess.Popen([sys.executable,str(project/'tools/remote_update.py'),str(runtime),'--java',str(java),'serve',str(root/'public1'),'--cert',str(cert),'--key',str(tls_key),'--port',str(port),'--owner75',str(owner_root)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
  try:
   context=ssl.create_default_context(cafile=str(cert));url='https://localhost:'+str(port)+'/genesis/'
   def get(name):
    with urllib.request.urlopen(url+name,context=context,timeout=4) as response:return response.read()
   for _ in range(100):
    if process.poll() is not None:raise AssertionError(process.communicate())
    try:data=get('offer.bin');break
    except (urllib.error.URLError,TimeoutError):time.sleep(.1)
   else:raise AssertionError('Dynamic HTTPS did not start')
   assert data==(root/'public2/offer.bin').read_bytes();count+=1
   pointer=owner_root/'publication75';saved=pointer.read_bytes();pointer.unlink();assert get('offer.bin')==(root/'public1/offer.bin').read_bytes();count+=1
   pointer.write_bytes(saved);assert get('offer.bin')==(root/'public2/offer.bin').read_bytes();count+=1
   pointer.write_bytes(b'invalid')
   try:get('offer.bin');raise AssertionError('Invalid pointer was served')
   except urllib.error.HTTPError as error:assert error.code==503;count+=1
   pointer.write_bytes(saved)
   for name in ('private.der','owner74.key','../settings.json','public.der?private=true'):
    try:get(name);raise AssertionError('Private or query route served')
    except urllib.error.HTTPError as error:assert error.code==404;count+=1
  finally:
   process.terminate()
   try:process.communicate(timeout=10)
   except subprocess.TimeoutExpired:process.kill();process.communicate()
 print('Dedicated75 actual two-release/generation/history/HTTPS checks passed:',count,'; native Job/save/READY were explicit models, not Minecraft or Windows.')
