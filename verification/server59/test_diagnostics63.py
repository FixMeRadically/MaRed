"""Controller failures without a game process: diagnostic privacy and owner cleanup."""
from pathlib import Path
from types import SimpleNamespace
import json,sys,tempfile,unittest
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.diagnostics63 import Diagnostics,PilotFailure,failure_data
from first58 import controller,launch62
from first58.common import atomic,json_write,sign

class DiagnosticTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
  self.diag=Diagnostics(self.root,'abc123')
 def report(self):return json.loads((self.root/'report.json').read_text())
 def test_no_logs_still_writes_report_and_exact_safe_reason(self):
  self.diag.step('validate_args','client',0,phase='STARTING_BASELINE')
  error=self.diag.failure(ValueError('Launch command redirects profile or loads an external agent'))
  r=self.report();self.assertEqual(r['role'],'client');self.assertEqual(r['action'],'validate_args');self.assertFalse(any(x['exists'] for x in r['logs']));self.assertIn('Java agent',error.display)
 def test_unknown_message_tokens_and_paths_not_serialized(self):
  secret='SUPER_SECRET_ACCESS_TOKEN';filename='secret-key.der'
  try:raise ValueError('--accessToken '+secret+' C:/private/'+filename)
  except Exception as e:self.diag.failure(e)
  text=(self.root/'report.json').read_text();self.assertNotIn(secret,text);self.assertNotIn(filename,text);self.assertIn('test_diagnostics63.py',text)
 def test_subprocess_command_and_stderr_never_serialized(self):
  import subprocess
  e=subprocess.CalledProcessError(1,['java','--accessToken','TOKEN'],stderr='TOKEN')
  self.diag.failure(e);self.assertNotIn('TOKEN',(self.root/'report.json').read_text())
 def test_oserror_keeps_numeric_code_without_sensitive_filename(self):
  self.diag.failure(FileNotFoundError(2,'private token','SECRET_PATH'))
  self.assertEqual(self.report()['errno'],2);self.assertNotIn('SECRET_PATH',(self.root/'report.json').read_text())
 def test_chained_exception_keeps_types_not_messages(self):
  try:
   try:raise KeyError('TOKEN')
   except KeyError as e:raise RuntimeError('TOKEN') from e
  except Exception as e:self.diag.failure(e)
  self.assertEqual(self.report()['exception_types'],['RuntimeError','KeyError'])
  self.assertNotIn('TOKEN',(self.root/'report.json').read_text())
 def test_events_bounded_and_repeated_polls_do_not_write(self):
  self.diag.step('runtime_status','server',0)
  with patch('first58.diagnostics63.atomic') as write:
   for _ in range(1000):self.diag.step('runtime_status','server',0)
   write.assert_not_called()
  for i in range(110):self.diag.step('runtime_status','client' if i%2 else 'server',0)
  self.assertEqual(len(json.loads((self.root/'controller-events63.json').read_text())['events']),96)
 def test_diagnostic_io_failure_still_returns_visible_error(self):
  with patch('first58.diagnostics63.atomic',side_effect=PermissionError('TOKEN')):
   self.diag.step('spawn','client',0);e=self.diag.failure(ValueError('Native argv'))
  self.assertIn('Не удалось записать',e.display);self.assertNotIn('TOKEN',e.display)
 def test_constructor_failure_diagnosed_without_job(self):
  with self.assertRaises(PilotFailure):controller.Pilot(self.root,self.root)
  self.assertEqual(self.report()['action'],'load_state');self.assertFalse(any(x['exists'] for x in self.report()['logs']))
 def pilot(self):
  p=controller.Pilot.__new__(controller.Pilot);p.root=self.root;p.project=self.root;p.attempt='abc123';p.diagnostics=self.diag;p.config={'run':'11111111-1111-1111-1111-111111111111'};p.notify=lambda _:None;p.jobs={};p.seq={};p.argv=Mock(return_value=['java']);p.ready=Mock();return p
 def test_preflight_failure_never_starts_server(self):
  p=self.pilot();p.start=Mock()
  def bad(role,generation):
   p.diagnostics.step('validate_args',role,generation)
   if role=='client':raise ValueError('Launch command redirects profile or loads an external agent')
   return ['java']
  p.argv=bad
  with patch.object(controller,'os',SimpleNamespace(name='nt')):
   with self.assertRaises(PilotFailure):p.run()
  p.start.assert_not_called();self.assertEqual(self.report()['role'],'client');self.assertEqual(self.report()['last_phase'],'STARTING_BASELINE')
 def test_primary_reason_survives_cleanup_exception(self):
  p=self.pilot()
  def bad(*args):p.diagnostics.step('spawn','client',0);raise ValueError('Native argv')
  p.start=bad;p.cleanup=Mock(side_effect=RuntimeError('PRIVATE'))
  with patch.object(controller,'os',SimpleNamespace(name='nt')):
   with self.assertRaises(PilotFailure) as caught:p.run()
  self.assertIn('Windows runner',caught.exception.display);self.assertEqual(self.report()['action'],'spawn');self.assertNotIn('PRIVATE',(self.root/'report.json').read_text())
 def test_cleanup_attempts_every_job_even_if_first_handle_close_fails(self):
  p=self.pilot();a=Mock();b=Mock();a.empty.return_value=True;b.empty.return_value=True;a.close.side_effect=OSError('fail');p.jobs={('client',0):a,('server',0):b}
  with self.assertRaises(OSError):p.cleanup()
  a.close.assert_called_once();b.close.assert_called_once();self.assertFalse(p.jobs)
 def test_real_existing_log_reported_without_reading_its_contents(self):
  (self.root/'server-0-abc123.log').write_text('TOKEN')
  self.diag.failure(ValueError('Native argv'));self.assertTrue(self.report()['logs'][0]['exists']);self.assertNotIn('TOKEN',(self.root/'report.json').read_text())
 def test_process_exit_code_kept_without_extra_fields(self):
  self.diag.process('client',0,{'active':0,'rootExited':True,'exitCode':7,'logFailed':False,'argv':'TOKEN'})
  self.diag.failure(ValueError('Native argv'));p=self.report()['processes'][0]
  self.assertEqual(p['exitCode'],7);self.assertNotIn('TOKEN',(self.root/'report.json').read_text())
 def test_report_is_written_before_notify_failure(self):
  p=self.pilot();p.notify=Mock(side_effect=RuntimeError('UI failed'))
  with self.assertRaises(RuntimeError):p.report('STARTING_BASELINE')
  self.assertEqual(self.report()['phase'],'STARTING_BASELINE')
 def test_short_many_arguments_wrapped_for_native_128_limit(self):
  argv=['java']+['x']*128
  wrapped=launch62.wrap_long(argv,self.root);self.assertEqual(len(wrapped),2)
  self.assertEqual(launch62.argfile_tokens(Path(wrapped[1][1:]).read_text()),argv[1:])
 def test_exact_128_arguments_remain_direct(self):
  argv=['java']+['x']*127;self.assertEqual(launch62.wrap_long(argv,self.root),argv)
 def test_argument_packing_preserves_literals(self):
  argv=['java']+['-Dsetting='+str(i) for i in range(127)]+['a b',r'C:\a b\c','quote"','hash#value','']
  wrapped=launch62.wrap_long(argv,self.root)
  self.assertEqual(launch62.argfile_tokens(Path(wrapped[1][1:]).read_text()),argv[1:])
 def test_many_arguments_do_not_allow_nested_argfile(self):
  with self.assertRaisesRegex(ValueError,'nested'):launch62.wrap_long(['java','@hidden']+['x']*128,self.root)

if __name__=='__main__':unittest.main(verbosity=2)
