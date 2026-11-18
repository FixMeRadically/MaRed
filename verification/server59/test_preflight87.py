"""Real read-only profile scans/copies/disk probes and explicit source-Job barrier models."""
from filesystem_capabilities124 import fixture_symlink
from pathlib import Path
from types import SimpleNamespace
import errno,io,json,os,shutil,sys,tempfile,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import preflight87 as pf
from first58.desktop66 import live_client_snapshot
from first58.remote70 import Worker
from first58.repeat71 import publish
from first58.bridge67 import read
import test_remote_install70 as fixture

class Disk87(unittest.TestCase):
 def setUp(self):
  f=fixture.Policy70();f.setUp();self.addCleanup(f.doCleanups);self.f=f;self.root=f.root;self.source=f.source;self.dest=self.root/'destination';self.dest.mkdir();self.approved=__import__('first58.install70',fromlist=['policy']).policy(f.source/'mods',f.archive,f.info);self.info=f.info
 def runcheck(self,**kw):
  return pf.assess(self.source,self.dest,self.info,self.approved,self.f.archive,**kw)
 def space(self,n):return patch.object(pf.shutil,'disk_usage',return_value=SimpleNamespace(free=n))
 def test_read_only_source_and_no_worlds(self):
  (self.source/'saves/world').mkdir(parents=True);(self.source/'saves/world/level.dat').write_bytes(b'world');(self.source/'logs').mkdir();(self.source/'logs/latest.log').write_bytes(b'log');before=self.f.snapshot();r=self.runcheck();self.assertEqual(self.f.snapshot(),before);self.assertEqual(r['files'],2);self.assertEqual(r['stage'],'CHECKED');self.assertFalse(list(self.dest.iterdir()))
 def test_actual_copier_matches_scanner_selection(self):
  for name in ('config/a.txt','resourcepacks/a.zip','mods.before70/no.jar','remote70-temp/no','other/saves/no','other/config/b.txt'):
   p=self.source/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'fixture')
  r=self.runcheck();target=self.root/'copy';copied=live_client_snapshot(self.source,target,excluded={'mods.before70','remote70-temp'});self.assertEqual(r['files'],copied['files']);self.assertEqual(r['profileBytes'],copied['bytes']);self.assertFalse((target/'other/saves').exists())
 def test_root_only_exclusion_does_not_hide_nested_normal_name(self):
  p=self.source/'other/mods.before70/keep.txt';p.parent.mkdir(parents=True);p.write_bytes(b'keep');self.assertEqual(self.runcheck()['files'],3)
 def test_bootstrap_omitted_by_package_is_counted(self):
  r=self.runcheck();self.assertGreaterEqual(r['payloadBytes'],sum(i['bytes'] for i in self.info['files'])+(self.source/'mods/mared.jar').stat().st_size)
 def test_peak_includes_profile_payload_archive_and_margin(self):
  r=self.runcheck();self.assertEqual(r['requiredBytes'],r['profileAllocated']+r['payloadBytes']+r['devBytes']+2*r['archiveBytes']+pf.MARGIN)
 def test_disk_shortage_refuses_without_mutating_source(self):
  before=self.f.snapshot()
  with self.space(0),self.assertRaisesRegex(pf.PreparationError,'DISK_SPACE') as e:self.runcheck()
  self.assertEqual(e.exception.report['stage'],'REFUSED');self.assertEqual(e.exception.report['availableBytes'],0);self.assertEqual(before,self.f.snapshot());self.assertFalse(list(self.dest.iterdir()))
 def test_exact_required_free_is_allowed(self):
  required=self.runcheck()['requiredBytes']
  with self.space(required):self.assertEqual(self.runcheck()['stage'],'CHECKED')
 def test_one_byte_short_refused(self):
  required=self.runcheck()['requiredBytes']
  with self.space(required-1),self.assertRaises(pf.PreparationError):self.runcheck()
 def test_write_access_probe_failure(self):
  with patch.object(pf.tempfile,'mkstemp',side_effect=PermissionError(errno.EACCES,'private path token')),self.assertRaisesRegex(pf.PreparationError,'WRITE_ACCESS') as e:self.runcheck()
  self.assertNotIn('token',str(e.exception));self.assertFalse(list(self.dest.iterdir()))
 def test_disk_full_during_probe_is_safely_reported(self):
  with patch.object(pf.os,'fsync',side_effect=OSError(errno.ENOSPC,'private')),self.assertRaisesRegex(pf.PreparationError,'DISK_SPACE'):self.runcheck()
  self.assertFalse(list(self.dest.iterdir()))
 def test_read_sharing_violation_safe_reason(self):
  error=PermissionError(errno.EACCES,'private accessToken=secret');self.assertEqual(pf.error_code(error),'PREFLIGHT87_READ_ACCESS');self.assertEqual(pf.error_code(error,True),'PREFLIGHT87_WRITE_ACCESS')
 def test_walk_permission_failure_is_not_silently_skipped(self):
  def denied(*args,**kwargs):kwargs['onerror'](PermissionError(errno.EACCES,'private folder'));yield
  with patch.object(pf.os,'walk',side_effect=denied),self.assertRaisesRegex(pf.PreparationError,'READ_ACCESS'):self.runcheck()
 def test_linked_file_refused(self):
  fixture_symlink(self.source/'alias',self.source/'mods/mared.jar')
  with self.assertRaisesRegex(pf.PreparationError,'LINKED_PATH'):self.runcheck()
 def test_linked_directory_refused(self):
  fixture_symlink(self.source/'alias',self.source/'mods',target_is_directory=True)
  with self.assertRaisesRegex(pf.PreparationError,'LINKED_PATH'):self.runcheck()
 def test_excluded_world_symlink_is_never_followed(self):
  fixture_symlink(self.source/'saves',self.root,target_is_directory=True);self.assertEqual(self.runcheck()['files'],2)
 @unittest.skipUnless(hasattr(os,'mkfifo'),'requires FIFO for actual special-file rejection')
 def test_fifo_refused_without_blocking_open(self):
  os.mkfifo(self.source/'pipe')
  with self.assertRaisesRegex(pf.PreparationError,'SPECIAL_FILE'):self.runcheck()
 def test_file_budget_matches_copier(self):
  with patch.object(pf,'MAX_FILES',1):
   with self.assertRaisesRegex(pf.PreparationError,'PROFILE_LIMIT'):self.runcheck()
   with self.assertRaisesRegex(ValueError,'PROFILE_LIMIT'):live_client_snapshot(self.source,self.root/'copy')
 def test_byte_budget_refused(self):
  with patch.object(pf,'MAX_BYTES',1),self.assertRaisesRegex(pf.PreparationError,'PROFILE_LIMIT'):self.runcheck()
 def test_node_budget_refused(self):
  with patch.object(pf,'MAX_NODES',1),self.assertRaisesRegex(pf.PreparationError,'PROFILE_LIMIT'):self.runcheck()
 def test_depth_budget_refused(self):
  with patch.object(pf,'MAX_DEPTH',0),self.assertRaisesRegex(pf.PreparationError,'PROFILE_LIMIT'):self.runcheck()
 def test_cancel_before_scan(self):
  with self.assertRaises(InterruptedError):self.runcheck(cancel=lambda:True)
  self.assertFalse(list(self.dest.iterdir()))
 def test_cancel_mid_scan(self):
  n=[0]
  def cancel():n[0]+=1;return n[0]>3
  with self.assertRaises(InterruptedError):self.runcheck(cancel=cancel)
 def test_progress_contains_only_safe_metrics(self):
  seen=[];self.runcheck(progress=seen.append);self.assertTrue(seen);self.assertEqual(seen[0]['stage'],'SCANNING');self.assertEqual(seen[-1]['stage'],'CHECKED')
  for report in seen:self.assertEqual(pf.public_report(report),report);self.assertNotIn(str(self.source),json.dumps(report));self.assertNotIn('accessToken',json.dumps(report))
 def test_dev_snapshot_copy_counted(self):
  dev=self.root/'dev';dev.mkdir();(dev/'core.jar').write_bytes(b'core');r=self.runcheck(dev=dev);self.assertGreaterEqual(r['devBytes'],2*pf.ALLOCATION)
 def test_destination_inside_source_refused(self):
  self.dest=self.source/'bad';self.dest.mkdir()
  with self.assertRaisesRegex(pf.PreparationError,'INPUT'):self.runcheck()
 def test_unknown_report_fields_not_for_ui(self):
  self.assertIsNone(pf.public_report(dict(self.runcheck(),accessToken='secret')))
 def test_boolean_metric_not_accepted(self):
  self.assertIsNone(pf.public_report(dict(self.runcheck(),files=True)))
 def test_unsafe_archive_size_refused(self):
  with self.f.archive.open('wb') as f:f.truncate(301*1024**2)
  with self.assertRaisesRegex(pf.PreparationError,'INPUT'):self.runcheck()
 def test_growth_during_copy_is_bounded(self):
  import first58.desktop66 as copier
  original=copier.sha
  def grow(p,*args):
   digest=original(p,*args)
   if p==self.source/'mods/mared.jar':p.write_bytes(p.read_bytes()+b'growth')
   return digest
  with patch.object(copier,'sha',side_effect=grow),self.assertRaisesRegex(ValueError,'changed during'):live_client_snapshot(self.source,self.root/'copy')

