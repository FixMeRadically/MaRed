"""Two ACTUAL signed HTTPS candidates and real file generations, with explicit Job/exit models.
No Windows process or Minecraft launch is claimed by this test.
"""
from pathlib import Path
import copy,json,os,subprocess,sys,uuid
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.common import inventory,sha
from first58.bridge67 import write,read
from first58.remote70 import Worker
from first58.repeat71 import Cycle71,packet,publish
from first58.install70 import files,policy
from test_remote_install70 import mod

class JobModel:
 def __init__(self):self.ended=False
 def status(self):return {'rootExited':self.ended,'exitCode':0 if self.ended else None,'logFailed':False}
 def empty(self):return self.ended

class LeaseModel:
 def closed(self):return True
 def normal(self):return True

def verified(java,cp,props,cache,candidate):
 p=subprocess.run([str(java),*props,'-cp',cp,'com.fixmer.genesis.technology.server.remote.RemoteInstallCli70','verify',str(cache),str(candidate),'1.21.1','21.1.264'],capture_output=True,text=True,timeout=60)
 assert p.returncode==0,p.stderr;return json.loads(p.stdout)

def begin(root,java,cp,props,cache):
 candidate=next((cache/'downloads').iterdir());info=verified(java,cp,props,cache,candidate)
 source=root/'source71';(source/'mods').mkdir(parents=True);mod(source/'mods/mared.jar','mared',adapter=True);mod(source/'mods/old.jar','example','old');(source/'options.txt').write_text('first options')
 source_before={str(p.relative_to(source)):p.read_bytes() for p in source.rglob('*') if p.is_file()}
 w=Worker.__new__(Worker);w.folder=root/'owner71';w.root=w.folder/'run';(w.root/'generations').mkdir(parents=True);w.source=source;w.key=b'1'*32;(w.root/'channel.key').write_bytes(w.key);w.id=str(uuid.uuid4());w.account=str(uuid.uuid4());w.info=info;w.archive=candidate/'client.zip';w.spec={'clientCommand':[str(java),'fixture.Main','--gameDir','{profile}']};w.r={'cache':str(cache),'neoforge':'21.1.264'};w.baseline=files(source/'mods');w.approved=policy(source/'mods',w.archive,info);w.lease=LeaseModel();w.build();w.job=JobModel()
 assert {str(p.relative_to(source)):p.read_bytes() for p in source.rglob('*') if p.is_file()}==source_before
 return w

def finish(w,java,cp,props,cache):
 checks=0;before=w.root/'generations/client-1';candidate=next(p for p in (cache/'downloads').iterdir() if p.name!=Path(w.archive).parent.name);info=verified(java,cp,props,cache,candidate)
 assert info['release']!=w.info['release'] and info['files']!=w.info['files'];checks+=1
 request=str(uuid.uuid4());publish(w,request,'ACTIVE');env={**os.environ,'GENESIS_REMOTE70_ROOT':str(w.root),'GENESIS_REMOTE70_RUN':w.id}
 def bridge(*args):return subprocess.run([str(java),'-cp',cp,'RemoteRepeatChecks71',*map(str,args)],cwd=before,env=env,capture_output=True,text=True,timeout=30)
 assert bridge('status').stdout.strip()=='ACTIVE';checks+=1
 result=bridge('request',request,'PREPARE',cache,candidate);assert result.returncode==0,result.stderr;offer=packet(w);assert offer['request']==request and offer['candidate']==str(candidate);checks+=1
 cycle=Cycle71(w,offer);cycle.verify_remote=lambda:verified(java,cp,props,cache,candidate);cycle.prepare();assert cycle.job is None;assert not (cycle.root/'generations/client-1').exists();checks+=1
 publish(w,request,'READY',cycle);assert bridge('status').stdout.strip()=='READY';checks+=1
 assert bridge('request',request,'ARM',cache,candidate).returncode==0;cycle.final_check();checks+=1
 # Files are saved in the managed old profile AFTER Prepare, then source Job ends normally.
 (before/'options.txt').write_text('latest options before second exit');(before/'config/mared/script_projects/p').mkdir(parents=True);(before/'config/mared/script_projects/p/latest.java').write_text('latest saved script');(before/'config/mared/script_projects/p/mods.before70').mkdir();(before/'config/mared/script_projects/p/mods.before70/keep.txt').write_text('project data');(before/'saves/local').mkdir(parents=True);(before/'saves/local/level.dat').write_bytes(b'not copied')
 snapshot={str(p.relative_to(before)):p.read_bytes() for p in before.rglob('*') if p.is_file()};w.job.ended=True;cycle.build();after=cycle.root/'generations/client-1'
 assert (after/'options.txt').read_text()=='latest options before second exit';assert (after/'config/mared/script_projects/p/latest.java').read_text()=='latest saved script';assert (after/'config/mared/script_projects/p/mods.before70/keep.txt').read_text()=='project data';checks+=1
 assert not (after/'saves').exists();assert inventory(after)==cycle.config['inventory'];assert files(after/'mods')==cycle.approved['after'];checks+=1
 assert {str(p.relative_to(before)):p.read_bytes() for p in before.rglob('*') if p.is_file()}==snapshot;assert (after/'mods/mared.jar').read_bytes()==(before/'mods/mared.jar').read_bytes();checks+=1
 assert files(after/'mods.before70')==cycle.approved['before'];checks+=1
 old_env=env;env={**env,'GENESIS_REMOTE70_ROOT':str(cycle.root),'GENESIS_REMOTE70_RUN':cycle.id};old_cwd=before;before=after;publish(cycle,str(uuid.UUID(int=0)),'ACTIVE')
 assert bridge('status').stdout.strip()=='ACTIVE';checks+=1
 # A signed old-generation heartbeat is rejected by the Java reader of the new generation.
 path=cycle.root/'repeat71.status';saved=path.read_bytes();wrong=read(path,cycle.key);wrong['run']=w.id;write(path,cycle.key,wrong);assert bridge('status').returncode!=0;path.write_bytes(saved);checks+=1
 saved=path.read_bytes();path.write_bytes(saved+b'TAMPER');assert bridge('status').returncode!=0;path.write_bytes(saved);checks+=1
 assert bridge('request',str(uuid.UUID(int=0)),'ARM',cache,candidate).returncode!=0;checks+=1
 print(f'Remote71 actual signed HTTPS/file/channel checks passed: {checks}; Job exits were explicit models, no Minecraft/Windows launch.')
 return checks
