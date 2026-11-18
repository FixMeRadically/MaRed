"""Final-registration repair of an actual file copy, with no process start or world recopy."""
from pathlib import Path
import sys,unittest,uuid
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import repair78 as r,dedicated74 as b
from first58.bridge67 import read,write
from first58.common import sha,json_write
import test_dedicated77 as f

class Repair78(unittest.TestCase):
 def setUp(self):
  base=f.Recovery77();base.setUp();self.addCleanup(base.doCleanups);self.base=base;base.recover();self.root=base.root;self.target=base.target;self.s,self.key=b.settings(self.root);self.ns,self.nk=b.settings(self.target);(self.target/'import77.pending').write_text('77');(self.target/'recovery77.ready').unlink()
 def repair(self):return r.repair(self.root,self.root,Path('java'))
 def test_complete_claimed_copy_finishes_without_recopy_or_start(self):
  before=b.tree(self.target/'generations/server-0','world');source=b.tree(self.root/'generations/server-0','world')
  with patch.object(b,'copy_tree',side_effect=AssertionError('world recopied')):result=self.repair()
  self.assertFalse(result['started']);self.assertTrue(result['repairedRegistration']);self.assertEqual(before,b.tree(self.target/'generations/server-0','world'));self.assertEqual(source,b.tree(self.root/'generations/server-0','world'));self.assertFalse((self.target/'import77.pending').exists());self.assertTrue((self.target/'recovery77.ready').is_file())
 def test_repair_can_be_repeated_before_start(self):
  self.repair();self.repair();self.assertFalse((self.target/'started74').exists())
 def test_inspector_only_offers_unstarted_pending_copy(self):
  self.assertTrue(r.inspect(self.root));self.repair();self.assertFalse(r.inspect(self.root))
 def test_started_target_never_repaired(self):
  (self.target/'started74').write_text(self.ns['run'])
  with self.assertRaisesRegex(ValueError,'ALREADY_STARTED'):self.repair()
 def test_unclaimed_partial_copy_never_repaired(self):
  (self.root/'recovered77').unlink()
  with self.assertRaises(ValueError):self.repair()
  self.assertTrue((self.target/'import77.pending').exists())
 def test_wrong_target_run_never_repaired(self):
  d=read(self.root/'recovered77',self.key);d['targetRun']=str(uuid.uuid4());write(self.root/'recovered77',self.key,d)
  with self.assertRaisesRegex(ValueError,'BINDING'):self.repair()
 def test_wrong_source_run_never_repaired(self):
  d=read(self.target/'recovery77',self.nk);d['sourceRun']=str(uuid.uuid4());write(self.target/'recovery77',self.nk,d)
  with self.assertRaisesRegex(ValueError,'BINDING'):self.repair()
 def test_world_changed_never_repaired(self):
  (self.target/'generations/server-0/world/level.dat').write_bytes(b'changed')
  with self.assertRaisesRegex(ValueError,'COPY_CHANGED'):self.repair()
 def test_source_changed_never_repaired(self):
  (self.root/'generations/server-0/world/level.dat').write_bytes(b'changed')
  with self.assertRaisesRegex(ValueError,'COPY_CHANGED'):self.repair()
 def test_settings_source_path_change_refused(self):
  d=dict(self.ns,source=str(self.root/'different'));json_write(self.target/'settings.json',d);write(self.target/'settings74',self.nk,dict(schema='74',sha=sha(self.target/'settings.json')))
  with self.assertRaisesRegex(ValueError,'BINDING'):self.repair()
 def test_private_publisher_key_change_refused(self):
  (self.target/'publisher73/private.der').write_bytes(b'different')
  with self.assertRaisesRegex(ValueError,'IDENTITY'):self.repair()
 def test_history_change_refused(self):
  r.History75(self.target,self.nk,self.ns['run']).append('UNKNOWN',0,0)
  with self.assertRaisesRegex(ValueError,'HISTORY'):self.repair()
 def test_conflicting_ready_marker_not_overwritten(self):
  write(self.target/'recovery77.ready',self.nk,dict(schema='77',run=self.ns['run'],ticket='b'*64));before=(self.target/'recovery77.ready').read_bytes()
  with self.assertRaisesRegex(ValueError,'READY_CONFLICT'):self.repair()
  self.assertEqual(before,(self.target/'recovery77.ready').read_bytes())
 def test_old_participant_journal_never_reactivated(self):
  (self.target/'control73').mkdir()
  with self.assertRaisesRegex(ValueError,'RUNTIME_HISTORY'):self.repair()
 def test_runtime_receipt_in_unstarted_copy_refused(self):
  (self.target/'server-0.status').write_bytes(b'old receipt')
  with self.assertRaisesRegex(ValueError,'RUNTIME_HISTORY'):self.repair()
 def test_extra_generation_never_adopted(self):
  (self.target/'generations/server-1').mkdir()
  with self.assertRaisesRegex(ValueError,'GENERATIONS'):self.repair()
 def test_owner_marker_change_refused(self):
  write(self.target/'owner77',self.nk,dict(schema='77',run=str(uuid.uuid4())))
  with self.assertRaisesRegex(ValueError,'OWNER'):self.repair()
 def test_failure_preserves_pending_barrier_and_other_files(self):
  (self.target/'publisher73/private.der').write_bytes(b'changed');before={str(x.relative_to(self.target)):x.read_bytes() for x in self.target.rglob('*') if x.is_file() and x.name!='controller.lock'}
  with self.assertRaises(ValueError):self.repair()
  after={str(x.relative_to(self.target)):x.read_bytes() for x in self.target.rglob('*') if x.is_file() and x.name!='controller.lock'};self.assertEqual(before,after)
if __name__=='__main__':unittest.main()