class Barrier87(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.w=Worker.__new__(Worker);self.w.root=self.root;self.w.source=self.root/'source';(self.w.source/'mods').mkdir(parents=True);self.w.lease=Mock();self.w.lease.closed.return_value=False;self.w.lease.normal.return_value=True;self.w.cancelled=Mock(return_value=False);self.w.baseline={};self.w.info={};self.w.verify_remote=Mock(return_value={});self.w.require_source=Mock();self.w.approved={'after':{}};self.w.staged_mods=self.root/'prepared-mods70';self.w.staged_mods.mkdir();self.w.spec={};self.w.archive=self.root/'archive';self.w.archive.write_bytes(b'archive');self.w.state=Mock();self.w.phase='PREPARING'
 def test_final_check_disk_failure_never_requests_source_stop(self):
  self.w.check_storage87=Mock(side_effect=ValueError('PREFLIGHT87_DISK_SPACE'));self.w.wait_action=Mock();self.w.build=Mock();self.w.start=Mock()
  with patch('first58.remote70.sha',return_value='sha'),patch('first58.remote70.files',return_value={}),patch('first58.remote70.policy',return_value={}):
   self.w.info={'archiveSha256':'sha'};self.w.verify_remote.return_value=self.w.info
   with self.assertRaisesRegex(ValueError,'DISK_SPACE'):self.w._activate(Mock(),lambda:False,Mock())
  self.w.wait_action.assert_not_called();self.w.build.assert_not_called();self.w.start.assert_not_called()
 def test_fresh_release_check_follows_potentially_slow_profile_scan(self):
  events=[];self.w.check_storage87=Mock(side_effect=lambda **kw:events.append('scan'));self.w.info={'archiveSha256':'sha'}
  def fresh():events.append('fresh');return self.w.info
  self.w.verify_remote=Mock(side_effect=fresh)
  with patch('first58.remote70.sha',return_value='sha'),patch('first58.remote70.files',return_value={}),patch('first58.remote70.policy',return_value={}):self.w.final_check()
  self.assertEqual(events,['scan','fresh'])
 def test_build_rechecks_after_normal_source_exit(self):
  self.w.lease.closed.return_value=True;self.w.check_storage87=Mock(side_effect=ValueError('PREFLIGHT87_DISK_SPACE'))
  with self.assertRaisesRegex(ValueError,'DISK_SPACE'):self.w.build()
  self.assertFalse((self.root/'generations/client-1.building').exists())
 def test_build_still_requires_closed_source(self):
  self.w.check_storage87=Mock()
  with self.assertRaisesRegex(ValueError,'SOURCE_EXIT'):self.w.build()
  self.w.check_storage87.assert_not_called()
 def test_build_still_requires_normal_exit(self):
  self.w.lease.closed.return_value=True;self.w.lease.normal.return_value=False;self.w.check_storage87=Mock()
  with self.assertRaisesRegex(ValueError,'SOURCE_EXIT'):self.w.build()
  self.w.check_storage87.assert_not_called()
 def test_cancel_during_build_never_makes_published_generation(self):
  self.w.lease.closed.return_value=True;self.w.cancelled.return_value=True;self.w.check_storage87=Mock(side_effect=InterruptedError('REMOTE70_CANCELLED'))
  with self.assertRaises(InterruptedError):self.w.build()
  self.assertFalse((self.root/'generations/client-1').exists())
 def test_report_reserve_released_before_failure_write(self):
  self.w.reserve_report87();p=self.root/'report-reserve87';self.assertEqual(p.stat().st_size,256*1024);self.w.folder=self.root;self.w.id=str(uuid.uuid4());self.w.record_failure(OSError(errno.ENOSPC,'token=private'));self.assertFalse(p.exists());self.assertIn('мест',self.w.failure_reason);self.assertNotIn('token',self.w.failure_reason);self.assertTrue((self.root/'failure70.json').is_file())
 def test_failure_report_write_failure_does_not_mask_original_reason(self):
  self.w.reserve_report87();self.w.folder=self.root;self.w.state=Mock(side_effect=OSError(errno.ENOSPC,'private'));self.w.record_failure(OSError(errno.ENOSPC,'token=private'));self.assertIn('мест',self.w.failure_reason)
 def test_unknown_preexisting_reserve_not_overwritten(self):
  p=self.root/'report-reserve87';p.write_bytes(b'user file')
  with self.assertRaises(FileExistsError):self.w.reserve_report87()
  self.w.release_report87();self.assertEqual(p.read_bytes(),b'user file')
 def test_repeat_heartbeat_metrics_remain_authenticated(self):
  owner=SimpleNamespace(config=dict(run=str(uuid.uuid4()),account=str(uuid.uuid4()),inventory='a'*64),root=self.root,key=b'a'*32);cycle=SimpleNamespace(approved=dict(delta={}),root=self.root/'next',preflight87={**{k:0 for k in pf.METRICS},'schema':87,'stage':'CHECKED','code':''})
  publish(owner,str(uuid.uuid4()),'READY',cycle);d=read(self.root/'repeat71.status',owner.key);self.assertEqual(json.loads(d['preflight87'])['schema'],87);self.assertEqual(d['inventory'],owner.config['inventory'])
if __name__=='__main__':unittest.main()
