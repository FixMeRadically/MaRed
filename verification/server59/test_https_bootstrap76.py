"""Fixed HTTP paths, freshness, private-file isolation. Signature math is exercised by JDK/TLS fixtures."""
import sys,time,uuid,unittest,tempfile
from pathlib import Path
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
import test_dedicated74 as base
from first58.bridge67 import write,body
from first58.https_bootstrap76 import Routes76,FIELDS
class RoutesChecks76(unittest.TestCase):
 def setUp(self):
  helper=base.OwnerBoundaries74();helper.setUp();self.addCleanup(helper.doCleanups);self.root=helper.root;self.key=helper.key;self.run=helper.s['run'];self.release=str(uuid.uuid4());write(self.root/'owner76',self.key,{'schema':'76','run':self.run});self.folder=self.root/'releases/r1/public';self.folder.mkdir(parents=True);self.archive='client-'+'a'*64+'.zip'
  for name in ('public.der','offer.bin','offer.sig',self.archive):(self.folder/name).write_bytes(b'public')
  self.info={'release':self.release,'endpoint':'https://localhost/genesis/','archive':self.archive};self.verify=Mock(return_value=self.info);self.publication=Mock(endpoint=self.info['endpoint'],publisher=b'public');self.routes=Routes76(self.root,'/genesis/',self.publication,self.verify);self.d=dict(schema='76',run=self.run,at=str(int(time.time()*1000)),serial='1',phase='TRANSITION',label='r1',generation='1',release=self.release);write(self.root/'bootstrap76.local',self.key,self.d)
 def request(self,name,method='GET'):
  h=Mock();h.path='/genesis/bootstrap76/'+name;h.command=method;self.assertTrue(self.routes.route(h));return h
 def test_selected_export(self):h=self.request(self.release+'/offer.bin');h.send_response.assert_called_once_with(200)
 def test_unknown_release(self):h=self.request(str(uuid.uuid4())+'/offer.bin');h.send_error.assert_called_once_with(404)
 def test_private_key(self):h=self.request(self.release+'/private.der');h.send_error.assert_called_once_with(404)
 def test_query(self):h=self.request('state?token=private');h.send_error.assert_called_once_with(404)
 def test_traversal(self):h=self.request('../owner74.key');h.send_error.assert_called_once_with(404)
 def test_post(self):h=self.request('state','POST');h.send_error.assert_called_once_with(404)
 def test_expired_heartbeat(self):self.d['at']='1';write(self.root/'bootstrap76.local',self.key,self.d);h=self.request(self.release+'/offer.bin');h.send_error.assert_called_once_with(503)
 def test_wrong_owner(self):self.d['run']=str(uuid.uuid4());write(self.root/'bootstrap76.local',self.key,self.d);h=self.request(self.release+'/offer.bin');h.send_error.assert_called_once_with(503)
 def test_wrong_mac(self):write(self.root/'bootstrap76.local',b'x'*32,self.d);h=self.request(self.release+'/offer.bin');h.send_error.assert_called_once_with(503)
 def test_withdrawn(self):self.d['phase']='UNAVAILABLE';write(self.root/'bootstrap76.local',self.key,self.d);h=self.request(self.release+'/offer.bin');h.send_error.assert_called_once_with(404)
 def test_endpoint_change(self):self.info['endpoint']='https://other/genesis/';h=self.request(self.release+'/offer.bin');h.send_error.assert_called_once_with(503)
 def test_publisher_change(self):(self.folder/'public.der').write_bytes(b'changed');h=self.request(self.release+'/offer.bin');h.send_error.assert_called_once_with(503)
 def test_unexpected_file(self):(self.folder/'owner74.key').write_bytes(b'secret');h=self.request(self.release+'/offer.bin');h.send_error.assert_called_once_with(503)
 def test_missing_signed_state(self):h=self.request('state');h.send_error.assert_called_once_with(503)
if __name__=='__main__':unittest.main()
