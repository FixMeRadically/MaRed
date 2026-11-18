"""Real filesystem locks/HMAC and explicit native/readiness models. No Windows/MC claim."""
import sys,tempfile,unittest,time,json,uuid
from pathlib import Path
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.bootstrap76 import ApplyLease76,Gate76,Combined76,attach
from first58.common import json_write
from first58.bridge67 import write,read
import test_dedicated74 as base
from first58.dedicated76 import Owner76
class Lease76(unittest.TestCase):
 def setUp(self):self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.held=[];self.addCleanup(lambda:[x.close() for x in self.held])
 def lease(self,account='a',game='localhost:25565',endpoint='https://localhost/genesis/'):
  x=ApplyLease76(self.root,account,game,endpoint);self.held.append(x);return x
 def test_second_owner_refused(self):
  self.lease()
  with self.assertRaisesRegex(ValueError,'OWNER_BUSY'):self.lease()
 def test_release_allows_recovery(self):self.lease().close();self.lease()
 def test_other_account_separate(self):self.lease();self.lease(account='b')
 def test_other_server_separate(self):self.lease();self.lease(game='other:25565')
 def test_real_process_exclusion_and_recovery(self):
  import subprocess
  held=self.lease();tools=str(Path(__file__).resolve().parents[2]/'tools');code="import sys;sys.path.insert(0,sys.argv[1]);from first58.bootstrap76 import ApplyLease76;x=ApplyLease76(sys.argv[2],'a','localhost:25565','https://localhost/genesis/');x.close()"
  r=subprocess.run([sys.executable,'-c',code,tools,str(self.root)],capture_output=True);self.assertNotEqual(r.returncode,0);held.close();r=subprocess.run([sys.executable,'-c',code,tools,str(self.root)],capture_output=True);self.assertEqual(r.returncode,0,r.stderr)
 def test_idempotent_close(self):x=self.lease();x.close();x.close();self.lease()
class OwnerFeed76(unittest.TestCase):
 def setUp(self):
  helper=base.OwnerBoundaries74();helper.setUp();self.addCleanup(helper.doCleanups);self.root=helper.root;self.key=helper.key;self.s=helper.s;write(self.root/'owner75',self.key,{'schema':'75','run':self.s['run']});write(self.root/'owner76',self.key,{'schema':'76','run':self.s['run']});self.owner=Owner76(helper.owner.project,self.root);self.owner.report('ACTIVE_BASELINE')
 def active(self):
  o=self.owner;o.current=1;o.ready_generation1=True;o.active_label76='r1';o.previous_info={'release':str(uuid.uuid4())};job=Mock();job.status.return_value={'rootExited':False,'exitCode':None,'logFailed':False};o.jobs={1:job};o.status=Mock(return_value={'phase':'READY'});return o
 def packet(self):return read(self.root/'bootstrap76.local',self.key)
 def test_no_unpublished_baseline_feed(self):self.assertFalse((self.root/'bootstrap76.local').exists())
 def test_active_ready(self):o=self.active();o.report('ACTIVE_UPDATED');self.assertEqual(self.packet()['phase'],'READY')
 def test_port_alone_is_not_ready(self):o=self.active();o.status.return_value=None;o.report();self.assertEqual(self.packet()['phase'],'UNAVAILABLE')
 def test_dead_job_no_ready(self):o=self.active();o.jobs[1].status.return_value['rootExited']=True;o.report();self.assertEqual(self.packet()['phase'],'UNAVAILABLE')
 def test_stopping_withdraws(self):o=self.active();o.stop_requested=True;o.report('STOPPED_SAVED');self.assertEqual(self.packet()['phase'],'UNAVAILABLE')
 def test_failure_withdraws(self):o=self.active();o.report('FAILED');self.assertEqual(self.packet()['phase'],'UNAVAILABLE')
 def test_preparation_failure_keeps_existing_ready(self):o=self.active();o.report('PREPARATION_FAILED');self.assertEqual(self.packet()['phase'],'READY')
 def test_serial_increases(self):o=self.active();o.report();a=int(self.packet()['serial']);o.report();self.assertGreater(int(self.packet()['serial']),a)
 def transition(self):
  o=self.active();release=str(uuid.uuid4());o.pending={'label':'r2','sourceGen':1,'targetGen':2,'info':{'release':release},'approved':{'delta':{}}};o.last_pump={'phase':'COMMITTED','release':release,'admission':False};return o
 def test_commit_targets_next_generation(self):o=self.transition();o.report();d=self.packet();self.assertEqual((d['phase'],d['generation'],d['label']),('TRANSITION','2','r2'))
 def test_new_runtime_before_admission_still_waits(self):o=self.transition();o.jobs[2]=o.jobs.pop(1);o.current=2;o.report();self.assertEqual(self.packet()['phase'],'TRANSITION')
 def test_native_and_admission_ready(self):o=self.transition();o.jobs[2]=o.jobs.pop(1);o.current=2;o.last_pump['admission']=True;o.report();self.assertEqual(self.packet()['phase'],'READY')
 def test_command_ack_preserves_proven_ready(self):o=self.active();o.status.return_value={'phase':'COMMAND_ACCEPTED'};o.report();self.assertEqual(self.packet()['phase'],'READY')
