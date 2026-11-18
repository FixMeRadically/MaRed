"""Real signed policy/permit files; source player/online/operator proof belongs to Minecraft adapter."""
from pathlib import Path
import base64,copy,json,os,sys,tempfile,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import access88 as access
from first58.bridge67 import read,write,sign
from first58.dedicated75 import Owner75

class Access88(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.key=b'k'*32;self.run=str(uuid.uuid4());self.actor=str(uuid.uuid4());self.now=1700000000000;access.initialize(self.root,self.key,self.run)
  self.d=dict(schema='75',run=self.run,generation='1',request=str(uuid.uuid4()),action='STAGE',label='r1');self.grant('PREPARER')
 def grant(self,role):
  s,_=access.state(self.root,self.key,self.run);s['revision']=str(int(s['revision'])+1);s['subjects']=self.actor+'='+role+'\n' if role else '';write(self.root/'access88.state',self.key,s)
 def permit(self,**changes):
  s,_=access.state(self.root,self.key,self.run);p=dict(schema='88',run=self.run,actor=self.actor,root='false',revision=s['revision'],generation='1',request=self.d['request'],action='STAGE',label='r1',issued=str(self.now-1000),expires=str(self.now+14000));p.update(changes);return {**self.d,'permit88':base64.b64encode(sign(self.key,p)).decode()}
 def test_valid_permission_accepted_and_audited(self):access.consume(self.root,self.key,self.run,self.permit(),self.now);s,_=access.state(self.root,self.key,self.run);self.assertIn('ACCEPTED',s['audit']);self.assertIn(self.actor,s['audit'])
 def test_revoked_after_queue_refused(self):
  d=self.permit();self.grant(None)
  with self.assertRaisesRegex(ValueError,'REVOKED'):access.consume(self.root,self.key,self.run,d,self.now)
  self.assertIn('REJECTED',read(self.root/'access88.state',self.key)['audit'])
 def test_unrelated_revision_change_also_requires_new_request(self):
  d=self.permit();self.grant('PREPARER')
  with self.assertRaisesRegex(ValueError,'REVOKED'):access.consume(self.root,self.key,self.run,d,self.now)
 def test_expired_permit_refused(self):
  with self.assertRaisesRegex(ValueError,'REVOKED'):access.consume(self.root,self.key,self.run,self.permit(expires=str(self.now)),self.now)
 def test_future_issue_refused(self):
  with self.assertRaisesRegex(ValueError,'REVOKED'):access.consume(self.root,self.key,self.run,self.permit(issued=str(self.now+3000),expires=str(self.now+15000)),self.now)
 def test_overlong_ttl_refused(self):
  with self.assertRaisesRegex(ValueError,'REVOKED'):access.consume(self.root,self.key,self.run,self.permit(expires=str(self.now+15001)),self.now)
 def test_root_flag_is_bound_inside_signature(self):
  d=self.permit();raw=base64.b64decode(d['permit88']);raw=raw.replace(b'root=ZmFsc2U',b'root=dHJ1ZQ');d['permit88']=base64.b64encode(raw).decode()
  with self.assertRaises(ValueError):access.consume(self.root,self.key,self.run,d,self.now)
 def test_actor_cannot_be_changed_by_outer_queue(self):
  d=self.permit(actor=str(uuid.uuid4()))
  with self.assertRaisesRegex(ValueError,'REVOKED'):access.consume(self.root,self.key,self.run,d,self.now)
 def test_other_generation_refused(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):access.consume(self.root,self.key,self.run,self.permit(generation='2'),self.now)
 def test_other_label_refused(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):access.consume(self.root,self.key,self.run,self.permit(label='r2'),self.now)
 def test_other_action_refused(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):access.consume(self.root,self.key,self.run,self.permit(action='COMMIT'),self.now)
 def test_other_nonce_refused(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):access.consume(self.root,self.key,self.run,self.permit(request=str(uuid.uuid4())),self.now)
 def test_other_owner_refused(self):
  with self.assertRaisesRegex(ValueError,'BINDING'):access.consume(self.root,self.key,self.run,self.permit(run=str(uuid.uuid4())),self.now)
 def test_ungranted_commit_refused_even_with_matching_outer_action(self):
  self.d['action']='COMMIT'
  with self.assertRaisesRegex(ValueError,'REVOKED'):access.consume(self.root,self.key,self.run,self.permit(action='COMMIT'),self.now)
 def test_operator_permit_still_has_revision_and_expiry_fence(self):
  self.d['action']='SHUTDOWN';d=self.permit(root='true',action='SHUTDOWN');self.grant(None)
  with self.assertRaisesRegex(ValueError,'REVOKED'):access.consume(self.root,self.key,self.run,d,self.now)
 def test_local_os_action_is_audited_without_player_spoofing(self):
  access.consume(self.root,self.key,self.run,self.d,self.now);s,_=access.state(self.root,self.key,self.run);self.assertIn('|LOCAL|LOCAL|STAGE|ACCEPTED|',s['audit'])
 def test_missing_policy_never_resets_existing_marker(self):
  (self.root/'access88.state').unlink()
  with self.assertRaises((ValueError,OSError)):access.initialize(self.root,self.key,self.run)
  self.assertFalse((self.root/'access88.state').exists())
 def test_existing_policy_never_overwritten_on_initialize(self):
  before=(self.root/'access88.state').read_bytes();access.initialize(self.root,self.key,self.run);self.assertEqual(before,(self.root/'access88.state').read_bytes())
 def test_foreign_marker_refused(self):
  write(self.root/'owner88',self.key,dict(schema='88',run=str(uuid.uuid4())))
  with self.assertRaisesRegex(ValueError,'NEW_OWNER'):access.consume(self.root,self.key,self.run,self.permit(),self.now)
 def test_tampered_state_refused(self):
  (self.root/'access88.state').write_bytes(b'unsigned')
  with self.assertRaises(ValueError):access.consume(self.root,self.key,self.run,self.d,self.now)
 def test_unknown_state_field_refused(self):
  s=read(self.root/'access88.state',self.key);s['extra']='bad';write(self.root/'access88.state',self.key,s)
  with self.assertRaisesRegex(ValueError,'STATE'):access.state(self.root,self.key,self.run)
 def test_unknown_role_refused(self):
  s=read(self.root/'access88.state',self.key);s['subjects']=self.actor+'=ROOT\n';write(self.root/'access88.state',self.key,s)
  with self.assertRaisesRegex(ValueError,'STATE'):access.state(self.root,self.key,self.run)
 def test_duplicate_subject_refused(self):
  s=read(self.root/'access88.state',self.key);s['subjects']*=2;write(self.root/'access88.state',self.key,s)
  with self.assertRaisesRegex(ValueError,'STATE'):access.state(self.root,self.key,self.run)
 def test_bounded_audit_preserves_monotonic_sequence(self):
  for i in range(80):self.d['request']=str(uuid.uuid4());access.consume(self.root,self.key,self.run,self.permit(),self.now)
  s,_=access.state(self.root,self.key,self.run);self.assertEqual(len(s['audit'].splitlines()),64);self.assertEqual(s['sequence'],'80');self.assertTrue(s['audit'].startswith('17|'))
 def test_maintainer_can_commit_but_cannot_gain_root_control(self):
  self.grant('MAINTAINER');self.d['action']='COMMIT';access.consume(self.root,self.key,self.run,self.permit(action='COMMIT'),self.now)
  self.d['action']='SHUTDOWN'
  with self.assertRaisesRegex(ValueError,'REVOKED'):access.consume(self.root,self.key,self.run,self.permit(action='SHUTDOWN'),self.now)
 def test_unsupported_role_cannot_grant_shutdown(self):self.assertNotIn('SHUTDOWN',set.union(*access.ROLES.values()));self.assertNotIn('LOCAL_TEST_ON',set.union(*access.ROLES.values()))

class OwnerQueue88(unittest.TestCase):
 def setUp(self):
  fixture=Access88();fixture.setUp();self.addCleanup(fixture.doCleanups);self.f=fixture;w=Owner75.__new__(Owner75);self.w=w;w.root=fixture.root;w.key=fixture.key;w.s=dict(run=fixture.run);w.current=1;w.last_request=None;w.seen=set()
 def test_revoked_request_is_acknowledged_but_never_returned_for_execution(self):
  f=self.f;d=f.permit();f.grant(None);write(f.root/'request74',f.key,d)
  with patch('first58.access88.time.time',return_value=f.now/1000),self.assertRaisesRegex(ValueError,'ACCESS88_REFUSED'):self.w.receive()
  self.assertEqual(read(f.root/'request74.ack',f.key)['request'],d['request']);self.assertIsNone(self.w.receive())
 def test_busy_access_lock_cannot_crash_owner_with_oserror(self):
  f=self.f;write(f.root/'request74',f.key,f.permit())
  with patch('first58.access88.consume',side_effect=BlockingIOError('private path')),self.assertRaisesRegex(ValueError,'ACCESS88_REFUSED'):self.w.receive()
 def test_transient_lock_contention_never_acknowledges_or_executes(self):
  f=self.f;d=f.permit();write(f.root/'request74',f.key,d)
  with patch('first58.access88.consume',side_effect=access.AccessBusy88('ACCESS88_BUSY')):self.assertIsNone(self.w.receive())
  self.assertEqual(self.w.seen,set());self.assertIsNone(self.w.last_request);self.assertFalse((f.root/'request74.ack').exists())
  with patch('first58.access88.time.time',return_value=f.now/1000):self.assertEqual(self.w.receive(),d)
  self.assertEqual(self.w.seen,{d['request']})
 def test_revoke_between_deferred_attempts_still_refuses(self):
  f=self.f;d=f.permit();write(f.root/'request74',f.key,d)
  with patch('first58.access88.consume',side_effect=access.AccessBusy88('ACCESS88_BUSY')):self.assertIsNone(self.w.receive())
  f.grant(None)
  with patch('first58.access88.time.time',return_value=f.now/1000),self.assertRaisesRegex(ValueError,'ACCESS88_REFUSED'):self.w.receive()
  self.assertEqual(read(f.root/'request74.ack',f.key)['request'],d['request'])
 def test_foreign_generation_never_reaches_consume(self):
  f=self.f;write(f.root/'request74',f.key,{**f.permit(), 'generation':'2'})
  with patch('first58.access88.consume') as consume,self.assertRaisesRegex(ValueError,'STALE_GENERATION'):self.w.receive()
  consume.assert_not_called()
 def test_plain_local_action_on88_remains_allowed_and_audited(self):
  f=self.f;write(f.root/'request74',f.key,f.d);self.assertEqual(self.w.receive(),f.d);self.assertIn('LOCAL',read(f.root/'access88.state',f.key)['audit'])

class Bootstrap88(unittest.TestCase):
 def setUp(self):
  import zipfile
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.mods=self.root/'generations/server-0/mods';self.mods.mkdir(parents=True);self.key=b'b'*32;self.run=str(uuid.uuid4())
 def jar(self,name,adapter=True,mared=True):
  import zipfile
  with zipfile.ZipFile(self.mods/name,'w') as z:
   if mared:z.writestr('com/fixmer/mared/Mared.class',b'fixture, not game bytecode')
   if adapter:z.writestr('com/fixmer/mared/technology/server/remote/ServerAccessPayloads88.class',b'fixture')
 def start(self):
  from first58.dedicated88 import Owner88
  def model(o,*args,**kwargs):o.root=self.root;o.key=self.key;o.s=dict(run=self.run,initialGeneration='0')
  with patch('first58.dedicated88.Owner85.__init__',autospec=True,side_effect=model):return Owner88(Path('/explicit-project-fixture'),self.root)
 def test_new_adapter_installs_bound_empty_policy(self):
  self.jar('mared.jar');self.start();s,users=access.state(self.root,self.key,self.run);self.assertEqual(s['revision'],'0');self.assertEqual(users,{})
 def test_old_adapter_refused_before_acl_capability(self):
  self.jar('mared.jar',adapter=False)
  with self.assertRaisesRegex(ValueError,'INSTALL_SERVER_BOOTSTRAP'):self.start()
  self.assertFalse((self.root/'owner88').exists())
 def test_multiple_bootstraps_refused(self):
  self.jar('mared.jar');self.jar('other.jar')
  with self.assertRaisesRegex(ValueError,'INSTALL_SERVER_BOOTSTRAP'):self.start()
 def test_missing_bootstrap_refused(self):
  self.jar('library.jar',mared=False)
  with self.assertRaisesRegex(ValueError,'INSTALL_SERVER_BOOTSTRAP'):self.start()

if __name__=='__main__':unittest.main()
