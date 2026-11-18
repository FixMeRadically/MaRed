#!/usr/bin/env python3
"""Actual renewal and metadata transaction integration; Java21/Job/READY are explicit fixtures."""
from pathlib import Path
from unittest.mock import Mock,patch
import argparse,json,shutil,subprocess,sys,tempfile,zipfile

def main():
 p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);p.add_argument('--classes',type=Path,required=True);p.add_argument('--fixtures',type=Path,required=True);a=p.parse_args();project=a.project.resolve();java=a.java.resolve();sys.path.insert(0,str(project/'tools'))
 from first58 import dedicated74 as base,dedicated77 as imports,publication83,publication79
 from first58.dedicated85 import Owner85
 from first58.bridge67 import write,read
 from first58.common import inventory
 def jar(path,modid,value,bootstrap=False):
  with zipfile.ZipFile(path,'w') as z:
   z.writestr('META-INF/neoforge.mods.toml',f'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="{modid}"\nversion="{value}"\n');z.writestr('data',value)
   if bootstrap:
    for name in ('com/fixmer/mared/Mared.class','com/fixmer/mared/technology/server/firstlaunch/RemoteClient70.class','com/fixmer/mared/technology/server/firstlaunch/DedicatedServer74.class','com/fixmer/mared/technology/server/remote/ServerAdmin75.class','com/fixmer/mared/mixin/BootstrapConnectMixin76.class'):z.writestr(name,b'explicit fixture, not executable game bytecode')
 with tempfile.TemporaryDirectory(prefix='genesis-first83-files-') as temp:
  home=Path(temp);runtime=home/'runtime';(runtime/'runtime').mkdir(parents=True)
  with zipfile.ZipFile(runtime/'runtime/core.jar','w',zipfile.ZIP_DEFLATED) as z:
   for file in a.classes.resolve().rglob('*.class'):z.write(file,file.relative_to(a.classes.resolve()))
  source=home/'source';(source/'mods').mkdir(parents=True);(source/'world').mkdir();(source/'world/session.lock').write_bytes(b'');(source/'world/level.dat').write_bytes(b'original saved world');(source/'server.properties').write_text('level-name=world\nserver-port=25565\nonline-mode=false\nserver-ip=127.0.0.1\n');(source/'eula.txt').write_text('eula=true\n');(source/'user_jvm_args.txt').write_text('-Xmx1G\n');args=source/'libraries/net/neoforged/neoforge/21.1.264/win_args.txt';args.parent.mkdir(parents=True);args.write_text('net.example.Fixture\n');jar(source/'mods/mared.jar','mared','1',True);jar(source/'mods/example.jar','example','1')
  before=base.tree(source,'world',raw=True);root=home/'owner';original_run=subprocess.run
  def version_fixture(cmd,*args,**kwargs):
   if list(map(str,cmd))==[str(java),'-version']:return Mock(returncode=0,stdout='',stderr='openjdk version "21.0.12"')
   return original_run(cmd,*args,**kwargs)
  with patch.object(base.subprocess,'run',side_effect=version_fixture):imports.initialize(runtime,source,root,java)
  assert base.tree(source,'world',raw=True)==before;assert not (source/'config/mared/publisher73').exists();s,key=base.settings(root);assert s['initialGeneration']==1;assert (root/'generations/server-1').is_dir();assert not (root/'generations/server-0').exists();checks=5
  client=home/'client';server=home/'next-server';shutil.copytree(source/'mods',client);shutil.copytree(source/'mods',server)
  import socket,ssl,urllib.request
  sock=socket.socket();sock.bind(('127.0.0.1',0));port=sock.getsockname()[1];sock.close();endpoint=f'https://localhost:{port}/'
  preview=publication83.first(runtime,root,client,server,'r1',3600,'localhost:25565',endpoint);assert preview['sequence']==1;assert not (root/'publisher73/counter79').exists();publication83.first(runtime,root,client,server,'r1',3600,'localhost:25565',endpoint,preview['ticket']);checks+=3
  owner=Owner85(runtime,root);owner.current=1;owner.current_inventory=s['initialInventory'];job=Mock();job.status.return_value=dict(rootExited=False,exitCode=None,logFailed=False);job.empty.return_value=False;owner.jobs={1:job};owner.ready_generation1=True;session=read(root/'server-1.session',key);receipt={k:session[k] for k in ('schema','run','generation','inventory')};receipt.update(sequence='0',request='00000000-0000-0000-0000-000000000000',phase='READY',round='none',at='1');write(root/'server-1.status',key,receipt)
  current=root/'generations/server-1';unchanged=base.tree(current,'world',raw=True);owner.publish_baseline('r1');assert owner.active_label76=='r1';assert base.tree(current,'world',raw=True)==unchanged;assert read(root/'publication75',key)['label']=='r1';checks+=3

  # Save TLS against the original fresh public folder, then let it expire after renewal.
  from first58 import https78
  cert=home/'cert.pem';tlskey=home/'tls.key'
  original_run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','2','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost','-keyout',str(tlskey),'-out',str(cert)],check=True,capture_output=True)
  https78.save(runtime,root,root/'releases/r1/public',cert,tlskey,'127.0.0.1',java)
  from first58 import publication85
  cfg=publication85.config(root);owner.renew85(cfg);assert owner.active_label76.startswith('renew-');second=owner.active_label76;assert owner.previous_info['sequence']==2;assert read(root/'publication75',key)['label']==second;assert read(root/'baseline83',key)['label']==second;assert read(root/'publication85.tx',key)['phase']=='COMMITTED';assert read(root/'renew85.pending',key)['phase']=='DONE';checks+=7
  assert base.tree(current,'world',raw=True)==unchanged;job.close.assert_not_called();checks+=2
  # An actual signed candidate, then an injected disk failure between the metadata writes.
  owner.core85('renew',root/'publisher73',root/'releases'/second,root/'releases','repair-candidate',86400)
  atomic=publication85.atomic;failed=[False]
  def fail_one(path,data,*args,**kwargs):
   if path.name=='publication75' and not failed[0]:failed[0]=True;raise OSError('explicit disk failure fixture')
   return atomic(path,data,*args,**kwargs)
  with patch.object(publication85,'atomic',side_effect=fail_one):
   try:owner.publish_baseline('repair-candidate');raise AssertionError('disk failure missing')
   except OSError:pass
  assert read(root/'bootstrap76.local',key)['phase']=='UNAVAILABLE';checks+=1
  assert read(root/'baseline83',key)['label']=='repair-candidate';assert read(root/'publication75',key)['label']==second;assert read(root/'publication85.tx',key)['phase']=='PREPARED';checks+=3
  assert owner.repair85();assert owner.active_label76=='repair-candidate';assert read(root/'publication75',key)['label']=='repair-candidate';assert read(root/'publication85.tx',key)['phase']=='COMMITTED';checks+=4
  # Explicit restore after another partial switch returns metadata, never world/JAR bytes.
  owner.core85('renew',root/'publisher73',root/'releases/repair-candidate',root/'releases','restore-candidate',86400);failed[0]=False
  with patch.object(publication85,'atomic',side_effect=fail_one):
   try:owner.publish_baseline('restore-candidate');raise AssertionError('disk failure missing')
   except OSError:pass
  assert owner.repair85(True);assert owner.active_label76=='repair-candidate';assert read(root/'publication75',key)['label']=='repair-candidate';assert read(root/'baseline83',key)['label']=='repair-candidate';assert read(root/'publication85.tx',key)['phase']=='ABORTED';assert owner.paused85;checks+=6
  owner.renew85(cfg);assert owner.active_label76.startswith('renew-');assert read(root/'publication85.tx',key)['phase']=='COMMITTED';checks+=2
  # Selection succeeded but the author-state DONE receipt failed: reconcile, never sign again.
  from first58 import dedicated85
  original_write=dedicated85.write;failed_done=[False]
  def fail_done(path,key,packet):
   if path.name=='renew85.pending' and packet.get('phase')=='DONE' and not failed_done[0]:failed_done[0]=True;raise OSError('explicit final DONE failure fixture')
   return original_write(path,key,packet)
  with patch.object(dedicated85,'write',side_effect=fail_done):
   try:owner.renew85(cfg);raise AssertionError('DONE write failure missing')
   except OSError:pass
  pending=owner.pending85();assert pending['phase']=='AUTHORING';assert owner.active_label76==pending['label'];checks+=2
  count_before=len(list((root/'releases').iterdir()));owner.resume85(pending);assert owner.pending85()['phase']=='DONE';assert len(list((root/'releases').iterdir()))==count_before;checks+=2
  original_run([str(java),'-cp',str(a.classes.resolve())+__import__('os').pathsep+str(a.fixtures.resolve()),'com.fixmer.genesis.verification.RenewalFixture85',str(root/'releases/r1'),str(root/'publisher73')],check=True,capture_output=True)
  from first58.renew85 import Manager85
  transport=Manager85(runtime,root,java)
  try:
   transport.tick(True);assert transport.state=='RUNNING_TLS',(transport.state,transport.reason);checks+=1
   context=ssl.create_default_context(cafile=str(cert))
   with urllib.request.urlopen(endpoint+'offer.bin',context=context,timeout=10) as response:body=response.read()
   assert body==(root/'releases'/owner.active_label76/'public/offer.bin').read_bytes();checks+=1
   server=transport.server;thread=transport.thread;owner.renew85(cfg);transport.tick(True);assert transport.server is server and transport.thread is thread;checks+=1
   with urllib.request.urlopen(endpoint+'offer.bin',context=context,timeout=10) as response:body=response.read()
   assert body==(root/'releases'/owner.active_label76/'public/offer.bin').read_bytes();checks+=1
  finally:transport.close()
  assert transport.server is None;checks+=1
  assert base.tree(current,'world',raw=True)==unchanged;assert base.tree(source,'world',raw=True)==before;job.close.assert_not_called();checks+=3
  print(f'Renewal85 actual copy/identity -> first baseline -> renewal -> torn-write repair -> restore -> actual TLS restart/live routing: {checks} linked checks passed; Java21 version gate and native Job/READY are explicit fixtures, no Minecraft launched.')
if __name__=='__main__':main()
