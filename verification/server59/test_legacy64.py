"""Regression for BootstrapLauncher Files.readAllLines legacy classpath contract."""
from pathlib import Path
import json,sys,tempfile,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import launch62

class LegacyTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
  self.source=self.root/'IDE output';self.source.mkdir()
  a=self.source/'com/fixmer/mared/technology/server/firstlaunch/FirstLaunchClient58.class';a.parent.mkdir(parents=True);a.write_bytes(b'fixture')
  self.core=self.root/'core.jar';self.core.write_bytes(b'core fixture')
  self.modlauncher=self.root/'modlauncher.jar';self.modlauncher.write_bytes(b'loader fixture')
  self.asm=self.root/'library with spaces.jar';self.asm.write_bytes(b'library fixture')
  self.file=self.root/'legacy.txt'
 def freeze(self,text):
  self.file.write_bytes(text.encode('utf-8'))
  return launch62.snapshot({'devModClasses':'MaRed%%'+str(self.source),'clientCommand':['java','-DlegacyClassPath.file='+str(self.file),'Main']},self.root/'frozen',self.core)
 def test_multiline_preserves_bootstrap_provider_even_when_core_is_last(self):
  frozen=self.freeze(str(self.modlauncher)+'\n'+str(self.asm)+'\n'+str(self.source)+'\n'+str(self.root/'genesis-core-0.1.38.jar')+'\n')
  self.assertEqual(frozen['legacy'],[str(self.modlauncher),str(self.asm),'{profile}/.genesis-dev62/mods/MaRed','{profile}/.genesis-dev62/core.jar'])
 def test_crlf_same_as_lf(self):
  frozen=self.freeze(str(self.modlauncher)+'\r\n'+str(self.source)+'\r\n')
  self.assertEqual(frozen['legacy'],[str(self.modlauncher),'{profile}/.genesis-dev62/mods/MaRed'])
 def test_utf8_bom_and_no_final_newline(self):
  self.file.write_bytes(b'\xef\xbb\xbf'+str(self.modlauncher).encode())
  self.assertEqual(launch62.legacy_file_entries(self.file),[str(self.modlauncher)])
 def test_internal_empty_line_refused(self):
  self.file.write_text(str(self.modlauncher)+'\n\n'+str(self.asm))
  with self.assertRaises(ValueError):launch62.legacy_file_entries(self.file)
 def test_empty_file_refused(self):
  self.file.write_bytes(b'')
  with self.assertRaises(ValueError):launch62.legacy_file_entries(self.file)
 def test_tab_control_refused(self):
  self.file.write_text('C:/a\tfile.jar')
  with self.assertRaises(ValueError):launch62.legacy_file_entries(self.file)
 def test_semicolon_inside_real_filename_not_split(self):
  self.file.write_text(str(self.root/'valid;name.jar')+'\n')
  self.assertEqual(launch62.legacy_file_entries(self.file),[str(self.root/'valid;name.jar')])
 def test_both_generations_write_individual_lines_and_resolve_frozen_paths(self):
  frozen=self.freeze(str(self.modlauncher)+'\n'+str(self.source)+'\n')
  import shutil
  outputs=[]
  for generation in (0,1):
   profile=self.root/f'client-{generation}';profile.mkdir();shutil.copytree(self.root/'frozen',profile/launch62.ROOT)
   launch62.runtime(profile,{'devSnapshot':frozen})
   data=(profile/'client-legacy62.txt').read_text();entries=data.splitlines();outputs.append(entries)
   self.assertEqual(entries,[str(self.modlauncher),str(profile/'.genesis-dev62/mods/MaRed')]);self.assertTrue(data.endswith('\n'));self.assertNotIn('{profile}',data)
  self.assertNotEqual(outputs[0][1],outputs[1][1])
 def test_old_embedded_newline_snapshot_refused(self):
  with self.assertRaises(ValueError):launch62.validate_legacy_entries(['one.jar\ntwo.jar'])
 def test_budget_and_missing_file(self):
  self.file.write_bytes(b'x'*(1024*1024+1))
  with self.assertRaises(ValueError):launch62.legacy_file_entries(self.file)
  with self.assertRaises(ValueError):launch62.legacy_file_entries(self.root/'missing.txt')

if __name__=='__main__':unittest.main(verbosity=2)
