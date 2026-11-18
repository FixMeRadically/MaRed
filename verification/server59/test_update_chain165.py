"""Compose the actual member/public gate and worker loops. Only clock/network/game are models."""
from pathlib import Path
from types import SimpleNamespace
import sys, unittest
from unittest.mock import Mock, patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.admission82 import Combined82
from first58.remote70 import Worker

class AutomaticChain165(unittest.TestCase):
 def setUp(self):
  self.now=0.
  self.w=Worker.__new__(Worker)
  self.w.job=Mock();self.w.job.status.return_value={'rootExited':False,'logFailed':False}
  self.w.cancelled=lambda:False;self.w.state=Mock()
  self.w.command=Mock(side_effect=lambda phase:dict(sequence='1',request='request',phase=phase))
  self.w.status=Mock(return_value=dict(sequence='1',request='request',phase='WORLD_FRAME_READY'))
  self.member=SimpleNamespace(w=self.w,automatic73=True,installed=Mock(),check=Mock(),cfg={'client':'c'})
  self.public=SimpleNamespace(check=Mock())
  self.g=Combined82(self.public,self.member);self.w.gate73=self.g
  self.g.before_join=Mock()
  self.ask=Mock(side_effect=AssertionError('Unexpected modal question in automatic update'))
  self.enter_context(patch('first58.remote70.time.monotonic',side_effect=lambda:self.now))
  self.enter_context(patch('first58.remote70.time.sleep',side_effect=self.sleep))
 def enter_context(self,context):self.addCleanup(context.__exit__,None,None,None);return context.__enter__()
 def sleep(self,n):self.now+=n
 def test_combined_gate_keeps_automatic_mode(self):self.assertIs(getattr(self.g,'automatic73',False),True)
 def test_delayed_admission_does_not_ask(self):
  self.w.status.return_value={'phase':'READY'}
  def ready():
   if self.now<65:raise ValueError('ADMISSION82_WAITING')
  self.g.before_join.side_effect=ready
  self.g.installed(Mock(),lambda:False,self.ask)
  self.assertGreaterEqual(self.now,65);self.ask.assert_not_called()
 def test_admission_has_bounded_failure(self):
  self.w.status.return_value={'phase':'READY'};self.g.before_join.side_effect=ValueError('ADMISSION82_WAITING')
  with self.assertRaisesRegex(ValueError,'ADMISSION82_WAIT_TIMEOUT'):self.g.installed(Mock(),lambda:False,self.ask)
  self.assertLessEqual(self.now,181);self.ask.assert_not_called()
 def test_delayed_server_joins_without_question(self):
  def ready():
   if self.now<65:raise ValueError('ADMISSION82_WAITING')
  self.g.before_join.side_effect=ready
  self.assertTrue(self.w.join(Mock(),lambda:False,self.ask));self.assertGreaterEqual(self.now,65)
  self.ask.assert_not_called()
 def test_permanent_unavailable_server_ends_without_question(self):
  self.g.before_join.side_effect=ValueError('ADMISSION82_WAITING')
  with self.assertRaisesRegex(ValueError,'REMOTE70_JOIN_TIMEOUT'):self.w.join(Mock(),lambda:False,self.ask)
  self.assertLessEqual(self.now,181);self.ask.assert_not_called()
 def test_cancel_during_admission_does_not_join(self):
  self.w.status.return_value={'phase':'READY'};self.g.before_join.side_effect=ValueError('ADMISSION82_WAITING')
  with self.assertRaises(InterruptedError):self.g.installed(Mock(),lambda:self.now>=7,self.ask)
  self.w.command.assert_not_called()
 def test_closed_round_not_retried(self):
  self.g.before_join.side_effect=InterruptedError('CONTROL73_CANCELLED')
  with self.assertRaises(InterruptedError):self.w.join(Mock(),lambda:False,self.ask)
  self.w.command.assert_not_called();self.assertEqual(self.now,0)
 def test_busy_then_failed_then_success(self):
  self.w.status.side_effect=[dict(sequence='1',request='request',phase=p) for p in ('JOIN_BUSY','JOIN_FAILED','WORLD_FRAME_READY')]
  self.assertTrue(self.w.join(Mock(),lambda:False,self.ask))
  self.assertEqual([c.args[0] for c in self.w.command.call_args_list],['JOIN','JOIN','JOIN','FOCUS'])
 def test_dead_child_does_not_retry(self):
  self.w.job.status.return_value['rootExited']=True
  with self.assertRaisesRegex(ValueError,'CHILD_ERROR'):self.w.join(Mock(),lambda:False,self.ask)
  self.w.command.assert_not_called()
 def test_wrong_ack_never_marks_world_ready(self):
  self.w.status.return_value=dict(sequence='0',request='stale',phase='WORLD_FRAME_READY')
  with self.assertRaisesRegex(ValueError,'REMOTE70_JOIN_TIMEOUT'):self.w.join(Mock(),lambda:False,self.ask)
  self.assertNotIn('FOCUS',[c.args[0] for c in self.w.command.call_args_list])


