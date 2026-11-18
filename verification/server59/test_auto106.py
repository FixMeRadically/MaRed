"""Real signed handoff records/files. Source HANDLE, Job and Minecraft callbacks are explicit fixtures."""
from pathlib import Path
import contextlib,json,sys,tempfile,time,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import auto106
from first58.bridge67 import read,write
from first58.common import inventory,sha
from first58.inplace104 import root_for
from first58.agent106 import OPTION,without_approved_agent
from first58.dedicated74 import validate_argv
class Auto106(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.server=Path(self.tmp.name)/'server';self.server.mkdir();(self.server/'mods').mkdir();self.root=root_for(self.server);self.root.mkdir(parents=True);self.key=b'x'*32;(self.root/'owner74.key').write_bytes(self.key);self.app=self.root/'app'/('a'*64);self.app.mkdir(parents=True);self.java=Path(self.tmp.name)/'java.exe';self.java.write_bytes(b'explicit fixture');self.ticket=str(uuid.uuid4());self.now=int(time.time()*1000)
  self.record=dict(schema='106',ticket=self.ticket,profile=str(self.server),app=str(self.app),java=str(self.java),pid='123',created=str(self.now-1000),inventory=inventory(self.server),issued=str(self.now),argv=json.dumps(['@user_jvm_args.txt','@libraries/net/neoforged/neoforge/21.1.264/win_args.txt']))
  self.config=dict(argv=[str(self.java),'@user_jvm_args.txt','@libraries/net/neoforged/neoforge/21.1.264/win_args.txt','nogui'])
  p=patch('first58.auto106.config',return_value=self.config);p.start();self.addCleanup(p.stop);self.save();self.source=Mock();self.source.__enter__=Mock(return_value=self.source);self.source.__exit__=Mock(return_value=False);self.source.exited.return_value=True;self.source.exit_code.return_value=0;self.created=[]
  self.owner=Mock();self.owner.phase='STOPPED';self.owner.run_loop.side_effect=lambda:self.owner.bootstrap()
 def save(self):write(self.root/'auto106.request',self.key,self.record)
 def release(self,**kw):
  d=dict(schema='106',ticket=self.ticket,profile=str(self.server),phase='RELEASED_FOR_HANDOFF',inventory=self.record['inventory']);d.update(kw);write(self.root/'auto106.release',self.key,d)
 def owner_factory(self,*args):self.created.append(args);return self.owner
 def handoff(self,**kwargs):auto106.handoff(self.server,self.app,self.java,self.ticket,source_factory=Mock(return_value=self.source),owner_factory=self.owner_factory,independent=Mock(),**kwargs)
 def test_success_new_owner_created_only_after_exit_and_release(self):
  self.release();self.handoff();self.assertEqual(len(self.created),1);self.owner.run_loop.assert_called_once();self.owner.close.assert_called_once();self.assertEqual(read(self.root/'auto106.answer',self.key)['result'],'WAITING_FOR_SOURCE_EXIT');self.source.__exit__.assert_called_once()
 def test_no_release_does_not_start_owner(self):
  with self.assertRaises(Exception):self.handoff()
  self.assertFalse(self.created)
 def test_wrong_release_ticket_refused(self):
  self.release(ticket=str(uuid.uuid4()))
  with self.assertRaisesRegex(ValueError,'NOT_RELEASED'):self.handoff()
  self.assertFalse(self.created)
 def test_source_failure_does_not_start_owner(self):
  self.release();self.source.exit_code.return_value=7
  with self.assertRaisesRegex(ValueError,'EXIT_FAILED'):self.handoff()
  self.assertFalse(self.created)
 def test_source_wait_timeout_never_kills_source(self):
  self.source.exited.return_value=False;clock=Mock(side_effect=[0,91])
  with self.assertRaisesRegex(ValueError,'EXIT_TIMEOUT'):self.handoff(clock=clock,sleep=Mock())
  self.assertFalse(self.created);self.source.terminate.assert_not_called();self.source.kill.assert_not_called()
 def test_cancel_releases_waiter_without_owner(self):
  self.source.exited.return_value=False;write(self.root/'auto106.cancel',self.key,dict(schema='106',ticket=self.ticket));self.handoff();self.assertFalse(self.created)
 def test_other_cancel_ticket_is_not_authority(self):
  self.source.exited.side_effect=[False,True];self.release();write(self.root/'auto106.cancel',self.key,dict(schema='106',ticket=str(uuid.uuid4())));self.handoff(sleep=Mock());self.assertEqual(len(self.created),1)
 def test_mod_change_after_source_exit_blocks_start(self):
  self.release()
  def altered(): (self.server/'mods/foreign.jar').write_bytes(b'foreign');return 0
  self.source.exit_code.side_effect=altered
  with self.assertRaisesRegex(ValueError,'MODS_CHANGED'):self.handoff()
  self.assertFalse(self.created)
 def test_native_identity_rejection_blocks_owner(self):
  with self.assertRaises(OSError):auto106.handoff(self.server,self.app,self.java,self.ticket,source_factory=Mock(side_effect=OSError('source identity refused')),owner_factory=self.owner_factory,independent=Mock())
  self.assertFalse(self.created)
 def test_expired_source_record_refused(self):
  with self.assertRaisesRegex(ValueError,'SOURCE_RECORD'):auto106.registration(self.server,self.app,self.java,self.ticket,self.now+60001)
 def test_future_source_record_refused(self):
  with self.assertRaisesRegex(ValueError,'SOURCE_RECORD'):auto106.registration(self.server,self.app,self.java,self.ticket,self.now-1)
 def test_pid_zero_refused(self):
  self.record['pid']='0';self.save()
  with self.assertRaisesRegex(ValueError,'SOURCE_RECORD'):auto106.registration(self.server,self.app,self.java,self.ticket)
 def test_extra_agent_cannot_hide_in_source_argv(self):
  self.record['argv']=json.dumps(['-javaagent:other.jar']);self.save()
  with self.assertRaisesRegex(ValueError,'SOURCE_ARGUMENTS'):auto106.registration(self.server,self.app,self.java,self.ticket)
 def test_hmac_tamper_blocks_handoff(self):
  (self.root/'auto106.request').write_bytes(b'forged')
  with self.assertRaises(Exception):self.handoff()
  self.assertFalse(self.created)
 def test_own_agent_pin_checks_both_files(self):
  boot=self.root/'boot106';boot.mkdir();(boot/'agent.jar').write_bytes(b'own agent');(boot/'core.jar').write_bytes(b'own core');write(self.root/'agent106',self.key,dict(schema='106',profile=str(self.server),sha=sha(boot/'agent.jar'),coreSha=sha(boot/'core.jar')))
  text='-Xmx2G\n'+OPTION+'\n';self.assertNotIn(OPTION,without_approved_agent(self.server,text));(boot/'core.jar').write_bytes(b'changed')
  with self.assertRaisesRegex(ValueError,'AGENT_BINDING'):without_approved_agent(self.server,text)
 def test_default_validator_still_refuses_all_agents(self):
  (self.server/'user_jvm_args.txt').write_text(OPTION)
  with self.assertRaisesRegex(ValueError,'ARGV_REDIRECT'):validate_argv([str(self.java),'@user_jvm_args.txt'],self.server)
 def test_duplicate_own_agent_refused(self):
  with self.assertRaisesRegex(ValueError,'DUPLICATE'):without_approved_agent(self.server,OPTION+'\n'+OPTION)

class StarterResult106(unittest.TestCase):
 def test_matching_live_owner_is_returned_with_warning_and_original_trace(self):
  import importlib.util
  path=Path(__file__).resolve().parents[2]/'tools/server104.py';spec=importlib.util.spec_from_file_location('starter106',path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
  with tempfile.TemporaryDirectory() as tmp:
   server=Path(tmp);root=root_for(server);root.mkdir(parents=True);(root/'logs').mkdir();(root/'owner74.key').write_bytes(b'x'*32);key=b'x'*32;app=server/'app';java=server/'java.exe';run=str(uuid.uuid4());ticket=str(uuid.uuid4())
   write(root/'start104',key,dict(schema='104',ticket=ticket,profile=str(server),app=str(app),java=str(java)));write(root/'start104.answer',key,dict(schema='104',ticket=ticket,result='STARTED',error='',run=run))
   with patch.object(module,'prepare',return_value=({},root)),patch.object(module,'start_locked',side_effect=PermissionError('sharing fixture')),patch.object(module,'current',return_value=dict(run=run,phase='RUNNING')):
    self.assertEqual(module.start(server,app,java)['phase'],'RUNNING')
   self.assertIn('sharing fixture',(root/'logs/helper-warning106.log').read_text())
 def test_unrelated_owner_does_not_turn_failure_into_success(self):
  import importlib.util
  path=Path(__file__).resolve().parents[2]/'tools/server104.py';spec=importlib.util.spec_from_file_location('starter106_bad',path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
  with tempfile.TemporaryDirectory() as tmp:
   server=Path(tmp);root=root_for(server);root.mkdir(parents=True);(root/'logs').mkdir();(root/'owner74.key').write_bytes(b'x'*32);app=server/'app';java=server/'java.exe';ticket=str(uuid.uuid4());key=b'x'*32
   write(root/'start104',key,dict(schema='104',ticket=ticket,profile=str(server),app=str(app),java=str(java)));write(root/'start104.answer',key,dict(schema='104',ticket=ticket,result='STARTED',error='',run=str(uuid.uuid4())))
   with patch.object(module,'prepare',return_value=({},root)),patch.object(module,'start_locked',side_effect=PermissionError('sharing fixture')),patch.object(module,'current',return_value=dict(run=str(uuid.uuid4()),phase='RUNNING')):
    with self.assertRaises(PermissionError):module.start(server,app,java)

class NativeSourcePolicy106(unittest.TestCase):
 """WinAPI seams only. This does not simulate or certify a Windows process exit."""
 def setUp(self):
  import types,first58.source106 as m
  self.m=m;self.k=Mock();self.k.OpenProcess.return_value=123;self.created=1800000000000;self.dead=False
  def times(handle,created,*rest):created._obj.value=(self.created+11644473600000)*10000;return 1
  def exit_code(handle,result):result._obj.value=0;return 1
  self.k.GetProcessTimes.side_effect=times;self.k.WaitForSingleObject.side_effect=lambda *args:0 if self.dead else 258;self.k.GetExitCodeProcess.side_effect=exit_code
  for p in [patch.object(m,'os',types.SimpleNamespace(name='nt')),patch.object(m.C,'WinDLL',return_value=self.k,create=True)]:p.start();self.addCleanup(p.stop)
 def test_handle_is_pinned_and_only_observed(self):
  with self.m.Source106(10,self.created) as source:
   self.assertFalse(source.exited());self.dead=True;self.assertTrue(source.exited());self.assertEqual(source.exit_code(),0)
  self.k.OpenProcess.assert_called_once_with(0x1000|0x100000,False,10);self.k.TerminateProcess.assert_not_called();self.k.AssignProcessToJobObject.assert_not_called();self.k.CloseHandle.assert_called_once_with(123)
 def test_creation_time_mismatch_closes_handle(self):
  with self.assertRaisesRegex(ValueError,'SOURCE_IDENTITY'):self.m.Source106(10,self.created-1)
  self.k.CloseHandle.assert_called_once_with(123);self.k.TerminateProcess.assert_not_called()
 def test_already_exited_source_cannot_acknowledge(self):
  self.dead=True
  with self.assertRaisesRegex(ValueError,'ALREADY_EXITED'):self.m.Source106(10,self.created)
  self.k.CloseHandle.assert_called_once_with(123)
 def test_live_source_cannot_produce_exit_code(self):
  with self.m.Source106(10,self.created) as source:
   with self.assertRaisesRegex(ValueError,'STILL_ACTIVE'):source.exit_code()

if __name__=='__main__':unittest.main()
