"""Account response policy and real HMAC challenge files, with explicit current-user/HTTP process models."""
from pathlib import Path
from types import SimpleNamespace
import copy,http.client,json,os,subprocess,sys,tempfile,time,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import auth89 as auth
from first58.bridge67 import read,write
from first58.session61 import offline_uuid
from first58.remote70 import Worker

class Profile89(unittest.TestCase):
 def setUp(self):self.user=dict(account=str(uuid.uuid4()),name='TestPlayer',accessToken='private-fixture89',userType='msa')
 def transport(self,code=200,**changes):return Mock(return_value=(code,json.dumps(dict(id=self.user['account'].replace('-',''),name=self.user['name'],**changes)).encode()))
 def test_valid_profile(self):self.assertEqual(auth.online(self.user,self.transport()),'AUTH89_OK')
 def test_hyphenated_id(self):self.assertEqual(auth.online(self.user,lambda t:(200,json.dumps(dict(id=self.user['account'],name=self.user['name'])).encode())),'AUTH89_OK')
 def test_wrong_uuid(self):
  with self.assertRaisesRegex(auth.AuthError,'ACCOUNT_CHANGED'):auth.online(self.user,lambda t:(200,json.dumps(dict(id=uuid.uuid4().hex,name=self.user['name'])).encode()))
 def test_wrong_name(self):
  with self.assertRaisesRegex(auth.AuthError,'ACCOUNT_CHANGED'):auth.online(self.user,lambda t:(200,json.dumps(dict(id=self.user['account'],name='Other')).encode()))
 def test_401(self):
  with self.assertRaisesRegex(auth.AuthError,'LOGIN_REQUIRED'):auth.online(self.user,self.transport(401))
 def test_403(self):
  with self.assertRaisesRegex(auth.AuthError,'LOGIN_REQUIRED'):auth.online(self.user,self.transport(403))
 def test_404(self):
  with self.assertRaisesRegex(auth.AuthError,'PROFILE_REQUIRED'):auth.online(self.user,self.transport(404))
 def test_rate_limit(self):
  with self.assertRaisesRegex(auth.AuthError,'UNAVAILABLE'):auth.online(self.user,self.transport(429))
 def test_service_down(self):
  with self.assertRaisesRegex(auth.AuthError,'UNAVAILABLE'):auth.online(self.user,self.transport(503))
 def test_redirect_is_not_followed(self):
  f=self.transport(302)
  with self.assertRaisesRegex(auth.AuthError,'UNAVAILABLE'):auth.online(self.user,f)
  self.assertEqual(f.call_count,1)
 def test_duplicate_json_fields_refused(self):
  with self.assertRaisesRegex(auth.AuthError,'RESPONSE'):auth.online(self.user,lambda t:(200,b'{"id":"a","id":"b","name":"TestPlayer"}'))
 def test_oversized_response_refused(self):
  with self.assertRaisesRegex(auth.AuthError,'RESPONSE'):auth.online(self.user,lambda t:(200,b'x'*32769))
 def test_html_response_refused(self):
  with self.assertRaisesRegex(auth.AuthError,'RESPONSE'):auth.online(self.user,lambda t:(200,b'<html>private</html>'))
 def test_network_exception_does_not_leak_token(self):
  with self.assertRaisesRegex(auth.AuthError,'UNAVAILABLE') as e:auth.online(self.user,Mock(side_effect=OSError('private-fixture89')))
  self.assertNotIn('fixture',str(e.exception))
 def test_empty_or_placeholder_never_contacts_network(self):
  for token in ('','0','null','offline','${access_token}'):
   f=Mock()
   with self.assertRaisesRegex(auth.AuthError,'LOGIN_REQUIRED'):auth.online(dict(self.user,accessToken=token),f)
   f.assert_not_called()
 def test_header_injection_refused(self):
  f=Mock()
  with self.assertRaisesRegex(auth.AuthError,'SESSION'):auth.online(dict(self.user,accessToken='a\r\nb'),f)
  f.assert_not_called()
 def test_legacy_online_refused(self):
  with self.assertRaisesRegex(auth.AuthError,'LOGIN_REQUIRED'):auth.online(dict(self.user,userType='legacy'),Mock())
 def test_child_probe_token_not_in_argv(self):
  with patch.object(auth.subprocess,'run',return_value=SimpleNamespace(returncode=0,stdout=b'AUTH89_OK\n')) as run:auth.probe(self.user)
  args=run.call_args.args[0];kw=run.call_args.kwargs;self.assertNotIn(self.user['accessToken'],' '.join(args));self.assertIn('-I',args);self.assertEqual(json.loads(kw['input'])['accessToken'],self.user['accessToken']);self.assertEqual(kw['timeout'],16)
 def test_child_timeout_is_safe(self):
  with patch.object(auth.subprocess,'run',side_effect=subprocess.TimeoutExpired('helper',16)),self.assertRaisesRegex(auth.AuthError,'UNAVAILABLE'):auth.probe(self.user)
 def test_unknown_child_output_is_never_reported(self):
  with patch.object(auth.subprocess,'run',return_value=SimpleNamespace(returncode=2,stdout=b'private-fixture89')),self.assertRaisesRegex(auth.AuthError,'UNAVAILABLE'):auth.probe(self.user)
 def test_duplicate_auth_option_refused(self):
  with self.assertRaisesRegex(auth.AuthError,'SESSION'):auth.session(['java','--uuid',self.user['account'],'--uuid',self.user['account']])

