from pathlib import Path
import sys,tempfile,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from local_recovery91 import report
class Diagnostics91(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.data=self.root/'run/client/config/mared/dev/deployments';self.data.mkdir(parents=True)
 def test_empty_is_not_runtime_verification(self):r=report(self.root);self.assertEqual(r['trial'],'NONE');self.assertFalse(r['runtime_verified'])
 def test_only_safe_fields(self):
  (self.data/'trial.properties').write_text('phase=APPLIED\nworldRisk91=true\nrecovery91=RESTORE_REQUESTED\naccessToken=PRIVATE_SECRET\nworld=PRIVATE_PATH\n');r=report(self.root);self.assertEqual(r['decision'],'RESTORE_REQUESTED');self.assertTrue(r['world_risk']);self.assertNotIn('PRIVATE',str(r))
 def test_manual_choice_is_distinct(self):
  (self.data/'last-result.properties').write_text('status=MANUALLY_ACCEPTED91\n');r=report(self.root);self.assertEqual(r['last_result'],'MANUALLY_ACCEPTED91');self.assertFalse(r['runtime_verified'])
 def test_duplicate_is_unreadable(self):
  (self.data/'trial.properties').write_text('phase=APPLIED\nphase=COMMITTED\n');self.assertEqual(report(self.root)['read_status'],'UNREADABLE')
 def test_oversized_is_unreadable(self):
  (self.data/'trial.properties').write_bytes(b'a'*65537);self.assertEqual(report(self.root)['read_status'],'UNREADABLE')
 def test_review_does_not_claim_world_rollback(self):
  (self.data/'world-review91.properties').write_text('phase=RESTORED_REVIEW\n');r=report(self.root);self.assertTrue(r['restored_review']);self.assertNotIn('world_restored',r)
if __name__=='__main__':unittest.main()
