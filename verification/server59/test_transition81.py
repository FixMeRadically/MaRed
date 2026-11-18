"""Deterministic progress/receipt barriers; Jobs and control transport are explicit models here."""
from pathlib import Path
import copy,sys,threading,time,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.transition81 import Progress81,validate
from first58.dedicated81 import Owner81
from first58 import dedicated75 as old
from first58.bridge67 import read,write
import test_dedicated74 as fixture

class Receipts81(unittest.TestCase):
 def setUp(self):
  self.fence=dict(round='round',release='release',client='a'*64,total='3');self.state=dict(phase='COMMITTED',round='round',release='release',client='a'*64,total=3,needed=1,installed=0,joined=0,serial=1,admission=False)
 def test_valid_frozen_cohort(self):self.assertEqual(validate(self.state,self.fence,None,False),self.state)
 def test_wrong_round(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):validate(dict(self.state,round='another'),self.fence,None,False)
 def test_wrong_release(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):validate(dict(self.state,release='another'),self.fence,None,False)
 def test_wrong_client_archive(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):validate(dict(self.state,client='b'*64),self.fence,None,False)
 def test_preparing_not_commit(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):validate(dict(self.state,phase='PREPARING'),self.fence,None,False)
 def test_denominator_cannot_shrink(self):
  with self.assertRaisesRegex(ValueError,'COUNTS'):validate(dict(self.state,total=2),self.fence,None,False)
 def test_threshold_cannot_change(self):
  with self.assertRaisesRegex(ValueError,'COUNTS'):validate(dict(self.state,needed=2),self.fence,None,False)
 def test_no_installed_over_total(self):
  with self.assertRaisesRegex(ValueError,'COUNTS'):validate(dict(self.state,installed=4),self.fence,None,False)
 def test_no_joined_without_installed(self):
  with self.assertRaisesRegex(ValueError,'COUNTS'):validate(dict(self.state,joined=1),self.fence,None,False)
 def test_negative_count(self):
  with self.assertRaisesRegex(ValueError,'COUNTS'):validate(dict(self.state,installed=-1),self.fence,None,False)
 def test_boolean_is_not_count(self):
  with self.assertRaisesRegex(ValueError,'COUNTS'):validate(dict(self.state,serial=True),self.fence,None,False)
 def test_string_is_not_count(self):
  with self.assertRaisesRegex(ValueError,'COUNTS'):validate(dict(self.state,installed='3'),self.fence,None,False)
 def test_early_admission(self):
  with self.assertRaisesRegex(ValueError,'EARLY_ADMISSION'):validate(dict(self.state,admission=True),self.fence,None,False)
 def test_repeat_serial_is_not_fresh_progress(self):
  with self.assertRaisesRegex(ValueError,'ROLLBACK'):validate(self.state,self.fence,self.state,False)
 def test_installed_cannot_regress(self):
  with self.assertRaisesRegex(ValueError,'ROLLBACK'):validate(dict(self.state,serial=3),self.fence,dict(self.state,serial=2,installed=1),False)
 def test_admission_cannot_regress(self):
  with self.assertRaisesRegex(ValueError,'ROLLBACK'):validate(dict(self.state,serial=3,installed=3),self.fence,dict(self.state,serial=2,installed=3,admission=True),True)
 def test_ready_target_admission_valid(self):validate(dict(self.state,serial=3,installed=3,admission=True),self.fence,self.state,True)

class ProgressChecks81(unittest.TestCase):
 def setUp(self):self.now=0;self.p=Progress81(lambda:self.now);self.s=dict(total=3,installed=1,joined=0,admission=False)
 def view(self,stage='WAITING_ALL_INSTALLED',fresh=True,ready=False):return self.p.view(stage,self.s,fresh,'',ready)
 def test_heartbeat_does_not_reset_idle(self):self.view();self.now=31;d=self.view();self.assertTrue(d['stalled']);self.assertEqual(d['idleMs'],31000)
 def test_receipt_progress_resets_idle(self):self.view();self.now=31;self.s['installed']=2;self.assertEqual(self.view()['idleMs'],0)
 def test_wait_ack_does_not_fake_progress(self):self.view();self.now=31;self.p.acknowledge('request');d=self.view();self.assertTrue(d['stalled']);self.assertEqual(d['installed'],1);self.assertEqual(d['total'],3);self.assertEqual(d['lastAck'],'request')
 def test_stale_is_not_can_close(self):self.s['admission']=True;self.assertFalse(self.view(fresh=False,ready=True)['canClose'])
 def test_no_target_no_close(self):self.s['admission']=True;self.assertFalse(self.view(ready=False)['canClose'])
 def test_ready_is_not_admission(self):self.assertFalse(self.view(ready=True)['canClose'])
 def test_only_ready_and_admitted_close(self):self.s['admission']=True;self.assertTrue(self.view(ready=True)['canClose'])
 def test_time_can_exceed_a_month(self):self.view();self.now=40*86400;self.assertEqual(self.view()['elapsedMs'],40*86400*1000)

