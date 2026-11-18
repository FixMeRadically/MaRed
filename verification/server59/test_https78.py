"""Real HMAC/config/threads and bounded owner lifetime; TLS certificate parsing has a separate real-JDK fixture."""
from pathlib import Path
import copy,json,sys,threading,time,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import https78 as h,dedicated78 as owner
from first58.bridge67 import read,write
from first58.common import sha
import test_dedicated74 as fixture

class Transport:
 def __init__(self):self.stop=threading.Event();self.closed=False;self.connections_closed=False
 def serve_forever(self):self.stop.wait()
 def shutdown(self):self.stop.set()
 def close_connections(self):self.connections_closed=True
 def server_close(self):self.closed=True

class Https78(unittest.TestCase):
 def setUp(self):
  f=fixture.OwnerBoundaries74();f.setUp();self.addCleanup(f.doCleanups);self.root=f.root;self.s=f.s;self.key=f.key;write(self.root/'owner77',self.key,dict(schema='77',run=self.s['run']));(self.root/'publisher73').mkdir();(self.root/'publisher73/public.der').write_bytes(b'pub');self.public=self.root/'public';self.public.mkdir();(self.public/'public.der').write_bytes(b'pub');(self.public/'offer.bin').write_bytes(b'offer');(self.public/'offer.sig').write_bytes(b'sig');(self.public/('client-'+'a'*64+'.zip')).write_bytes(b'zip');self.cert=self.root/'cert.pem';self.cert.write_bytes(b'cert');self.pem=self.root/'key.pem';self.pem.write_bytes(b'key')
  self.cfg=dict(schema='78',run=self.s['run'],revision=str(uuid.uuid4()),enabled='true',public=str(self.public),cert=str(self.cert),key=str(self.pem),bind='127.0.0.1',port='8443',endpoint='https://localhost:8443/genesis/',certSha=sha(self.cert),keySha=sha(self.pem),publicSeal=h.base.digest(h.base.tree(self.public,'unused')),fingerprint='b'*64,expires=str(int(time.time()*1000)+86400000));self.servers=[]
 def store(self,**changes):
  cfg=dict(self.cfg,**changes);write(self.root/'https78.cfg',self.key,cfg);return cfg
 def build(self,*args):t=Transport();self.servers.append(t);return t
 def manager(self):
  m=h.Manager78(self.root,self.root,'java',self.build);self.addCleanup(m.close);return m
 def checked(self,*args):
  _,_,d=h.load(self.root);return d
 def test_no_config_is_honest_not_configured(self):
  m=self.manager();m.tick(True);self.assertEqual(m.state,'NOT_CONFIGURED');self.assertEqual(self.servers,[])
 def test_owned_thread_starts_and_closes_with_owner(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=self.checked):m.tick(True)
  self.assertEqual(m.state,'RUNNING_TLS');self.assertTrue(m.thread.is_alive());m.close();self.assertTrue(self.servers[0].closed);self.assertTrue(self.servers[0].connections_closed);self.assertEqual(h.status(self.root)['state'],'STOPPED')
 def test_unchanged_configuration_does_not_restart(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=self.checked) as check:m.tick(True);m.tick(True);self.assertEqual(check.call_count,1)
  self.assertEqual(len(self.servers),1)
 def test_new_revision_reconfigures_only_after_old_thread_stops(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=self.checked):m.tick(True);self.store(revision=str(uuid.uuid4()));m.tick(True)
  self.assertEqual(len(self.servers),2);self.assertTrue(self.servers[0].closed);self.assertFalse(self.servers[1].closed)
 def test_disabled_config_has_no_transport(self):
  self.store(enabled='false');m=self.manager();m.tick(True);self.assertEqual(m.state,'DISABLED');self.assertEqual(self.servers,[])
 def test_disable_closes_active_connections(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=self.checked):m.tick(True);h.disable(self.root);m.tick(True)
  self.assertTrue(self.servers[0].connections_closed);self.assertEqual(m.state,'DISABLED')
 def test_tampered_config_refused(self):
  write(self.root/'https78.cfg',b'x'*32,self.cfg);m=self.manager();m.tick(True);self.assertEqual(m.state,'FAILED');self.assertEqual(self.servers,[])
 def test_wrong_owner_run_refused(self):
  self.store(run=str(uuid.uuid4()))
  with self.assertRaisesRegex(ValueError,'CONFIG'):h.load(self.root)
 def test_unexpected_config_field_refused(self):
  self.store(untrusted='yes')
  with self.assertRaisesRegex(ValueError,'CONFIG'):h.load(self.root)
 def test_invalid_revision_refused(self):
  self.store(revision='not-a-uuid')
  with self.assertRaises(ValueError):h.load(self.root)
 def test_active_cert_change_closes_transport_but_does_not_raise(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=self.checked):m.tick(True);self.cert.write_bytes(b'other');m.tick(True)
  self.assertEqual(m.reason,'HTTPS78_CERTIFICATE_CHANGED');self.assertTrue(self.servers[0].closed)
 def test_active_private_key_change_closes_transport(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=self.checked):m.tick(True);self.pem.write_bytes(b'other secret');m.tick(True)
  self.assertEqual(m.state,'FAILED');self.assertTrue(self.servers[0].closed)
 def test_expired_active_certificate_closes_transport(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=self.checked):m.tick(True);m.active['expires']='1';m.tick(True)
  self.assertEqual(m.state,'FAILED')
 def test_failed_revision_is_not_retried_in_hot_loop(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=ValueError('HTTPS78_KEY_PAIR')) as check:m.tick(True);m.tick(True);m.tick(True);self.assertEqual(check.call_count,1)
 def test_explicit_resave_retries_failed_revision(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=ValueError('HTTPS78_KEY_PAIR')):m.tick(True)
  self.store(revision=str(uuid.uuid4()))
  with patch.object(h,'check',side_effect=self.checked):m.tick(True)
  self.assertEqual(m.state,'RUNNING_TLS')
 def active_round(self):write(self.root/'view75',self.key,dict(schema='75',run=self.s['run'],phase='WAITING_ALL_INSTALLED',busy='false',updated=str(int(time.time()*1000)),control=json.dumps(dict(phase='COMMITTED'))))
 def test_disable_during_commit_is_refused_without_file_change(self):
  self.store();self.active_round();before=(self.root/'https78.cfg').read_bytes()
  with self.assertRaisesRegex(ValueError,'UPDATE_BUSY'):h.disable(self.root)
  self.assertEqual(before,(self.root/'https78.cfg').read_bytes())
 def test_save_during_commit_is_refused_before_validation(self):
  self.active_round()
  with patch.object(h,'check') as check:
   with self.assertRaisesRegex(ValueError,'UPDATE_BUSY'):h.save(self.root,self.root,self.public,self.cert,self.pem,'127.0.0.1','java')
   check.assert_not_called()
 def test_manager_defers_a_racing_change_during_commit(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=self.checked):
   m.tick(True);self.active_round();self.store(revision=str(uuid.uuid4()),enabled='false');m.tick(True);self.assertEqual(m.state,'CHANGE_DEFERRED');self.assertFalse(self.servers[0].closed);(self.root/'view75').unlink();m.tick(True);self.assertEqual(m.state,'DISABLED')
 def failed_status(self):
  write(self.root/'https78.status',self.key,dict(schema='78',run=self.s['run'],at=str(int(time.time()*1000)),state='FAILED',code='HTTPS78_CERTIFICATE_CHANGED',revision=self.cfg['revision'],endpoint=self.cfg['endpoint'],fingerprint=self.cfg['fingerprint']))
 def test_same_address_tls_repair_is_allowed_during_commit(self):
  self.store();self.active_round();self.failed_status();new=dict(self.cfg,revision=str(uuid.uuid4()))
  with patch.object(h,'check',return_value=new):h.save(self.root,self.root,self.public,self.cert,self.pem,'127.0.0.1','java')
  self.assertEqual(h.load(self.root)[2]['revision'],new['revision'])
 def test_tls_repair_cannot_change_listener_address_during_commit(self):
  self.store();self.active_round();self.failed_status();before=(self.root/'https78.cfg').read_bytes();new=dict(self.cfg,bind='::1',revision=str(uuid.uuid4()))
  with patch.object(h,'check',return_value=new):
   with self.assertRaisesRegex(ValueError,'UPDATE_BUSY'):h.save(self.root,self.root,self.public,self.cert,self.pem,'::1','java')
  self.assertEqual(before,(self.root/'https78.cfg').read_bytes())
 def test_stale_failed_status_does_not_authorize_live_reconfiguration(self):
  self.store();self.active_round();self.failed_status();d=read(self.root/'https78.status',self.key);d['at']='1';write(self.root/'https78.status',self.key,d)
  with self.assertRaisesRegex(ValueError,'UPDATE_BUSY'):h.save(self.root,self.root,self.public,self.cert,self.pem,'127.0.0.1','java')
 def test_disable_is_still_forbidden_during_failed_committed_update(self):
  self.store();self.active_round();self.failed_status()
  with self.assertRaisesRegex(ValueError,'UPDATE_BUSY'):h.disable(self.root)
 def test_manager_repairs_failed_https_without_waiting_for_commit_to_finish(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=ValueError('HTTPS78_KEY_PAIR')):m.tick(True)
  self.active_round();self.store(revision=str(uuid.uuid4()))
  with patch.object(h,'check',side_effect=self.checked):m.tick(True)
  self.assertEqual(m.state,'RUNNING_TLS')
 def test_final_stopped_view_does_not_block_configuration(self):
  self.store();self.active_round();d=read(self.root/'view75',self.key);d['phase']='STOPPED_SAVED';write(self.root/'view75',self.key,d);h.disable(self.root)
 def test_stale_old_view_does_not_block_configuration(self):
  self.store();self.active_round();d=read(self.root/'view75',self.key);d['updated']='1';write(self.root/'view75',self.key,d);h.disable(self.root)
 def test_diagnostic_write_failure_does_not_stop_world_owner(self):
  m=self.manager()
  with patch.object(h,'write',side_effect=OSError('disk full')):m.tick(True);m.close()
 def test_listen_failure_is_not_reported_as_running(self):
  self.store();m=self.manager();m.builder=Mock(side_effect=OSError('port busy'))
  with patch.object(h,'check',side_effect=self.checked):m.tick(True)
  self.assertEqual(m.reason,'HTTPS78_LISTEN_FAILED');self.assertIsNone(m.thread)
 def test_status_contains_no_private_key_bytes(self):
  self.pem.write_bytes(b'PRIVATE_CONTENT_DO_NOT_EXPORT');self.store(keySha=sha(self.pem));self.assertNotIn('PRIVATE_CONTENT_DO_NOT_EXPORT',json.dumps(h.status(self.root)))
 def test_status_distinguishes_configuration_pending_from_active_revision(self):
  self.store();m=self.manager()
  with patch.object(h,'check',side_effect=self.checked):m.tick(True)
  self.store(revision=str(uuid.uuid4()));self.assertEqual(h.status(self.root)['state'],'CHANGE_PENDING')
 def test_stale_heartbeat_not_running(self):
  self.store();m=self.manager();m.emit();d=read(self.root/'https78.status',self.key);d['at']='1';write(self.root/'https78.status',self.key,d);self.assertEqual(h.status(self.root)['state'],'OWNER_UNAVAILABLE')
 def test_inherited_config_rebinds_run_and_preserves_operator_edits(self):
  self.store();other=self.root/'other';other.mkdir();s=copy.deepcopy(self.s);s['run']=str(uuid.uuid4());profile=other/'generations/server-0';h.base.copy_tree(self.root/'generations/server-0',profile,'world');(other/'owner74.key').write_bytes(b'n'*32);h.base.json_write(other/'settings.json',s);write(other/'settings74',b'n'*32,dict(schema='74',sha=sha(other/'settings.json')));h.inherit(self.root,other);_,_,d=h.load(other);self.assertEqual(d['run'],s['run']);self.assertNotEqual(d['revision'],self.cfg['revision']);d['enabled']='false';write(other/'https78.cfg',b'n'*32,d);h.inherit(self.root,other);self.assertEqual(h.load(other)[2]['enabled'],'false')
 def test_unstarted_listener_is_closed_without_deadlocking_shutdown(self):
  m=self.manager();server=Mock();server.shutdown.side_effect=AssertionError('shutdown before serve_forever');m.server=server;m.thread=None;self.assertTrue(m.stop_transport());server.shutdown.assert_not_called();server.server_close.assert_called_once()
 def test_hung_listener_prevents_replacement_and_does_not_block_world_forever(self):
  m=self.manager();server=Transport();m.server=server;m.thread=threading.Thread(target=server.serve_forever,daemon=True);m.thread.start();server.shutdown=lambda:None;start=time.monotonic();self.assertFalse(m.stop_transport());self.assertLess(time.monotonic()-start,4);self.assertIs(m.server,server);server.stop.set();m.thread.join(timeout=1);self.assertTrue(m.stop_transport())
 def test_owner_failure_still_closes_https(self):
  instance=owner.Owner78.__new__(owner.Owner78);instance.root=self.root;instance.key=self.key;instance.s=self.s;instance.project=self.root
  mgr=Mock()
  with patch.object(owner,'independent_owner'),patch.object(owner,'Manager78',return_value=mgr),patch.object(owner.Owner77,'run',side_effect=ValueError('world failed')):
   with self.assertRaisesRegex(ValueError,'world failed'):instance.run()
  mgr.close.assert_called_once()
 def test_independent_owner_check_precedes_https_start(self):
  instance=owner.Owner78.__new__(owner.Owner78);instance.root=self.root
  with patch.object(owner,'independent_owner',side_effect=ValueError('in client job')),patch.object(owner,'Manager78') as manager:
   with self.assertRaises(ValueError):instance.run()
   manager.assert_not_called()
if __name__=='__main__':unittest.main()
