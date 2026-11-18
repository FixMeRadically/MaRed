"""JOIN gate, retries and private owner feed; runtime/HTTP are explicit models here."""
from pathlib import Path
from types import SimpleNamespace
import base64,copy,io,sys,tempfile,time,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.admission82 import Combined82
from first58.bootstrap76 import attach
from first58.bootstrap_wire76 import decode
from first58.remote70 import Worker
from first58.dedicated82 import Owner82
from first58.bridge67 import read,write
import test_dedicated74 as fixture

def packet(d):return ''.join(k+'='+base64.urlsafe_b64encode(str(v).encode()).decode().rstrip('=')+'\n' for k,v in sorted(d.items())).encode()
class Gate82(unittest.TestCase):
 def setUp(self):
  self.w=SimpleNamespace(status=Mock(return_value={'phase':'READY'}),job=Mock(),cancelled=Mock(return_value=False),spec={'clientCommand':['java']},r={'neoforge':'21.1.264'},folder=Path('/fixture'),root=Path('/fixture/run'))
  self.w.job.status.return_value={'rootExited':False,'logFailed':False}
  self.m=Mock();self.m.w=self.w;self.m.cfg={'client':'client'};self.m.file=Path('/private/config');self.m.key=Path('/public/key');self.m.inventory='d'*64;self.state=dict(phase='COMMITTED',admission='true',installed='3',needed='1',sequence='5',clients='client,INSTALLED,ack');self.m.check.return_value=self.state
  self.p=Mock();self.p.check.return_value={'phase':'READY'};self.g=Combined82(self.p,self.m);self.g.runtime82=True;self.proof=dict(owner='owner',target='2',serverInventory='b'*64,serial='1',controlSequence='4')
 def proof_mock(self):self.g.proof=Mock(return_value=self.proof)
 def test_source_final_check_still_requires_fresh_public_offer(self):self.g.check();self.p.check.assert_called_once();self.m.check.assert_called_once()
 def test_runtime_must_be_confirmed_before_any_join(self):
  self.g.runtime82=False
  with self.assertRaisesRegex(ValueError,'RUNTIME_REQUIRED'):self.g.before_join()
  self.m.check.assert_not_called()
 def test_installed_runtime_requires_ready(self):
  self.w.status.return_value={'phase':'JOIN_FAILED'}
  with self.assertRaisesRegex(ValueError,'RUNTIME_REQUIRED'):self.g.installed(Mock(),lambda:False,Mock())
  self.m.installed.assert_not_called()
 def test_expired_public_does_not_block_installed_member_proof(self):
  self.proof_mock();self.p.check.side_effect=ValueError('expired');self.assertEqual(self.g.before_join(),'MEMBER82');self.p.check.assert_not_called()
 def test_legacy_fresh_ready_is_supported(self):
  self.g.proof=Mock(side_effect=ValueError('not available'));self.assertEqual(self.g.before_join(),'PUBLIC76')
 def test_no_proof_no_fresh_offer_no_join(self):
  self.g.proof=Mock(side_effect=ValueError('not available'));self.p.check.side_effect=ValueError('expired')
  with self.assertRaises(ValueError):self.g.before_join()
 def test_transitional_legacy_state_not_ready(self):
  self.g.proof=Mock(side_effect=ValueError('not available'));self.p.check.return_value={'phase':'TRANSITION'}
  with self.assertRaisesRegex(ValueError,'WAITING'):self.g.before_join()
 def test_prepared_is_not_installed(self):
  self.m.check.return_value={**self.state,'clients':'client,PREPARED,ack'};self.proof_mock()
  with self.assertRaisesRegex(ValueError,'WAITING'):self.g.before_join()
  self.g.proof.assert_not_called()
 def test_unknown_member_not_accepted(self):
  self.m.check.return_value={**self.state,'clients':'another,INSTALLED,ack'}
  with self.assertRaisesRegex(ValueError,'WAITING'):self.g.before_join()
 def test_admission_required(self):
  self.m.check.return_value={**self.state,'admission':'false'}
  with self.assertRaisesRegex(ValueError,'WAITING'):self.g.before_join()
 def test_threshold_required(self):
  self.m.check.return_value={**self.state,'installed':'0'}
  with self.assertRaisesRegex(ValueError,'WAITING'):self.g.before_join()
 def test_control_must_not_precede_proof(self):
  self.proof_mock();self.g.last=self.proof;self.m.check.return_value={**self.state,'sequence':'3'}
  with self.assertRaisesRegex(ValueError,'CONTROL_BEHIND'):self.g.before_join()
  self.p.check.assert_not_called()
 def test_closed_round_aborts_without_public_fallback(self):
  self.m.check.side_effect=InterruptedError('closed')
  with self.assertRaises(InterruptedError):self.g.before_join()
  self.p.check.assert_not_called()
 def test_runtime_ack_then_proof(self):
  self.proof_mock();self.g.runtime82=False;self.g.installed(Mock(),lambda:False,Mock());self.assertTrue(self.g.runtime82);self.m.installed.assert_called_once()
 def test_cancel_during_wait(self):
  with self.assertRaises(InterruptedError):self.g.installed(Mock(),lambda:True,Mock())
 def test_dead_child_during_wait(self):
  self.w.job.status.return_value['rootExited']=True
  with self.assertRaisesRegex(ValueError,'CHILD_ERROR'):self.g.installed(Mock(),lambda:False,Mock())
 def test_joined_and_abort_delegated(self):self.g.joined();self.g.abort();self.m.joined.assert_called_once();self.m.abort.assert_called_once()
 def proof_call(self,d):
  with patch('first58.admission82.core_classpath',return_value='core'),patch('first58.admission82.subprocess.run',return_value=SimpleNamespace(returncode=0,stdout=base64.b64encode(packet(d)).decode())):return self.g.proof()
 def test_actual_python_wire_accepts_twenty_three_fields(self):
  from first58.admission_wire82 import FIELDS
  d={k:'x' for k in FIELDS};d.update(self.proof);self.assertEqual(len(decode(packet(d))),23);self.assertEqual(self.proof_call(d),d)
 def test_proof_serial_rollback_rejected(self):
  self.proof_call({**self.proof,'serial':'2'})
  with self.assertRaisesRegex(ValueError,'ROLLBACK'):self.proof_call(self.proof)
 def test_proof_same_serial_equivocation_rejected(self):
  self.proof_call(self.proof)
  with self.assertRaisesRegex(ValueError,'ROLLBACK'):self.proof_call({**self.proof,'issued':'new'})
 def test_server_generation_cannot_change(self):
  self.proof_call(self.proof)
  with self.assertRaisesRegex(ValueError,'ROLLBACK'):self.proof_call({**self.proof,'serial':'2','target':'3'})
 def test_server_owner_cannot_change(self):
  self.proof_call(self.proof)
  with self.assertRaisesRegex(ValueError,'ROLLBACK'):self.proof_call({**self.proof,'serial':'2','owner':'other'})

