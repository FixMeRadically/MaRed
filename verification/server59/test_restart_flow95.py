from filesystem_capabilities124 import fixture_symlink
from pathlib import Path
import contextlib,io,sys,tempfile,unittest,uuid
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
import restart_flow95 as flow
class Diagnostic95(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.path=self.root/'run/client/config/mared/dev/restart95-last.properties';self.path.parent.mkdir(parents=True);self.text='schema=95\nstate=DONE\nrequest='+str(uuid.uuid4())+'\nsource_launch='+str(uuid.uuid4())+'\nreason=normal_exit_and_save_confirmed\n';self.path.write_text(self.text)
 def test_history_only(self):self.assertEqual(flow.read(self.root)['current_liveness'],'not_checked')
 def test_explicit_profile(self):self.assertEqual(flow.read(self.root,self.root/'run/client')['last_recorded_state'],'DONE')
 def test_no_report(self):
  self.path.unlink()
  with self.assertRaises(ValueError):flow.read(self.root)
 def test_ambiguous(self):
  other=self.root/'config/mared/dev/restart95-last.properties';other.parent.mkdir(parents=True);other.write_text(self.text)
  with self.assertRaises(ValueError):flow.read(self.root)
 def test_malformed(self):
  for text in [self.text+'schema=95\n',self.text.replace('schema=95','schema=94'),self.text.replace('state=DONE','state=FREE'),self.text.replace('reason=normal_exit_and_save_confirmed','reason=private-value'),self.text.replace('request=','request=bad')]:
   self.path.write_text(text)
   with self.assertRaises(ValueError):flow.read(self.root)
 def test_oversized(self):
  self.path.write_text(self.text+'#'+'x'*4096)
  with self.assertRaises(ValueError):flow.read(self.root)
 def test_secret_fields_not_returned(self):
  self.path.write_text(self.text+'controlToken=PRIVATE_TOKEN\ngrant=PRIVATE_GRANT\n');self.assertNotIn('PRIVATE',str(flow.read(self.root)))
 def test_link_refused(self):
  real=self.path.with_suffix('.real');self.path.rename(real);fixture_symlink(self.path,real)
  with self.assertRaises(ValueError):flow.read(self.root)
 def test_cli_read_only(self):
  from unittest.mock import patch
  old=self.path.read_bytes()
  with patch.object(sys,'argv',['flow',str(self.root)]),contextlib.redirect_stdout(io.StringIO()) as out:self.assertEqual(flow.main(),0)
  self.assertIn('not_checked',out.getvalue());self.assertEqual(old,self.path.read_bytes())
if __name__=='__main__':unittest.main()
