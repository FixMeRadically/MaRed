"""Real files/HMAC with injected Job/READY and configuration boundaries. No Minecraft/Windows certification."""
from pathlib import Path
import contextlib,importlib.util,io,sys,unittest
from unittest.mock import Mock,patch
import test_inplace104 as previous
from first58.bridge67 import read,write
from first58.common import inventory

class EndFixture(RuntimeError):pass
class Controller105(unittest.TestCase):
 def setUp(self):
  self.base=previous.Owner('test_review_blocks_bootstrap');self.base.setUp();self.addCleanup(self.base.doCleanups);self.o=self.base.o;self.root=self.base.root;self.server=self.base.server;self.key=self.base.key
 def session(self):
  self.o.expected=inventory(self.server);self.o.phase='RUNNING';self.o.job=Mock();self.o.job.status.return_value=dict(rootExited=False,active=1,exitCode=None,logFailed=False)
 def run_until_next_receive(self,action='RESTART'):
  self.o.bootstrap=Mock();self.o.start_game=self.session;self.o.receive=Mock(side_effect=[action,EndFixture()])
  with patch('first58.inplace104.time.sleep'),self.assertRaises(EndFixture):self.o.run_loop()
 def test_bad_restart_config_keeps_running_server(self):
  self.o.stop_game=Mock()
  with patch('first58.inplace104.config',side_effect=ValueError('SERVER104_ACCEPT_EULA')):self.run_until_next_receive()
  self.o.stop_game.assert_not_called();self.assertEqual(self.o.phase,'RUNNING');self.assertEqual(self.o.request_result,'REJECTED');self.assertEqual(self.o.error,'SERVER104_ACCEPT_EULA')
 def test_new_configuration_used_after_confirmed_save(self):
  calls=[];old=dict(self.o.c);new=dict(old,port='25566');self.o.bootstrap=Mock()
  def start():
   calls.append(dict(self.o.c));self.session()
   if len(calls)==2:self.o.job.status.return_value=dict(rootExited=True,active=0,exitCode=0,logFailed=False)
  self.o.start_game=start;self.o.receive=Mock(return_value='RESTART');self.o.stop_game=Mock(side_effect=lambda:setattr(self.o,'job',None))
  with patch('first58.inplace104.config',return_value=new),patch('first58.inplace104.time.sleep'):self.o.run_loop()
  self.assertEqual([c['port'] for c in calls],['25565','25566']);self.assertEqual(self.o.request_result,'COMPLETED');self.assertEqual((self.server/'world/level.dat').read_bytes(),b'WORLD MUST NOT CHANGE')
 def test_mod_change_before_stop_is_rejected(self):
  self.o.bootstrap=Mock();self.o.stop_game=Mock()
  def start():self.session();previous.jar(self.base.a,b'foreign')
  self.o.start_game=start;self.o.receive=Mock(side_effect=['RESTART',EndFixture()])
  with patch('first58.inplace104.time.sleep'),self.assertRaises(EndFixture):self.o.run_loop()
  self.o.stop_game.assert_not_called();self.assertEqual(self.o.error,'SERVER105_MODS_CHANGED');self.assertEqual(self.o.phase,'RUNNING')
 def test_mod_change_after_save_does_not_start_unknown_code(self):
  self.o.bootstrap=Mock();self.o.start_game=Mock(side_effect=self.session);self.o.receive=Mock(return_value='RESTART')
  def stop():self.o.job=None;previous.jar(self.base.a,b'changed after stop')
  self.o.stop_game=stop
  with patch('first58.inplace104.time.sleep'):self.o.run_loop()
  self.assertEqual(self.o.start_game.call_count,1);self.assertEqual(self.o.phase,'FAILED');self.assertEqual(self.o.error,'SERVER105_MODS_CHANGED')
 def test_review_marker_does_not_block_retry_of_pending_restore(self):
  old=self.base.a.read_bytes();self.base.c.stage({'a.jar':self.base.b});self.base.c.apply();write(self.root/'review104',self.key,dict(schema='104',reason='INTERRUPTED_CHANGE'));self.o.start_game=Mock();self.o.run_loop()
  self.assertEqual(self.base.a.read_bytes(),old);self.assertIsNone(self.base.c.load());self.o.start_game.assert_not_called();self.assertEqual(self.o.phase,'RECOVERY_REQUIRED')
 def test_review_marker_and_restored_pointer_finish_safely(self):
  self.base.c.stage({'a.jar':self.base.b});self.base.c.apply();self.base.c.restore();write(self.root/'review104',self.key,dict(schema='104',reason='INTERRUPTED_CHANGE'));self.o.run_loop();self.assertIsNone(self.base.c.load());self.assertEqual(self.o.phase,'RECOVERY_REQUIRED')
 def cli(self):
  p=Path(__file__).resolve().parents[2]/'tools/server104.py';spec=importlib.util.spec_from_file_location('cli105',p);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
 def ack(self):
  self.o.report('RECOVERY_REQUIRED');write(self.root/'review104',self.key,dict(schema='104',reason='WORLD_MAY_HAVE_CHANGED'));java=self.server/'java.exe';java.write_bytes(b'explicit fixture')
  m=self.cli()
  with patch.object(sys,'argv',['server104.py',str(self.base.app),'acknowledge',str(self.server),'--java',str(java)]),contextlib.redirect_stdout(io.StringIO()):m.main()
 def test_python_acknowledgement_unblocks_start_state(self):
  self.ack();self.assertEqual(read(self.root/'state104',self.key)['phase'],'STOPPED');self.assertFalse((self.root/'review104').exists())
 def test_python_acknowledgement_does_not_ignore_pending_changes(self):
  self.base.c.stage({'a.jar':self.base.b})
  with self.assertRaisesRegex(ValueError,'RECOVERY_PENDING'):self.ack()
  self.assertTrue((self.root/'review104').exists());self.assertIsNotNone(self.base.c.load())
 def test_signed_outcome_identifies_rejected_remote_request(self):
  from first58.inplace104 import request
  self.o.report('RUNNING');request(self.server,'RESTART');d=read(self.root/'request74',self.key)
  with patch('first58.inplace104.access88.consume',side_effect=ValueError('ACCESS88_REVOKED_OR_EXPIRED')):
   with self.assertRaises(ValueError):self.o.receive()
  self.o.report();status=read(self.root/'state104',self.key);self.assertEqual(status['request'],d['request']);self.assertEqual(status['requestResult'],'REJECTED')
 def test_filename_contract_matches_runtime(self):
  for name in ['space name.jar','a..jar','upper.JAR','bad:name.jar']:
   with self.assertRaises(ValueError):self.base.c.stage({name:self.base.b})
 def test_plan_read_is_bounded(self):
  self.base.c.stage({'a.jar':self.base.b});folder=self.base.c.load()[2];(folder/'plan.json').write_bytes(b'x'*300000)
  with self.assertRaisesRegex(ValueError,'CHANGE_PLAN'):self.base.c.load()
 def test_oversized_request_is_rejected_without_closing_job(self):
  self.session();job=self.o.job;(self.root/'request74').write_bytes(b'x'*200000)
  with self.assertRaisesRegex(ValueError,'REQUEST_LIMIT'):self.o.receive()
  job.close.assert_not_called()
 def test_key_read_rejects_oversized_file(self):
  from first58.inplace104 import identity
  (self.root/'owner74.key').write_bytes(b'x'*200000)
  with self.assertRaisesRegex(ValueError,'SERVER104_KEY'):identity(self.server)

class RuntimeEnvironment105(unittest.TestCase):
 def test_version_probe_does_not_inherit_agents_or_python_paths(self):
  import os
  base=previous.Configuration('test_single_installed_runtime_detected');base.setUp();self.addCleanup(base.doCleanups)
  with patch.dict(os.environ,{'JAVA_TOOL_OPTIONS':'-javaagent:wrong.jar','PYTHONPATH':'wrong-path','GENESIS_REMOTE70_ROOT':'old-profile'}),patch('first58.inplace104.subprocess.run',return_value=Mock(returncode=0,stdout='',stderr='openjdk version "21.0.12"')) as run:
   base.configure();env=run.call_args.kwargs['env'];self.assertNotIn('JAVA_TOOL_OPTIONS',env);self.assertNotIn('PYTHONPATH',env);self.assertNotIn('GENESIS_REMOTE70_ROOT',env)

if __name__=='__main__':unittest.main()