class Handoff89(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.account=str(uuid.uuid4());self.w=SimpleNamespace(root=self.root,key=b'k'*32,id=str(uuid.uuid4()),source=self.root/'source',account=self.account,r=dict(auth89=True,account=self.account,authMode='ONLINE',pid=123,birth=456),spec=dict(clientCommand=['java','--gameDir','{profile}','--uuid',self.account,'--username','TestPlayer','--accessToken','old-private-fixture','--userType','msa']),cancelled=Mock(return_value=False),require_source=Mock(),phase='ARMING',state=Mock(),job=Mock(),config=None)
 def answer(self,**overrides):
  def wait(n):
   d=read(self.root/'auth89.request',self.w.key);d.update(answered=str(int(time.time()*1000)),name='TestPlayer',accessToken='fresh-private-fixture',userType='msa');d.update(overrides);write(self.root/'auth89.response',self.w.key,d)
  return patch.object(auth.time,'sleep',side_effect=wait)
 def test_current_client_refreshes_token_in_memory(self):
  with self.answer():argv,s=auth.refresh(self.w)
  self.assertEqual(s['accessToken'],'fresh-private-fixture');self.assertNotIn('old-private-fixture',argv);self.assertFalse((self.root/'auth89.request').exists());self.assertFalse((self.root/'auth89.response').exists())
 def test_other_nonce_refused_and_deleted(self):
  with self.answer(nonce=str(uuid.uuid4())),self.assertRaisesRegex(auth.AuthError,'BINDING'):auth.refresh(self.w)
  self.assertFalse((self.root/'auth89.response').exists())
 def test_other_source_run_refused(self):
  with self.answer(sourceRun=str(uuid.uuid4())),self.assertRaisesRegex(auth.AuthError,'BINDING'):auth.refresh(self.w)
 def test_other_account_refused(self):
  with self.answer(account=str(uuid.uuid4())),self.assertRaisesRegex(auth.AuthError,'BINDING'):auth.refresh(self.w)
 def test_other_mode_refused(self):
  with self.answer(mode='LOCAL_OFFLINE'),self.assertRaisesRegex(auth.AuthError,'BINDING'):auth.refresh(self.w)
 def test_other_profile_refused(self):
  with self.answer(profile='/other'),self.assertRaisesRegex(auth.AuthError,'BINDING'):auth.refresh(self.w)
 def test_account_change_error_contains_no_token(self):
  def denied(n):
   d=read(self.root/'auth89.request',self.w.key);d.update(answered=str(int(time.time()*1000)),error='AUTH89_ACCOUNT_CHANGED');write(self.root/'auth89.response',self.w.key,d)
  with patch.object(auth.time,'sleep',side_effect=denied),self.assertRaisesRegex(auth.AuthError,'ACCOUNT_CHANGED'):auth.refresh(self.w)
 def test_changed_username_refused(self):
  with self.answer(name='Other'),self.assertRaisesRegex(auth.AuthError,'ACCOUNT_CHANGED'):auth.refresh(self.w)
 def test_unknown_response_field_refused(self):
  with self.answer(extra='bad'),self.assertRaisesRegex(auth.AuthError,'BINDING'):auth.refresh(self.w)
 def test_cancel_removes_one_use_secret(self):
  self.w.cancelled.return_value=True
  with self.assertRaises(InterruptedError):auth.refresh(self.w)
  self.assertFalse((self.root/'auth89.response').exists());self.assertFalse((self.root/'auth89.request').exists())
 def test_offline_initial_uuid_is_not_forced_to_online_identity(self):
  self.w.r.update(authMode='LOCAL_OFFLINE',account=str(uuid.UUID(int=0)));self.w.account=offline_uuid('TestPlayer');a=self.w.spec['clientCommand'];a[a.index('--uuid')+1]=self.w.account;a[a.index('--accessToken')+1]='0';a[a.index('--userType')+1]='legacy'
  with self.answer(account=self.w.r['account'],accessToken='',userType='legacy'):argv,s=auth.refresh(self.w)
  self.assertEqual(s['account'],self.w.account);self.assertEqual(s['accessToken'],'0');self.assertEqual(s['userType'],'legacy')
 def test_managed_current_generation_proof(self):
  self.w.config=dict(run=self.w.id,authMode='ONLINE');self.w.job.status.return_value={'rootExited':False}
  with self.answer():argv,s=auth.refresh(self.w,joining=True)
  self.assertEqual(s['account'],self.account)
 def test_old_managed_root_has_no_fallback_auth(self):
  self.w.config=dict(run=self.w.id)
  with self.assertRaisesRegex(auth.AuthError,'NEW_OWNER_REQUIRED'):auth.refresh(self.w,joining=True)
 def test_previous_owned_client_is_peer_for_repeat(self):
  self.w.previous=SimpleNamespace(root=self.root/'previous',key=b'p'*32,config=dict(run=str(uuid.uuid4())),account=self.account)
  root,key,ctx=auth.peer(self.w);self.assertEqual(root,self.w.previous.root);self.assertEqual(key,b'p'*32);self.assertEqual(ctx['scope'],'MANAGED');self.assertEqual(ctx['sourceRun'],self.w.previous.config['run'])
 def test_offline_guard_never_probes_network(self):
  self.w.r['authMode']='LOCAL_OFFLINE'
  with patch.object(auth,'refresh',return_value=(self.w.spec['clientCommand'],{})),patch.object(auth,'probe') as probe:auth.check(self.w)
  probe.assert_not_called();self.assertEqual(self.w.auth89['stage'],'OFFLINE')
 def test_rejected_probe_does_not_replace_launch_credentials(self):
  original=self.w.spec['clientCommand'][:]
  with self.answer(),patch.object(auth,'probe',side_effect=auth.AuthError('AUTH89_LOGIN_REQUIRED')),self.assertRaisesRegex(auth.AuthError,'LOGIN_REQUIRED'):auth.check(self.w)
  self.assertEqual(self.w.spec['clientCommand'],original);self.assertEqual(self.w.auth89['stage'],'REFUSED');self.assertNotIn('private',json.dumps(self.w.auth89))
 def test_token_rotated_during_probe_does_not_close_source_or_replace_argv(self):
  original=self.w.spec['clientCommand'][:];a=auth.session(original);b=dict(a,accessToken='rotated-fixture')
  with patch.object(auth,'refresh',side_effect=[(original,a),(original,b)]),patch.object(auth,'probe'),self.assertRaisesRegex(auth.AuthError,'SESSION_CHANGED'):auth.check(self.w)
  self.assertEqual(self.w.spec['clientCommand'],original);self.assertEqual(self.w.auth89['stage'],'REFUSED')
 def test_short_join_cache_avoids_request_spam(self):
  self.w.auth_cache89=(time.monotonic(),)
  with patch.object(auth,'refresh') as refresh:auth.check(self.w,joining=True)
  refresh.assert_not_called()
 def test_clock_backwards_does_not_extend_cache(self):
  self.w.auth_cache89=(time.monotonic()+100,)
  with patch.object(auth,'refresh',side_effect=auth.AuthError('AUTH89_SESSION')) as refresh,self.assertRaises(auth.AuthError):auth.check(self.w,joining=True)
  refresh.assert_called_once()

class ShutdownFence89(unittest.TestCase):
 def test_auth_failure_before_cover_never_requests_commit(self):
  w=Worker.__new__(Worker);w.state=Mock();w.require_source=Mock();w.check_storage87=Mock();w.wait_action=Mock();w.verify_remote=Mock();w.build=Mock();w.start=Mock();notify=Mock()
  with patch('first58.auth89.check',side_effect=auth.AuthError('AUTH89_LOGIN_REQUIRED')),self.assertRaises(auth.AuthError):w._activate(notify,lambda:False,Mock())
  notify.assert_not_called();w.wait_action.assert_not_called();w.verify_remote.assert_not_called();w.build.assert_not_called();w.start.assert_not_called()
 def test_disabled_legacy_path_is_explicit(self):
  w=SimpleNamespace(r={})
  with patch.object(auth,'refresh') as refresh:auth.check(w)
  refresh.assert_not_called()
if __name__=='__main__':unittest.main()