class JoinLoop82(unittest.TestCase):
 def setUp(self):
  self.now=0;self.w=Worker.__new__(Worker);self.w.job=Mock();self.w.job.status.return_value={'rootExited':False,'logFailed':False};self.w.state=Mock();self.w.cancelled=Mock(return_value=False);self.w.gate73=SimpleNamespace(before_join=Mock());self.w.command=Mock(side_effect=lambda phase:dict(sequence='1',request='request',phase=phase));self.w.status=Mock(return_value=dict(sequence='1',request='request',phase='WORLD_FRAME_READY'))
 def sleep(self,n):self.now+=n
 def run_join(self,ask=None):
  with patch('first58.remote70.time.monotonic',side_effect=lambda:self.now),patch('first58.remote70.time.sleep',side_effect=self.sleep):return self.w.join(Mock(),lambda:False,ask or Mock(return_value=False))
 def test_join_is_guarded(self):self.assertTrue(self.run_join());self.w.gate73.before_join.assert_called_once();self.assertEqual([c.args[0] for c in self.w.command.call_args_list],['JOIN','FOCUS'])
 def test_no_fresh_proof_never_starts_connection(self):
  self.w.gate73.before_join.side_effect=ValueError('stale');self.assertFalse(self.run_join());self.assertEqual([c.args[0] for c in self.w.command.call_args_list],['CANCEL']);self.w.status.assert_not_called()
 def test_transient_proof_failure_waits_then_joins(self):
  self.w.gate73.before_join.side_effect=[ValueError('temporary'),None];self.assertTrue(self.run_join());self.assertGreaterEqual(self.now,1);self.assertEqual(self.w.gate73.before_join.call_count,2)
 def test_each_failed_connection_needs_new_proof(self):
  self.w.status.side_effect=[dict(sequence='1',request='request',phase='JOIN_FAILED'),dict(sequence='1',request='request',phase='WORLD_FRAME_READY')];self.assertTrue(self.run_join());self.assertEqual(self.w.gate73.before_join.call_count,2)
 def test_command_write_failure_is_not_treated_as_stale_proof(self):
  self.w.command.side_effect=ValueError('disk')
  with self.assertRaisesRegex(ValueError,'disk'):self.run_join()
 def test_closed_round_aborts_attempt(self):
  self.w.gate73.before_join.side_effect=InterruptedError('closed')
  with self.assertRaises(InterruptedError):self.run_join()
  self.w.command.assert_not_called()

