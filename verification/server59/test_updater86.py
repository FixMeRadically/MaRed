"""Actual HTTP range/file policy tests plus explicit Java/readiness response models. No Minecraft/native process."""
from pathlib import Path
from types import SimpleNamespace
import base64,copy,hashlib,io,json,shutil,sys,tempfile,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import http_range86,rebind86,bootstrap76
from first58.install70 import policy,files
import test_remote_install70 as fixture

class Range86(unittest.TestCase):
 def setUp(self):self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.sha=hashlib.sha256(b'0123456789').hexdigest();self.file=self.root/('client-'+self.sha+'.zip');self.file.write_bytes(b'0123456789')
 def serve(self,headers):
  h=SimpleNamespace(headers=headers,wfile=io.BytesIO(),status=None,output={});h.send_response=lambda n:setattr(h,'status',n);h.send_header=lambda k,v:h.output.update({k:v});h.end_headers=lambda:None;http_range86.serve(h,self.file);return h
 def test_full(self):h=self.serve({});self.assertEqual(h.status,200);self.assertEqual(h.wfile.getvalue(),b'0123456789')
 def test_suffix_from_offset(self):h=self.serve({'Range':'bytes=4-'});self.assertEqual(h.status,206);self.assertEqual(h.wfile.getvalue(),b'456789');self.assertEqual(h.output['Content-Range'],'bytes 4-9/10')
 def test_closed_range(self):h=self.serve({'Range':'bytes=2-5'});self.assertEqual(h.wfile.getvalue(),b'2345')
 def test_same_etag_resumes(self):self.assertEqual(self.serve({'Range':'bytes=4-','If-Range':'"'+self.sha+'"'}).status,206)
 def test_mismatched_etag_restarts(self):h=self.serve({'Range':'bytes=4-','If-Range':'"other"'});self.assertEqual(h.status,200);self.assertEqual(len(h.wfile.getvalue()),10)
 def test_weak_etag_restarts(self):self.assertEqual(self.serve({'Range':'bytes=4-','If-Range':'W/"'+self.sha+'"'}).status,200)
 def test_multi_range_rejected(self):self.assertEqual(self.serve({'Range':'bytes=0-1,4-5'}).status,416)
 def test_out_of_bounds_rejected(self):self.assertEqual(self.serve({'Range':'bytes=10-'}).status,416)
 def test_end_beyond_file_rejected(self):self.assertEqual(self.serve({'Range':'bytes=1-100'}).status,416)
 def test_negative_rejected(self):self.assertEqual(self.serve({'Range':'bytes=-5'}).status,416)
 def test_reverse_rejected(self):self.assertEqual(self.serve({'Range':'bytes=7-2'}).status,416)
 def test_excessive_integer_rejected(self):self.assertEqual(self.serve({'Range':'bytes='+'9'*50+'-'}).status,416)
 def test_metadata_not_partially_served(self):self.file=self.root/'offer.bin';self.file.write_bytes(b'offer');h=self.serve({'Range':'bytes=2-'});self.assertEqual(h.status,200);self.assertNotIn('ETag',h.output)

