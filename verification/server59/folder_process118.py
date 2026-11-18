"""Cross-language folder104 -> signed pair -> real control journal -> Changes104 -> target READY.
The game/Windows Job/save callbacks below are explicit fixtures, not Minecraft certification.
"""
from pathlib import Path
import argparse,contextlib,json,os,shutil,subprocess,sys,tempfile,threading,time,uuid,zipfile
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.inplace104 import Owner104,root_for
from first58.folder118 import initialize,settings118
from first58.bridge67 import read,write
from first58.common import inventory,sha
from first58 import automatic156,https78,publication83

def main():
 modes=('apply','cancel','changed','client-only','crash','world-crash','interrupted','staged-tamper','install-timeout','auto-grace','expired-offers')
 p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--classpath-file',type=Path,required=True);p.add_argument('--mode',choices=modes);a=p.parse_args();cp=os.pathsep.join(json.loads(a.classpath_file.read_text()));checks=0
 def ok(x):
  nonlocal checks
  checks+=1
  if not x:raise AssertionError('folder118 check '+str(checks))
 def call(action,base):subprocess.run([a.java,'-cp',cp,'com.fixmer.genesis.technology.server.remote.FolderChecks118',action,str(base)],check=True,timeout=30)
 def archive_core(app):
  # Use an actual compiled core JAR; never let fixture process discover project classes accidentally.
  target=app/'runtime/core.jar';target.parent.mkdir(parents=True)
  with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
   for item in cp.split(os.pathsep):
    path=Path(item)
    if path.is_dir():
     for file in path.rglob('*.class'):
      name=str(file.relative_to(path)).replace(os.sep,'/')
      if name.startswith('com/fixmer/genesis/technology/') and name not in z.namelist():z.write(file,name)
    elif path.suffix=='.jar':
     with zipfile.ZipFile(path) as source:
      for name in source.namelist():
       if name.startswith('com/fixmer/genesis/technology/') and name.endswith('.class') and name not in z.namelist():z.writestr(name,source.read(name))
  return target
 for mode in (a.mode,) if a.mode else modes:
  with tempfile.TemporaryDirectory(prefix='Genesis folder118 ') as tmp:
   base=Path(tmp);server=base/'server';server.mkdir();(server/'mods').mkdir();(server/'world').mkdir();(server/'world/level.dat').write_bytes(b'world remains in exactly this directory');(server/'server.properties').write_text('level-name=world\nserver-port=25565\nserver-ip=\nonline-mode=false\n');root=root_for(server);root.mkdir(parents=True);key=b'k'*32;(root/'owner74.key').write_bytes(key);app=base/'app';app.mkdir();archive_core(app)
   c=dict(argv=['game-is-a-fixture'],java=str(Path(a.java).resolve()),neoforge='21.1.264',minecraft='1.21.1',level='world',port='25565',bind='',auth='false')
   class Job:
    def __init__(self,channel,g):self.channel=channel;self.g=g;self.dead=False;self.done=set()
    def status(self):
     file=self.channel/f'server-{self.g}.command'
     if file.exists():
      d=read(file,key)
      if d['request'] not in self.done:
       self.done.add(d['request']);phase='COMMAND_ACCEPTED'
       if d['action']=='QUIESCE':phase='QUIESCED'
       if d['action'] in {'SHUTDOWN','SAVE_STOP'}:phase='STOPPED_SAVED';self.dead=True
       write(self.channel/f'server-{self.g}.status',key,{k:d[k] for k in ('schema','run','generation','inventory','sequence','request','round')}|dict(phase=phase,at=str(int(time.time()*1000))))
     return dict(rootExited=self.dead,active=0 if self.dead else 1,exitCode=0 if self.dead else None,logFailed=False)
    def close(self):ok(self.dead)
   starts=[]
   def factory(argv,cwd,env,log):
    channel=root/'runs'/env['GENESIS_INPLACE_LAUNCH'];g=int(env['GENESIS_DEDICATED74_GENERATION']);session=read(channel/f'server-{g}.session',key);starts.append(g)
    if g==2 and mode in {'crash','world-crash'}:
     if mode=='world-crash':(channel/'world104').write_bytes(b'world loading fixture')
     job=Job(channel,g);job.dead=True;return job
    write(channel/f'server-{g}.status',key,{k:session[k] for k in ('schema','run','generation','inventory')}|dict(sequence='0',request=str(uuid.UUID(int=0)),phase='READY',round='none',at=str(int(time.time()*1000))))
    return Job(channel,g)
   with patch('first58.inplace104.config',return_value=c),patch('first58.inplace104.old.stopped_world',side_effect=lambda *args:contextlib.nullcontext()):
    o=Owner104(server,app,Path(a.java),factory);o.rollout118=initialize(o);f=o.rollout118
    try:
     call('setup-client-only' if mode=='client-only' else 'setup',base);o.generation=0;o.start_game();ok(starts==[1]);ok(settings118(root,key)['profile']==str(server));old=(server/'mods/demo.jar').read_bytes();world=(server/'world/level.dat').read_bytes()
     if mode=='expired-offers':call('expire-offers',base)
     f.stage('r1',True);ok(read(root/'publication75',key)['label']=='r1')
     if mode=='expired-offers':
      try:f.handle('STAGE_AUTO','r2');raise AssertionError('expired candidate was staged')
      except ValueError as error:ok(str(error)=='REMOTE_OFFER_EXPIRED')
      ok(f.pending is None and read(root/'publication75',key)['label']=='r1')
      identity=root/'publisher73';releases=root/'releases'
      expired=publication83.verify_pair(app,root,releases/'r2',identity/'public.der',f.checkpoint,historical=True)
      job=str(uuid.uuid4())
      automatic156.store(root,key,job,dict(schema='156',job=job,run=o.run,phase='READY',label='r2',release=expired['release']))
      ok(automatic156.renew_ready_offer(app,root,job))
      made=automatic156.load(root,key,job)
      renewed=publication83.verify_pair(app,root,releases/made['label'],identity/'public.der',f.checkpoint)
      ok(renewed['release']==made['release'] and renewed['parent']==expired['release'])
      ok(renewed['files']==expired['files'])
      ok(inventory(server)==o.expected and (server/'world/level.dat').read_bytes()==world)
      continue
     tls={k:'' for k in https78.FIELDS};tls.update(schema='78',run=o.run,revision=str(uuid.uuid4()),enabled='true',public=str(root/'releases/r1/public'),cert=str(base/'cert'),key=str(base/'private'),bind='127.0.0.1',port='8443',endpoint='https://localhost:8443/',expires=str(int(time.time()*1000)+3600000));tls.update({k:'a'*64 for k in ('certSha','keySha','publicSeal','fingerprint')});write(root/'https78.cfg',key,tls);write(root/'https78.status',key,dict(schema='78',run=o.run,at=str(int(time.time()*1000)),state='RUNNING_TLS',code='',revision=tls['revision'],endpoint=tls['endpoint'],fingerprint=tls['fingerprint']))
     # The game/save fixtures do not own a real HTTPS port. Transport has its own TLS regression.
     def probe_fixture(project,java,action,endpoint,publisher):
      assert action=='probe'
      info=https78.public_info(project,java,root/'releases/r2/public')
      return dict(release=info['release'],signer=sha(publisher,256),archive=info['archive'][7:-4])
     with patch.object(https78,'java_check',side_effect=probe_fixture):
      if mode=='auto-grace':f.handle('STAGE_AUTO','r2')
      else:f.stage('r2')
     ok((server/'world/level.dat').read_bytes()==world);ok(f.pending['clientOnly']==(mode=='client-only'));ok((server/'mods/demo.jar').read_bytes()==old if mode!='client-only' else o.changes.load() is None)
     call('consent',base)
     if mode=='cancel':
      call('cancel',base);f.https.tick=lambda:None;ok(f.tick());ok(f.pending is None);ok(read(root/'publication75',key)['label']=='r1');ok((server/'mods/demo.jar').read_bytes()==old);continue
     if mode=='staged-tamper':
      transaction=o.changes.load();(transaction[2]/'after/demo.jar').write_bytes(b'changed after preview')
      try:f.verify_pending();raise AssertionError('changed staged bytes accepted')
      except ValueError as failed:ok(str(failed)=='FOLDER118_ARTIFACT_CHANGED')
      continue
     if mode=='changed':
      (server/'mods/demo.jar').write_bytes(b'foreign edit');
      try:f.verify_pending();raise AssertionError('foreign edits accepted')
      except ValueError:ok(True)
      call('unprepared',base);continue
     if mode=='auto-grace':
      ok(f.pending.get('autoCommit') is True)
      command=f.command
      def auto_command(action,*args,**kwargs):
       if action=='COMMIT':call('commit',base)
       return command(action,*args,**kwargs)
      f.https.tick=lambda:None
      with patch.object(f,'command',side_effect=auto_command) as sent:
       ok(f.tick());ok(any(c.args[0]=='COMMIT' for c in sent.call_args_list))
      f.next=0
      with patch('first58.folder118.INSTALL_WINDOW',2):
       ok(f.tick())
      ok(starts==[1,2]);ok(f.target_ready());ok(not f.state['admission'])
      with f.lock:call('install',base)
      end=time.monotonic()+15
      while not (f.fresh() and f.state.get('admission')):
       if time.monotonic()>end:raise AssertionError('admission did not open after client installation')
       time.sleep(.1)
      ok(f.state['installed']==3)
      continue
     call('commit',base);f.state=f.tool('inspect',root);f.at=time.monotonic()
     if mode=='install-timeout':
      with patch('first58.folder118.INSTALL_WINDOW',0):
       ok(f.activate())
      ok(starts==[1,2]);ok(f.pending is None);ok(o.changes.load() is None)
      ok((server/'mods/demo.jar').read_bytes()==old);ok((server/'world/level.dat').read_bytes()==world)
      ok(read(root/'publication75',key)['label']=='r1')
      ok(read(root/'rollout118',key)['phase']=='RESTORED')
      ok(not (root/'control73/active73').exists());ok(o.phase=='RUNNING' and not o.busy)
      continue
     if mode=='interrupted':
      o.receive=lambda:'SHUTDOWN'
      ok(not f.activate());ok(o.all_exited());f.close()
      recovered=Owner104(server,app,Path(a.java),factory);recovered.rollout118=initialize(recovered)
      try:
       recovered.run_loop();ok(recovered.phase=='RECOVERY_REQUIRED');ok(starts==[1]);ok((server/'mods/demo.jar').read_bytes()==old)
       from first58.folder118 import recover118
       recover118(app,server,a.java);ok(read(root/'rollout118',key)['phase']=='CLOSED');ok(read(root/'publication75',key)['label']=='r1');ok(not (root/'prepared74').exists())
      finally:recovered.rollout118.close()
      continue
     errors=[]
     def install_after_delegation():
      try:
       end=time.monotonic()+20
       while not f.fence:
        if time.monotonic()>end:raise AssertionError('no captured fence')
        time.sleep(.05)
       with f.lock:call('install',base)
      except Exception as failed:errors.append(failed)
     t=threading.Thread(target=install_after_delegation);t.start()
     if mode in {'crash','world-crash'}:
      try:f.activate();raise AssertionError('crash accepted')
      except ValueError as failed:restored=f.failed(failed)
      t.join(30);ok(not errors);ok(not (root/'admission82.local').exists())
      if mode=='crash':ok(restored);ok(starts==[1,2,3]);ok((server/'mods/demo.jar').read_bytes()==old);ok(read(root/'rollout118',key)['phase']=='RESTORED')
      else:ok(not restored);ok(o.phase=='RECOVERY_REQUIRED');ok((root/'review104').exists());ok(starts==[1,2])
      continue
     ok(f.activate());t.join(30);ok(not errors);ok(starts==[1,2]);ok((server/'world/level.dat').read_bytes()==world);ok(not (root/'generations').exists());ok(f.target_ready());ok(o.changes.load() is None)
     end=time.monotonic()+15
     while not (f.fresh() and f.state.get('admission')):
      if time.monotonic()>end:raise AssertionError('admission missing '+str(f.error))
      o.report();time.sleep(.1)
     ok(f.state['installed']==3 and f.state['needed']==1);end=time.monotonic()+5
     while not (root/'admission82.local').is_file():
      if time.monotonic()>end:raise AssertionError('no local admission')
      o.report();time.sleep(.1)
     ok(True);call("publish",base);ok((root/"bootstrap76.state").is_file());ok(any((root/"control73/public").glob("*.ready82")))
     with f.lock:call('joined',base)
     time.sleep(1.2);f.finish();ok(f.pending is None);ok(read(root/'rollout118',key)['phase']=='CLOSED')
    finally:
     f.close()
 print('Folder118 actual pair/Ed25519/HMAC/immutable-fence/CAS/source-channel/three-member/archive-apply checks passed:',checks,'; TLS readiness, game/save and native Job are explicit fixtures; no Minecraft/Windows runtime.')
if __name__=='__main__':main()
