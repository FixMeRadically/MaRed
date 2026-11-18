from pathlib import Path
import json,sys,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import publication79 as p
from first58.bridge67 import write
import test_dedicated74 as fixture

class Publication79(unittest.TestCase):
 def setUp(self):
  f=fixture.OwnerBoundaries74();f.setUp();self.addCleanup(f.doCleanups);self.root=f.root;self.key=f.key;self.s=f.s;self.client=self.root/'next-client';self.server=self.root/'next-server';self.client.mkdir();self.server.mkdir();self.release=str(uuid.uuid4());self.seed=self.root/'releases/r1/public';self.seed.mkdir(parents=True)
  write(self.root/'publication75',self.key,dict(schema='75',run=self.s['run'],label='r1',release=self.release))
  self.info=dict(release=self.release,minecraft=self.s['minecraft'],neoforge=self.s['neoforge'],game='localhost:'+self.s['port'])
 def execute(self,ticket=None,responses=None,**changes):
  info=dict(self.info,**changes);preview=dict(ticket='a'*64,client=[dict(bytes=3,path='mods/a.jar')]*22,server=[],excludedClient=[],excludedServer=[])
  replies=responses or [Mock(returncode=0,stdout=json.dumps(info)),Mock(returncode=0,stdout=json.dumps(preview))]
  with patch.object(p,'core_classpath',return_value='core.jar'),patch.object(p.subprocess,'run',side_effect=replies) as call:
   result=p.run(self.root,self.root,self.client,self.server,'r2',86400,ticket)
  return result,call
 def test_publication_pointer_selected(self):self.assertEqual(p.baseline(self.root,self.key),(self.seed,self.release))
 def test_pointer_wrong_run_rejected(self):
  write(self.root/'publication75',self.key,dict(schema='75',run=str(uuid.uuid4()),label='r1',release=self.release))
  with self.assertRaisesRegex(ValueError,'BASELINE'):p.baseline(self.root,self.key)
 def test_pointer_unknown_field_rejected(self):
  write(self.root/'publication75',self.key,dict(schema='75',run=self.s['run'],label='r1',release=self.release,extra='x'))
  with self.assertRaisesRegex(ValueError,'BASELINE'):p.baseline(self.root,self.key)
 def test_forged_pointer_rejected(self):
  write(self.root/'publication75',b'x'*32,dict(schema='75',run=self.s['run'],label='r1',release=self.release))
  with self.assertRaises(ValueError):p.baseline(self.root,self.key)
 def test_path_traversal_label_rejected(self):
  write(self.root/'publication75',self.key,dict(schema='75',run=self.s['run'],label='../x',release=self.release))
  with self.assertRaises(ValueError):p.baseline(self.root,self.key)
 def test_preview_bounds_view_but_keeps_complete_count(self):
  result,calls=self.execute();self.assertEqual(result['clientCount'],22);self.assertEqual(result['clientBytes'],66);self.assertEqual(len(result['client']),20);self.assertIn('preview',calls.call_args.args[0]);self.assertNotIn('--ticket',calls.call_args.args[0])
 def test_author_exact_ticket_forwarded(self):
  _,calls=self.execute('a'*64);self.assertEqual(calls.call_args.args[0][-1],'a'*64);self.assertIn('author',calls.call_args.args[0])
 def test_invalid_ticket_not_executed(self):
  with patch.object(p.subprocess,'run') as call:
   with self.assertRaisesRegex(ValueError,'TICKET'):p.run(self.root,self.root,self.client,self.server,'r2',86400,'bad')
   call.assert_not_called()
 def test_busy_rejected_before_java(self):
  write(self.root/'view75',self.key,dict(busy='true',control='{}'))
  with patch.object(p.subprocess,'run') as call:
   with self.assertRaisesRegex(ValueError,'UPDATE_BUSY'):p.run(self.root,self.root,self.client,self.server,'r2',86400)
   call.assert_not_called()
 def test_committed_round_rejected(self):
  write(self.root/'view75',self.key,dict(busy='false',control=json.dumps(dict(phase='COMMITTED'))))
  with self.assertRaisesRegex(ValueError,'UPDATE_BUSY'):p.available(self.root,self.key)
 def test_malformed_control_rejected(self):
  write(self.root/'view75',self.key,dict(busy='false',control='{'))
  with self.assertRaisesRegex(ValueError,'OWNER_VIEW'):p.available(self.root,self.key)
 def test_live_generation_mods_rejected(self):
  self.client=self.root/'generations/server-0/mods'
  with self.assertRaisesRegex(ValueError,'LIVE_SOURCE'):self.execute()
 def test_wrong_release_rejected_before_author(self):
  with self.assertRaisesRegex(ValueError,'BASELINE'):self.execute(release=str(uuid.uuid4()))
 def test_wrong_minecraft_rejected(self):
  with self.assertRaisesRegex(ValueError,'BASELINE'):self.execute(minecraft='1.21.2')
 def test_wrong_loader_rejected(self):
  with self.assertRaisesRegex(ValueError,'BASELINE'):self.execute(neoforge='21.1.999')
 def test_wrong_port_rejected(self):
  with self.assertRaisesRegex(ValueError,'BASELINE'):self.execute(game='localhost:25566')
 def test_offline_remote_address_rejected(self):
  self.s['auth']='false'
  with patch.object(p.base,'settings',return_value=(self.s,self.key)):
   with self.assertRaisesRegex(ValueError,'OFFLINE_LOOPBACK'):self.execute(game='remote.example:'+self.s['port'])
 def test_error_output_does_not_echo_paths_or_token(self):
  responses=[Mock(returncode=0,stdout=json.dumps(self.info)),Mock(returncode=2,stderr='secret/path/token')]
  with self.assertRaisesRegex(ValueError,'PUBLICATION79_REJECTED'):self.execute(responses=responses)
 def test_known_error_retained(self):
  responses=[Mock(returncode=0,stdout=json.dumps(self.info)),Mock(returncode=2,stderr='PUBLICATION79_INPUT_CHANGED')]
  with self.assertRaisesRegex(ValueError,'INPUT_CHANGED'):self.execute(responses=responses)
 def test_recovered_source_cannot_publish_again(self):
  (self.root/'recovered77').write_text('claimed')
  with self.assertRaisesRegex(ValueError,'RECOVERED_ROOT'):p.available(self.root,self.key)
 def test_incomplete_target_cannot_publish(self):
  (self.root/'import77.pending').write_text('pending')
  with self.assertRaisesRegex(ValueError,'INCOMPLETE_ROOT'):p.available(self.root,self.key)
 def test_no_baseline_honest_error(self):
  (self.root/'publication75').unlink()
  with patch('first58.https78.load',return_value=(self.s,self.key,None)):
   with self.assertRaisesRegex(ValueError,'BASELINE_REQUIRED'):p.baseline(self.root,self.key)
 def test_explicit_fallback_for_first_release(self):
  (self.root/'publication75').unlink()
  with patch('first58.https78.load',return_value=(self.s,self.key,None)):self.assertEqual(p.baseline(self.root,self.key,self.seed),(self.seed,None))
 def test_existing_owner_baseline_overrides_manual_fallback(self):self.assertEqual(p.baseline(self.root,self.key,self.root/'other'),(self.seed,self.release))
 def test_recovered_baseline_selected(self):
  (self.root/'publication75').unlink();write(self.root/'recovery77',self.key,dict(schema='77',run=self.s['run'],label='r1',release=self.release));self.assertEqual(p.baseline(self.root,self.key),(self.seed,self.release))
if __name__=='__main__':unittest.main()
