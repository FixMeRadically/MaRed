"""Worker gate ordering tests. Network signatures/TLS are tested separately by the real JDK fixture."""
from pathlib import Path
from types import SimpleNamespace
import base64,hashlib,sys,tempfile,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.control73 import Gate73,decode,prepare
from first58.bridge67 import body

class GateChecks73(unittest.TestCase):
 def setUp(self):
  t=tempfile.TemporaryDirectory();self.addCleanup(t.cleanup);self.root=Path(t.name);(self.root/'candidate').mkdir();(self.root/'candidate/public.der').write_bytes(b'explicit fixture key; crypto tested by JDK')
  self.c={k:str(uuid.uuid4()) for k in ('instance','round','client','account','ticket','release','distribution')};self.c.update(schema='73',authMode='LOCAL_OFFLINE',secret='a'*64,archive='b'*64,game='localhost:25565',endpoint='https://localhost/genesis/',signer=hashlib.sha256((self.root/'candidate/public.der').read_bytes()).hexdigest())
  self.w=SimpleNamespace(root=self.root/'run',folder=self.root,approved={'after':{'one.jar':'d'*64}},r={'authMode':'LOCAL_OFFLINE','candidate':str(self.root/'candidate')},account=self.c['account'],info={'release':self.c['release'],'archiveSha256':self.c['archive'],'distribution':self.c['distribution'],'game':self.c['game'],'endpoint':self.c['endpoint']},require_source=Mock(),state=Mock(),cancelled=Mock(return_value=False));self.w.root.mkdir()
 def gate(self,c=None):return Gate73(self.w,body(self.c if c is None else c))
 def test_auth_failure_does_not_announce_prepared(self):
  check=Mock(side_effect=ValueError('AUTH89_LOGIN_REQUIRED'))
  with patch.object(Gate73,'prepare') as announce:
   with self.assertRaisesRegex(ValueError,'AUTH89_LOGIN_REQUIRED'):
    prepare(self.w,body(self.c),before_ready=check)
   announce.assert_not_called()
  self.assertIsInstance(self.w.gate73,Gate73)
 def test_silent_control_endpoint_has_a_deadline(self):
  g=self.gate();g.call=Mock(return_value='QUEUED')
  g.status=Mock(side_effect=ValueError('CONTROL73_NETWORK'))
  now=[0]
  def sleep(seconds):now[0]+=seconds
  ask=Mock()
  with patch('first58.control73.time.monotonic',side_effect=lambda:now[0]),patch('first58.control73.time.sleep',side_effect=sleep):
   with self.assertRaisesRegex(ValueError,'CONTROL73_WAIT_TIMEOUT'):
    g.receipt('PREPARED','COMMIT',ask=ask)
  ask.assert_not_called();self.assertFalse(g.committed)
 def test_config_round_trip(self):self.assertEqual(decode(body(self.c)),self.c)
 def test_duplicate_field_is_not_config(self):
  with self.assertRaises(ValueError):decode(body(self.c)+b'schema=NzM\n')
 def test_noncanonical_order_refused(self):
  with self.assertRaises(ValueError):decode(b'b=MQ\na=Mg\n')
 def test_unknown_cfg_field_refused(self):
  with self.assertRaises(ValueError):self.gate({**self.c,'exec':'command'})
 def test_account_binding(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):self.gate({**self.c,'account':str(uuid.uuid4())})
 def test_auth_mode_binding(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):self.gate({**self.c,'authMode':'ONLINE'})
 def test_archive_binding(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):self.gate({**self.c,'archive':'f'*64})
 def test_pinned_identity(self):
  with self.assertRaisesRegex(ValueError,'PIN'):self.gate({**self.c,'signer':'f'*64})
 def test_stale_sequence_not_accepted(self):
  g=self.gate();g.last=20;g.call=Mock(return_value=base64.b64encode(body({'sequence':'19'})).decode())
  with self.assertRaisesRegex(ValueError,'ROLLBACK'):g.status()
 def test_cancelled_round_never_commits(self):
  g=self.gate();g.call=Mock(return_value=base64.b64encode(body({'sequence':'1','phase':'CANCELLED'})).decode())
  with self.assertRaises(InterruptedError):g.prepare()
  self.assertFalse(g.committed)
 def test_closed_round_never_passes_final_check(self):
  g=self.gate();g.call=Mock(return_value=base64.b64encode(body({'sequence':'1','phase':'CLOSED'})).decode())
  with self.assertRaises(InterruptedError):g.check()
 def test_preparing_does_not_authorize_shutdown(self):
  g=self.gate();g.status=Mock(return_value={'phase':'PREPARING'})
  with self.assertRaisesRegex(ValueError,'NOT_COMMITTED'):g.check()
 def test_queue_success_is_not_commit(self):
  g=self.gate();request=uuid.uuid4();g.call=Mock(return_value='QUEUED');g.status=Mock(return_value={'clients':self.c['client']+',PREPARED,'+str(request),'phase':'PREPARING'});n=[0]
  def cancel():n[0]+=1;return n[0]>1
  with patch('first58.control73.uuid.uuid4',return_value=request),patch('first58.control73.time.sleep'):
   with self.assertRaises(InterruptedError):g.receipt('PREPARED','COMMIT',cancel=cancel)
  self.assertFalse(g.committed);self.w.require_source.assert_called_once()
 def test_signed_commit_and_matching_ack_authorize(self):
  g=self.gate();request=uuid.uuid4();g.call=Mock(return_value='QUEUED');g.status=Mock(return_value={'clients':self.c['client']+',PREPARED,'+str(request),'phase':'COMMITTED'})
  with patch('first58.control73.uuid.uuid4',return_value=request):g.prepare()
  self.assertTrue(g.committed)
 def test_commit_with_wrong_ack_not_permission(self):
  g=self.gate();g.call=Mock(return_value='QUEUED');g.status=Mock(return_value={'clients':self.c['client']+',PREPARED,'+str(uuid.uuid4()),'phase':'COMMITTED'});n=[0]
  def cancel():n[0]+=1;return n[0]>1
  with patch('first58.control73.time.sleep'):
   with self.assertRaises(InterruptedError):g.receipt('PREPARED','COMMIT',cancel=cancel)
  self.assertFalse(g.committed)
 def test_installed_waits_for_explicit_admission(self):
  g=self.gate();q=uuid.uuid4();g.call=Mock(return_value='QUEUED');g.status=Mock(return_value={'clients':self.c['client']+',INSTALLED,'+str(q),'phase':'COMMITTED','admission':'false','installed':'1','needed':'1'});n=[0]
  def cancel():n[0]+=1;return n[0]>1
  with patch('first58.control73.uuid.uuid4',return_value=q),patch('first58.control73.time.sleep'):
   with self.assertRaises(InterruptedError):g.receipt('INSTALLED','ADMISSION',cancel=cancel)
 def test_admission_waits_for_threshold(self):
  g=self.gate();q=uuid.uuid4();g.call=Mock(return_value='QUEUED');g.status=Mock(return_value={'clients':self.c['client']+',INSTALLED,'+str(q),'phase':'COMMITTED','admission':'true','installed':'1','needed':'2'});n=[0]
  def cancel():n[0]+=1;return n[0]>1
  with patch('first58.control73.uuid.uuid4',return_value=q),patch('first58.control73.time.sleep'):
   with self.assertRaises(InterruptedError):g.receipt('INSTALLED','ADMISSION',cancel=cancel)
 def test_after_commit_no_abort_sent(self):
  g=self.gate();g.committed=True;g.call=Mock();g.abort();g.call.assert_not_called()
 def test_installed_requires_actual_inventory(self):
  g=self.gate();g.receipt=Mock()
  with patch('first58.control73.inventory',return_value='f'*64):
   with self.assertRaisesRegex(ValueError,'INVENTORY'):g.installed(Mock(),Mock(),Mock())
  g.receipt.assert_not_called()
 def test_join_receipt_survives_lost_response_with_same_nonce(self):
  g=self.gate();g.call=Mock(side_effect=[ValueError('CONTROL73_NETWORK'),'QUEUED'])
  g.status=Mock(side_effect=ValueError('CONTROL73_NETWORK'))
  self.assertFalse(g.joined())
  g.status.side_effect=None
  g.status.return_value={'clients':self.c['client']+',JOINED,'+str(g.join_request)}
  self.assertTrue(g.joined())
  self.assertEqual(g.call.call_args_list[0],g.call.call_args_list[1])
if __name__=='__main__':unittest.main()
