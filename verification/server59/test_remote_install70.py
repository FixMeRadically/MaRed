"""Real file policy/installation plus explicit models of source/process lifecycle."""
from pathlib import Path
import hashlib,json,sys,tempfile,unittest,uuid,zipfile
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.install70 import policy,install,files,ADAPTER
from first58.remote70 import Worker,request,action
from first58.common import inventory,json_write

def mod(path,modid='example',value='1',adapter=False):
 with zipfile.ZipFile(path,'w') as z:
  z.writestr('META-INF/neoforge.mods.toml',f'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="{modid}"\nversion="{value}"\n')
  z.writestr('data',value)
  if adapter:z.writestr(ADAPTER,b'explicit filesystem fixture, not executable game bytecode')
 return path

class Policy70(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.source=self.root/'source';(self.source/'mods').mkdir(parents=True)
  mod(self.source/'mods/mared.jar','mared',adapter=True);mod(self.source/'mods/old.jar',value='old');self.target=mod(self.root/'example.jar',value='new');self.package(self.target)
 def package(self,*jars):
  self.archive=self.root/'candidate.zip';self.info={'files':[]}
  with zipfile.ZipFile(self.archive,'w') as z:
   for jar in jars:
    data=jar.read_bytes();z.writestr('files/mods/'+jar.name,data);self.info['files'].append(dict(path='mods/'+jar.name,bytes=len(data),sha256=hashlib.sha256(data).hexdigest()))
  self.info['archiveSha256']=hashlib.sha256(self.archive.read_bytes()).hexdigest()
 def snapshot(self):return {str(p.relative_to(self.source)):p.read_bytes() for p in self.source.rglob('*') if p.is_file()}
 def test_bootstrap_is_preserved_when_absent_in_package(self):
  p=policy(self.source/'mods',self.archive,self.info);self.assertIn('mared.jar',p['after']);self.assertEqual(p['delta']['removed'],['old.jar'])
 def test_new_copy_installs_without_changing_source(self):
  before=self.snapshot();p=policy(self.source/'mods',self.archive,self.info);import shutil
  out=self.root/'generations/client.building';out.parent.mkdir();shutil.copytree(self.source,out);from first58.approval120 import issue
  from first58.install70 import approval_seal120
  token=str(__import__('uuid').uuid4());issue(self.root,'REMOTE',token,approval_seal120(self.info,p));installed=install(out,self.archive,self.info,p,approval=dict(scope=str(self.root),transaction=token))
  self.assertEqual(installed,inventory(out));self.assertEqual(files(out/'mods'),p['after']);self.assertEqual(before,self.snapshot());self.assertEqual((out/'mods/mared.jar').read_bytes(),(self.source/'mods/mared.jar').read_bytes())
 def test_install_refuses_live_or_published_profile(self):
  p=policy(self.source/'mods',self.archive,self.info)
  with self.assertRaisesRegex(ValueError,'NEW_PROFILE'):install(self.source,self.archive,self.info,p)
 def test_replacement_of_mared_is_rejected(self):
  new=mod(self.root/'mared.jar','mared','changed',True);self.package(new)
  with self.assertRaisesRegex(ValueError,'BOOTSTRAP_CHANGE'):policy(self.source/'mods',self.archive,self.info)
 def test_renamed_mared_is_not_a_bootstrap_upgrade_escape(self):
  new=mod(self.root/'renamed.jar','mared','changed',True);self.package(new)
  with self.assertRaisesRegex(ValueError,'BOOTSTRAP_CHANGE'):policy(self.source/'mods',self.archive,self.info)
 def test_missing_adapter_refused_before_source_closure(self):
  mod(self.source/'mods/mared.jar','mared',adapter=False)
  with self.assertRaisesRegex(ValueError,'BOOTSTRAP'):policy(self.source/'mods',self.archive,self.info)
 def test_duplicate_bootstrap_refused(self):
  mod(self.source/'mods/mared2.jar','mared',adapter=True)
  with self.assertRaisesRegex(ValueError,'BOOTSTRAP'):policy(self.source/'mods',self.archive,self.info)
 def test_same_bootstrap_may_be_listed_in_signed_inventory(self):
  self.package(self.source/'mods/mared.jar',self.target);p=policy(self.source/'mods',self.archive,self.info);self.assertEqual(len(p['after']),2)
 def test_frozen_dev_bootstrap_is_allowed_without_top_level_mared(self):
  (self.source/'mods/mared.jar').unlink();self.assertEqual(policy(self.source/'mods',self.archive,self.info,dev=True)['bootstrap'],{})
 def test_two_target_jars_with_same_modid_refused(self):
  other=mod(self.root/'other.jar');self.package(self.target,other)
  with self.assertRaisesRegex(ValueError,'MOD_METADATA'):policy(self.source/'mods',self.archive,self.info)
 def test_loader_changes_refused(self):
  other=mod(self.root/'loader.jar','neoforge');self.package(other)
  with self.assertRaisesRegex(ValueError,'LOADER_CHANGE'):policy(self.source/'mods',self.archive,self.info)
 def test_nested_mods_are_not_silently_dropped(self):
  (self.source/'mods/nested').mkdir()
  with self.assertRaisesRegex(ValueError,'NESTED_MODS'):policy(self.source/'mods',self.archive,self.info)
 def test_target_modified_after_approval_refused(self):
  p=policy(self.source/'mods',self.archive,self.info);mod(self.target,value='changed');self.package(self.target);import shutil
  out=self.root/'client.building';shutil.copytree(self.source,out)
  with self.assertRaisesRegex(ValueError,'SOURCE_CHANGED'):install(out,self.archive,self.info,p)
 def test_source_modified_after_approval_refused(self):
  p=policy(self.source/'mods',self.archive,self.info);import shutil
  out=self.root/'client.building';shutil.copytree(self.source,out);mod(out/'mods/old.jar',value='mutated')
  with self.assertRaisesRegex(ValueError,'SOURCE_CHANGED'):install(out,self.archive,self.info,p)
 def test_payload_hash_conflict_is_rejected(self):
  self.info['files'][0]['sha256']='f'*64
  with self.assertRaisesRegex(ValueError,'PACKAGE_CHANGED'):policy(self.source/'mods',self.archive,self.info)
 def test_payload_bound_cannot_be_bypassed_by_zip_metadata(self):
  self.info['files'][0]['bytes']=1
  with self.assertRaisesRegex(ValueError,'PACKAGE_CHANGED'):policy(self.source/'mods',self.archive,self.info)

 def test_prepared_mods_are_not_promoted_after_tampering(self):
  from first58.approval120 import issue
  from first58.install70 import approval_seal120,promote_remote_mods,write_verified_mods
  approved=policy(self.source/'mods',self.archive,self.info)
  staged=self.root/'prepared-mods70'
  write_verified_mods(self.source/'mods',staged,self.archive,self.info,approved)
  original=self.snapshot()
  target=self.root/'generations/client-1.building';target.mkdir(parents=True)
  token=str(uuid.uuid4());issue(self.root,'REMOTE',token,approval_seal120(self.info,approved))
  jar=staged/'example.jar';data=jar.read_bytes();jar.write_bytes(b'changed')
  with self.assertRaisesRegex(ValueError,'PACKAGE_CHANGED'):
   promote_remote_mods(target,staged,self.info,approved,token)
  self.assertFalse((target/'mods').exists())
  jar.write_bytes(data)
  self.assertEqual(promote_remote_mods(target,staged,self.info,approved,token),inventory(target))
  self.assertEqual(self.snapshot(),original)

class Lifecycle70(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.w=Worker.__new__(Worker);self.w.lease=Mock();self.w.lease.closed.return_value=True;self.w.lease.normal.return_value=True;self.w.root=self.root;self.w.source=self.root/'source';self.w.source.mkdir();self.w.baseline={};self.w.state=Mock();self.w.final_check=Mock();self.w.wait_action=Mock();self.w.wait=Mock();self.w.cancelled=Mock(return_value=False);self.w.verify_remote=Mock(return_value={});self.w.info={};self.w.build=Mock();self.w.start=Mock();self.w.job=Mock();self.w.job.status.return_value={'rootExited':True,'logFailed':False,'exitCode':0};self.w.status=Mock();self.w.join=Mock(return_value=True);self.w.stop=Mock();self.w.command=Mock()
 def test_build_requires_closed_source(self):
  self.w.lease.closed.return_value=False
  with self.assertRaisesRegex(ValueError,'SOURCE_EXIT'):Worker.build(self.w)
 def test_build_requires_normal_source_exit(self):
  self.w.lease.normal.return_value=False
  with self.assertRaisesRegex(ValueError,'SOURCE_EXIT'):Worker.build(self.w)
 def test_preflight_failure_never_requests_commit(self):
  self.w.final_check.side_effect=ValueError('REMOTE70_CANDIDATE');notify=Mock()
  with self.assertRaises(ValueError):self.w._activate(notify,lambda:False,Mock())
  self.w.wait_action.assert_not_called();self.w.start.assert_not_called();notify.assert_not_called()
 def test_cover_failure_never_requests_commit(self):
  with self.assertRaises(ValueError):self.w._activate(Mock(side_effect=ValueError('cover')),lambda:False,Mock())
  self.w.wait_action.assert_not_called();self.w.build.assert_not_called()
 def test_nonzero_source_exit_does_not_build_or_spawn(self):
  self.w.lease.normal.return_value=False
  with self.assertRaisesRegex(ValueError,'SOURCE_EXIT'):self.w._activate(Mock(),lambda:False,Mock())
  self.w.build.assert_not_called();self.w.start.assert_not_called()
 def test_cancel_after_exit_does_not_install(self):
  with self.assertRaises(InterruptedError):self.w._activate(Mock(),lambda:True,Mock())
  self.w.build.assert_not_called();self.w.start.assert_not_called()
 def test_changed_latest_offer_after_exit_does_not_install(self):
  self.w.verify_remote.return_value={'different':True}
  with self.assertRaisesRegex(ValueError,'CANDIDATE'):self.w._activate(Mock(),lambda:False,Mock())
  self.w.build.assert_not_called();self.w.start.assert_not_called()
 def test_new_client_runtime_failure_does_not_claim_active(self):
  self.w.wait.side_effect=[None,ValueError('REMOTE70_READY')]
  with self.assertRaisesRegex(ValueError,'READY'):self.w._activate(Mock(),lambda:False,Mock())
  self.w.join.assert_not_called();self.assertNotIn(('ACTIVE',),[c.args for c in self.w.state.call_args_list])
 def test_one_client_success_path(self):
  self.w._activate(Mock(),lambda:False,Mock());self.w.build.assert_called_once();self.w.start.assert_called_once();self.w.join.assert_called_once();self.w.state.assert_any_call('ACTIVE');self.w.state.assert_any_call('COMPLETE')

if __name__=='__main__':unittest.main()
