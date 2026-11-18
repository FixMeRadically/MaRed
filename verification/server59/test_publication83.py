"""First import and baseline activation boundaries; process/READY and Java outputs are explicit models."""
from pathlib import Path
from types import SimpleNamespace
import copy,json,sys,tempfile,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import dedicated74 as base
from first58.dedicated83 import Owner83
from first58 import publication83 as pub
from first58.bridge67 import read,write
from first58.common import inventory,json_write,sha
import test_dedicated74 as fixture

class Activation83(unittest.TestCase):
 def setUp(self):
  f=fixture.OwnerBoundaries74();f.setUp();self.addCleanup(f.doCleanups);self.f=f;self.root=f.root
  for n in (75,76,77):write(f.root/f'owner{n}',f.key,dict(schema=str(n),run=f.s['run']))
  self.o=Owner83(f.root,f.root);self.source=self.root/'generations/server-1';f.source.rename(self.source);base.session(self.root,f.s,f.key,1,f.s['initialInventory']);self.o.current=1;self.o.jobs={1:f.job};self.o.ready_generation1=True;self.o.status=Mock(return_value=dict(phase='READY'));self.o.report=Mock();self.o.info=dict(release='release',client='a'*64,server='b'*64,minecraft='1.21.1',neoforge='21.1.264',game='localhost:25565',endpoint='https://localhost:8443/',distribution='dist',signer='signer',sequence=1,parent='none');self.o.tool=Mock(return_value=self.o.info);self.verify=patch('first58.dedicated83.pub.verify_pair',return_value=self.o.info);self.verify.start();self.addCleanup(self.verify.stop);self.pair=self.root/'releases/r1';self.plan=dict(delta=dict(added=[],changed=[],removed=[]))
  self.policy=patch('first58.dedicated83.policy',return_value=self.plan);self.policy.start();self.addCleanup(self.policy.stop);self.call=patch('first58.dedicated83.pub.call',return_value={'path':str(self.pair)});self.rpc=self.call.start();self.addCleanup(self.call.stop)
 def unchanged(self):return {str(p.relative_to(self.source)):p.read_bytes() for p in self.source.rglob('*') if p.is_file()}
 def test_exact_first_selects_without_world_or_mod_changes(self):
  before=self.unchanged();self.o.publish_baseline('r1');self.assertEqual(self.unchanged(),before);self.assertEqual(read(self.root/'publication75',self.f.key)['release'],'release');self.assertEqual(self.o.active_label76,'r1');self.f.job.close.assert_not_called()
 def test_client_renewal_same_code_selects_new_release(self):
  self.o.previous_info={**self.o.info,'release':'old'};self.o.active_label76='old';self.o.successor=Mock(return_value=True);self.rpc.return_value={'seal':'same'};self.o.publish_baseline('r1');self.assertEqual(self.o.previous_info['release'],'release');self.f.job.close.assert_not_called()
 def test_changed_client_requires_normal_update(self):
  self.o.previous_info={**self.o.info,'release':'old'};self.o.active_label76='old';self.o.successor=Mock(return_value=True);self.rpc.side_effect=[{'seal':'new'},{'seal':'old'}]
  with self.assertRaisesRegex(ValueError,'CLIENT_CODE_DIFFERS'):self.o.publish_baseline('r1')
  self.assertFalse((self.root/'publication75').exists())
 def test_changed_server_requires_normal_update(self):
  self.plan['delta']['changed']=['example.jar']
  with self.assertRaisesRegex(ValueError,'SERVER_CODE_DIFFERS'):self.o.publish_baseline('r1')
 def test_added_server_mod_requires_normal_update(self):
  self.plan['delta']['added']=['new.jar']
  with self.assertRaisesRegex(ValueError,'SERVER_CODE_DIFFERS'):self.o.publish_baseline('r1')
 def test_removed_server_mod_requires_normal_update(self):
  self.plan['delta']['removed']=['old.jar']
  with self.assertRaisesRegex(ValueError,'SERVER_CODE_DIFFERS'):self.o.publish_baseline('r1')
 def test_commit_blocks_baseline_switch(self):
  self.o.external=True
  with self.assertRaisesRegex(ValueError,'UPDATE_BUSY'):self.o.publish_baseline('r1')
 def test_prepared_update_blocks_baseline_switch(self):
  self.o.pending={'label':'r2'}
  with self.assertRaisesRegex(ValueError,'UPDATE_BUSY'):self.o.publish_baseline('r1')
 def test_dead_job_is_not_ready(self):
  self.f.job.status.return_value['rootExited']=True
  with self.assertRaisesRegex(ValueError,'SERVER_NOT_READY'):self.o.publish_baseline('r1')
 def test_missing_runtime_receipt_is_not_ready(self):
  self.o.status.return_value=None
  with self.assertRaisesRegex(ValueError,'SERVER_NOT_READY'):self.o.publish_baseline('r1')
 def test_generation_zero_has_no_initial_baseline_admission(self):
  self.o.current=0
  with self.assertRaisesRegex(ValueError,'UPDATE_BUSY'):self.o.publish_baseline('r1')
 def test_changed_profile_refused(self):
  (self.source/'mods/unknown.jar').write_bytes(b'new')
  with self.assertRaisesRegex(ValueError,'RUNTIME_CHANGED'):self.o.publish_baseline('r1')
 def test_wrong_first_pair_not_selected(self):
  self.rpc.return_value={'path':str(self.root/'releases/another')}
  with self.assertRaisesRegex(ValueError,'FIRST_REQUIRED'):self.o.publish_baseline('r1')
 def test_renewal_identity_may_not_change(self):
  self.o.previous_info={**self.o.info,'release':'old','endpoint':'https://other/'};self.o.active_label76='old';self.o.successor=Mock(return_value=True);self.rpc.return_value={'seal':'same'}
  with self.assertRaisesRegex(ValueError,'IDENTITY'):self.o.publish_baseline('r1')
 def test_source_changes_during_verification_refused(self):
  def mutate(*args,**kwargs):(self.source/'mods/change.jar').write_bytes(b'changed');return {'path':str(self.pair)}
  self.rpc.side_effect=mutate
  with self.assertRaisesRegex(ValueError,'RUNTIME_CHANGED'):self.o.publish_baseline('r1')
 def test_stop_queued_during_verification_wins(self):
  write(self.root/'request74',self.f.key,dict(action='SHUTDOWN'))
  with self.assertRaisesRegex(ValueError,'STOP_PENDING'):self.o.publish_baseline('r1')
 def test_activation_error_does_not_stop_live_world(self):
  from first58.dedicated82 import Owner82
  self.o.publish_baseline=Mock(side_effect=ValueError('PUBLICATION83_CLIENT_CODE_DIFFERS'))
  with patch.object(Owner82,'receive',return_value=dict(action='PUBLISH_BASELINE',label='r1')):self.assertIsNone(self.o.receive())
  self.f.job.close.assert_not_called()