class OwnerFeed82(unittest.TestCase):
 def setUp(self):
  f=fixture.OwnerBoundaries74();f.setUp();self.addCleanup(f.doCleanups);self.f=f;self.root=f.root
  for n in (75,76,77):write(f.root/f'owner{n}',f.key,dict(schema=str(n),run=f.s['run']))
  self.o=Owner82(f.root,f.root);self.o.current=1;self.o.jobs={1:f.job};self.o.pending=dict(label='r1',sourceGen=0,targetGen=1,info={'release':'release'},approved={'delta':{}});self.o.commit81=dict(round='round',release='release',client='a'*64,newInventory='b'*64);self.o.external=True;self.o.snapshot81=Mock(return_value=(dict(admission=True,serial=10),True,''));self.o.target_ready81=Mock(return_value=True)
 def feed(self):return read(self.root/'admission82.local',self.f.key)
 def test_ready_committed_feed(self):self.o.report('ACTIVE_UPDATED');self.assertEqual(self.feed()['target'],'1');self.assertEqual(self.feed()['controlSequence'],'10')
 def test_diagnostic_write_failure_does_not_close_world(self):
  with patch('first58.dedicated82.write',side_effect=OSError('disk')):self.o.report('ACTIVE_UPDATED')
  self.assertFalse((self.root/'admission82.local').exists());self.f.job.close.assert_not_called()
 def test_serial_increases(self):self.o.report('ACTIVE_UPDATED');old=int(self.feed()['serial']);self.o.report();self.assertGreater(int(self.feed()['serial']),old)
 def test_stale_sample_withdraws_feed(self):
  self.o.report('ACTIVE_UPDATED');self.o.snapshot81.return_value=(dict(admission=True,serial=10),False,'error');self.o.report();self.assertFalse((self.root/'admission82.local').exists())
 def test_close_unknown_withdraws_feed(self):self.o.report('ACTIVE_UPDATED');self.o.closing81=True;self.o.report();self.assertFalse((self.root/'admission82.local').exists())
 def test_not_ready_withdraws_feed(self):self.o.report('ACTIVE_UPDATED');self.o.target_ready81.return_value=False;self.o.report();self.assertFalse((self.root/'admission82.local').exists())
 def test_stop_withdraws_feed(self):self.o.report('ACTIVE_UPDATED');self.o.stop_requested=True;self.o.report();self.assertFalse((self.root/'admission82.local').exists())
 def test_early_world_copy_has_no_join_proof(self):self.o.report('COPYING_SAVED_WORLD');self.assertFalse((self.root/'admission82.local').exists())
 def test_missing_admission_has_no_join_proof(self):self.o.snapshot81.return_value=(dict(admission=False,serial=10),True,'');self.o.report('WAITING_THRESHOLD');self.assertFalse((self.root/'admission82.local').exists())

if __name__=='__main__':unittest.main()
