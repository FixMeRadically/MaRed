"""Actual file-copy, original Worker and signed-channel tests; native/Minecraft facts are fixtures."""
from pathlib import Path
import copy,hashlib,json,os,secrets,shutil,sys,tempfile,unittest,uuid,zipfile
from types import SimpleNamespace
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import return119 as rt,remote70,control73,repeat71
from first58.common import inventory
from first58.bridge67 import read,write

class Lease:
 def __init__(self,closed=True,normal=True):self.c=closed;self.n=normal
 def closed(self):return self.c
 def normal(self):return self.n
class Job:
 def __init__(self,code=1,active=0,exited=True):self.closed=False;self.s=dict(rootExited=exited,active=active,exitCode=code,logFailed=False)
 def status(self):return dict(self.s)
 def close(self):self.closed=True

class Return119(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.base=Path(self.tmp.name);self.source=self.base/'source';(self.source/'mods').mkdir(parents=True)
  with zipfile.ZipFile(self.source/'mods/mared.jar','w') as z:z.writestr(rt.ADAPTER,b'adapter fixture')
  (self.source/'mods/test.jar').write_bytes(b'old code');(self.source/'saves/world').mkdir(parents=True);(self.source/'saves/world/level.dat').write_bytes(b'world not copied')
  (self.source/'config').mkdir();(self.source/'config/settings.txt').write_text('old settings')
  self.folder=self.base/'request';self.folder.mkdir();self.root=self.folder/'run';self.root.mkdir();(self.root/'generations/client-1/logs').mkdir(parents=True)
  (self.root/'client.log').write_text('failure --accessToken hidden-token\npassword=hidden-password\nBearer hidden-bearer')
  (self.root/'generations/client-1/logs/latest.log').write_text('loader error')
  self.id=str(uuid.uuid4());self.w=remote70.Worker.__new__(remote70.Worker)
  self.w.__dict__.update(folder=self.folder,root=self.root,source=self.source,id=self.id,account=str(uuid.uuid4()),r={'request':self.id,'neoforge':'21.1.264','authMode':'LOCAL_OFFLINE'},spec={'clientCommand':['java','--gameDir','{profile}','--quickPlayMultiplayer','localhost:25565']},info={'game':'localhost:25565'},lease=Lease(),job=Job(),key=secrets.token_bytes(32),baseline=rt.files(self.source/'mods'),approved={'bootstrap':{'mared.jar':'unused'},'delta':[]},seq=0,pending_command=None,phase='STARTING',failure_reason='',return_result119=None,return_attempt119=False,returning119=False,world_ready119=False,report_reserve87=False,gate73=SimpleNamespace())
  self.before={str(p.relative_to(self.source)):p.read_bytes() for p in self.source.rglob('*') if p.is_file()}
  self.messages=[]
 def cancelled(self):return False
 def start(self):
  self.w.job=Job(0,1,False)
  write(self.w.root/'client.status',self.w.key,dict(run=self.id,account=self.w.account,inventory=self.w.config['inventory'],phase='MENU_FRAME_READY',sequence='0',request=str(uuid.UUID(int=0))))
 def restore(self,error='REMOTE70_CHILD_ERROR'):
  with patch.object(self.w,'start',side_effect=self.start),patch.object(self.w,'watch') as watch:
   rt.restore_menu(self.w,ValueError(error),self.messages.append,lambda:False,lambda msg:False)
   self.assertEqual(watch.call_count,1)
 def test_restore_new_copy_and_report(self):
  oldjob=self.w.job;self.restore();self.assertTrue(oldjob.closed);profile=self.w.root/'generations/client-1';self.assertEqual(rt.files(profile/'mods'),self.w.baseline);self.assertFalse((profile/'saves').exists());self.assertEqual(self.before,{str(p.relative_to(self.source)):p.read_bytes() for p in self.source.rglob('*') if p.is_file()});self.assertEqual(self.w.config['return119'],'MENU_ONLY');self.assertNotIn('--quickPlayMultiplayer',self.w.spec['clientCommand']);self.assertIsNone(self.w.gate73);self.assertEqual(read(profile/'remote70.marker',self.w.key),self.w.config);self.assertIn('kind=hold91',(profile/'config/mared/dev/resume.properties').read_text());text=(profile/'config/mared/dev/failures101'/self.id/'console.log').read_text();self.assertNotIn('hidden-token',text);self.assertNotIn('hidden-password',text);self.assertNotIn('hidden-bearer',text);self.assertIn('loader error',text);self.assertEqual(json.loads((self.root/'return119.json').read_text())['status'],'RESTORED');self.assertIn('BASELINE_READY',self.messages)
 def test_unreadable_log_does_not_block_return(self):
  with patch.object(Path,'open',side_effect=PermissionError()):self.assertIn('unavailable',rt.tail(self.root/'client.log'))
 def test_crash_log_redacted(self):
  folder=self.root/'generations/client-1/crash-reports';folder.mkdir();(folder/'crash.txt').write_text('crash password=secret119');self.restore();text=(self.w.root/'generations/client-1/config/mared/dev/failures101'/self.id/'crash.log').read_text();self.assertIn('crash',text);self.assertNotIn('secret119',text)
 def test_sealed_owned_exit_survives_handle_close(self):
  job=Job(0);job.empty=lambda:True;lease=repeat71.OwnedLease71(job);lease.seal119();job.close();job.status=lambda:(_ for _ in ()).throw(RuntimeError('closed handle'));self.assertTrue(lease.closed());self.assertTrue(lease.normal())
 def test_live_owned_exit_cannot_be_sealed(self):
  job=Job(0,1,False);job.empty=lambda:False;lease=repeat71.OwnedLease71(job)
  with self.assertRaisesRegex(ValueError,'REMOTE70_SOURCE_EXIT'):lease.seal119()
 def test_repeated_return_hands_lifetime_to_owner(self):
  with patch.object(self.w,'start',side_effect=self.start),patch.object(self.w,'watch') as watch:
   rt.restore_menu(self.w,ValueError('REMOTE70_CHILD_ERROR'),self.messages.append,lambda:False,lambda msg:False,wait_lifetime=False);watch.assert_not_called()
 def test_actual_repeat_loop_returns_after_closed_source_handle(self):
  owner=repeat71.RepeatingWorker71.__new__(repeat71.RepeatingWorker71);owner.__dict__.update(self.w.__dict__)
  shutil.copytree(self.source,owner.root/'generations/client-1',dirs_exist_ok=True)
  owner.config=dict(run=owner.id,account=owner.account,inventory=inventory(owner.root/'generations/client-1'))
  owner.r.update(cache=str(self.base/'cache'),candidate=str(self.base/'candidate'));owner.info.update(distribution=str(uuid.uuid4()),endpoint='https://localhost:27691/')
  owner.job=Job(0,1,False);owner.job.empty=lambda:owner.job.s['rootExited'];owner.seen71=set();owner.active71=owner
  def stopped():owner.job.s.update(rootExited=True,active=0,exitCode=0)
  owner.stop=stopped
  offer=dict(schema='71',**owner.config,request=str(uuid.uuid4()),action='PREPARE',cache=owner.r['cache'],candidate=owner.r['candidate'])
  write(owner.root/'repeat71.request',owner.key,offer);cycle=repeat71.Cycle71(owner,offer)
  cycle.info=dict(owner.info,release=str(uuid.uuid4()));cycle.baseline=rt.files(cycle.source/'mods');cycle.approved=dict(bootstrap={'mared.jar':'fixture'},delta={})
  def prepare():cycle.root.mkdir(parents=True);(cycle.root/'generations').mkdir();cycle.state('READY')
  cycle.prepare=prepare;cycle.final_check=lambda:None;cycle.verify_remote=lambda:cycle.info;cycle.build=lambda:None
  def start():
   if not cycle.returning119:raise ValueError('REMOTE70_CHILD_ERROR')
   cycle.job=Job(0,1,False);write(cycle.root/'client.status',cycle.key,dict(run=cycle.id,account=cycle.account,inventory=cycle.config['inventory'],phase='MENU_FRAME_READY',sequence='0',request=str(uuid.UUID(int=0))))
  cycle.start=start;cycle.stop=lambda:None;owner.begin=lambda current,packet:cycle;cycle.cancelled=lambda:False
  def packet(current):return None if current is cycle else dict(offer,action='ARM' if hasattr(owner,'offered119') else 'PREPARE')
  def next_packet(current):
   value=packet(current);owner.offered119=True;return value
  with patch.object(repeat71,'packet',side_effect=next_packet),patch.object(repeat71.time,'sleep'):
   owner.watch(self.messages.append,lambda:bool(cycle.return_result119 and cycle.return_result119['status']=='RESTORED'),lambda msg:False)
  self.assertIs(owner.active71,cycle);self.assertTrue(cycle.lease.normal());self.assertTrue(owner.job is None);self.assertEqual(cycle.return_result119['status'],'RESTORED');self.assertEqual(cycle.config['return119'],'MENU_ONLY')
 def test_hung_owned_candidate_uses_retained_job_termination(self):
  self.w.job=Job(active=1);job=self.w.job;calls=[]
  def terminated():calls.append('owned-job');job.s.update(rootExited=True,active=0,exitCode=119)
  job.terminate_failed119=terminated;times=iter([0,31])
  with patch.object(rt.time,'monotonic',side_effect=lambda:next(times)):rt.quiet(self.w)
  self.assertEqual(calls,['owned-job']);self.assertTrue(job.closed);self.assertIsNone(self.w.job)
 def test_normal_source_required(self):
  for lease in (Lease(False,False),Lease(True,False)):
   self.w.lease=lease;self.assertFalse(rt.eligible(self.w,ValueError('REMOTE70_CHILD_ERROR'),lambda:False))
 def test_no_return_on_network_silence(self):self.assertFalse(rt.eligible(self.w,ValueError('CONTROL73_NETWORK'),lambda:False))
 def test_no_return_after_world(self):self.w.world_ready119=True;self.assertFalse(rt.eligible(self.w,ValueError('REMOTE70_CHILD_ERROR'),lambda:False))
 def test_no_return_on_user_exit(self):self.assertFalse(rt.eligible(self.w,ValueError('REMOTE70_CHILD_ERROR'),lambda:True))
 def test_server_cancel_returns_menu(self):self.assertTrue(rt.eligible(self.w,InterruptedError('CONTROL73_CANCELLED'),lambda:False));self.restore('CONTROL73_CANCELLED')
 def test_one_attempt(self):self.restore();self.assertFalse(rt.eligible(self.w,ValueError('REMOTE70_CHILD_ERROR'),lambda:False))
 def test_changed_source_refused(self):
  (self.source/'mods/test.jar').write_bytes(b'foreign edit')
  with self.assertRaisesRegex(ValueError,'REMOTE70_SOURCE_CHANGED'):self.restore()
  self.assertEqual(json.loads((self.root/'return119.json').read_text())['status'],'ACTION_REQUIRED')
 def test_unknown_adapter_refused(self):
  with patch.object(rt,'adapter',return_value=False):
   with self.assertRaisesRegex(ValueError,'RETURN119_ADAPTER_REQUIRED'):self.restore()
 def test_owned_descendants_must_exit(self):
  self.w.job=Job(active=1);times=iter([0,31])
  with patch.object(rt.time,'monotonic',side_effect=lambda:next(times)):
   with self.assertRaisesRegex(ValueError,'RETURN119_CHILD_BUSY'):rt.quiet(self.w)
  self.assertIsNotNone(self.w.job);self.assertFalse(self.w.job.closed)
 def test_exit_zero_before_ready_is_failure(self):
  self.w.job=Job(0)
  with self.assertRaisesRegex(ValueError,'REMOTE70_CHILD_ERROR'):self.w.wait(lambda:False,1,'timeout')
 def test_clean_stop_wait_allowed(self):
  self.w.job=Job(0);self.w.stopping119=True;self.assertTrue(self.w.wait(lambda:True,1,'timeout'))
 def test_installed_wait_detects_crash(self):
  gate=control73.Gate73.__new__(control73.Gate73);gate.w=self.w
  with self.assertRaisesRegex(ValueError,'REMOTE70_CHILD_ERROR'):gate.receipt('INSTALLED','ADMISSION')
 def test_invalid_quickplay_refused(self):
  with self.assertRaisesRegex(ValueError,'RETURN119_ARGUMENTS'):rt.menu_argv(['java','--server'])
 def test_equals_quickplay_removed(self):self.assertEqual(rt.menu_argv(['java','--server=localhost','--port=1','--gameDir','{profile}']),['java','--gameDir','{profile}'])
 def test_return_error_not_recursive(self):
  with patch.object(rt,'restore_menu',side_effect=ValueError('RETURN119_MENU_TIMEOUT')) as restore:
   with self.assertRaises(remote70.PilotFailure):self.w.return_or_fail119(ValueError('REMOTE70_CHILD_ERROR'),self.messages.append,lambda:False,lambda msg:False)
   self.assertEqual(restore.call_count,1);self.assertEqual(self.w.phase,'FAILED')

if __name__=='__main__':unittest.main(verbosity=2)
