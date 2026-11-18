"""Actual controller-side recheck of signed device bindings; no game or online account is simulated."""
import sys,tempfile,uuid,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import maop113
from first58.bridge67 import write
class Device153Tests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.key=bytes(32);self.run=str(uuid.uuid4());self.actor=str(uuid.uuid4());self.fp='ab'*32;self.profile=self.root/'profile';self.profile.mkdir();self.devices=self.profile/'devices153';self.devices.write_bytes(('GENESIS-DEVICES153\n'+self.actor+':'+self.fp+'\n').encode('ascii'))
  write(self.root/'maop153.binding',self.key,dict(schema='153',run=self.run,profile=str(self.profile),devices=str(self.devices)))
 def tearDown(self):self.temp.cleanup()
 def test_trusted_device(self):self.assertTrue(maop113.device153(self.root,self.key,self.run,self.actor,self.fp))
 def test_other_actor(self):self.assertFalse(maop113.device153(self.root,self.key,self.run,str(uuid.uuid4()),self.fp))
 def test_other_fingerprint(self):self.assertFalse(maop113.device153(self.root,self.key,self.run,self.actor,'cd'*32))
 def test_revocation_is_fresh(self):
  self.assertTrue(maop113.device153(self.root,self.key,self.run,self.actor,self.fp));self.devices.write_bytes(b'GENESIS-DEVICES153\n');self.assertFalse(maop113.device153(self.root,self.key,self.run,self.actor,self.fp))
 def test_binding_cannot_escape_profile(self):
  outside=self.root/'devices153';outside.write_bytes(self.devices.read_bytes());write(self.root/'maop153.binding',self.key,dict(schema='153',run=self.run,profile=str(self.profile),devices=str(outside)))
  with self.assertRaises(ValueError):maop113.device153(self.root,self.key,self.run,self.actor,self.fp)
 def test_wrong_run(self):
  with self.assertRaises(ValueError):maop113.device153(self.root,self.key,str(uuid.uuid4()),self.actor,self.fp)
 def test_corrupt_signature(self):
  path=self.root/'maop153.binding';path.write_bytes(path.read_bytes()+b'broken')
  with self.assertRaises(ValueError):maop113.device153(self.root,self.key,self.run,self.actor,self.fp)
 def test_invalid_file(self):
  self.devices.write_bytes(b'GENESIS-DEVICES153\ninvalid\n')
  with self.assertRaises(ValueError):maop113.device153(self.root,self.key,self.run,self.actor,self.fp)
if __name__=='__main__':unittest.main()
