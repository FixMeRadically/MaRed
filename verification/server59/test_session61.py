"""Synthetic session tests; no real credentials and no online-authentication claim."""
from pathlib import Path
import json,sys,tempfile,unittest,uuid
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.session61 import consume,apply
from first58.wizard_model import SetupError
class SessionTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
  self.root=Path(self.temp.name);self.profile=self.root/'client';self.profile.mkdir()
  self.path=self.root/'client-session61.json';self.account=str(uuid.uuid4())
  self.data={'schema':61,'pid':123,'profile':str(self.profile),'account':self.account,'name':'TestPlayer','accessToken':'synthetic-token-not-real','userType':'msa','issuedAt':1000000}
 def use(self,**change):
  data={**self.data,**change};self.path.write_text(json.dumps(data));return consume(self.root,self.path,123,self.profile,self.account,now=1001000)
 def test_valid_consumed_once(self):
  self.assertEqual(self.use(),self.data);self.assertFalse(self.path.exists())
 def test_already_consumed_session_reports_setup_error(self):
  self.use()
  with self.assertRaises(SetupError):
   consume(self.root,self.path,123,self.profile,self.account,now=1001000)
 def test_consuming_old_file_does_not_remove_new_handoff(self):
  from unittest.mock import patch
  from first58.common import json_read
  newer={**self.data,'issuedAt':1000100}
  def read_claimed(path):
   self.path.write_text(json.dumps(newer))
   return json_read(path)
  with patch('first58.session61.json_read',side_effect=read_claimed):
   self.use()
  self.assertEqual(json.loads(self.path.read_text()),newer)
 def test_missing_cli_auth_added(self):
  result=apply(['java','main','--gameDir','{profile}'],self.data)
  for key in ('--uuid','--username','--accessToken','--userType'):self.assertEqual(result.count(key),1)
  self.assertEqual(result[result.index('--accessToken')+1],self.data['accessToken'])
 def test_stale_cli_token_replaced(self):
  result=apply(['java','main','--accessToken','old','--username','old'],self.data)
  self.assertNotIn('old',result)
 def test_equals_form_replaced(self):
  result=apply(['java','main','--accessToken=old','--uuid='+self.account],self.data)
  self.assertEqual(result.count('--uuid'),1);self.assertNotIn('--accessToken=old',result)
 def test_wrong_pid_refused_and_deleted(self):
  with self.assertRaises(SetupError):self.use(pid=456)
  self.assertFalse(self.path.exists())
 def test_wrong_account_refused(self):
  with self.assertRaises(SetupError):self.use(account=str(uuid.uuid4()))
 def test_wrong_profile_refused(self):
  with self.assertRaises(SetupError):self.use(profile=str(self.root))
 def test_stale_refused(self):
  with self.assertRaises(SetupError):self.use(issuedAt=1)
 def test_future_refused(self):
  with self.assertRaises(SetupError):self.use(issuedAt=2000000)
 def test_offline_tokens_refused_without_echo(self):
  for value in ('0','','offline','null','${auth_access_token}'):
   with self.assertRaisesRegex(SetupError,'online-токена'):self.use(accessToken=value)
 def test_token_controls_refused(self):
  with self.assertRaises(SetupError):self.use(accessToken='secret\nvalue')
 def test_legacy_user_refused(self):
  with self.assertRaises(SetupError):self.use(userType='legacy')
 def test_cli_uuid_conflict_refused(self):
  with self.assertRaises(SetupError):apply(['java','--uuid',str(uuid.uuid4())],self.data)
 def test_unknown_file_outside_root_preserved(self):
  other=self.profile/'client-session61.json';other.write_text(json.dumps(self.data))
  with self.assertRaises(SetupError):consume(self.root,other,123,self.profile,self.account,now=1001000)
  self.assertTrue(other.exists())
if __name__=='__main__':unittest.main(verbosity=2)
