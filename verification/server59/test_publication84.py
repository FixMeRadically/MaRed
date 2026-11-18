"""Local backend routing/guard models; archive/signature acceptance is tested by Java and linked file checks."""
from pathlib import Path
import copy,sys,unittest
from unittest.mock import patch,Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import publication84 as pub
import test_dedicated74 as fixture
class Backend84(unittest.TestCase):
 def setUp(self):
  f=fixture.OwnerBoundaries74();f.setUp();self.addCleanup(f.doCleanups);self.f=f;self.root=f.root;self.seed=self.root/'releases/r1/public';self.info=dict(release='first',minecraft='1.21.1',neoforge='21.1.264',game='localhost:25565',endpoint='https://localhost:8443/')
  self.p=patch.object(pub.publication79,'baseline',return_value=(self.seed,'first'));self.p.start();self.addCleanup(self.p.stop);self.command=patch.object(pub.publication83,'command');self.call=self.command.start();self.addCleanup(self.command.stop);self.call.side_effect=lambda *a,**k:copy.deepcopy(self.info) if a[2].endswith('PublicationStatus79') else dict(ticket='a'*64,client=[],server=[])
 def run84(self,action='script-prepare',input=None,**kw):return pub.run(self.root,self.root,action,input or self.root/'snapshot',**kw)
 def test_prepare_is_local_complete_set_cli(self):
  self.run84(side='BOTH');args=self.call.call_args.args;self.assertTrue(args[2].endswith('ScriptRelease84'));self.assertEqual(args[3],'prepare');self.assertEqual(args[-1],'BOTH');self.assertIn(self.root/'drafts84',args)
 def test_side_required(self):
  with self.assertRaisesRegex(ValueError,'SIDE_REQUIRED'):self.run84(side='NONE')
 def test_baseline_release_bound(self):
  self.info['release']='wrong'
  with self.assertRaisesRegex(ValueError,'BASELINE'):self.run84(side='BOTH')
 def test_neoforge_bound(self):
  self.info['neoforge']='21.1.235'
  with self.assertRaisesRegex(ValueError,'BASELINE'):self.run84(side='BOTH')
 def test_minecraft_bound(self):
  self.info['minecraft']='1.20.1'
  with self.assertRaisesRegex(ValueError,'BASELINE'):self.run84(side='BOTH')
 def test_port_bound(self):
  self.info['game']='localhost:25566'
  with self.assertRaisesRegex(ValueError,'BASELINE'):self.run84(side='BOTH')
 def test_offline_game_public_refused(self):
  self.info['game']='example.org:25565'
  with self.assertRaisesRegex(ValueError,'OFFLINE_LOOPBACK'):self.run84(side='BOTH')
 def test_offline_https_public_refused(self):
  self.info['endpoint']='https://example.org/'
  with self.assertRaisesRegex(ValueError,'OFFLINE_LOOPBACK'):self.run84(side='BOTH')
 def test_live_generation_refused(self):
  with self.assertRaisesRegex(ValueError,'LIVE_SOURCE'):self.run84(input=self.f.source/'mods',side='BOTH')
 def test_draft_must_belong_to_owner(self):
  with self.assertRaisesRegex(ValueError,'DRAFT_ROOT'):self.run84(action='script-preview',label='r2',ttl=3600)
 def test_draft_from_another_root_refused(self):
  with self.assertRaisesRegex(ValueError,'DRAFT_ROOT'):self.run84(action='script-author',input=self.root/'other/draft',label='r2',ttl=3600,ticket='a'*64)
 def test_preview_compacts(self):
  result=self.run84(action='script-preview',input=self.root/'drafts84/d1',label='r2',ttl=3600);self.assertEqual(result['clientCount'],0);self.assertEqual(result['serverBytes'],0)
 def test_short_ttl_refused(self):
  with self.assertRaisesRegex(ValueError,'TTL'):self.run84(action='script-preview',input=self.root/'drafts84/d1',label='r2',ttl=59)
 def test_long_ttl_refused(self):
  with self.assertRaisesRegex(ValueError,'TTL'):self.run84(action='script-preview',input=self.root/'drafts84/d1',label='r2',ttl=604801)
 def test_ticket_required(self):
  with self.assertRaisesRegex(ValueError,'PREVIEW_REQUIRED'):self.run84(action='script-author',input=self.root/'drafts84/d1',label='r2',ttl=3600)
 def test_author_passes_exact_ticket(self):
  self.run84(action='script-author',input=self.root/'drafts84/d1',label='r2',ttl=3600,ticket='b'*64);self.assertEqual(self.call.call_args.args[-1],'b'*64)
 def test_recovered_root_blocked(self):
  (self.root/'recovered77').write_text('recovered')
  with self.assertRaisesRegex(ValueError,'RECOVERED_ROOT'):self.run84(side='BOTH')
 def test_preparing_import_blocked(self):
  (self.root/'import77.pending').write_text('pending')
  with self.assertRaisesRegex(ValueError,'INCOMPLETE_ROOT'):self.run84(side='BOTH')
 def test_arbitrary_action_refused(self):
  with self.assertRaisesRegex(ValueError,'ACTION'):self.run84(action='shell')
if __name__=='__main__':unittest.main()
