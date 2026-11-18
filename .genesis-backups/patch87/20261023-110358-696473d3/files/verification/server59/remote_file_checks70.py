"""Real verified cache -> new files -> Java local seal integration.
The source HANDLE is explicitly modeled; there is NO Minecraft or Windows proof here.
Called by the actual HTTPS fixture while its test TLS host is still running.
"""
from pathlib import Path
import json,os,subprocess,sys,uuid
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.common import sha,inventory
from first58.bridge67 import read as signed_read
from first58.install70 import policy,files
from first58.remote70 import Worker
from test_remote_install70 import mod

def check_files(root,java,classpath,props,cache):
 root=Path(root);cache=Path(cache);candidate=next((cache/'downloads').iterdir());checks=0
 command=[str(java),*props,'-cp',classpath,'com.fixmer.genesis.technology.server.remote.RemoteInstallCli70','verify',str(cache),str(candidate),'1.21.1','21.1.264']
 def core():return subprocess.run(command,capture_output=True,text=True,timeout=60)
 result=core();assert result.returncode==0,result.stderr;info=json.loads(result.stdout);assert info['installAuthority'] is False;checks+=1
 source=root/'source70';(source/'mods').mkdir(parents=True);mod(source/'mods/mared.jar','mared',adapter=True);mod(source/'mods/old.jar','example','old')
 (source/'config/mared/script_projects/example/src').mkdir(parents=True);(source/'config/mared/script_projects/example/src/Main.java').write_text('latest saved source',encoding='utf-8');(source/'options.txt').write_text('latest options',encoding='utf-8');(source/'saves/local-world').mkdir(parents=True);(source/'saves/local-world/level.dat').write_bytes(b'NEVER COPY A LOCAL WORLD')
 before={str(p.relative_to(source)):p.read_bytes() for p in source.rglob('*') if p.is_file()}
 w=Worker.__new__(Worker);w.root=root/'install70';(w.root/'generations').mkdir(parents=True);w.source=source;w.key=b'k'*32;(w.root/'channel.key').write_bytes(w.key);w.id=str(uuid.uuid4());w.account=str(uuid.uuid4());w.info=info;w.archive=candidate/'client.zip';w.spec={'clientCommand':[str(java),'fixture.Main','--gameDir','{profile}']};w.r={'neoforge':'21.1.264'};w.baseline=files(source/'mods');w.approved=policy(source/'mods',w.archive,info)
 class EndedSourceModel:
  def closed(self):return True
  def normal(self):return True
 w.lease=EndedSourceModel();w.build();profile=w.root/'generations/client-1'
 assert (profile/'config/mared/script_projects/example/src/Main.java').read_text()=='latest saved source';assert (profile/'options.txt').read_text()=='latest options';checks+=1
 assert not (profile/'saves').exists();checks+=1
 assert {str(p.relative_to(source)):p.read_bytes() for p in source.rglob('*') if p.is_file()}==before;checks+=1
 assert (profile/'mods/mared.jar').read_bytes()==(source/'mods/mared.jar').read_bytes();assert files(profile/'mods')==w.approved['after'];assert inventory(profile)==w.config['inventory'];checks+=1
 env={**os.environ,'GENESIS_REMOTE70_ROOT':str(w.root),'GENESIS_REMOTE70_RUN':w.id}
 def session(extra=None):return subprocess.run([str(java),'-cp',classpath,'RemoteSessionChecks70'],cwd=profile,env={**env,**(extra or {})},capture_output=True,text=True,timeout=30)
 opened=session();assert opened.returncode==0,opened.stderr;reply=signed_read(w.root/'client.status',w.key);assert reply['phase']=='READY' and w.status()==reply;checks+=1
 assert session({'GENESIS_REMOTE70_RUN':str(uuid.uuid4())}).returncode!=0;checks+=1
 marker=profile/'remote70.marker';original=marker.read_bytes();marker.write_bytes(original+b'TAMPER');assert session().returncode!=0;marker.write_bytes(original);checks+=1
 original_archive=w.archive.read_bytes();w.archive.write_bytes(original_archive+b'TAMPER');assert core().returncode!=0;w.archive.write_bytes(original_archive);checks+=1
 signature=candidate/'offer.sig';saved=signature.read_bytes();signature.write_bytes(bytes([saved[0]^1])+saved[1:]);assert core().returncode!=0;signature.write_bytes(saved);checks+=1
 print(f'Remote70 real cache/file/channel checks passed: {checks}; source HANDLE and loaded mod list were explicit models, not game/runtime verification.')
 return checks
