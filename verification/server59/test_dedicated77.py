"""Recovery77: real files, HMACs, locks and copy boundaries; process/Java21 init explicitly modeled."""
from filesystem_capabilities124 import fixture_symlink
from pathlib import Path
import copy,json,os,subprocess,sys,tempfile,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import dedicated77 as r,dedicated74 as b
from first58.dedicated75 import History75
from first58.bridge67 import read,write
from first58.common import inventory,sha,json_write
import test_dedicated74 as f

class Recovery77(unittest.TestCase):
 def setUp(self):
  base=f.OwnerBoundaries74();base.setUp();self.addCleanup(base.doCleanups);self.base=base;self.root=base.root;self.s=base.s;self.key=base.key;self.source=base.source;self.saved=base.saved();(self.root/'publisher73').mkdir();(self.root/'publisher73/public.der').write_bytes(b'public');(self.root/'publisher73/private.der').write_bytes(b'private');(self.root/'releases').mkdir();History75(self.root,self.key,self.s['run']).append('BASELINE_READY',0,0)
  self.p=r.checkpoint(self.root,self.s,self.key,0);self.target=self.root.parent/(self.root.name+'-new');self.addCleanup(lambda:__import__('shutil').rmtree(self.target,ignore_errors=True))
 def row(self):return r.catalog(self.root)['rows'][0]
 def init_model(self,project,source,target,java,publisher=None,recovering=False,source_locked=False):
  # Java21/native private-ACL bootstrap is not available in this Linux file fixture.
  target.mkdir();(target/'import77.pending').write_text('77');(target/'generations').mkdir();(target/'releases').mkdir();(target/'stages').mkdir();b.copy_tree(source,target/'generations/server-0','world');s=copy.deepcopy(self.s);s['run']=str(uuid.uuid4());s['source']=str(source);s['initialInventory']=inventory(target/'generations/server-0');key=b'n'*32;(target/'owner74.key').write_bytes(key);json_write(target/'settings.json',s);write(target/'settings74',key,dict(schema='74',sha=sha(target/'settings.json')));b.session(target,s,key,0,s['initialInventory']);(target/'publisher73').mkdir()
  for n in ('public.der','private.der'):(target/'publisher73'/n).write_bytes((publisher/n).read_bytes())
  for v in (75,76,77):write(target/f'owner{v}',key,dict(schema=str(v),run=s['run']))
  return target
 def recover(self,g=0,ticket=None,older=False):
  ticket=ticket or next(x['ticket'] for x in r.catalog(self.root)['rows'] if x['generation']==g)
  with patch.object(r,'initialize',self.init_model):return r.recover(self.root,self.root,g,ticket,self.target,Path('java'),older)
 def test_clean_catalog_never_prints_key_or_paths_to_private_identity(self):
  view=r.catalog(self.root);self.assertTrue(view['rows'][0]['eligible']);self.assertEqual(view['latest'],0);self.assertNotIn('private',json.dumps(view));self.assertNotIn(self.key.hex(),json.dumps(view))
 def test_old_owner_without_checkpoint_is_listed_but_not_eligible(self):
  (self.root/'saved77-0').unlink();self.assertEqual(self.row()['reason'],'DEDICATED77_NO_CLEAN_CHECKPOINT');self.assertFalse(self.row()['eligible'])
 def test_receipt_phase_not_enough_without_matching_nonce(self):
  d=read(self.root/'server-0.status',self.key);d['request']=str(uuid.uuid4());write(self.root/'server-0.status',self.key,d);self.assertFalse(self.row()['eligible'])
 def test_mod_change_disables_recovery(self):
  (self.source/'mods/mared.jar').write_bytes(b'changed');self.assertEqual(self.row()['reason'],'DEDICATED77_INVENTORY_CHANGED')
 def test_checkpoint_forgery_disables_recovery(self):
  write(self.root/'saved77-0',b'x'*32,self.p);self.assertFalse(self.row()['eligible'])
 def test_status_hmac_forgery_disables_recovery(self):
  write(self.root/'server-0.status',b'x'*32,self.saved);self.assertFalse(self.row()['eligible'])
 def test_changed_world_rejected_at_copy_boundary(self):
  row=self.row();(self.source/'world/level.dat').write_bytes(b'new unknown data')
  with self.assertRaisesRegex(ValueError,'PROFILE_CHANGED'):self.recover(ticket=row['ticket'])
  self.assertFalse(self.target.exists())
 def test_changed_config_rejected_at_copy_boundary(self):
  row=self.row();(self.source/'config/latest.txt').write_text('manual change')
  with self.assertRaisesRegex(ValueError,'PROFILE_CHANGED'):self.recover(ticket=row['ticket'])
 def test_session_change_invalidates_selection(self):
  row=self.row();History75(self.root,self.key,self.s['run']).append('STOPPING',0,0)
  with self.assertRaisesRegex(ValueError,'SELECTION_CHANGED'):self.recover(ticket=row['ticket'])
 def test_world_copy_and_private_identity_preserved_new_run(self):
  before=b.tree(self.source,'world');result=self.recover();self.assertEqual(result['generation'],0);self.assertEqual(b.tree(self.source,'world'),before);self.assertEqual(b.tree(self.target/'generations/server-0','world'),before);s,key=b.settings(self.target);self.assertNotEqual(s['run'],self.s['run']);self.assertNotEqual(key,self.key);self.assertTrue((self.target/'recovery77.ready').is_file());self.assertFalse((self.target/'import77.pending').exists());self.assertFalse((self.target/'control73').exists());self.assertEqual((self.target/'publisher73/private.der').read_bytes(),b'private')
 def test_previous_root_cannot_fork_twice(self):
  self.recover();self.assertEqual(self.row()['reason'],'DEDICATED77_ALREADY_RECOVERED')
 def test_source_and_target_cannot_overlap(self):
  with self.assertRaisesRegex(ValueError,'DISJOINT'):r.recover(self.root,self.root,0,self.row()['ticket'],self.root/'nested',Path('java'))
 def test_existing_target_not_replaced(self):
  self.target.mkdir();(self.target/'keep').write_text('keep')
  with self.assertRaisesRegex(ValueError,'DISJOINT'):self.recover()
  self.assertEqual((self.target/'keep').read_text(),'keep')
 def add_generation(self,n,clean=False):
  dest=self.root/f'generations/server-{n}';b.copy_tree(self.source,dest,'world');(dest/'world/session.lock').write_bytes(b'0');b.session(self.root,self.s,self.key,n,inventory(dest));cmd=read(self.root/'server-0.command',self.key);cmd['generation']=str(n);write(self.root/f'server-{n}.command',self.key,cmd);d=dict(self.saved,generation=str(n));write(self.root/f'server-{n}.status',self.key,d)
  if clean:r.checkpoint(self.root,self.s,self.key,n)
 def test_newer_incomplete_generation_not_silently_hidden(self):
  self.add_generation(1);view=r.catalog(self.root);self.assertEqual(view['latest'],1);self.assertFalse(view['rows'][0]['eligible']);self.assertTrue(view['rows'][1]['eligible']);self.assertFalse(view['rows'][1]['latest'])
 def test_older_requires_explicit_choice(self):
  self.add_generation(1)
  with self.assertRaisesRegex(ValueError,'OLDER_WORLD'):self.recover(g=0)
  self.assertFalse(self.target.exists())
 def test_explicit_older_copy_does_not_delete_newer_world(self):
  self.add_generation(1);self.recover(older=True);self.assertTrue((self.root/'generations/server-1/world/level.dat').is_file())
 def test_nonzero_baseline_keeps_generation_and_does_not_duplicate_world(self):
  self.add_generation(2,True);self.recover(g=2);s,key=b.settings(self.target);self.assertEqual(s['initialGeneration'],2);self.assertTrue((self.target/'generations/server-2/world/level.dat').is_file());self.assertFalse((self.target/'generations/server-0').exists());self.assertEqual(read(self.target/'server-2.session',key)['generation'],'2')
 def test_receipt_without_normal_exit_never_seals(self):
  owner=r.Owner77.__new__(r.Owner77);owner.jobs={0:Mock()};owner.jobs[0].status.return_value=dict(rootExited=True,exitCode=1);owner.jobs[0].empty.return_value=True
  with self.assertRaisesRegex(ValueError,'EMPTY_JOB'):owner.confirmed_stop(0)
 def test_living_descendant_never_seals(self):
  owner=r.Owner77.__new__(r.Owner77);owner.jobs={0:Mock()};owner.jobs[0].status.return_value=dict(rootExited=True,exitCode=0);owner.jobs[0].empty.return_value=False
  with self.assertRaisesRegex(ValueError,'EMPTY_JOB'):owner.confirmed_stop(0)
 def test_confirmed_stop_uses_retained_job_and_does_not_close_it(self):
  owner=r.Owner77.__new__(r.Owner77);owner.jobs={0:self.base.job};owner.root=self.root;owner.s=self.s;owner.key=self.key;owner.previous_info=None;owner.active_label76=None;owner.confirmed_stop(0);self.base.job.close.assert_not_called();self.assertTrue(self.row()['eligible'])
 def test_noncanonical_generation_path_rejected(self):
  (self.root/'generations/server-01').mkdir()
  with self.assertRaisesRegex(ValueError,'GENERATION_PATH'):r.catalog(self.root)
 def test_symlink_not_followed(self):
  if os.name=='nt':self.skipTest('portable symlink fixture')
  fixture_symlink(self.root/'generations/server-1',self.source,target_is_directory=True)
  with self.assertRaises(ValueError):r.catalog(self.root)
 def test_oversized_generation_not_accepted(self):
  (self.root/'generations/server-33').mkdir()
  with self.assertRaisesRegex(ValueError,'GENERATION_LIMIT'):r.catalog(self.root)
 def test_tampered_history_cannot_issue_ticket(self):
  (self.root/'history75/0001.entry').write_bytes(b'corrupt');self.assertFalse(self.row()['eligible'])
 def test_copy_failure_preserves_old_source_and_refuses_incomplete_root(self):
  def broken(*a,**kw):self.init_model(*a,**kw);raise ValueError('COPY_FAIL')
  with patch.object(r,'initialize',broken):
   with self.assertRaisesRegex(ValueError,'COPY_FAIL'):r.recover(self.root,self.root,0,self.row()['ticket'],self.target,Path('java'))
  self.assertTrue((self.target/'import77.pending').exists());self.assertFalse((self.root/'recovered77').exists());self.assertTrue((self.source/'world/level.dat').is_file())
 def test_world_changed_while_copying_is_rejected(self):
  def changed(*a,**kw):out=self.init_model(*a,**kw);(self.source/'world/level.dat').write_bytes(b'changed during copy');return out
  with patch.object(r,'initialize',changed):
   with self.assertRaisesRegex(ValueError,'COPY_CHANGED'):r.recover(self.root,self.root,0,self.row()['ticket'],self.target,Path('java'))
  self.assertFalse((self.target/'recovery77.ready').exists());self.assertFalse((self.root/'recovered77').exists())
 def test_locked_import_does_not_reacquire_nonreentrant_windows_world_lock(self):
  import contextlib,zipfile
  with zipfile.ZipFile(self.source/'mods/mared.jar','a') as z:
   z.writestr('com/fixmer/mared/Mared.class',b'fixture')
   z.writestr('com/fixmer/mared/technology/server/firstlaunch/DedicatedServer74.class',b'fixture')
  runtime=self.source/'libraries/net/neoforged/neoforge/21.1.264';runtime.mkdir(parents=True);(runtime/'win_args.txt').write_text('-Xmx1G');(self.source/'user_jvm_args.txt').write_text('-Xmx1G')
  entered=[False];calls=[0]
  @contextlib.contextmanager
  def nonreentrant(*args):
   if entered[0]:raise AssertionError('Windows world lock reacquired')
   entered[0]=True;calls[0]+=1
   try:yield
   finally:entered[0]=False
  with patch.object(b,'stopped_world',nonreentrant),patch.object(b.subprocess,'run',return_value=Mock(returncode=0,stdout='',stderr='openjdk version "21.0.1"')),patch.object(b,'java_call',return_value=''):
   with b.stopped_world(self.source,'world'):b.initialize(Path('/nonexistent-project'),self.source,self.target,Path(sys.executable).resolve(),publisher=self.root/'publisher73',recovering=True,source_locked=True)
  self.assertEqual(calls[0],1);self.assertTrue((self.target/'import77.pending').exists())
 def test_used_owner_cli_refuses_before_touching_journal_or_final_view(self):
  (self.root/'started74').write_text(self.s['run']);before={str(p.relative_to(self.root)):p.read_bytes() for p in self.root.rglob('*') if p.is_file()};cli=Path(__file__).resolve().parents[2]/'tools/dedicated_server75.py';result=subprocess.run([sys.executable,str(cli),str(self.root),'run',str(self.root)],capture_output=True,text=True);self.assertEqual(result.returncode,2);self.assertIn('DEDICATED77_USE_RECOVERY',result.stderr);self.assertEqual(before,{str(p.relative_to(self.root)):p.read_bytes() for p in self.root.rglob('*') if p.is_file()})
 def test_recovered_owner_run_never_replays_a_used_root(self):
  self.recover();(self.target/'started74').write_text('already used');owner=r.Owner77(self.root,self.target)
  with patch.object(r.previous.Owner76,'run') as run:
   with self.assertRaisesRegex(ValueError,'USE_RECOVERY'):owner.run()
   run.assert_not_called()
 def test_successor_folder_is_recoverable_from_old_root_catalog(self):
  self.recover();self.assertEqual(r.catalog(self.root)['successor'],str(self.target))
 def test_partial_recovery_owner_constructor_cannot_start(self):
  def broken(*a,**kw):self.init_model(*a,**kw);raise ValueError('COPY_FAIL')
  with patch.object(r,'initialize',broken):
   with self.assertRaises(ValueError):r.recover(self.root,self.root,0,self.row()['ticket'],self.target,Path('java'))
  with self.assertRaisesRegex(ValueError,'INCOMPLETE_RECOVERY'):r.Owner77(self.root,self.target)
 def legacy(self):
  (self.root/'saved77-0').unlink();write(self.root/'owner76',self.key,dict(schema='76',run=self.s['run']));write(self.root/'view75',self.key,dict(schema='75',run=self.s['run'],generation='0',phase='STOPPED_SAVED'))
 def test_legacy76_clean_whole_owner_has_explicit_import_kind(self):
  self.legacy();row=self.row();self.assertTrue(row['eligible']);self.assertEqual(row['kind'],'LEGACY76');self.assertEqual(row['reason'],'LEGACY_STOPPED_IMPORT')
 def test_legacy_import_is_new_copy_not_fabricated_historical_checkpoint(self):
  self.legacy();self.recover();self.assertFalse((self.root/'saved77-0').exists());s,key=b.settings(self.target);self.assertEqual(read(self.target/'recovery77',key)['kind'],'LEGACY76')
 def test_legacy_failed_shutdown_is_not_importable(self):
  self.legacy();write(self.root/'view75',self.key,dict(schema='75',run=self.s['run'],generation='0',phase='STOP_NOT_CONFIRMED'));self.assertFalse(self.row()['eligible'])
 def test_legacy_stale_run_is_not_importable(self):
  self.legacy();write(self.root/'view75',self.key,dict(schema='75',run=str(uuid.uuid4()),generation='0',phase='STOPPED_SAVED'));self.assertFalse(self.row()['eligible'])
 def test_legacy_only_latest_generation_can_be_imported(self):
  self.legacy();self.add_generation(1);view=r.catalog(self.root);self.assertTrue(all(not x['eligible'] for x in view['rows']))
 def test_legacy_world_change_requires_new_selection(self):
  self.legacy();row=self.row();(self.source/'world/level.dat').write_bytes(b'explicit later file change')
  with self.assertRaisesRegex(ValueError,'SELECTION_CHANGED'):self.recover(ticket=row['ticket'])
 def test_bad_77_checkpoint_never_falls_back_to_legacy_import(self):
  self.legacy();write(self.root/'saved77-0',b'x'*32,self.p);self.assertFalse(self.row()['eligible'])
 def test_recovery_report_marks_incomplete_root(self):
  def broken(*a,**kw):self.init_model(*a,**kw);raise ValueError('TEST_COPY_FAIL')
  with patch.object(r,'initialize',broken):
   with self.assertRaisesRegex(ValueError,'TEST_COPY_FAIL'):r.recover(self.root,self.root,0,self.row()['ticket'],self.target,Path('java'))
  report=json.loads((self.target/'recovery77-report.json').read_text());self.assertEqual(report['phase'],'INCOMPLETE');self.assertFalse(report['automaticStart'])
 def test_cross_process_owner_lock_blocks_catalog(self):
  # A real competing OS lock, not an in-process POSIX lock (which would be reentrant).
  if os.name=='nt':self.skipTest('this subprocess fixture targets POSIX CI; Windows uses msvcrt')
  code="import sys;from pathlib import Path;sys.path.insert(0,sys.argv[1]);from first58.dedicated77 import owner_lease;\nwith owner_lease(Path(sys.argv[2])):\n print('locked',flush=True);sys.stdin.read()"
  child=subprocess.Popen([sys.executable,'-c',code,str(Path(__file__).resolve().parents[2]/'tools'),str(self.root)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
  try:
   self.assertEqual(child.stdout.readline().strip(),'locked')
   with self.assertRaisesRegex(ValueError,'OWNER_ACTIVE'):r.catalog(self.root)
  finally:child.stdin.close();child.wait(timeout=5);child.stdout.close()
 def test_cross_process_world_lock_blocks_recovery(self):
  if os.name=='nt':self.skipTest('POSIX CI file-lock fixture')
  code="import fcntl,sys;f=open(sys.argv[1],'r+b');fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB,1);print('locked',flush=True);sys.stdin.read()"
  child=subprocess.Popen([sys.executable,'-c',code,str(self.source/'world/session.lock')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
  try:
   self.assertEqual(child.stdout.readline().strip(),'locked')
   with self.assertRaisesRegex(ValueError,'WORLD_BUSY'):self.recover()
  finally:child.stdin.close();child.wait(timeout=5);child.stdout.close()
if __name__=='__main__':unittest.main()