class LaunchNormalization165(unittest.TestCase):
 def setUp(self):
  import tempfile
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.profile=Path(self.temp.name)
 def normalize(self,args):
  from first58.capture import normalize_client_argv
  return normalize_client_argv(args,self.profile)[0]
 def test_both_game_directory_forms(self):
  for option in (['--gameDir',str(self.profile)],['--gameDir='+str(self.profile)]):
   self.assertEqual(self.normalize(['java','Main',*option]),['java','Main','--gameDir','{profile}'])
 def test_quick_play_flags_do_not_survive_either_form(self):
  for flag in ('--server','--port','--quickPlayPath','--quickPlayMultiplayer','--quickPlaySingleplayer','--quickPlayRealms'):
   for values in ([flag,'target'],[flag+'=target']):
    with self.subTest(flag=flag,inline=len(values)==1):
     self.assertEqual(self.normalize(['java','Main','--gameDir',str(self.profile),*values]),['java','Main','--gameDir','{profile}'])
 def test_duplicate_directory_is_rejected(self):
  with self.assertRaises(ValueError):self.normalize(['java','Main','--gameDir',str(self.profile),'--gameDir='+str(self.profile)])
 def test_missing_quick_play_value_is_rejected(self):
  with self.assertRaises(ValueError):self.normalize(['java','Main','--gameDir',str(self.profile),'--server','--uuid','x'])
 def test_other_options_are_preserved(self):
  args=['java','Main','--gameDir',str(self.profile),'--username','player','--accessToken','fixture-only','--assetsDir','/assets']
  self.assertEqual(self.normalize(args)[4:],args[4:])


class Recovery165(unittest.TestCase):
 def test_control_deadline_can_restore_only_after_normal_source_exit(self):
  from first58.return119 import eligible
  for code in ('ADMISSION82_WAIT_TIMEOUT','CONTROL73_WAIT_TIMEOUT'):
   w=SimpleNamespace(lease=SimpleNamespace(closed=lambda:True,normal=lambda:True),cancelled=lambda:False)
   self.assertTrue(eligible(w,ValueError(code),lambda:False))
   w.world_ready119=True;self.assertFalse(eligible(w,ValueError(code),lambda:False))
   w.world_ready119=False;w.lease.normal=lambda:False;self.assertFalse(eligible(w,ValueError(code),lambda:False))
 def test_return_gate_is_bounded_automatic(self):
  from first58.return120 import ReturnGate120
  self.assertIs(ReturnGate120.automatic73,True)
 def test_missing_old_server_permission_keeps_restored_menu(self):
  from first58.return120 import reconnect
  for code in ('REMOTE70_JOIN_TIMEOUT','AUTH89_LOGIN_REQUIRED','AUTH89_HANDOFF_TIMEOUT','AUTH89_UNAVAILABLE'):
   with self.subTest(code=code):
    w=SimpleNamespace(config={'return120':'fixture'},join=Mock(side_effect=ValueError(code)),command=Mock(),state=Mock(),return_result119={},job=Mock())
    self.assertFalse(reconnect(w,Mock(),lambda:False,Mock()))
    w.command.assert_called_once_with('CANCEL');w.state.assert_called_once_with('RESTORED')
    self.assertEqual(w.return_result119['reconnectReason'],code);w.job.close.assert_not_called();self.assertIsNone(w.gate73)
 def test_actual_child_failure_is_not_hidden_as_restored_menu(self):
  from first58.return120 import reconnect
  w=SimpleNamespace(config={'return120':'fixture'},join=Mock(side_effect=ValueError('REMOTE70_CHILD_ERROR')))
  with self.assertRaisesRegex(ValueError,'REMOTE70_CHILD_ERROR'):reconnect(w,Mock(),lambda:False,Mock())
  self.assertIsNone(w.gate73)

if __name__=='__main__':unittest.main()
