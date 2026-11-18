"""Real files/HMAC/changed-JAR recovery. Job, clock and READY are explicit fixtures; no Minecraft/Windows certification."""
from pathlib import Path
import sys,unittest,tempfile,zipfile,uuid,contextlib
from unittest.mock import patch,Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.changes104 import Changes104
from first58.inplace104 import Owner104,root_for,current,request,lease
from first58.bridge67 import read,write
from first58.common import inventory

def jar(p,body=b'v1',marker=False):
 with zipfile.ZipFile(p,'w') as z:
  z.writestr('META-INF/neoforge.mods.toml',b'modLoader="javafml"');z.writestr('data',body)
  if marker:z.writestr('com/fixmer/mared/technology/server/firstlaunch/InPlaceWorld104.class',b'explicit fixture')
 return p
from approval_fixture120 import stage as fixture_stage120

class Changes(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.server=Path(self.tmp.name)/'server';self.server.mkdir();(self.server/'mods').mkdir();(self.server/'world').mkdir();(self.server/'world/level.dat').write_bytes(b'WORLD MUST NOT CHANGE');self.root=root_for(self.server);self.root.mkdir(parents=True);self.key=b'k'*32;(self.root/'owner74.key').write_bytes(self.key);self.c=Changes104(self.server,self.root,self.key);self.a=jar(self.server/'mods/a.jar');self.b=jar(Path(self.tmp.name)/'b.jar',b'v2')
 def test_changed_bytes_only_and_world_unchanged(self):
  jar(self.server/'mods/unchanged.jar');fixture_stage120(self.c,{'a.jar':self.b});d,p,f=self.c.load();self.assertEqual(set(p['changes']),{'a.jar'});self.assertEqual({p.name for p in (f/'before').iterdir()},{'a.jar'});self.c.apply();self.assertEqual(self.a.read_bytes(),self.b.read_bytes());self.c.restore();self.c.finish(False);self.assertIsNone(self.c.load());self.assertEqual((self.server/'world/level.dat').read_bytes(),b'WORLD MUST NOT CHANGE');self.assertFalse((self.root/'generations').exists())
 def test_add_remove_restore(self):
  old=self.a.read_bytes();fixture_stage120(self.c,{'new.jar':self.b},['a.jar']);self.c.apply();self.assertFalse(self.a.exists());self.c.restore();self.assertEqual(self.a.read_bytes(),old);self.assertFalse((self.server/'mods/new.jar').exists())
 def test_partial_apply_restores(self):
  old=self.a.read_bytes();fixture_stage120(self.c,{'a.jar':self.b,'new.jar':self.b});self.a.write_bytes(self.b.read_bytes());self.c.restore();self.assertEqual(self.a.read_bytes(),old);self.assertFalse((self.server/'mods/new.jar').exists())
 def test_apply_idempotent(self):fixture_stage120(self.c,{'a.jar':self.b});self.c.apply();self.c.apply();self.c.finish(True);self.assertIsNone(self.c.load())
 def test_same_bytes_no_journal(self):self.assertIsNone(fixture_stage120(self.c,{'a.jar':self.a}));self.assertIsNone(self.c.load())
 def test_pending_rejected(self):
  fixture_stage120(self.c,{'a.jar':self.b})
  with self.assertRaisesRegex(ValueError,'PENDING'):fixture_stage120(self.c,{'a.jar':self.b})
 def test_foreign_mod_blocks_all_writes(self):
  old=self.a.read_bytes();fixture_stage120(self.c,{'a.jar':self.b});jar(self.server/'mods/foreign.jar')
  with self.assertRaisesRegex(ValueError,'FOREIGN'):self.c.apply()
  self.assertEqual(self.a.read_bytes(),old)
 def test_foreign_target_blocks_restore(self):
  fixture_stage120(self.c,{'a.jar':self.b});self.c.apply();self.a.write_bytes(b'foreign')
  with self.assertRaisesRegex(ValueError,'FOREIGN'):self.c.restore()
  self.assertEqual(self.a.read_bytes(),b'foreign')
 def test_saved_bytes_tamper_rejected(self):
  fixture_stage120(self.c,{'a.jar':self.b});self.c.apply();f=self.c.load()[2];(f/'before/a.jar').write_bytes(b'broken')
  with self.assertRaisesRegex(ValueError,'BYTES'):self.c.restore()
 def test_plan_tamper_rejected(self):
  fixture_stage120(self.c,{'a.jar':self.b});f=self.c.load()[2];(f/'plan.json').write_text('{}')
  with self.assertRaisesRegex(ValueError,'PLAN'):self.c.load()
 def test_hmac_tamper_rejected(self):
  fixture_stage120(self.c,{'a.jar':self.b});(self.root/'change104').write_bytes(b'forged')
  with self.assertRaises(Exception):self.c.load()
 def test_traversal_rejected(self):
  for name in ('../a.jar','/tmp/a.jar','x\\a.jar','UPPER.JAR','a:bad.jar'):
   with self.assertRaises(ValueError):fixture_stage120(self.c,{name:self.b})
 def test_non_mod_rejected(self):
  with zipfile.ZipFile(self.b,'w') as z:z.writestr('plain','no metadata')
  with self.assertRaisesRegex(ValueError,'METADATA'):fixture_stage120(self.c,{'a.jar':self.b})
 def test_case_collision_rejected(self):
  jar(self.server/'mods/A.jar')
  if len(list((self.server/'mods').glob('*.jar')))==1:self.skipTest('Filesystem maps A.jar and a.jar to the same file; cannot create the collision fixture')
  with self.assertRaisesRegex(ValueError,'CASE'):self.c.inventory()
 def test_case_only_rename_rejected_before_staging(self):
  with self.assertRaisesRegex(ValueError,'SERVER104_MOD_CASE_COLLISION'):
   self.c.review({'A.jar':self.b},['a.jar'])
  self.assertIsNone(self.c.load())
 def test_failed_stage_can_retry_same_approval(self):
  updates={'a.jar':self.b};approval=self.c.approve(self.c.review(updates))
  with patch('first58.changes104.shutil.copyfile',side_effect=OSError('fixture disk error')):
   with self.assertRaises(OSError):self.c.stage(updates,approval=approval)
  self.assertFalse((self.root/'changes'/approval['transaction']).exists())
  self.assertIsNone(self.c.load())
  self.assertEqual(self.c.stage(updates,approval=approval),approval['transaction'])
 def test_inventory_rejects_noncanonical_extension(self):
  jar(self.server/'mods/extra.JAR')
  with self.assertRaisesRegex(ValueError,'Unsafe mod name'):inventory(self.server)
 def test_terminal_clear_after_crash(self):
  fixture_stage120(self.c,{'a.jar':self.b});self.c.apply();d=self.c.load()[0];self.c.phase(d,'COMMITTED');self.assertTrue(self.c.clear_terminal());self.assertIsNone(self.c.load())
 def test_unconfirmed_commit_rejected(self):
  fixture_stage120(self.c,{'a.jar':self.b})
  with self.assertRaisesRegex(ValueError,'CONFIRM'):self.c.finish(True)

class Owner(Changes):
 # Inherited transaction tests also exercise the owner-shaped directory.
 def setUp(self):
  super().setUp();self.app=Path(self.tmp.name)/'app';self.app.mkdir();jar(self.app/'mared.jar',marker=True)
  config=dict(argv=['fixture-java'],java='fixture-java',neoforge='21.1.264',minecraft='1.21.1',level='world',port='25565',bind='',auth='false')
  p=patch('first58.inplace104.config',return_value=config);p.start();self.addCleanup(p.stop)
  self.owner=Owner104(self.server,self.app,Path('unused'));self.o=self.owner
  self.idle=patch('first58.inplace104.old.stopped_world',side_effect=lambda *a:contextlib.nullcontext());self.idle.start();self.addCleanup(self.idle.stop)
 def job(self,**kwargs):
  d=dict(rootExited=False,active=1,exitCode=None,logFailed=False);d.update(kwargs);j=Mock();j.status.return_value=d;self.o.job=j;return j
 def begin(self):
  self.o.channel=self.root/'runs'/str(uuid.uuid4());self.o.channel.mkdir();self.o.generation=1;self.o.expected=inventory(self.server);return self.job()
 def status(self,**kw):
  d=dict(schema='74',run=self.o.run,generation=str(self.o.generation),inventory=self.o.expected,sequence='0',request=str(uuid.uuid4()),phase='READY',round='none',at='1');d.update(kw);write(self.o.channel/f'server-{self.o.generation}.status',self.key,d);return d
 def test_ready_uses_actual_folder_fresh_channels(self):
  calls=[]
  def factory(argv,cwd,env,log):
   self.assertEqual(cwd,self.server);self.assertEqual(env['GENESIS_INPLACE_VERSION'],'104');calls.append(env['GENESIS_INPLACE_LAUNCH']);self.status();return self.job()
  self.o.job_factory=factory
  with patch('first58.inplace104.old.environment',return_value={}):
   self.o.start_game();self.o.job.status.return_value=dict(rootExited=True,active=0,exitCode=0,logFailed=False);self.o.start_game()
  self.assertEqual(len(set(calls)),2);self.assertEqual(self.o.generation,2);self.assertFalse((self.root/'generations').exists());self.assertEqual(current(self.server)['phase'],'RUNNING')
 def test_startup_error_receipt_survives_process_exit_race(self):
  self.o.job_factory=lambda *args:self.job(rootExited=True,active=0,exitCode=1)
  self.o.receipt=Mock(side_effect=[None,{'phase':'ERROR'}])
  with patch('first58.inplace104.old.environment',return_value={}):
   with self.assertRaisesRegex(ValueError,'SERVER104_RUNTIME_ERROR'):self.o.start_game()
  self.assertEqual(self.o.receipt.call_count,2)

 def test_live_process_with_log_failure_fails_before_timeout(self):
  self.o.job_factory=lambda *args:self.job(logFailed=True)
  with patch('first58.inplace104.old.environment',return_value={}),patch('first58.inplace104.time.sleep') as sleep:
   with self.assertRaisesRegex(ValueError,'SERVER104_STARTUP_EXIT'):self.o.start_game()
  sleep.assert_not_called()

 def test_ordinary_stop_uses_shutdown_matching_save(self):
  j=self.begin()
  def receipt():
   cmd=read(self.o.channel/'server-1.command',self.key);self.assertEqual(cmd['action'],'SHUTDOWN');j.status.return_value=dict(rootExited=True,active=0,exitCode=0,logFailed=False);return dict(cmd,phase='STOPPED_SAVED')
  self.o.receipt=receipt;self.o.stop_game();j.close.assert_called_once();self.assertIsNone(self.o.job)
 def test_ordinary_restart_is_distinct_from_shutdown_and_retains_saved_receipt(self):
  j=self.begin()
  def receipt():
   cmd=read(self.o.channel/'server-1.command',self.key);self.assertEqual(cmd['action'],'RESTART125');j.status.return_value=dict(rootExited=True,active=0,exitCode=0,logFailed=False);return dict(cmd,phase='STOPPED_SAVED')
  self.o.receipt=receipt;self.o.stop_game('RESTART125');j.close.assert_called_once();self.assertIsNone(self.o.job)
 def test_wrong_save_nonce_blocks_restart(self):
  j=self.begin()
  def receipt():
   c=read(self.o.channel/'server-1.command',self.key);j.status.return_value=dict(rootExited=True,active=0,exitCode=0,logFailed=False);return dict(c,phase='STOPPED_SAVED',request=str(uuid.uuid4()))
  self.o.receipt=receipt
  with self.assertRaisesRegex(ValueError,'SAVE_UNCONFIRMED'):self.o.stop_game()
 def test_live_descendant_prevents_reap(self):
  j=self.job(rootExited=True,active=1,exitCode=0)
  with self.assertRaisesRegex(ValueError,'STILL_ACTIVE'):self.o.reap()
  j.close.assert_not_called()
 def test_crashed_tree_can_be_reaped(self):j=self.job(rootExited=True,active=0,exitCode=5);self.o.reap();j.close.assert_called_once()
 def test_receipt_wrong_profile_generation_rejected(self):
  self.begin();self.status(generation='2')
  with self.assertRaisesRegex(ValueError,'BINDING'):self.o.receipt()
 def test_request_and_replay(self):
  self.o.phase='RUNNING';self.o.report();request(self.server,'RESTART');self.assertEqual(self.o.receive(),'RESTART');self.assertIsNone(self.o.receive())
 def test_pending_request_not_overwritten(self):
  self.o.phase='RUNNING';self.o.report();request(self.server,'RESTART')
  with self.assertRaisesRegex(ValueError,'PENDING'):request(self.server,'SHUTDOWN')
 def test_wrong_generation_request_rejected(self):
  self.o.phase='RUNNING';self.o.report();request(self.server,'RESTART');p=self.root/'request74';d=read(p,self.key);d['generation']='999';write(p,self.key,d)
  with self.assertRaisesRegex(ValueError,'REQUEST'):self.o.receive()
 def test_revoked_command_is_acknowledged_without_action(self):
  self.o.phase='RUNNING';self.o.report();request(self.server,'RESTART')
  with patch('first58.inplace104.access88.consume',side_effect=ValueError('ACCESS88_REVOKED_OR_EXPIRED')):
   with self.assertRaises(ValueError):self.o.receive()
  self.assertIsNone(self.o.receive());request(self.server,'SHUTDOWN');self.assertEqual(self.o.receive(),'SHUTDOWN')
 def test_busy_access_lock_does_not_acknowledge_or_consume_request(self):
  from first58.access88 import AccessBusy88
  self.o.report('RUNNING');request(self.server,'RESTART')
  with patch('first58.inplace104.access88.consume',side_effect=AccessBusy88('busy')):
   with self.assertRaises(AccessBusy88):self.o.receive()
  self.assertFalse((self.root/'request74.ack').exists())
  self.assertEqual(self.o.receive(),'RESTART')
 def test_request_limit_still_permits_graceful_shutdown(self):
  self.o.report('RUNNING');self.o.seen=set(range(8192));request(self.server,'SHUTDOWN')
  self.assertEqual(self.o.receive(),'SHUTDOWN')
 def test_shut_down_is_not_blocked_by_rollout_tick_error(self):
  self.o.start_game=Mock(side_effect=self.begin);self.o.stop_game=Mock()
  rollout=Mock();rollout.view.return_value={};rollout.tick.side_effect=ValueError('broken periodic task')
  self.o.rollout118=rollout;self.o.receive=Mock(return_value='SHUTDOWN')
  self.o.run_loop()
  self.o.stop_game.assert_called_once_with('SHUTDOWN')
  rollout.tick.assert_not_called()
 def test_client_only_recovery_uses_durable_activation(self):
  from first58.common import sha
  from first58.folder118 import recover118
  mods=inventory(self.server)
  binding=self.root/'rollout118.binding'
  write(binding,self.key,dict(schema='118',run=self.o.run,label='next',priorLabel='prior',
   release='new-release',priorRelease='old-release',oldInventory=mods,newInventory=mods))
  for phase,label,release in [('ACTIVE_UPDATED','next','new-release'),('INTERRUPTED','prior','old-release')]:
   with self.subTest(phase=phase):
    write(self.root/'rollout118',self.key,dict(schema='118',run=self.o.run,label='next',
     phase=phase,binding=sha(binding,16384)))
    with patch('first58.folder118.base.core',return_value={'release':release}) as core,patch('first58.folder118.policy',return_value={'before':{},'after':{}}):
     recover118(self.app,self.server,'java')
    self.assertEqual(core.call_args.args[3],self.root/'releases'/label)
    self.assertEqual(read(self.root/'publication75',self.key)['label'],label)
 def test_state_owner_binding(self):
  self.o.report();d=read(self.root/'owner104',self.key);d['profile']=str(self.app);write(self.root/'owner104',self.key,d)
  with self.assertRaisesRegex(ValueError,'BINDING'):current(self.server)
 def test_review_blocks_bootstrap(self):
  write(self.root/'review104',self.key,dict(schema='104',reason='fixture'));self.o.bootstrap=Mock();self.o.run_loop();self.o.bootstrap.assert_not_called();self.assertEqual(self.o.phase,'RECOVERY_REQUIRED')
 def test_interrupted_change_restored_without_start(self):
  old=self.a.read_bytes();fixture_stage120(self.c,{'a.jar':self.b});self.c.apply();self.o.start_game=Mock();self.o.run_loop();self.assertEqual(self.a.read_bytes(),old);self.assertEqual(self.o.phase,'RECOVERY_REQUIRED');self.o.start_game.assert_not_called()
 def test_interrupted_restore_pointer_requires_review(self):
  fixture_stage120(self.c,{'a.jar':self.b});self.c.apply();self.c.restore();self.o.bootstrap=Mock();self.o.run_loop();self.o.bootstrap.assert_not_called();self.assertEqual(self.o.phase,'RECOVERY_REQUIRED');self.assertIsNone(self.c.load())
 def test_bootstrap_apply_failure_is_restored_immediately(self):
  old=self.a.read_bytes()
  def broken():
   fixture_stage120(self.c,{'a.jar':self.b});self.c.apply();raise OSError('injected disk failure after apply')
  self.o.bootstrap=broken
  with self.assertRaises(OSError):self.o.run_loop()
  self.assertEqual(self.a.read_bytes(),old);self.assertEqual(self.o.phase,'BOOTSTRAP_FAILED_RESTORED')
 def test_hold_retains_job_even_when_status_disk_write_fails(self):
  j=self.job();live=dict(rootExited=False,active=1,exitCode=None);dead=dict(rootExited=True,active=0,exitCode=0);j.status.side_effect=[live,live,dead];self.o.report=Mock(side_effect=OSError('disk full'));self.o.receive=Mock(return_value=None)
  with patch('first58.inplace104.time.sleep'):self.o.hold_live('SERVER104_FIXTURE')
  j.close.assert_not_called();self.assertEqual(self.o.report.call_count,2)
 def test_failed_bootstrap_restores_jar(self):
  # Existing Genesis JAR identification is actual ZIP metadata, not a mocked scan.
  with zipfile.ZipFile(self.a,'a') as z:z.writestr('com/fixmer/mared/Mared.class',b'old')
  old=self.a.read_bytes();self.o.start_game=Mock(side_effect=ValueError('SERVER104_STARTUP_EXIT'))
  with self.assertRaises(ValueError):self.o.run_loop()
  self.assertEqual(self.a.read_bytes(),old);self.assertEqual(self.o.phase,'BOOTSTRAP_FAILED_RESTORED')
 def test_failed_after_world_marks_review(self):
  def fail():
   self.o.channel=self.root/'runs'/str(uuid.uuid4());self.o.channel.mkdir();(self.o.channel/'world104').write_bytes(b'fixture marker');raise ValueError('SERVER104_STARTUP_EXIT')
  self.o.start_game=fail
  with self.assertRaises(ValueError):self.o.run_loop()
  self.assertEqual(self.o.phase,'RECOVERY_REQUIRED');self.assertTrue((self.root/'review104').exists())
 def test_unknown_exit_reports_failure(self):
  self.o.bootstrap=Mock();self.o.start_game=lambda:self.job(rootExited=True,active=0,exitCode=7);self.o.run_loop();self.assertEqual(self.o.phase,'FAILED')

class Configuration(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.server=Path(self.tmp.name);self.java=self.server/'java.exe';self.java.write_bytes(b'fixture');(self.server/'eula.txt').write_text('eula=true\n');self.runtime('21.1.264')
  p=patch('first58.inplace104.subprocess.run',return_value=Mock(returncode=0,stdout='',stderr='openjdk version "21.0.12"'));p.start();self.addCleanup(p.stop)
  (self.server/'user_jvm_args.txt').write_text('# JVM settings\n')
  (self.server/'run.bat').write_text('java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.264/win_args.txt %*\npause\n')
 def runtime(self,version):
  p=self.server/'libraries/net/neoforged/neoforge'/version/'win_args.txt';p.parent.mkdir(parents=True);p.write_text('--launchTarget neoforgeserver');return p
 def configure(self):
  from first58.inplace104 import config
  return config(self.server,self.java)
 def test_single_installed_runtime_detected(self):self.assertEqual(self.configure()['neoforge'],'21.1.264')
 def test_multiple_versions_use_existing_run_bat_reference(self):
  self.runtime('21.1.263');(self.server/'run.bat').write_text('java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.264/win_args.txt %*');self.assertEqual(self.configure()['neoforge'],'21.1.264')
 def test_windows_backslash_reference(self):
  self.runtime('21.1.263');(self.server/'run.bat').write_text(r'java @user_jvm_args.txt @libraries\net\neoforged\neoforge\21.1.264\win_args.txt');self.assertEqual(self.configure()['neoforge'],'21.1.264')
 def test_ambiguous_runtime_refused_without_launch(self):
  self.runtime('21.1.263')
  (self.server/'run.bat').unlink()
  with self.assertRaisesRegex(ValueError,'ONE_NEOFORGE_RUNTIME'):self.configure()
 def test_eula_not_accepted_by_tool(self):
  (self.server/'eula.txt').write_text('eula=false')
  with self.assertRaisesRegex(ValueError,'ACCEPT_EULA'):self.configure()
  self.assertEqual((self.server/'eula.txt').read_text(),'eula=false')

class IndependentLaunchPolicy(unittest.TestCase):
 """API seams, not a Windows kernel test. Actual acceptance is serverNativeChecks104."""
 def setUp(self):
  import types,first58.independent104 as mod
  self.m=mod;self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.cwd=Path(self.tmp.name);self.exe=self.cwd/'python.exe';self.exe.write_bytes(b'fixture');self.k=Mock();self.k.GetCurrentProcess.return_value=123;self.u=Mock();self.u.GetShellWindow.return_value=0;self.a=Mock();self.inside=False
  def member(process,job,out):out._obj.value=self.inside;return 1
  self.k.IsProcessInJob.side_effect=member
  for p in [patch.object(mod,'os',types.SimpleNamespace(name='nt',environ={'SYSTEMROOT':'C:\\Windows','SECRET_TOKEN':'not inherited'})),patch.object(mod.C,'WinDLL',side_effect=lambda name,**kw:{'kernel32':self.k,'user32':self.u,'advapi32':self.a}[name],create=True)]:p.start();self.addCleanup(p.stop)
 def test_outside_job_uses_no_window_and_no_handles(self):
  with patch.object(self.m.subprocess,'Popen') as start:
   start.return_value.pid=42;self.assertEqual(self.m.spawn([str(self.exe),'owner'],self.cwd),42);kw=start.call_args.kwargs;self.assertTrue(kw['close_fds']);self.assertEqual(kw['creationflags'],0x08000000);self.assertNotIn('SECRET_TOKEN',kw['env'])
 def test_permitted_breakaway_does_not_modify_job(self):
  self.inside=True
  with patch.object(self.m.subprocess,'Popen') as start:
   start.return_value.pid=43;self.assertEqual(self.m.spawn([str(self.exe),'owner'],self.cwd),43);self.assertEqual(start.call_args.kwargs['creationflags'],0x09000000);self.k.SetInformationJobObject.assert_not_called()
 def test_no_desktop_refuses_after_breakaway_denied(self):
  self.inside=True
  with patch.object(self.m.subprocess,'Popen',side_effect=OSError('breakaway denied')):
   with self.assertRaisesRegex(ValueError,'DESKTOP_LAUNCH_UNAVAILABLE'):self.m.spawn([str(self.exe),'owner'],self.cwd)
  self.k.OpenProcess.assert_not_called();self.k.TerminateProcess.assert_not_called()
 def test_non_python_executable_refused(self):
  self.exe=self.cwd/'java.exe';self.exe.write_bytes(b'fixture')
  with patch.object(self.m.subprocess,'Popen') as start:
   with self.assertRaisesRegex(ValueError,'ARGUMENTS'):self.m.spawn([str(self.exe)],self.cwd)
   start.assert_not_called()
 def test_control_characters_refused(self):
  with patch.object(self.m.subprocess,'Popen') as start:
   with self.assertRaisesRegex(ValueError,'ARGUMENTS'):self.m.spawn([str(self.exe),'bad\ncommand'],self.cwd)
   start.assert_not_called()


class CompletedRolloutShutdown(unittest.TestCase):
 def setUp(self):
  from first58.folder118 import FolderRollout118
  self.rollout=FolderRollout118.__new__(FolderRollout118)
  self.rollout.target=True
  self.rollout.closing=False
  self.rollout.fence={'total':'2'}
  self.rollout.state={'admission':True,'joined':2}
  self.rollout.fresh=Mock(return_value=True)
  self.rollout.target_ready=Mock(return_value=True)
  self.rollout.finish=Mock()

 def test_shut_down_closes_round_with_all_joined(self):
  self.rollout.finish_completed()
  self.rollout.finish.assert_called_once_with()

 def test_unconfirmed_round_is_retained_for_recovery(self):
  cases=(('joined',1),('admission',False))
  for key,value in cases:
   with self.subTest(key=key),patch.dict(self.rollout.state,{key:value}):
    self.rollout.finish_completed()
  self.rollout.fresh.return_value=False
  self.rollout.finish_completed()
  self.rollout.fresh.return_value=True
  self.rollout.target_ready.return_value=False
  self.rollout.finish_completed()
  self.rollout.finish.assert_not_called()

 def test_close_can_be_retried_after_the_pump_is_stopped(self):
  self.rollout.closing=True
  self.rollout.fresh.return_value=False
  self.rollout.finish_completed()
  self.rollout.finish.assert_called_once_with()

if __name__=='__main__':unittest.main()