class OwnerChecks81(unittest.TestCase):
 def setUp(self):
  f=fixture.OwnerBoundaries74();f.setUp();self.addCleanup(f.doCleanups);self.f=f;self.root=f.root
  for n in (75,76,77):write(f.root/f'owner{n}',f.key,dict(schema=str(n),run=f.s['run']))
  self.o=Owner81(f.root,f.root);self.o.jobs={0:f.job};self.o.report('ACTIVE_BASELINE');self.o.commit81=dict(round='round',release='release',client='a'*64,total='3');self.o.pending=dict(label='r1',sourceGen=0,targetGen=1,info={'release':'release'},approved={'delta':{}});self.o.progress81=Progress81()
 def test_stale_all_installed_does_not_release_barrier(self):
  self.o.report=Mock()
  self.o.snapshot81=Mock(side_effect=[(dict(installed=3),False,''),(dict(installed=3),True,'')]);self.o.receive=Mock(return_value=None)
  with patch('first58.dedicated81.time.sleep') as sleep:self.o.wait_all_installed();sleep.assert_called_once()
  self.f.job.close.assert_not_called()
 def test_stop_wait_is_explicit_not_an_install(self):
  self.o.snapshot81=Mock(return_value=(dict(installed=1),True,''));self.o.receive=Mock(return_value=dict(action='SHUTDOWN'))
  with self.assertRaises(InterruptedError):self.o.wait_all_installed()
  self.assertTrue(self.o.stop_requested);self.f.job.close.assert_not_called()
 def test_control_error_immediately_invalidates_last_sample(self):
  self.o.control81=dict(installed=3);self.o.control_at81=time.monotonic();self.o.control_error81='CONTROL_UNAVAILABLE';self.assertFalse(self.o.snapshot81()[1])
 def test_no_target_ready_no_admission(self):
  self.o.snapshot81=Mock(return_value=(dict(installed=3,needed=1),True,''));self.assertFalse(self.o.advance_admission())
 def test_stale_sample_no_admission(self):
  self.o.snapshot81=Mock(return_value=(dict(installed=3,needed=1),False,''));self.o.target_ready81=Mock(return_value=True);self.assertFalse(self.o.advance_admission())
 def test_early_close_does_not_stop_pump(self):
  self.o.external=True;self.o.snapshot81=Mock(return_value=(dict(installed=3,total=3,joined=0,admission=False),True,''));self.o.fence=Mock()
  with self.assertRaisesRegex(ValueError,'WAIT_FOR_JOINED'):self.o.finish_round(True)
  self.assertFalse(self.o.pump_stop.is_set());self.o.fence.assert_not_called()
 def test_continue_wait_preserves_cohort(self):
  self.o.report=Mock();nonce=str(uuid.uuid4());before=copy.deepcopy(self.o.commit81)
  with patch.object(old.Owner75,'receive',return_value=dict(action='CONTINUE_WAIT',label='r1',request=nonce)):self.assertIsNone(self.o.receive())
  self.assertEqual(self.o.commit81,before);self.assertEqual(self.o.progress81.last_ack,nonce)
 def test_continue_wrong_label_not_acknowledged(self):
  self.o.report=Mock()
  with patch.object(old.Owner75,'receive',return_value=dict(action='CONTINUE_WAIT',label='other',request='request')):self.o.receive()
  self.assertEqual(self.o.progress81.last_ack,'none')
 def test_close_failure_keeps_world_and_retry_intent(self):
  self.o.external=True;self.o.target_ready81=Mock(return_value=True);self.o.snapshot81=Mock(return_value=(dict(round='round',installed=3,total=3,joined=0,admission=True),True,''));self.o.pump_thread=Mock();self.o.pump_thread.is_alive.return_value=False;self.o.fence=Mock(side_effect=ValueError('timeout'));self.o.report=Mock()
  with patch.object(old.Owner75,'receive',return_value=dict(action='CLOSE',label='r1')):self.assertIsNone(self.o.receive())
  self.assertTrue(self.o.closing81);self.assertTrue(self.o.external);self.assertIsNotNone(self.o.pending);self.f.job.close.assert_not_called()
 def test_continue_cannot_reopen_unknown_close(self):
  self.o.closing81=True;self.o.report=Mock()
  with patch.object(old.Owner75,'receive',return_value=dict(action='CONTINUE_WAIT',label='r1',request='r')):self.o.receive()
  self.assertEqual(self.o.progress81.last_ack,'none')
 def test_pump_recovers_after_transient_failure(self):
  self.o.control81=None;self.o.target_ready81=Mock(return_value=False)
  initial=dict(phase='COMMITTED',round='round',release='release',client='a'*64,total=3,needed=1,installed=1,joined=0,serial=1,admission=False)
  self.o.tool=Mock(side_effect=[ValueError('temporary'),initial]);stop=Mock();stop.is_set.side_effect=[False,False,True];stop.wait=Mock();self.o.pump_stop=stop
  self.o.start_pump('round');self.o.pump_thread.join(timeout=3)
  self.assertFalse(self.o.pump_thread.is_alive());self.assertEqual(self.o.snapshot81()[0]['installed'],1);self.assertTrue(self.o.snapshot81()[1]);self.assertEqual(self.o.snapshot81()[2],'');self.assertEqual([c.args[0] for c in stop.wait.call_args_list],[2,1]);self.f.job.close.assert_not_called()
 def test_invalid_control_never_replaces_verified_snapshot(self):
  good=dict(phase='COMMITTED',round='round',release='release',client='a'*64,total=3,needed=1,installed=1,joined=0,serial=1,admission=False)
  self.o.control81=good;self.o.control_at81=time.monotonic();self.o.target_ready81=Mock(return_value=False);self.o.tool=Mock(return_value=dict(good,total=2,serial=2));stop=Mock();stop.is_set.side_effect=[False,True];self.o.pump_stop=stop
  self.o.start_pump('round');self.o.pump_thread.join(timeout=3)
  state,fresh,error=self.o.snapshot81();self.assertEqual(state,good);self.assertFalse(fresh);self.assertEqual(error,'CONTROL_UNAVAILABLE');self.f.job.close.assert_not_called()
 def test_confirmed_close_clears_round_without_stopping_server(self):
  self.o.external=True;self.o.target_ready81=Mock(return_value=True);self.o.snapshot81=Mock(return_value=(dict(round='round',installed=3,total=3,joined=3,admission=True),True,''));self.o.pump_thread=Mock();self.o.pump_thread.is_alive.return_value=False;self.o.fence=Mock(return_value=dict(round='round',phase='CLOSED'));self.o.event=Mock()
  self.o.finish_round();self.o.fence.assert_called_once_with('close','round');self.assertFalse(self.o.external);self.assertIsNone(self.o.pending);self.assertIsNone(self.o.commit81);self.assertFalse(self.o.closing81);self.f.job.close.assert_not_called()
 def test_wrong_close_receipt_keeps_pending_round(self):
  self.o.external=True;self.o.target_ready81=Mock(return_value=True);self.o.snapshot81=Mock(return_value=(dict(round='round',installed=3,total=3,joined=3,admission=True),True,''));self.o.pump_thread=None;self.o.fence=Mock(return_value=dict(round='other',phase='CLOSED'))
  with self.assertRaisesRegex(ValueError,'CLOSE_UNCONFIRMED'):self.o.finish_round(True)
  self.assertTrue(self.o.closing81);self.assertTrue(self.o.external);self.assertIsNotNone(self.o.pending);self.f.job.close.assert_not_called()
 def test_pending_close_can_retry_without_old_poll_freshness(self):
  self.o.external=True;self.o.closing81=True;self.o.target_ready81=Mock(return_value=True);self.o.snapshot81=Mock(return_value=(dict(round='round'),False,'CONTROL_UNAVAILABLE'));self.o.pump_thread=None;self.o.fence=Mock(return_value=dict(round='round',phase='CLOSED'));self.o.event=Mock()
  self.o.finish_round(True);self.assertFalse(self.o.closing81);self.o.fence.assert_called_once_with('close','round');self.f.job.close.assert_not_called()
 def test_legacy_owner_cannot_receive_new_wait_action(self):
  self.o.commit81=None;self.o.progress81=None;self.o.busy75=True;self.o.report('WAITING_ALL_INSTALLED')
  with self.assertRaisesRegex(ValueError,'OWNER_REQUIRED'):old.submit(self.root,'CONTINUE_WAIT')
 def test_new_owner_accepts_wait_while_busy(self):
  self.o.commit81=None;self.o.progress81=None;write(self.root/'owner81',self.f.key,dict(schema='81',run=self.f.s['run']));self.o.busy75=True;self.o.report('WAITING_ALL_INSTALLED');old.submit(self.root,'CONTINUE_WAIT')

if __name__=='__main__':unittest.main()
