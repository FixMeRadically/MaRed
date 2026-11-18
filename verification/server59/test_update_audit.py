"""Regressions for the real updater and controller handoff paths."""
from pathlib import Path
from types import SimpleNamespace
import sys
import tempfile
import unittest
import uuid
from unittest.mock import Mock, patch

sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.common import json_write
from first58.control73 import Gate73
from first58.folder118 import FolderRollout118
from first58.remote70 import Worker
from first58.repeat71 import RepeatingWorker71


class UpdateAudit(unittest.TestCase):
 def test_commit_written_during_source_exit_is_observed(self):
  with tempfile.TemporaryDirectory() as tmp:
   worker=Worker.__new__(Worker)
   worker.folder=Path(tmp)
   worker.id=str(uuid.uuid4())
   def command(action):
    json_write(worker.folder/'command70.json',dict(schema=70,request=worker.id,action=action))
   def closed():
    command('COMMIT')
    return True
   command('ARM')
   worker.lease=SimpleNamespace(closed=closed)
   worker.wait_action('COMMIT',1)
   command('ARM')
   worker.lease=SimpleNamespace(closed=lambda:True)
   with self.assertRaisesRegex(ValueError,'REMOTE70_SOURCE_EXIT'):
    worker.wait_action('COMMIT',1)

 def test_post_response_loss_can_still_observe_signed_commit(self):
  gate=Gate73.__new__(Gate73)
  gate.w=SimpleNamespace(cancelled=lambda:False,require_source=Mock())
  gate.file=Path('config')
  gate.inventory='a'*64
  gate.cfg={'client':str(uuid.uuid4())}
  request=uuid.uuid4()
  gate.call=Mock(side_effect=ValueError('CONTROL73_NETWORK'))
  gate.status=Mock(return_value={'phase':'COMMITTED','clients':gate.cfg['client']+',PREPARED,'+str(request)})
  with patch('first58.control73.uuid.uuid4',return_value=request):
   gate.receipt('PREPARED','COMMIT')
  self.assertTrue(gate.committed)

 def test_repeating_watch_retries_first_join_receipt(self):
  worker=RepeatingWorker71.__new__(RepeatingWorker71)
  worker.job=Mock()
  worker.job.status.side_effect=[dict(rootExited=False,logFailed=False),dict(rootExited=False,logFailed=False),dict(rootExited=True,logFailed=False,exitCode=0)]
  worker.stop=Mock()
  worker.state=Mock()
  worker.gate73=Mock()
  worker.gate73.joined.side_effect=[False,True]
  worker.join_receipt_pending=True
  clock=iter(range(10,100,6))
  with patch('first58.repeat71.packet',return_value=None),patch('first58.repeat71.publish'),patch('first58.repeat71.time.sleep'),patch('first58.repeat71.time.monotonic',side_effect=lambda:next(clock)):
   worker.watch(Mock(),lambda:False,Mock())
  self.assertFalse(worker.join_receipt_pending)
  self.assertEqual(worker.gate73.joined.call_count,2)

 def test_tick_retries_closing_an_admitted_round(self):
  with tempfile.TemporaryDirectory() as tmp:
   rollout=FolderRollout118.__new__(FolderRollout118)
   rollout.root=Path(tmp)
   rollout.o=Mock()
   rollout.services120=Mock()
   rollout.https=Mock()
   rollout.signer=None
   rollout.pending={'label':'next'}
   rollout.fence={'total':'2'}
   rollout.state={'admission':True,'joined':2}
   rollout.closing=False
   rollout.target=True
   rollout.fresh=Mock(return_value=True)
   rollout.target_ready=Mock(return_value=True)
   rollout.finish=Mock(side_effect=[ValueError('FOLDER118_TEST_CLOSE'),None])
   rollout.next=0
   self.assertTrue(rollout.tick())
   self.assertEqual(rollout.error,'FOLDER118_TEST_CLOSE')
   rollout.next=0
   self.assertTrue(rollout.tick())
   self.assertEqual(rollout.finish.call_count,2)

 def test_idle_renewal_coalesces_full_verification_but_manual_renew_checks(self):
  from first58.folder120 import FolderServices120
  from first58.bridge67 import write
  with tempfile.TemporaryDirectory() as tmp:
   service=FolderServices120.__new__(FolderServices120)
   service.root=Path(tmp)
   service.key=b'k'*32
   service.o=SimpleNamespace(run='run',generation=1,expected='hash',busy=False)
   service.f=SimpleNamespace(pending=None)
   service.next=service.expiry_check_at=0
   service.expiry_binding=None
   service.revision=None
   service.request=''
   service.tx=Mock(return_value=None)
   pointer={'run':'run','label':'r1','release':'release'}
   service.guard=Mock(return_value=pointer)
   service.snapshot=Mock(return_value={'expires':4000000000000})
   service.renew=Mock()
   write(service.root/'publication75',service.key,pointer)
   cfg={'revision':'r','enabled':'false','margin':'60'}
   with patch('first58.folder120.publication85.config',return_value=cfg):
    service.tick()
    service.next=0
    service.tick()
    service.snapshot.assert_called_once()
    service.guard.assert_called_once()
    write(service.root/'renew85.request',service.key,dict(schema='85',run='run',request=str(uuid.uuid4()),action='RENEW'))
    service.next=0
    service.tick()
   self.assertEqual(service.guard.call_count,2)
   service.renew.assert_called_once_with(cfg,pointer)

 def test_ack_retry_runs_local_command_once_and_refuses_delayed_remote_command(self):
  from first58 import inplace104
  from first58.access88 import AccessBusy88
  from first58.bridge67 import write
  for remote in (False,True):
   with self.subTest(remote=remote),tempfile.TemporaryDirectory() as tmp:
    owner=inplace104.Owner104.__new__(inplace104.Owner104)
    owner.root=Path(tmp)
    owner.key=b'k'*32
    owner.run='run'
    owner.generation=1
    owner.phase='RUNNING'
    owner.pending_ack=None
    owner.last_request=b''
    owner.seen=set()
    request=dict(schema='75',run='run',generation='1',request=str(uuid.uuid4()),action='RESTART',label='r1')
    if remote:request['permit88']='fixture'
    write(owner.root/'request74',owner.key,request)
    with patch('first58.inplace104.access88.consume') as consume:
     with patch('first58.inplace104.write',side_effect=PermissionError('temporarily locked')):
      with self.assertRaises(AccessBusy88):owner.receive()
     if remote:
      with self.assertRaisesRegex(ValueError,'SERVER104_REMOTE_ACK_EXPIRED'):owner.receive()
     else:self.assertEqual(owner.receive(),'RESTART')
     self.assertIsNone(owner.receive())
     consume.assert_called_once()
