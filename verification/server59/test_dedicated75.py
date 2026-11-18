"""Real authenticated history/queue/copies with explicit process and READY models."""
from pathlib import Path
import copy,json,sys,tempfile,time,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.dedicated75 import Owner75,History75,submit
from first58 import dedicated74 as old
from first58.bridge67 import read,write,sign
from first58.common import inventory,sha,json_write
import test_dedicated74 as fixture74

class HistoryChecks75(unittest.TestCase):
 def setUp(self):
  t=tempfile.TemporaryDirectory();self.addCleanup(t.cleanup);self.root=Path(t.name);self.key=b'h'*32;self.run=str(uuid.uuid4());self.h=History75(self.root,self.key,self.run)
 def add(self):return self.h.append('SERVER_READY',0,1,str(uuid.uuid4()),str(uuid.uuid4()),'a'*64)
 def test_round_trip_chain(self):
  self.add();self.add();other=History75(self.root,self.key,self.run);self.assertEqual(other.rows,self.h.rows);self.assertEqual(other.head,self.h.head)
 def test_previous_entry_tamper(self):
  self.add();p=self.root/'history75/0001.entry';data=p.read_bytes();p.write_bytes(data[:-2]+(b'1' if data[-2:-1]==b'0' else b'0')+data[-1:])
  with self.assertRaises(ValueError):History75(self.root,self.key,self.run)
 def test_gap_rejected(self):
  self.add();self.add();(self.root/'history75/0001.entry').unlink()
  with self.assertRaisesRegex(ValueError,'GAP'):History75(self.root,self.key,self.run)
 def test_wrong_owner_run_rejected(self):
  self.add()
  with self.assertRaisesRegex(ValueError,'BINDING'):History75(self.root,self.key,str(uuid.uuid4()))
 def test_wrong_key_rejected(self):
  self.add()
  with self.assertRaises(ValueError):History75(self.root,b'x'*32,self.run)
 def test_reordering_signed_entries_rejected(self):
  self.add();self.add();a=self.root/'history75/0001.entry';b=self.root/'history75/0002.entry';first=a.read_bytes();a.write_bytes(b.read_bytes());b.write_bytes(first)
  with self.assertRaises(ValueError):History75(self.root,self.key,self.run)
 def test_summary_is_bounded(self):
  for _ in range(12):self.add()
  self.assertEqual(len(self.h.public()),8);self.assertNotIn('previous',self.h.public()[0]);self.assertNotIn('inventory',self.h.public()[0])
 def test_existing_entry_not_rewritten(self):
  (self.root/'history75/0001.entry').write_bytes(b'keep')
  with self.assertRaisesRegex(ValueError,'EXISTS'):self.add()
  self.assertEqual((self.root/'history75/0001.entry').read_bytes(),b'keep')

