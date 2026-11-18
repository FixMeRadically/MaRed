from pathlib import Path
import json,sys,tempfile,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from align_dev_version62 import align
class VersionTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
  self.props=self.root/'gradle.properties';self.props.write_bytes(b'minecraft_version=1.21.1\r\nneo_version=21.1.235\r\nmod_version=0.4.0\r\n')
  self.launch=self.root/'.vscode/launch.json';self.launch.parent.mkdir();self.launch.write_text(json.dumps({'configurations':[{'name':'keep','args':['--version','21.1.235','--fml.neoForgeVersion','21.1.235','--username','fix'],'env':{'MOD_CLASSES':'keep'}}]}))
 def test_only_versions_changed_and_backup_exact(self):
  original=self.props.read_bytes();backup=align(self.root,'21.1.264')
  self.assertEqual((backup/'gradle.properties').read_bytes(),original)
  self.assertEqual(self.props.read_bytes(),original.replace(b'21.1.235',b'21.1.264'))
  value=json.loads(self.launch.read_text());self.assertEqual(value['configurations'][0]['env'],{'MOD_CLASSES':'keep'})
  self.assertEqual(value['configurations'][0]['args'],['--version','21.1.264','--fml.neoForgeVersion','21.1.264','--username','fix'])
 def test_idempotent(self):
  align(self.root,'21.1.264');self.assertIsNone(align(self.root,'21.1.264'))
 def test_invalid_json_zero_mutation(self):
  before=self.props.read_bytes();self.launch.write_text('{broken')
  with self.assertRaises(ValueError):align(self.root,'21.1.264')
  self.assertEqual(self.props.read_bytes(),before)
 def test_duplicate_property_refused(self):
  self.props.write_bytes(self.props.read_bytes()+b'neo_version=21.1.264\n');before=self.props.read_bytes()
  with self.assertRaises(ValueError):align(self.root,'21.1.264')
  self.assertEqual(self.props.read_bytes(),before)
 def test_unsupported_version_refused(self):
  with self.assertRaises(ValueError):align(self.root,'21.2.1')
if __name__=='__main__':unittest.main(verbosity=2)
