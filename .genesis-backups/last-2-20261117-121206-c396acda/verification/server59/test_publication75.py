"""Authenticated export selection; signature cryptography exercised separately by the real JDK fixture."""
import sys,tempfile,json,unittest,uuid
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.common import json_write,sha
from first58.bridge67 import write
from first58.publication75 import Publication75
class PublicationChecks75(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)/'owner';self.root.mkdir();self.key=b'p'*32;self.run=str(uuid.uuid4());self.release=str(uuid.uuid4());(self.root/'owner74.key').write_bytes(self.key)
  profile=self.root/'generations/server-0';profile.mkdir(parents=True);(profile/'server.properties').write_text('level-name=world\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25565\n')
  # Dedicated74 settings require only a signed file hash here, not a real JVM/server.
  json_write(self.root/'settings.json',{'schema':74,'run':self.run,'level':'world'});write(self.root/'settings74',self.key,{'schema':'74','sha':sha(self.root/'settings.json',65536)});write(self.root/'owner75',self.key,{'schema':'75','run':self.run});(self.root/'publisher73').mkdir();(self.root/'publisher73/public.der').write_bytes(b'publisher');self.initial=self.root/'initial';self.next=self.root/'releases/r2/public'
  self.archive='client-'+'a'*64+'.zip';self.meta={'endpoint':'https://localhost/genesis/','archive':self.archive,'release':self.release}
  for folder in (self.initial,self.next):
   folder.mkdir(parents=True)
   for name in ('offer.bin','offer.sig',self.archive):(folder/name).write_bytes(b'public')
   (folder/'public.der').write_bytes(b'publisher')
  self.reader=Publication75(self.root,self.initial,lambda p:dict(self.meta))
 def pointer(self,**extra):write(self.root/'publication75',self.key,dict(schema='75',run=self.run,label='r2',release=self.release,**extra))
 def test_initial_and_switch(self):
  self.assertEqual(self.reader.snapshot()[0],self.initial);self.pointer();self.assertEqual(self.reader.snapshot()[0],self.next);self.assertEqual(len(self.reader.snapshot()[1]),4)
 def test_unknown_field(self):
  self.pointer(private='path')
  with self.assertRaises(ValueError):self.reader.snapshot()
 def test_wrong_mac(self):
  self.pointer();write(self.root/'publication75',b'z'*32,{'schema':'75','run':self.run,'label':'r2','release':self.release})
  with self.assertRaises(ValueError):self.reader.snapshot()
 def test_wrong_run(self):
  self.pointer();write(self.root/'publication75',self.key,{'schema':'75','run':str(uuid.uuid4()),'label':'r2','release':self.release})
  with self.assertRaises(ValueError):self.reader.snapshot()
 def test_traversal(self):
  write(self.root/'publication75',self.key,{'schema':'75','run':self.run,'label':'../x','release':self.release})
  with self.assertRaises(ValueError):self.reader.snapshot()
 def test_signer_change(self):
  self.pointer();(self.next/'public.der').write_bytes(b'wrong')
  with self.assertRaises(ValueError):self.reader.snapshot()
 def test_endpoint_change(self):
  self.pointer();self.meta['endpoint']='https://elsewhere/genesis/'
  with self.assertRaises(ValueError):self.reader.snapshot()
 def test_release_change(self):
  self.pointer();self.meta['release']=str(uuid.uuid4())
  with self.assertRaises(ValueError):self.reader.snapshot()
 def test_private_file_refused(self):
  self.pointer();(self.next/'private.der').write_bytes(b'never served')
  with self.assertRaises(ValueError):self.reader.snapshot()
 def test_oversized_pointer(self):
  (self.root/'publication75').write_bytes(b'0'*16385)
  with self.assertRaises(ValueError):self.reader.snapshot()
if __name__=='__main__':unittest.main()
