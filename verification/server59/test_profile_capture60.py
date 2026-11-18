"""Profile resolution uses Minecraft's known directory rather than the wizard cwd."""
from filesystem_capabilities124 import fixture_symlink
from pathlib import Path
import sys,tempfile,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.capture import resolve_profile
class ProfileCaptureTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
  self.root=Path(self.temp.name);self.profile=self.root/'client with spaces';self.profile.mkdir()
 def test_dot_uses_game_directory(self):self.assertEqual(resolve_profile('.',self.profile),self.profile)
 def test_relative_path_uses_actual_game_directory(self):self.assertEqual(resolve_profile('profiles/client',self.profile),self.profile)
 def test_absolute_matches(self):self.assertEqual(resolve_profile(str(self.profile),self.profile),self.profile)
 def test_absolute_mismatch_refused(self):
  with self.assertRaisesRegex(ValueError,'differs'):resolve_profile(str(self.root),self.profile)
 def test_no_hint_never_guesses_cwd(self):
  with self.assertRaisesRegex(ValueError,'in-game'):resolve_profile('.')
 def test_relative_hint_refused(self):
  with self.assertRaisesRegex(ValueError,'absolute'):resolve_profile('.',Path('.'))
 def test_missing_game_directory_refused(self):
  with self.assertRaisesRegex(ValueError,'unavailable'):resolve_profile('.',self.root/'missing')
 def test_symlink_hint_refused(self):
  linked=self.root/'linked';fixture_symlink(linked,self.profile,target_is_directory=True)
  with self.assertRaises(ValueError):resolve_profile('.',linked)
if __name__=='__main__':unittest.main(verbosity=2)
