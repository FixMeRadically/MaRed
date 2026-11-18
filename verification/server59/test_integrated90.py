from pathlib import Path
import sys,tempfile,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from integrated_last90 import report
class Diagnostics90(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.dev=self.root/'run/client/config/mared/dev';self.dev.mkdir(parents=True);self.log=self.root/'run/client/logs/latest.log';self.log.parent.mkdir()
 def test_only_known_markers_no_secrets(self):
  self.log.write_text('secret TOKEN_PRIVATE\n[Genesis integrated90] ACTIVE\n[Genesis integrated90] UNKNOWN\n[Genesis integrated90] STOPPED\n');r=report(self.root);self.assertEqual(r['game_markers'],['ACTIVE','STOPPED']);self.assertNotIn('TOKEN_PRIVATE',str(r))
 def test_bounded_history(self):
  self.log.write_text('[Genesis integrated90] SAVED\n'*100);self.assertEqual(len(report(self.root)['game_markers']),24)
 def test_hold_is_not_reported_as_success(self):
  log=self.dev/'logs/supervisor.log';log.parent.mkdir();log.write_text('INTEGRATED90_HOLD');r=report(self.root);self.assertTrue(r['hold_seen_in_log']);self.assertEqual(r['game_markers'],[])
 def test_missing_log_does_not_invent_readiness(self):self.assertEqual(report(self.root)['game_markers'],[])
 def test_source_is_not_runtime_proof(self):
  p=self.root/'src/main/java/com/fixmer/mared/dev/client/DevIntegratedRestart90.java';p.parent.mkdir(parents=True);p.touch();r=report(self.root);self.assertTrue(r['source90']);self.assertEqual(r['game_markers'],[])
if __name__=='__main__':unittest.main()