class OwnerChecks75(unittest.TestCase):
 def setUp(self):
  # Reuse the existing honest file fixture, then opt in to the new protocol with its key.
  base=fixture74.OwnerBoundaries74();base.setUp();self.addCleanup(base.doCleanups);self.root=base.root;self.s=base.s;self.key=base.key;self.source=base.source;self.job=base.job
  write(self.root/'owner75',self.key,{'schema':'75','run':self.s['run']});self.owner=Owner75(self.root,self.root);self.owner.jobs={0:self.job};self.owner.report('ACTIVE_BASELINE')
 def test_request_binds_current_generation(self):
  request=submit(self.root,'STAGE','r1');d=read(self.root/'request74',self.key);self.assertEqual(d['generation'],'0');self.assertEqual(d['schema'],'75');self.assertEqual(self.owner.receive()['request'],request)
 def test_unread_request_is_not_overwritten(self):
  submit(self.root,'STAGE')
  with self.assertRaisesRegex(ValueError,'PENDING'):submit(self.root,'OFFER')
 def test_identical_retry_is_idempotent(self):
  nonce=submit(self.root,'STAGE');raw=(self.root/'request74').read_bytes();self.assertEqual(submit(self.root,'STAGE',request=nonce),nonce);self.assertEqual(raw,(self.root/'request74').read_bytes())
 def test_conflicting_retry_refused(self):
  nonce=submit(self.root,'STAGE')
  with self.assertRaisesRegex(ValueError,'CONFLICT'):submit(self.root,'OFFER',request=nonce)
 def test_stale_generation_cannot_control_new_server(self):
  submit(self.root,'COMMIT');self.owner.current=1
  with self.assertRaisesRegex(ValueError,'STALE_GENERATION'):self.owner.receive()
  self.assertEqual(read(self.root/'request74.ack',self.key)['request'],read(self.root/'request74',self.key)['request']);self.job.close.assert_not_called()
 def test_zero_nonce_refused(self):
  with self.assertRaises(ValueError):submit(self.root,'STAGE',request=str(uuid.UUID(int=0)))
 def test_busy_owner_rejects_mutation(self):
  self.owner.busy75=True;self.owner.report()
  with self.assertRaisesRegex(ValueError,'BUSY'):submit(self.root,'STAGE')
 def test_busy_owner_still_accepts_shutdown(self):
  self.owner.busy75=True;self.owner.report();submit(self.root,'SHUTDOWN');self.assertEqual(self.owner.receive()['action'],'SHUTDOWN')
 def test_heartbeat_expiry_refuses_stage(self):
  d=read(self.root/'view75',self.key);d['updated']=str(int(time.time()*1000)-20000);write(self.root/'view75',self.key,d)
  with self.assertRaisesRegex(ValueError,'STALE'):submit(self.root,'STAGE')
 def test_arbitrary_action_refused(self):
  with self.assertRaises(ValueError):submit(self.root,'EXEC')
 def test_path_label_refused(self):
  with self.assertRaises(ValueError):submit(self.root,'STAGE','../other')
 def test_stopped_owner_refused(self):
  self.owner.report('STOPPED_SAVED')
  with self.assertRaisesRegex(ValueError,'UNAVAILABLE'):submit(self.root,'STAGE')
 def test_round_not_closed_before_joined(self):
  self.owner.external=True;self.owner.last_pump={'admission':True,'joined':1,'total':2};self.owner.tool=Mock()
  with self.assertRaisesRegex(ValueError,'WAIT_FOR_JOINED'):self.owner.finish_round()
  self.owner.tool.assert_not_called()
 def test_publisher_must_stop_before_round_close(self):
  self.owner.external=True;self.owner.last_pump={'admission':True,'joined':1,'total':1};self.owner.pump_thread=Mock();self.owner.pump_thread.is_alive.return_value=True;self.owner.tool=Mock()
  with self.assertRaisesRegex(ValueError,'PUBLISHER_BUSY'):self.owner.finish_round()
  self.owner.tool.assert_not_called()
 def test_compact_view_handles_large_mod_inventory(self):
  self.owner.pending={'info':{'release':'test'},'approved':{'delta':{'added':['mod-'+str(i)+'.jar' for i in range(256)],'removed':['old-'+str(i)+'.jar' for i in range(256)]}}};self.owner.report();d=read(self.root/'view75',self.key);delta=json.loads(d['delta']);self.assertEqual(delta['addedCount'],256);self.assertEqual(len(delta['added']),12);self.assertLess((self.root/'view75').stat().st_size,32768)
 def test_generation_limit_before_staging(self):
  self.owner.current=32
  with self.assertRaisesRegex(ValueError,'GENERATION_LIMIT'):self.owner.prepare('r33')
 def test_second_generation_is_readiness_gated(self):
  self.owner.current=2;self.owner.ready_generation1=False;self.assertFalse(self.owner.advance_admission());self.assertFalse(self.owner.admit)
if __name__=='__main__':unittest.main()
