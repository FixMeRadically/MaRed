#!/usr/bin/env python3
"""Actual Python/Java/CLI file integration. Java21 version and Gradle SUCCESS explicitly modeled."""
from pathlib import Path
from unittest.mock import Mock,patch
import argparse,json,shutil,subprocess,sys,tempfile,zipfile

def main():
 p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);p.add_argument('--classes',type=Path,required=True);p.add_argument('--fixtures',type=Path,required=True);a=p.parse_args();project=a.project.resolve();java=a.java.resolve();sys.path.insert(0,str(project/'tools'))
 from first58 import dedicated74 as base,dedicated77 as imports,publication83,publication79
 from first58.dedicated83 import Owner83
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
  preview=publication83.first(runtime,root,client,server,'r1',3600,'localhost:25565','https://localhost:8443/');assert preview['sequence']==1;assert not (root/'publisher73/counter79').exists();publication83.first(runtime,root,client,server,'r1',3600,'localhost:25565','https://localhost:8443/',preview['ticket']);checks+=3

  from first58 import publication84
  snapshot=Path(original_run([str(java),'-cp',str(a.classes.resolve())+__import__('os').pathsep+str(a.fixtures.resolve()),'com.fixmer.genesis.verification.ScriptBuildFixture84',str(home)],capture_output=True,text=True,check=True).stdout.strip())
  composed=publication84.run(runtime,root,'script-prepare',snapshot,side='BOTH');draft=Path(composed['draft']);assert composed['side']=='BOTH';assert (draft/'client/example.jar').is_file();assert (draft/'server/example.jar').is_file();checks+=3
  next=publication84.run(runtime,root,'script-preview',draft,label='r2',ttl=3600);assert next['clientCount']==1 and next['serverCount']==1;checks+=1
  code=home/'script-project/code.java';code.write_text('new unsaved-on-disk source')
  try:publication84.run(runtime,root,'script-author',draft,label='r2',ttl=3600,ticket=next['ticket']);raise AssertionError('stale source accepted')
  except ValueError as e:assert str(e)=='SCRIPT84_INPUTS_CHANGED'
  assert not (root/'releases/r2').exists();checks+=2;code.write_text('fixture source')
  made=publication84.run(runtime,root,'script-author',draft,label='r2',ttl=3600,ticket=next['ticket']);assert made['authored']=='r2';verified=base.core(runtime,java,'verify',root/'releases/r2',root/'publisher73/public.der');assert verified['release']==made['release'];assert (root/'releases/r2/script84').is_file();checks+=3
  assert base.tree(source,'world',raw=True)==before;assert (root/'generations/server-1/world/level.dat').read_bytes()==b'original saved world';checks+=2
  # CLI path itself, not a mocked parser, receives this already known draft.
  cmd=[sys.executable,str(project/'tools/dedicated_server75.py'),str(runtime),'script-preview',str(root),str(draft),'--label','r3','--ttl','3600'];cli=original_run(cmd,capture_output=True,text=True);assert cli.returncode==0,cli.stderr;assert json.loads(cli.stdout)['parent']!=made['release'];checks+=1
  # Original baseline is still selected: signed publication alone must not bypass consent/COMMIT.
  selected,_=publication79.baseline(root,key,project=runtime);assert selected==root/'releases/r1/public';assert not (root/'publication75').exists();assert not (root/'commits81').exists();checks+=3
  print(f'Script84 actual import -> first pair -> build snapshot -> compose -> stale refusal -> sign -> CLI: {checks} linked checks passed. Java21 version and Gradle SUCCESS are explicit fixtures; no Minecraft/native Job launched.')
if __name__=='__main__':main()
