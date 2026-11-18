from filesystem_capabilities124 import fixture_symlink
from pathlib import Path
import contextlib,io,sys,tempfile,unittest,uuid
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
import handoff_last96 as flow
class History96(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.path=self.root/'run/client/config/mared/dev/handoff96-last.properties';self.path.parent.mkdir(parents=True);self.text='schema=96\nid='+str(uuid.uuid4())+'\nphase=CONFIRMED\nreason=final_save_and_exit_verified\n';self.path.write_text(self.text)
 def test_history_only(self):self.assertEqual(flow.read(self.root)['current_liveness'],'not_checked')
 def test_explicit_profile(self):self.assertEqual(flow.read(self.root,self.root/'run/client')['last_recorded_phase'],'CONFIRMED')
 def test_missing(self):
  self.path.unlink()
  with self.assertRaises(ValueError):flow.read(self.root)
 def test_ambiguous(self):
  other=self.root/'config/mared/dev/handoff96-last.properties';other.parent.mkdir(parents=True);other.write_text(self.text)
  with self.assertRaises(ValueError):flow.read(self.root)
 def test_corrupt(self):
  for text in [self.text+'schema=96\n',self.text.replace('schema=96','schema=95'),self.text.replace('phase=CONFIRMED','phase=FREE'),self.text.replace('id=','id=bad'),self.text.replace('reason=final_save_and_exit_verified','reason=PRIVATE')]:
   self.path.write_text(text)
   with self.assertRaises(ValueError):flow.read(self.root)
 def test_oversized(self):
  self.path.write_text(self.text+'#'+'x'*4096)
  with self.assertRaises(ValueError):flow.read(self.root)
 def test_unknown_fields_not_exposed(self):
  self.path.write_text(self.text+'controlToken=PRIVATE_TOKEN\nmac=PRIVATE_SIGNATURE\n');self.assertNotIn('PRIVATE',str(flow.read(self.root)))
 def test_link_refused(self):
  real=self.path.with_suffix('.real');self.path.rename(real);fixture_symlink(self.path,real)
  with self.assertRaises(ValueError):flow.read(self.root)
 def test_cli_read_only(self):
  from unittest.mock import patch
  before=self.path.read_bytes()
  with patch.object(sys,'argv',['history96',str(self.root)]),contextlib.redirect_stdout(io.StringIO()) as out:self.assertEqual(flow.main(),0)
  self.assertIn('not_checked',out.getvalue());self.assertEqual(before,self.path.read_bytes())
if __name__=='__main__':unittest.main()
