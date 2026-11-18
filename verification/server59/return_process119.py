"""Actual old-profile copy + child JVM + real signed MENU/STOP/JOIN rejection.
Native Job/source exit and game rendered-frame facts are explicit fixtures, not Minecraft acceptance.
"""
from pathlib import Path
import argparse,importlib.util,json,os,subprocess,sys,time,unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parent))
import test_return119 as tests
from first58 import return119 as rt
from first58.bridge67 import read,write

class Child:
 def __init__(self,argv,cwd,env,log):
  self.log=Path(log).open('wb');self.p=subprocess.Popen(argv,cwd=cwd,env=env,stdout=self.log,stderr=subprocess.STDOUT);self.closed=False
 def status(self):
  code=self.p.poll();return dict(rootExited=code is not None,active=int(code is None),exitCode=code,logFailed=False)
 def empty(self):return self.p.poll()==0
 def close(self):
  if self.closed:return
  self.closed=True
  if self.p.poll() is None:self.p.kill()
  self.p.wait(timeout=10);self.log.close()

def process(java,classpath):
 case=tests.Return119();case.setUp();w=case.w;events=[];count=0
 try:
  w.spec['clientCommand']=[java,'-cp',classpath,'com.fixmer.genesis.technology.server.remote.RemoteReturnChecks119','--gameDir','{profile}'];w.job_factory=Child
  def watch(notify,cancel,ask):
   nonlocal count
   count+=1;assert w.status()['phase']=='MENU_FRAME_READY';assert w.job.status()['active']==1
  with patch.object(w,'watch',side_effect=watch):rt.restore_menu(w,ValueError('REMOTE70_CHILD_ERROR'),events.append,lambda:False,lambda msg:False)
  assert count==1 and events[-1]=='BASELINE_READY'
  from first58.repeat71 import publish
  env=dict(os.environ,GENESIS_REMOTE70_ROOT=str(w.root),GENESIS_REMOTE70_RUN=w.id)
  profile=w.root/'generations/client-1'
  for phase in ('RETURNING','RESTORED'):
   publish(w,str(__import__('uuid').UUID(int=0)),phase)
   check=subprocess.run([java,'-cp',classpath,'com.fixmer.genesis.technology.server.remote.RemoteReturnChecks119','repeat-status'],cwd=profile,env=env,capture_output=True,text=True,timeout=15)
   assert check.returncode==0,check.stderr
  w.stop();w.job.close();w.job=None
  profile=w.root/'generations/client-1'
  assert rt.files(profile/'mods')==w.baseline and not (profile/'saves').exists()
  assert case.before=={str(p.relative_to(w.source)):p.read_bytes() for p in w.source.rglob('*') if p.is_file()}
  command=w.command('JOIN');env=dict(os.environ,GENESIS_REMOTE70_ROOT=str(w.root),GENESIS_REMOTE70_RUN=w.id)
  check=subprocess.run([java,'-cp',classpath,'com.fixmer.genesis.technology.server.remote.RemoteReturnChecks119','reject-join'],cwd=profile,env=env,capture_output=True,text=True,timeout=15)
  assert check.returncode==0,check.stdout+check.stderr
  # Signed invalid mode and tampered inventory cannot turn recovery into admission.
  config=dict(w.config,return119='JOIN');write(w.root/'session70',w.key,config);write(profile/'remote70.marker',w.key,config)
  check=subprocess.run([java,'-cp',classpath,'com.fixmer.genesis.technology.server.remote.RemoteReturnChecks119','reject-join'],cwd=profile,env=env,capture_output=True,text=True,timeout=15)
  assert check.returncode!=0 and 'RETURN119_MODE' in check.stderr
  config=dict(w.config,inventory='0'*64);write(w.root/'session70',w.key,config);write(profile/'remote70.marker',w.key,config)
  check=subprocess.run([java,'-cp',classpath,'com.fixmer.genesis.technology.server.remote.RemoteReturnChecks119','reject-join'],cwd=profile,env=env,capture_output=True,text=True,timeout=15)
  assert check.returncode!=0 and 'REMOTE70_RUNTIME' in check.stderr
  config=dict(w.config);config.pop('return119');write(w.root/'session70',w.key,config);write(profile/'remote70.marker',w.key,config)
  check=subprocess.run([java,'-cp',classpath,'com.fixmer.genesis.technology.server.remote.RemoteReturnChecks119','normal'],cwd=profile,env=env,capture_output=True,text=True,timeout=15)
  assert check.returncode==0,check.stderr
  assert read(w.root/'client.status',w.key)['phase']=='READY'
  print('Return119 actual copied-profile/child-JVM/HMAC/menu/STOP/JOIN-denial/tamper checks passed; no Minecraft/Windows UI.')
 finally:
  if w.job:w.job.close()
  case.doCleanups()

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--classpath-file',type=Path,required=True);a=p.parse_args()
 result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(tests))
 if not result.wasSuccessful():raise SystemExit(1)
 entries=json.loads(a.classpath_file.read_text(encoding='utf-8-sig'));assert entries and all(isinstance(x,str) and Path(x).is_absolute() for x in entries)
 process(str(Path(a.java).absolute()),os.pathsep.join(entries))
if __name__=='__main__':main()
