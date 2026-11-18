"""Real owner-HMAC permits + fresh Maop policy. Minecraft source identities remain outside this suite."""
import base64,hashlib,tempfile,time,unittest,uuid
from pathlib import Path
from first58 import access88,maop113
from first58.bridge67 import write,sign,read
class Maop113(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.profile=Path(self.temp.name).resolve();self.root=self.profile/'owner';self.root.mkdir();self.file=self.profile/'world/genesis/maop113/permissions113.txt';self.file.parent.mkdir(parents=True);self.key=b'k'*32;self.run=str(uuid.uuid4());self.actor=str(uuid.uuid4());self.now=1700000000000
  access88.initialize(self.root,self.key,self.run);write(self.root/'owner113',self.key,dict(schema='113',run=self.run));write(self.root/'maop113.binding',self.key,dict(schema='113',run=self.run,profile=str(self.profile),policy=str(self.file)));self.policy('ADMIN')
 def policy(self,role,revision=1):
  body='GENESIS-MAOP113\nrevision='+str(revision)+'\n'+(self.actor+'='+role+'\n' if role else '')
  self.file.write_bytes((body+'sha256='+hashlib.sha256(body.encode()).hexdigest()+'\n').encode('ascii'))
 def permit(self,action='RESTART',root='true'):
  d=dict(schema='75',run=self.run,generation='1',request=str(uuid.uuid4()),action=action,label='r1');state,_=access88.state(self.root,self.key,self.run);p=dict(schema='88',run=self.run,actor=self.actor,root=root,revision=state['revision'],generation='1',request=d['request'],action=action,label='r1',issued=str(self.now-1000),expires=str(self.now+14000));d['permit88']=base64.b64encode(sign(self.key,p)).decode();return d
 def consume(self,d):access88.consume(self.root,self.key,self.run,d,self.now)
 def test_admin_permit_accepted(self):self.consume(self.permit());self.assertIn('ACCEPTED',read(self.root/'access88.state',self.key)['audit'])
 def test_console_revoke_rejects_previously_signed_root_permit(self):
  packet=self.permit();self.policy(None,2)
  with self.assertRaisesRegex(ValueError,'REVOKED'):self.consume(packet)
  self.assertIn('REJECTED',read(self.root/'access88.state',self.key)['audit'])
 def test_workspace_role_does_not_allow_restart(self):
  self.policy('WORKSPACE')
  with self.assertRaisesRegex(ValueError,'REVOKED'):self.consume(self.permit())
  self.assertTrue(maop113.authorize(self.root,self.key,self.run,self.actor,'WORKSPACE_CREATE'))
 def test_restart_role_cannot_publish(self):
  self.policy('RESTARTER');self.consume(self.permit())
  with self.assertRaisesRegex(ValueError,'REVOKED'):self.consume(self.permit('STAGE'))
 def test_publisher_cannot_restart(self):
  self.policy('PUBLISHER');self.consume(self.permit('STAGE'))
  with self.assertRaisesRegex(ValueError,'REVOKED'):self.consume(self.permit())
 def test_policy_removed_does_not_crash_controller_or_authorize(self):
  self.file.unlink()
  with self.assertRaisesRegex(ValueError,'REVOKED'):self.consume(self.permit())
 def test_binding_removed_does_not_fall_back_to_legacy_root(self):
  (self.root/'maop113.binding').unlink()
  with self.assertRaisesRegex(ValueError,'REVOKED'):self.consume(self.permit())
 def test_owner_removed_does_not_fall_back(self):
  (self.root/'owner113').unlink()
  with self.assertRaisesRegex(ValueError,'REVOKED'):self.consume(self.permit())
 def test_bad_binding_signature_denied(self):
  p=self.root/'maop113.binding';p.write_bytes(p.read_bytes()+b'x')
  with self.assertRaisesRegex(ValueError,'REVOKED'):self.consume(self.permit())
 def test_foreign_run_denied(self):
  write(self.root/'owner113',self.key,dict(schema='113',run=str(uuid.uuid4())))
  with self.assertRaisesRegex(ValueError,'REVOKED'):self.consume(self.permit())
 def test_policy_checksum_corruption_denied(self):
  self.file.write_text(self.file.read_text().replace('ADMIN','WORKSPACE'),encoding='ascii')
  with self.assertRaisesRegex(ValueError,'REVOKED'):self.consume(self.permit())
 def test_path_outside_profile_denied(self):
  with tempfile.TemporaryDirectory() as temp:
   path=Path(temp).resolve()/'permissions113.txt';path.write_bytes(self.file.read_bytes());write(self.root/'maop113.binding',self.key,dict(schema='113',run=self.run,profile=str(self.profile),policy=str(path)))
   with self.assertRaisesRegex(ValueError,'REVOKED'):self.consume(self.permit())
 def test_unsigned_local_console_command_remains_allowed(self):
  self.policy(None);packet=self.permit();packet.pop('permit88');self.consume(packet)
 def test_old_pre113_fixture_keeps_existing_contract(self):
  (self.root/'owner113').unlink();(self.root/'maop113.binding').unlink();self.policy(None);self.consume(self.permit())
 def test_canonical_order_and_revision(self):
  for value in [b'',b'x'*20001,b'GENESIS-MAOP113\nrevision=01\nsha256='+b'0'*64+b'\n']:
   self.file.write_bytes(value)
   with self.assertRaises(ValueError):maop113.policy(self.file)
 def test_all_roles_agree_on_workspace_creation(self):
  for role in maop113.RIGHTS:
   self.policy(role)
   self.assertEqual(maop113.authorize(self.root,self.key,self.run,self.actor,'WORKSPACE_CREATE'),role in {'ADMIN','WORKSPACE'})
if __name__=='__main__':unittest.main()