class FirstBackend83(unittest.TestCase):
 def setUp(self):
  f=fixture.OwnerBoundaries74();f.setUp();self.addCleanup(f.doCleanups);self.f=f;self.root=f.root
 def test_offline_public_host_refused_before_signing(self):
  with patch.object(pub,'call') as call:
   with self.assertRaisesRegex(ValueError,'OFFLINE_LOOPBACK'):pub.first(self.root,self.root,self.root/'client',self.root/'server','r1',3600,'example.org:25565','https://example.org/')
   call.assert_not_called()
 def test_wrong_game_port_refused(self):
  with self.assertRaisesRegex(ValueError,'ADDRESS'):pub.first(self.root,self.root,self.root/'client',self.root/'server','r1',3600,'localhost:25566','https://localhost:8443/')
 def test_live_mods_directory_refused(self):
  with self.assertRaisesRegex(ValueError,'LIVE_SOURCE'):pub.first(self.root,self.root,self.f.source/'mods',self.root/'server','r1',3600,'localhost:25565','https://localhost:8443/')
 def test_preview_compacts_after_java_verification(self):
  info=dict(ticket='a'*64,client=[dict(bytes=1,path='mods/a.jar')]*21,server=[])
  with patch.object(pub,'call',return_value=info):d=pub.first(self.root,self.root,self.root/'client',self.root/'server','r1',3600,'localhost:25565','https://localhost:8443/')
  self.assertEqual(d['clientCount'],21);self.assertEqual(len(d['client']),20)

if __name__=='__main__':unittest.main()