class JoinGate86(unittest.TestCase):
 def setUp(self):
  self.w=SimpleNamespace(spec={'clientCommand':['java']},folder=Path('/fixture'),r=dict(cache='/cache',candidate='/candidate',neoforge='21.1.264'),info=dict(release='old',archiveSha256='oldsha',distribution='d',game='g',endpoint='e'));self.g=bootstrap76.Gate76(self.w);self.d=dict(owner='owner',generation='1',release='old',archive='oldsha',inventory='inv',game='g',endpoint='e',signer='signer',distribution='d',serial='10',phase='READY',offer='old signed offer',sequence86='1')
 def answer(self,data):
  raw=''.join(k+'='+base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')+'\n' for k,v in sorted(data.items())).encode();return SimpleNamespace(returncode=0,stdout=base64.b64encode(raw).decode())
 def status(self,data):
  with patch.object(bootstrap76,'core_classpath',return_value=Path('/fixture/core.jar')),patch.object(bootstrap76.subprocess,'run',return_value=self.answer(data)):return self.g.status()
 def test_fresh_same_code_after_ready(self):self.g.runtime86=True;self.status(self.d);self.status(dict(self.d,release='renewed',archive='newsha',serial='11',offer='new signed offer',sequence86='2'));self.assertEqual(self.g.last,11)
 def test_release_change_before_runtime_refused(self):
  self.status(self.d)
  with self.assertRaisesRegex(ValueError,'CHANGED'):self.status(dict(self.d,release='renewed',serial='11'))
 def test_generation_change_refused(self):
  self.g.runtime86=True;self.status(self.d)
  with self.assertRaisesRegex(ValueError,'CHANGED'):self.status(dict(self.d,generation='2',serial='11'))
 def test_inventory_change_refused(self):
  self.g.runtime86=True;self.status(self.d)
  with self.assertRaisesRegex(ValueError,'CHANGED'):self.status(dict(self.d,inventory='other',serial='11'))
 def test_serial_rollback_refused(self):
  self.g.runtime86=True;self.status(self.d)
  with self.assertRaisesRegex(ValueError,'CHANGED'):self.status(dict(self.d,serial='9'))
 def test_release_sequence_rollback_refused(self):
  self.g.runtime86=True;self.status(dict(self.d,sequence86='3'))
  with self.assertRaisesRegex(ValueError,'ROLLBACK'):self.status(dict(self.d,serial='11',sequence86='2'))
 def test_same_sequence_changed_offer_refused(self):
  self.g.runtime86=True;self.status(self.d)
  with self.assertRaisesRegex(ValueError,'ROLLBACK'):self.status(dict(self.d,serial='11',offer='equivocated offer'))
 def test_identical_serial_changed_state_refused(self):
  self.g.runtime86=True;self.status(self.d)
  with self.assertRaisesRegex(ValueError,'EQUIVOCATION'):self.status(dict(self.d,phase='TRANSITION'))
 def test_first_join_derived_sequence_is_not_equivocation(self):
  original=dict(self.d);original.pop('sequence86');self.status(original);self.g.runtime86=True;self.status(self.d);self.assertEqual(self.g.last,10)
 def test_before_join_requires_runtime(self):
  with self.assertRaisesRegex(ValueError,'RUNTIME_REQUIRED'):self.g.before_join()
 def test_installed_requires_actual_ready_receipt(self):
  self.w.status=Mock(return_value=None)
  with self.assertRaisesRegex(ValueError,'RUNTIME_REQUIRED'):self.g.installed(Mock(),lambda:False,Mock())

class Rebind86(unittest.TestCase):
 def setUp(self):
  f=fixture.Policy70();f.setUp();self.addCleanup(f.doCleanups);self.f=f;self.root=f.root;self.folder=self.root/'worker';self.folder.mkdir();self.run=self.folder/'run';self.run.mkdir();self.cache=self.root/'cache';self.new=self.cache/'downloads'/str(uuid.uuid4());self.new.mkdir(parents=True);(self.new/'bootstrap76.cfg').write_bytes(b'explicit Java verification model');shutil.copyfile(f.archive,self.new/'client.zip');self.old=self.cache/'downloads'/str(uuid.uuid4());self.old.mkdir();(self.old/'bootstrap76.cfg').write_bytes(b'old model');self.archive=self.run/'candidate.zip';shutil.copyfile(f.archive,self.archive)
  self.info={**f.info,'game':'localhost:25565','distribution':str(uuid.uuid4()),'endpoint':'https://localhost/','release':str(uuid.uuid4()),'archiveSha256':hashlib.sha256(self.archive.read_bytes()).hexdigest(),'installAuthority':False};self.data={**self.info,'candidate':str(self.new),'release':str(uuid.uuid4())};self.w=SimpleNamespace(config=None,r=dict(cache=str(self.cache),candidate=str(self.old)),source=f.source,baseline=files(f.source/'mods'),folder=self.folder,root=self.run,spec={},info=self.info,archive=self.archive,approved=policy(f.source/'mods',f.archive,f.info),gate73=None)
  self.call=patch.object(rebind86,'call',side_effect=lambda w:copy.deepcopy(self.data));self.call.start();self.addCleanup(self.call.stop)
  def ready(g):g.last=20;g.expected=dict(owner='owner',generation='1',game='localhost:25565',endpoint='https://localhost/',distribution=self.info['distribution'],signer='signer',inventory='inventory')
  self.gate=patch.object(bootstrap76.Gate76,'prepare',autospec=True,side_effect=ready);self.gate.start();self.addCleanup(self.gate.stop)
 def test_identical_files_rebind_without_source_writes(self):
  before=self.f.snapshot();new=rebind86.refresh(self.w);self.assertEqual(self.f.snapshot(),before);self.assertEqual(new['release'],self.data['release']);self.assertEqual(self.w.r['candidate'],str(self.new));self.assertEqual(self.w.gate73.last,20)
 def test_failed_rebind_discards_only_its_temporary_copy(self):
  previous=self.archive.read_bytes();self.data['archiveSha256']='0'*64
  with self.assertRaisesRegex(ValueError,'REMOTE70_PACKAGE_CHANGED'):
   rebind86.refresh(self.w)
  self.assertEqual(self.archive.read_bytes(),previous)
  self.assertEqual(self.w.r['candidate'],str(self.old))
  self.assertEqual(list(self.run.glob('.candidate86-*')),[])
 def test_after_start_cannot_rebind(self):
  self.w.config=dict(inventory='runtime')
  with self.assertRaisesRegex(ValueError,'PRIVATE_ROUND'):rebind86.refresh(self.w)
 def test_cohort_request_never_rebound(self):
  (self.folder/'control73.cfg').write_bytes(b'private cohort')
  with self.assertRaisesRegex(ValueError,'PRIVATE_ROUND'):rebind86.refresh(self.w)
 def test_cohort_runtime_never_rebound(self):
  (self.run/'control73.cfg').write_bytes(b'private cohort')
  with self.assertRaisesRegex(ValueError,'PRIVATE_ROUND'):rebind86.refresh(self.w)
 def test_unknown_gate_cannot_be_replaced(self):
  self.w.gate73=Mock()
  with self.assertRaisesRegex(ValueError,'PRIVATE_ROUND'):rebind86.refresh(self.w)
 def test_changed_content_refused(self):
  self.data['files']=[{**self.data['files'][0],'sha256':'a'*64}]
  with self.assertRaisesRegex(ValueError,'CONTENT_CHANGED'):rebind86.refresh(self.w)
 def test_changed_server_refused(self):
  self.data['game']='other:25565'
  with self.assertRaisesRegex(ValueError,'CONTENT_CHANGED'):rebind86.refresh(self.w)
 def test_path_outside_cache_refused(self):
  self.data['candidate']=str(self.root/'outside'/str(uuid.uuid4()))
  with self.assertRaisesRegex(ValueError,'CONTENT_CHANGED'):rebind86.refresh(self.w)
 def test_modified_source_refused(self):
  (self.w.source/'mods/new.jar').write_bytes(b'changed')
  with self.assertRaisesRegex(ValueError,'SOURCE_CHANGED'):rebind86.refresh(self.w)
 def test_equal_serial_changed_state_cannot_rebind(self):
  self.w.gate73=bootstrap76.Gate76(self.w);self.w.gate73.last=20;self.w.gate73.expected=dict(owner='old')
  with self.assertRaisesRegex(ValueError,'CONTEXT_CHANGED'):rebind86.refresh(self.w)
  self.assertEqual(self.w.r['candidate'],str(self.old))
 def test_monotonic_gate_not_reset(self):
  self.w.gate73=bootstrap76.Gate76(self.w);self.w.gate73.last=21
  with self.assertRaisesRegex(ValueError,'CONTEXT_CHANGED'):rebind86.refresh(self.w)
  self.assertEqual(self.w.r['candidate'],str(self.old))
if __name__=='__main__':unittest.main()