class Successor76(unittest.TestCase):
 def setUp(self):
  helper=base.OwnerBoundaries74();helper.setUp();self.addCleanup(helper.doCleanups);self.root=helper.root;key=helper.key;run=helper.s['run'];write(self.root/'owner75',key,{'schema':'75','run':run});write(self.root/'owner76',key,{'schema':'76','run':run});self.owner=Owner76(self.root,self.root);self.owner.previous_info={'release':'one','sequence':1,'distribution':'d'};self.info={'release':'three','parent':'two','sequence':3,'distribution':'d'};self.folder=self.root/'releases/r2';self.folder.mkdir(parents=True);self.owner.tool=Mock(return_value={'release':'two','parent':'one','sequence':2,'distribution':'d'})
 def test_verified_cancelled_ancestor(self):self.assertTrue(self.owner.successor(self.info))
 def test_direct_parent(self):self.assertTrue(self.owner.successor({**self.info,'parent':'one'}))
 def test_missing_ancestor(self):self.owner.tool.return_value['release']='other';self.assertFalse(self.owner.successor(self.info))
 def test_wrong_distribution(self):self.owner.tool.return_value['distribution']='other';self.assertFalse(self.owner.successor(self.info))
 def test_non_decreasing_parent_sequence(self):self.owner.tool.return_value['sequence']=4;self.assertFalse(self.owner.successor(self.info))
 def test_cycle(self):self.owner.tool.return_value['parent']='two';self.assertFalse(self.owner.successor(self.info))
 def test_bad_signature(self):self.owner.tool.side_effect=ValueError('signature');self.assertFalse(self.owner.successor(self.info))
 def test_downgrade(self):self.assertFalse(self.owner.successor({**self.info,'sequence':1}))
class GateChecks76(unittest.TestCase):
 def setUp(self):
  self.w=Mock();self.w.info={'release':'r','archiveSha256':'a','distribution':'d','game':'g','endpoint':'e'};self.g=Gate76(self.w);self.d={'owner':'o','generation':'1','release':'r','archive':'a','inventory':'i','game':'g','endpoint':'e','signer':'s','distribution':'d','serial':'1','phase':'TRANSITION'}
 def test_initial_gate(self):
  with patch.object(self.g,'status',return_value=self.d):self.g.prepare()
 def test_release_changed(self):
  with patch.object(self.g,'status',return_value={**self.d,'release':'wrong'}),self.assertRaisesRegex(ValueError,'CHANGED'):self.g.check()
 def test_pair_checks_both(self):
  p,m=Mock(),Mock();g=Combined76(p,m);g.check();p.check.assert_called_once();m.check.assert_called_once()
 def test_installed_needs_both(self):
  p,m=Mock(),Mock();g=Combined76(p,m);g.installed(1,2,3);p.installed.assert_called_once_with(1,2,3);m.installed.assert_called_once_with(1,2,3)
 def test_public_gate_never_mints_cohort_receipt(self):g=Gate76(Mock());self.assertIsNone(g.joined());self.assertIsNone(g.abort())
class PublicWire76(unittest.TestCase):
 def encode(self,d):
  import base64
  return ''.join(k+'='+base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')+'\n' for k,v in sorted(d.items())).encode('ascii')
 def test_eighteen_public_fields(self):
  from first58.bootstrap_wire76 import decode
  d={'key'+str(i):str(i) for i in range(18)};self.assertEqual(decode(self.encode(d)),d)
 def test_private_limit_is_unchanged(self):
  from first58.bridge67 import body
  with self.assertRaises(ValueError):body({'key'+str(i):str(i) for i in range(18)})
 def test_field_limit(self):
  from first58.bootstrap_wire76 import decode
  with self.assertRaises(ValueError):decode(self.encode({'key'+str(i):str(i) for i in range(33)}))
 def test_duplicate(self):
  from first58.bootstrap_wire76 import decode
  with self.assertRaises(ValueError):decode(b'key=MQ\nkey=Mg\n')
 def test_reserved(self):
  from first58.bootstrap_wire76 import decode
  with self.assertRaises(ValueError):decode(b'sig=MQ\n')
 def test_noncanonical(self):
  from first58.bootstrap_wire76 import decode
  with self.assertRaises(ValueError):decode(b'key=MQ==\n')
 def test_size(self):
  from first58.bootstrap_wire76 import decode
  with self.assertRaises(ValueError):decode(b'a'*32769)
if __name__=='__main__':unittest.main()
