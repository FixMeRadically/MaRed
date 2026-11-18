#!/usr/bin/env python3
"""Real copied profiles/Ed25519/pairs/first author/renewal guards; Java21/Job/READY are explicit fixtures."""
from pathlib import Path
from unittest.mock import Mock,patch
import argparse,json,shutil,subprocess,sys,tempfile,zipfile

def main():
 p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);p.add_argument('--classes',type=Path,required=True);a=p.parse_args();project=a.project.resolve();java=a.java.resolve();sys.path.insert(0,str(project/'tools'))
 from first58 import dedicated74 as base,dedicated77 as imports,publication83,publication79
 from first58.dedicated83 import Owner83
 from first58.bridge67 import write,read
 from first58.common import inventory
 def jar(path,modid,value,bootstrap=False):
  with zipfile.ZipFile(path,'w') as z:
   z.writestr('META-INF/neoforge.mods.toml',f'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="{modid}"\nversion="{value}"\n');z.writestr('data',value)
   if bootstrap:
    for name in ('com/fixmer/mared/Mared.class','com/fixmer/mared/technology/server/firstlaunch/RemoteClient70.class','com/fixmer/mared/technology/server/firstlaunch/DedicatedServer74.class','com/fixmer/mared/technology/server/remote/ServerAdmin75.class','com/fixmer/mared/mixin/BootstrapConnectMixin76.class'):z.writestr(name,b'explicit fixture, not executable game bytecode')
 with tempfile.TemporaryDirectory(prefix='genesis-first83-данные-') as temp:
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
  preview=publication83.first(runtime,root,client,server,'r1',3600,'localhost:25565','https://localhost:8443/');assert preview['sequence']==1;assert not (root/'publisher73/counter79').exists();publication83.first(runtime,root,client,server,'r1',3600,'localhost:25565','https://localhost:8443/',preview['ticket']);checks+=3
  invalid_key=home/'other.der';invalid_key.write_bytes(b'wrong key')
  try:
   publication83.verify_pair(runtime,root,root/'releases/r1',invalid_key,lambda:None)
  except ValueError as error:
   assert str(error)=='DEDICATED74_PIN',str(error)
  else:
   raise AssertionError('signed publication accepted the wrong trusted key')
  checks+=1
  owner=Owner83(runtime,root);owner.current=1;owner.current_inventory=s['initialInventory'];job=Mock();job.status.return_value=dict(rootExited=False,exitCode=None,logFailed=False);job.empty.return_value=False;owner.jobs={1:job};owner.ready_generation1=True;session=read(root/'server-1.session',key);receipt={k:session[k] for k in ('schema','run','generation','inventory')};receipt.update(sequence='0',request='00000000-0000-0000-0000-000000000000',phase='READY',round='none',at='1');write(root/'server-1.status',key,receipt)
  current=root/'generations/server-1';unchanged=base.tree(current,'world',raw=True);owner.publish_baseline('r1');assert owner.active_label76=='r1';assert base.tree(current,'world',raw=True)==unchanged;assert read(root/'publication75',key)['label']=='r1';checks+=3
  # Same code, new immutable release/sequence/TTL; no server restart or original-profile write.
  next=publication79.run(runtime,root,client,server,'r2',7200);publication79.run(runtime,root,client,server,'r2',7200,next['ticket']);owner.publish_baseline('r2');assert owner.previous_info['sequence']==2;assert owner.active_label76=='r2';assert base.tree(current,'world',raw=True)==unchanged;job.close.assert_not_called();checks+=4
  # Refuse both code-changing paths without changing the chosen publication.
  jar(client/'example.jar','example','2');next=publication79.run(runtime,root,client,server,'r3',3600);publication79.run(runtime,root,client,server,'r3',3600,next['ticket'])
  try:owner.publish_baseline('r3');raise AssertionError('client change bypassed consent')
  except ValueError as e:assert str(e)=='PUBLICATION83_CLIENT_CODE_DIFFERS'
  jar(server/'example.jar','example','2');next=publication79.run(runtime,root,client,server,'r4',3600);publication79.run(runtime,root,client,server,'r4',3600,next['ticket'])
  try:owner.publish_baseline('r4');raise AssertionError('server change bypassed restart')
  except ValueError as e:assert str(e)=='PUBLICATION83_SERVER_CODE_DIFFERS'
  assert read(root/'publication75',key)['label']=='r2';assert base.tree(current,'world',raw=True)==unchanged;assert base.tree(source,'world',raw=True)==before;job.close.assert_not_called();checks+=6
  # The old one-shot74 entry point keeps its old requirement; only the repeatable panel opts into first83.
  with patch.object(base.subprocess,'run',side_effect=version_fixture):
   try:base.initialize(runtime,source,home/'legacy74',java);raise AssertionError('legacy74 silently opted into first83')
   except ValueError as e:assert str(e)=='DEDICATED74_SETUP_PUBLISHER73_FIRST'
  assert not (home/'legacy74/publisher73').exists();checks+=2
  # Check actual signer now sees the renewed baseline READY in this modeled running generation.
  base.java_call(runtime,java,'com.fixmer.genesis.technology.server.remote.BootstrapPublisher76','once',root);assert (root/'bootstrap76.state').is_file();checks+=1
  print(f'Publication83 actual first import -> signed pair -> selected baseline -> renewal -> changed-code refusal: {checks} checks passed. Java21 version check, native Job and runtime READY explicitly modeled; no Minecraft launched.')
if __name__=='__main__':main()
