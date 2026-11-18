from pathlib import Path
import contextlib,importlib.util,io,sys,tempfile,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
import profile_guard93 as guard

class Diagnostic93(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.profile=self.root/'run/client';self.path=self.profile/'config/mared/dev/profile-admission-last93.properties';self.path.parent.mkdir(parents=True);self.text='schema=93\nphase=before_main\npid=123\nrecorded_ms=1\nlaunch=unmanaged\n';self.path.write_text(self.text)
 def test_reports_history_without_claiming_process_is_free(self):
  value=guard.read(self.root);self.assertEqual(value['phase'],'before_main');self.assertEqual(value['current_liveness'],'not_checked');self.assertNotIn('profile_free',value)
 def test_explicit_profile(self):self.assertEqual(guard.read(self.root,self.profile)['pid_at_recording'],123)
 def test_no_report(self):
  self.path.unlink()
  with self.assertRaises(ValueError):guard.read(self.root)
 def test_ambiguous_requires_explicit_selection(self):
  other=self.root/'config/mared/dev/profile-admission-last93.properties';other.parent.mkdir(parents=True);other.write_text(self.text)
  with self.assertRaises(ValueError):guard.read(self.root)
  self.assertEqual(guard.read(self.root,self.profile)['schema'],93)
 def test_legacy_or_malformed_report(self):
  for text in [self.text.replace('schema=93','schema=92'),self.text+'schema=93\n',self.text.replace('phase=before_main','phase=free'),self.text.replace('pid=123','pid=not-a-pid'),self.text.replace('launch=unmanaged','launch=PRIVATE_TOKEN'),self.text.replace('recorded_ms=1','recorded_ms=-1')]:
   self.path.write_text(text)
   with self.assertRaises(ValueError):guard.read(self.root)
 def test_oversized(self):
  self.path.write_text(self.text+'#'+('x'*4096))
  with self.assertRaises(ValueError):guard.read(self.root)
 def test_constructor_only_is_explicit(self):
  self.path.write_text(self.text.replace('before_main','mod_constructor_only'));self.assertEqual(guard.read(self.root)['phase'],'mod_constructor_only')
 def test_report_does_not_expose_unknown_fields(self):
  self.path.write_text(self.text+'controlToken=PRIVATE_TOKEN\n');self.assertNotIn('PRIVATE_TOKEN',str(guard.read(self.root)))
 def test_cli_does_not_launch_any_process(self):
  from unittest.mock import patch
  with patch.object(sys,'argv',['profile_guard93',str(self.root)]),contextlib.redirect_stdout(io.StringIO()) as out:self.assertEqual(guard.main(),0)
  self.assertIn('not_checked',out.getvalue())
if __name__=='__main__':unittest.main()
