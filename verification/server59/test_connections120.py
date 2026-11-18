"""Mandatory approval floor and fixed return route. Game/Job facts tested in connections_process120 are fixtures."""
from pathlib import Path
import hashlib,io,json,secrets,sys,tempfile,time,unittest,uuid,zipfile
from types import SimpleNamespace
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.approval120 import issue,verify
from first58.changes104 import Changes104
from first58.common import sha
from first58.bridge67 import read,write
from first58.https_control73 import Routes73
from first58 import return120
from test_inplace104 import jar

class Approval120(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.server=self.root/'server';(self.server/'mods').mkdir(parents=True);self.scope=self.server/'.genesis/server104';self.scope.mkdir(parents=True);self.changes=Changes104(self.server,self.scope,secrets.token_bytes(32));jar(self.server/'mods/a.jar');self.next=jar(self.root/'next.jar',b'new')
 def reviewed(self):
  p=self.changes.review({'a.jar':self.next});return self.changes.stage({'a.jar':self.next},approval=self.changes.approve(p))
 def test_stage_without_explicit_review_refused(self):
  with self.assertRaisesRegex(ValueError,'APPROVAL120'):self.changes.stage({'a.jar':self.next})
  self.assertIsNone(self.changes.load())
 def test_apply_without_receipt_refused(self):
  token=self.reviewed();(self.scope/'approvals120'/token).unlink();old=(self.server/'mods/a.jar').read_bytes()
  with self.assertRaises((ValueError,OSError)):self.changes.apply()
  self.assertEqual(old,(self.server/'mods/a.jar').read_bytes())
 def test_tampered_receipt_refused(self):
  token=self.reviewed();p=self.scope/'approvals120'/token;p.write_bytes(p.read_bytes()+b'edit')
  with self.assertRaises((ValueError,OSError)):self.changes.apply()
 def test_restore_does_not_require_new_approval(self):
  token=self.reviewed();self.changes.apply();(self.scope/'approvals120'/token).unlink();self.changes.restore();self.changes.finish(False);self.assertIsNone(self.changes.load())
 def test_changed_review_refused(self):
  p=self.changes.review({'a.jar':self.next});token=self.changes.approve(p);jar(self.next,b'different')
  with self.assertRaisesRegex(ValueError,'APPROVAL120'):self.changes.stage({'a.jar':self.next},approval=token)
 def test_receipt_cannot_move_to_another_server(self):
  p=self.changes.review({'a.jar':self.next});token=self.changes.approve(p);other=self.root/'other';(other/'mods').mkdir(parents=True);(other/'mods/a.jar').write_bytes((self.server/'mods/a.jar').read_bytes());changes=Changes104(other,self.scope,self.changes.key)
  with self.assertRaisesRegex(ValueError,'APPROVAL120'):changes.stage({'a.jar':self.next},approval=token)
 def test_scope_and_purpose_bound(self):
  token=str(uuid.uuid4());issue(self.scope,'FOLDER',token,'a'*64)
  with self.assertRaises(ValueError):verify(self.scope,'REMOTE',token,'a'*64)
 def test_noncanonical_receipt_id_refused(self):
  with self.assertRaisesRegex(ValueError,'APPROVAL120_ID'):
   issue(self.scope,'FOLDER',uuid.uuid4().hex,'a'*64)
 def test_concurrent_issuance_keeps_exactly_one_artifact(self):
  from concurrent.futures import ThreadPoolExecutor
  issue(self.scope,'FOLDER',str(uuid.uuid4()),'a'*64)
  token=str(uuid.uuid4())
  def attempt(artifact):
   try:issue(self.scope,'FOLDER',token,artifact);return artifact
   except ValueError as error:
    self.assertEqual(str(error),'APPROVAL120_ALREADY_ISSUED');return None
  with ThreadPoolExecutor(max_workers=2) as pool:
   winners=[item for item in pool.map(attempt,('a'*64,'b'*64)) if item]
  self.assertEqual(len(winners),1)
  verify(self.scope,'FOLDER',token,winners[0])
 def test_reuse_refused(self):
  token=str(uuid.uuid4());issue(self.scope,'FOLDER',token,'a'*64)
  with self.assertRaises(ValueError):issue(self.scope,'FOLDER',token,'a'*64)

class ReturnRoute120(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.key=b'k'*32;(self.root/'owner74.key').write_bytes(self.key);self.control=self.root/'control73';(self.control/'public').mkdir(parents=True);self.round=str(uuid.uuid4());self.run=str(uuid.uuid4());self.now=int(time.time()*1000)
  write(self.root/'return120.local',self.key,dict(schema='120',run=self.run,round=self.round,generation='3',inventory='a'*64,at=str(self.now),serial='5'))
  # Route does NOT claim signature verification: real Java verifier rejects malformed signatures independently.
  body={'schema':'120','purpose':'RETURN_JOIN','owner':self.run,'round':self.round,'generation':'3','serverInventory':'a'*64,'expires':str(self.now+8000)}
  self.packet=''.join(k+'='+__import__('base64').urlsafe_b64encode(v.encode()).decode().rstrip('=')+'\n' for k,v in sorted(body.items())).encode()+b'sig=signature-is-fixture\n';(self.control/'public'/(self.round+'.return120')).write_bytes(self.packet)
 def request(self,path=None):
  statuses=[];h=SimpleNamespace(path=path or '/control73/'+self.round+'.return120',command='GET',wfile=io.BytesIO(),send_error=lambda code:statuses.append(code),send_response=lambda code:statuses.append(code),send_header=lambda *args:None,end_headers=lambda:None);matched=Routes73(self.control,'/').route(h);return matched,statuses,h.wfile.getvalue()
 def test_current_heartbeat_serves_fixed_path(self):m,c,data=self.request();self.assertTrue(m);self.assertEqual(c,[200]);self.assertEqual(data,self.packet)
 def test_absent_heartbeat_withdraws_even_saved_proof(self):(self.root/'return120.local').unlink();self.assertEqual(self.request()[1],[404])
 def test_stale_heartbeat_refused(self):
  d=read(self.root/'return120.local',self.key);write(self.root/'return120.local',self.key,dict(d,at=str(self.now-6000)));self.assertEqual(self.request()[1],[404])
 def test_other_round_refused(self):self.assertEqual(self.request('/control73/'+str(uuid.uuid4())+'.return120')[1],[404])
 def test_traversal_not_a_file_route(self):self.assertEqual(self.request('/control73/../owner74.key')[1],[404])

if __name__=='__main__':unittest.main(verbosity=2)
