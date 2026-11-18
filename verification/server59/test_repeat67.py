"""Local bridge and saved-file guards; process/game acknowledgements are explicit models."""
from filesystem_capabilities124 import fixture_symlink
from pathlib import Path
import hashlib,hmac,tempfile,sys,unittest,uuid
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import bridge67
from first58.repeat67 import Coordinator,validate_request,copy_files
from first58.common import atomic,sign,record

class BridgeTests(unittest.TestCase):
 def setUp(self):self.key=b'k'*32
 def test_unicode_spaces_empty_values_roundtrip(self):
  data={'jar':'C:/моды/my mod.jar','sha':'','action':'PREPARE'}
  self.assertEqual(bridge67.verify(self.key,bridge67.sign(self.key,data)),data)
 def test_changed_message_refused(self):
  data=bridge67.sign(self.key,{'action':'PREPARE'})
  with self.assertRaises(ValueError):bridge67.verify(self.key,data.replace(b'UFJFUEFSRQ',b'UFJFUEFSRA'))
 def test_wrong_key_refused(self):
  with self.assertRaises(ValueError):bridge67.verify(b'z'*32,bridge67.sign(self.key,{'action':'ARM'}))
 def test_reordered_even_with_valid_mac_refused(self):
  raw=b'z=eg\na=YQ\n';signed=raw+b'mac='+hmac.new(self.key,raw,hashlib.sha256).hexdigest().encode()+b'\n'
  with self.assertRaises(ValueError):bridge67.verify(self.key,signed)
 def test_duplicate_even_with_valid_mac_refused(self):
  raw=b'a=YQ\na=YQ\n';signed=raw+b'mac='+hmac.new(self.key,raw,hashlib.sha256).hexdigest().encode()+b'\n'
  with self.assertRaises(ValueError):bridge67.verify(self.key,signed)
 def test_padded_noncanonical_base64_refused(self):
  raw=b'a=YQ==\n';signed=raw+b'mac='+hmac.new(self.key,raw,hashlib.sha256).hexdigest().encode()+b'\n'
  with self.assertRaises(ValueError):bridge67.verify(self.key,signed)
 def test_oversized_message_and_key_refused(self):
  with self.assertRaises(ValueError):bridge67.sign(b'x',{'a':'v'})
  with self.assertRaises(ValueError):bridge67.sign(self.key,{'a':'x'*65537})
  with self.assertRaises(ValueError):bridge67.verify(self.key,b'x'*131073)
 def request(self):return {'instance':str(uuid.uuid4()),'run':str(uuid.uuid4()),'account':str(uuid.uuid4()),'nonce':str(uuid.uuid4()),'action':'PREPARE','jar':'','sha':'','side':'BOTH'}
 def test_bound_request_accepted(self):
  r=self.request();self.assertIs(validate_request(r,r['instance'],r),r)
 def test_old_run_account_and_instance_refused(self):
  r=self.request()
  for key in ('instance','run','account'):
   bad=dict(r);bad[key]=str(uuid.uuid4())
   with self.assertRaises(ValueError):validate_request(bad,r['instance'],r)
 def test_extra_fields_invalid_action_or_hash_refused(self):
  r=self.request()
  for bad in [{**r,'shell':'cmd'},{**r,'action':'RUN'},{**r,'jar':'one.jar','sha':'wrong'}]:
   with self.assertRaises(ValueError):validate_request(bad,r['instance'],r)

class SavedPairTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
  self.old=Mock();self.old.root=self.root;self.old.key=b'k'*32;self.old.jobs={};self.old.seq={('server',1):4,('client',1):3};self.old.config={'run':str(uuid.uuid4()),'account':str(uuid.uuid4()),'server1':'a'*64,'client1':'b'*64}
  self.coordinator=Coordinator.__new__(Coordinator)
  self.new=Mock();self.new.root=self.root/'new';(self.new.root/'generations').mkdir(parents=True)
 def receipts(self):
  for role,phase in [('server','STOPPED_SAVED'),('client','STOP_REQUESTED')]:
   nonce=str(uuid.uuid4());seq=self.old.seq[role,1]
   atomic(self.root/f'{role}.1.command',sign(self.old.key,record(self.old.config,role,1,'STOP',seq,nonce)))
   atomic(self.root/f'{role}.1.status',sign(self.old.key,record(self.old.config,role,1,phase,seq,nonce)))
 def test_world_copy_refused_with_live_job(self):
  self.old.jobs={('server',1):object()}
  with self.assertRaisesRegex(ValueError,'still active'):self.coordinator.publish_pair(self.old,1,self.new)
  self.assertFalse((self.new.root/'generations/server-1.building').exists())
 def test_world_copy_refused_without_save_receipts(self):
  with self.assertRaisesRegex(ValueError,'receipt mismatch'):self.coordinator.publish_pair(self.old,1,self.new)
  self.assertFalse((self.new.root/'generations/server-1.building').exists())
 def test_wrong_nonce_receipt_refused(self):
  self.receipts();atomic(self.root/'server.1.status',sign(self.old.key,record(self.old.config,'server',1,'STOPPED_SAVED',4,str(uuid.uuid4()))))
  with self.assertRaisesRegex(ValueError,'receipt mismatch'):self.coordinator.publish_pair(self.old,1,self.new)
 def test_wrong_inventory_receipt_refused(self):
  self.receipts();from first58.common import verify
  m=verify(self.old.key,(self.root/'server.1.status').read_bytes());m['inventory']='c'*64;atomic(self.root/'server.1.status',sign(self.old.key,m))
  with self.assertRaisesRegex(ValueError,'receipt mismatch'):self.coordinator.publish_pair(self.old,1,self.new)
 def test_workspace_copy_omits_build_cache_but_preserves_sources(self):
  source=self.root/'source';(source/'project/src').mkdir(parents=True);(source/'project/build').mkdir();(source/'project/.gradle').mkdir();(source/'project/src/Main.java').write_text('source');(source/'project/build/old.jar').write_bytes(b'old')
  out=copy_files(source,self.root/'workspace',{'build','.gradle'})
  self.assertTrue((out/'project/src/Main.java').exists());self.assertFalse((out/'project/build').exists());self.assertFalse((out/'project/.gradle').exists())
 def test_staging_server_excludes_live_world(self):
  source=self.root/'server';(source/'world/region').mkdir(parents=True);(source/'world/region/0.mca').write_bytes(b'live');(source/'server.properties').write_text('properties')
  out=copy_files(source,self.root/'template',{'world'})
  self.assertFalse((out/'world').exists());self.assertTrue((out/'server.properties').exists())
 def test_preexisting_target_refused(self):
  source=self.root/'source';source.mkdir();target=self.root/'target';target.mkdir()
  with self.assertRaises(ValueError):copy_files(source,target)
 def test_disjoint_copy_and_source_symlink_refused(self):
  source=self.root/'source';source.mkdir();fixture_symlink(source/'link',self.root/'server.1.status')
  with self.assertRaises(ValueError):copy_files(source,source/'nested')
  with self.assertRaises(ValueError):copy_files(source,self.root/'copy')
 def test_copy_checkpoint_cancels_before_reading_world(self):
  source=self.root/'source';source.mkdir()
  def cancel():raise InterruptedError('cancel')
  with self.assertRaises(InterruptedError):copy_files(source,self.root/'copy',checkpoint=cancel)


class ActivationTests(unittest.TestCase):
 def setUp(self):
  self.c=Coordinator.__new__(Coordinator);self.old=Mock();self.new=Mock();self.old.jobs={('server',1):Mock(),('client',1):Mock()};self.old.config={};self.new.config={}
  self.c.active=self.old;self.c.generation=1;self.c.pending={'pilot':self.new};self.c.verify_pending=Mock();self.c.consent=Mock(return_value=True);self.c.checkpoint=Mock();self.c.publish=Mock();self.c.publish_pair=Mock()
 def test_decline_does_not_close_either_game(self):
  self.c.consent.return_value=False;self.c.activate();self.old.stop.assert_not_called();self.new.start.assert_not_called();self.c.publish_pair.assert_not_called();self.assertIs(self.c.active,self.old);self.assertIsNone(self.c.pending)
 def test_invalid_package_never_requests_consent_or_stop(self):
  self.c.verify_pending.side_effect=ValueError('package changed')
  with self.assertRaisesRegex(ValueError,'package changed'):self.c.activate()
  self.c.consent.assert_not_called();self.old.stop.assert_not_called();self.new.start.assert_not_called()
 def test_package_changed_after_consent_does_not_stop(self):
  self.c.verify_pending.side_effect=[None,ValueError('package changed')]
  with self.assertRaisesRegex(ValueError,'package changed'):self.c.activate()
  self.old.stop.assert_not_called();self.c.publish_pair.assert_not_called()
 def test_cancel_after_consent_does_not_stop(self):
  self.c.checkpoint.side_effect=InterruptedError('cancel')
  with self.assertRaises(InterruptedError):self.c.activate()
  self.old.stop.assert_not_called();self.c.publish_pair.assert_not_called()
 def test_missing_owner_job_does_not_stop_survivor(self):
  del self.old.jobs['server',1]
  with self.assertRaisesRegex(ValueError,'Missing owner jobs'):self.c.activate()
  self.old.stop.assert_not_called();self.new.start.assert_not_called()
 def test_cover_failure_does_not_start_shutdown(self):
  self.old.notify.side_effect=ValueError('cover absent')
  with self.assertRaisesRegex(ValueError,'cover absent'):self.c.activate()
  self.old.stop.assert_not_called();self.old.cleanup.assert_not_called();self.new.cleanup.assert_not_called()
 def test_shutdown_failure_cleans_both_owners_without_world_publish(self):
  self.old.stop.side_effect=ValueError('save failed');self.old.cleanup.side_effect=ValueError('secondary cleanup');self.new.diagnostics.failure.return_value=RuntimeError('reported save failed')
  with self.assertRaisesRegex(RuntimeError,'reported save failed'):self.c.activate()
  self.c.publish_pair.assert_not_called();self.old.cleanup.assert_called_once();self.new.cleanup.assert_called_once();self.new.start.assert_not_called()
 def test_updated_server_failure_never_resumes_old_pair(self):
  self.new.start.side_effect=ValueError('server startup failed');self.new.diagnostics.failure.return_value=RuntimeError('reported startup failed')
  with self.assertRaisesRegex(RuntimeError,'reported startup failed'):self.c.activate()
  self.old.start.assert_not_called();self.c.publish_pair.assert_called_once();self.old.cleanup.assert_called_once();self.new.cleanup.assert_called_once()
 def test_success_starts_only_next_generation_one(self):
  self.new.join.return_value=True;self.c.activate()
  self.assertEqual(self.new.start.call_args_list,[__import__('unittest').mock.call('server',1),__import__('unittest').mock.call('client',1)])
  self.assertEqual(self.old.stop.call_args_list,[__import__('unittest').mock.call('client',1),__import__('unittest').mock.call('server',1)])
  self.assertIs(self.c.active,self.new);self.assertIsNone(self.c.pending);self.c.publish.assert_called_with('ACTIVE')

if __name__=='__main__':unittest.main(verbosity=2)
