"""Actual HMAC/atomic metadata tests; owner Job/readiness and Java answers explicitly modeled."""
from pathlib import Path
import json,sys,time,uuid,copy,unittest
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import publication85 as state,dedicated74 as base,renew85
from first58.bridge67 import read,write,sign
from first58.dedicated85 import Owner85
import test_dedicated74 as fixture

class Journal85(unittest.TestCase):
 def setUp(self):
  f=fixture.OwnerBoundaries74();f.setUp();self.addCleanup(f.doCleanups);self.f=f;self.root=f.root;self.j=state.Journal85(self.root);self.old=str(uuid.uuid4());self.new=str(uuid.uuid4());self.inv=f.s['initialInventory'];write(self.root/'publication75',f.key,dict(schema='75',run=f.s['run'],label='r1',release=self.old));write(self.root/'baseline83',f.key,dict(schema='83',run=f.s['run'],generation='1',inventory=self.inv,label='r1',release=self.old));self.before={p:(self.root/p).read_bytes() for p in state.POINTERS}
 def begin(self):return self.j.begin(1,self.inv,'r2',self.new)
 def test_begin_does_not_switch(self):
  d=self.begin();self.assertEqual(d['phase'],'PREPARED');self.assertEqual({p:(self.root/p).read_bytes() for p in state.POINTERS},self.before)
 def test_commit_both_pointers(self):
  self.j.apply(self.begin());self.assertEqual(self.j.load()['phase'],'COMMITTED')
  for p in state.POINTERS:self.assertEqual(read(self.root/p,self.f.key)['release'],self.new)
 def test_second_pointer_failure_repairs(self):
  d=self.begin();original=state.atomic;calls=[0]
  def fail(p,b):
   calls[0]+=1
   if calls[0]==2:raise OSError('disk failure')
   original(p,b)
  with patch.object(state,'atomic',side_effect=fail):
   with self.assertRaises(OSError):self.j.apply(d)
  self.assertEqual(read(self.root/'baseline83',self.f.key)['release'],self.new);self.assertEqual(read(self.root/'publication75',self.f.key)['release'],self.old);self.j.apply(self.j.load());self.assertEqual(self.j.load()['phase'],'COMMITTED')
 def test_final_journal_write_failure_is_recoverable(self):
  d=self.begin();original=state.write
  def fail(p,k,v):
   if v.get('phase')=='COMMITTED':raise OSError('disk failure')
   original(p,k,v)
  with patch.object(state,'write',side_effect=fail):
   with self.assertRaises(OSError):self.j.apply(d)
  self.assertEqual(self.j.load()['phase'],'PREPARED');self.j.apply(self.j.load());self.assertEqual(self.j.load()['phase'],'COMMITTED')
 def test_unknown_pointer_refuses_zero_overwrite(self):
  d=self.begin();write(self.root/'publication75',self.f.key,dict(schema='75',run=self.f.s['run'],label='other',release=str(uuid.uuid4())));before={p:(self.root/p).read_bytes() for p in state.POINTERS}
  with self.assertRaisesRegex(ValueError,'POINTER_CONFLICT'):self.j.apply(d)
  self.assertEqual(before,{p:(self.root/p).read_bytes() for p in state.POINTERS})
 def test_abort_restores_exact_old_bytes(self):
  d=self.begin();desired=json.loads(d['target']);state.atomic(self.root/'baseline83',self.j.unpack(desired['baseline83']));self.j.apply(d,abort=True);self.assertEqual({p:(self.root/p).read_bytes() for p in state.POINTERS},self.before);self.assertEqual(self.j.load()['phase'],'ABORTED')
 def test_partial_abort_finishes_only_abort(self):
  d=self.begin();desired=json.loads(d['target'])
  for p in state.POINTERS:state.atomic(self.root/p,self.j.unpack(desired[p]))
  original=state.atomic;calls=[0]
  def fail(p,b):
   calls[0]+=1
   if calls[0]==2:raise OSError('disk')
   original(p,b)
  with patch.object(state,'atomic',side_effect=fail):
   with self.assertRaises(OSError):self.j.apply(d,abort=True)
  self.assertEqual(self.j.load()['phase'],'ABORTING')
  with self.assertRaisesRegex(ValueError,'ABORT_PENDING'):self.j.apply(self.j.load())
  self.j.apply(self.j.load(),abort=True);self.assertEqual({p:(self.root/p).read_bytes() for p in state.POINTERS},self.before)
 def test_first_publication_can_restore_absence(self):
  for p in state.POINTERS:(self.root/p).unlink()
  d=self.begin();target=json.loads(d['target']);state.atomic(self.root/'baseline83',self.j.unpack(target['baseline83']));self.j.apply(d,abort=True);self.assertTrue(all(not (self.root/p).exists() for p in state.POINTERS))
 def test_new_target_blocked_by_unfinished_old(self):
  self.begin()
  with self.assertRaisesRegex(ValueError,'REPAIR_REQUIRED'):self.j.begin(1,self.inv,'r3',str(uuid.uuid4()))
 def test_current_target_resumes_same_transaction(self):
  d=self.begin();self.assertEqual(self.begin()['id'],d['id'])
 def test_corrupt_journal_fails_closed(self):
  self.begin();self.j.path.write_bytes(b'corrupt')
  with self.assertRaises(ValueError):self.j.load()
  self.assertEqual({p:(self.root/p).read_bytes() for p in state.POINTERS},self.before)
 def test_wrong_run_journal_refused(self):
  d=self.begin();write(self.j.path,self.f.key,dict(d,run=str(uuid.uuid4())))
  with self.assertRaisesRegex(ValueError,'JOURNAL'):self.j.load()
 def test_journal_changed_after_validation_refused(self):
  d=self.begin();write(self.j.path,self.f.key,dict(d,at='1'))
  with self.assertRaisesRegex(ValueError,'JOURNAL_CHANGED'):self.j.apply(d)
 def test_wrong_generation_target_refused(self):
  d=self.begin();write(self.j.path,self.f.key,dict(d,generation='2'))
  with self.assertRaisesRegex(ValueError,'JOURNAL'):self.j.load()
 def test_defaults_and_save(self):
  self.assertEqual(state.config(self.root)['ttl'],'86400');state.save(self.root,False,3600,300);self.assertEqual(state.config(self.root)['enabled'],'false')
 def test_invalid_settings_refused(self):
  for ttl,margin in [(59,60),(604801,60),(3600,1801),(3600,0)]:
   with self.assertRaises(ValueError):state.save(self.root,True,ttl,margin)
 def test_request_needs_new_owner(self):
  with self.assertRaisesRegex(ValueError,'NEW_OWNER_REQUIRED'):state.request(self.root,'RENEW')
 def test_request_is_signed_not_action_completion(self):
  write(self.root/'owner85',self.f.key,dict(schema='85',run=self.f.s['run']));r=state.request(self.root,'REPAIR');self.assertEqual(read(self.root/'renew85.request',self.f.key)['request'],r['queued']);self.assertFalse(self.j.path.exists())

 def test_stopped_owner_never_shows_watching(self):
  write(self.root/'publication85.status',self.f.key,dict(schema='85',run=self.f.s['run'],at=str(int(time.time()*1000)),state='WATCHING',code='',expires='0',certificate='NOT_CONFIGURED',certificateExpires='0'))
  write(self.root/'view75',self.f.key,dict(schema='75',run=self.f.s['run'],phase='STOPPED_SAVED',updated=str(int(time.time()*1000))))
  self.assertEqual(state.status(self.root)['state'],'STOPPED')
 def test_status_rejects_stale_report(self):
  write(self.root/'publication85.status',self.f.key,dict(schema='85',run=self.f.s['run'],at='1',state='WATCHING',code='',expires='0',certificate='NOT_CONFIGURED',certificateExpires='0'))
  self.assertEqual(state.status(self.root)['state'],'OWNER_UNAVAILABLE')

class OwnerBoundary85(unittest.TestCase):
 def setUp(self):
  f=fixture.OwnerBoundaries74();f.setUp();self.addCleanup(f.doCleanups);self.f=f;self.root=f.root
  for n in (75,76,77):write(self.root/f'owner{n}',f.key,dict(schema=str(n),run=f.s['run']))
  self.o=Owner85(self.root,self.root);f.source.rename(self.root/'generations/server-1');base.session(self.root,f.s,f.key,1,f.s['initialInventory']);f.s['initialGeneration']=1;self.o.s['initialGeneration']=1;base.json_write(self.root/'settings.json',f.s);write(self.root/'settings74',f.key,dict(schema='74',sha=base.sha(self.root/'settings.json',65536)));self.o.current=1;self.o.jobs={1:f.job};self.o.ready_generation1=True;self.o.health=Mock();self.o.report=Mock();self.o.status=Mock(return_value=dict(phase='READY'));self.o.active_label76='r1';self.o.previous_info=dict(release=str(uuid.uuid4()));self.o.core85=Mock(return_value=dict(expires=int(time.time()*1000)+3600000));self.o.renew85=Mock();self.o.config85='default'
 def test_idle_waits_until_margin(self):self.o.maintenance85();self.o.renew85.assert_not_called();self.assertEqual(self.o.state85,'WATCHING')
 def test_near_expiry_renews(self):self.o.core85.return_value['expires']=int(time.time()*1000)+60000;self.o.maintenance85();self.o.renew85.assert_called_once()
 def test_disabled_does_not_renew_expired(self):state.save(self.root,False,86400,900);self.o.core85.return_value['expires']=1;self.o.maintenance85();self.o.renew85.assert_not_called();self.assertEqual(self.o.state85,'DISABLED')
 def test_update_defers_renewal(self):self.o.pending=dict(label='r2');self.o.maintenance85();self.o.renew85.assert_not_called();self.assertEqual(self.o.state85,'WAITING_UPDATE')
 def test_active_round_defers_renewal(self):self.o.external=True;self.o.maintenance85();self.o.renew85.assert_not_called()
 def test_clock_jump_suspends(self):self.o.wall85-=3600;self.o.maintenance85();self.o.renew85.assert_not_called();self.assertEqual(self.o.state85,'CLOCK_CHANGED')
 def test_save_rearms_clock(self):self.o.wall85-=3600;state.save(self.root,True,86400,900);self.o.maintenance85();self.assertFalse(self.o.clock85)
 def test_renew_failure_does_not_close_job(self):self.o.core85.side_effect=ValueError('RENEWAL85_CODE_OR_IDENTITY_CHANGED');self.o.maintenance85();self.f.job.close.assert_not_called();self.assertEqual(self.o.state85,'SUSPENDED')
 def test_dead_job_guard(self):
  self.f.job.status.return_value['rootExited']=True
  with self.assertRaisesRegex(ValueError,'SERVER_NOT_READY'):self.o.guard85()
 def test_no_ready_guard(self):
  self.o.status.return_value=None
  with self.assertRaisesRegex(ValueError,'SERVER_NOT_READY'):self.o.guard85()
 def test_changed_runtime_guard(self):
  (self.root/'generations/server-1/mods/extra.jar').write_bytes(b'changed')
  with self.assertRaisesRegex(ValueError,'RUNTIME_CHANGED'):self.o.guard85()
 def test_stop_pending_guard(self):
  self.o.stop_requested=True
  with self.assertRaises(InterruptedError):self.o.guard85()
 def test_repair_requires_exact_generation(self):
  self.o.journal85.begin(2,self.f.s['initialInventory'],'r2',str(uuid.uuid4()))
  with self.assertRaisesRegex(ValueError,'GENERATION_CHANGED'):self.o.repair85()
 def test_corrupt_journal_cannot_kill_world_via_receive(self):
  self.o.journal85.path.write_bytes(b'broken')
  self.assertIsNone(self.o.receive());self.f.job.close.assert_not_called();self.assertEqual(self.o.state85,'REPAIR_REQUIRED')
 def test_manual_renew_works_with_auto_off(self):state.save(self.root,False,86400,900);state.request(self.root,'RENEW');self.o.maintenance85();self.o.renew85.assert_called_once()
 def test_request_not_repeated(self):state.request(self.root,'RENEW');self.assertEqual(self.o.action85(),'RENEW');self.assertIsNone(self.o.action85())
 def test_manual_request_waits_through_update(self):
  state.request(self.root,'RENEW');self.o.pending=dict(label='r2');self.o.maintenance85();self.assertEqual(self.o.request85,'');self.o.pending=None;self.o.next85=0;self.o.maintenance85();self.o.renew85.assert_called_once()
 def test_expired_target_requires_explicit_restore(self):
  tx=self.o.journal85.begin(1,self.f.s['initialInventory'],'r2',str(uuid.uuid4()));self.o.core85.return_value={'expires':1};self.o.publish_baseline=Mock()
  with self.assertRaisesRegex(ValueError,'TARGET_EXPIRED'):self.o.repair85()
  self.o.publish_baseline.assert_not_called();self.assertEqual(self.o.journal85.load()['phase'],'PREPARED')
 def test_no_baseline_cannot_auto_renew(self):self.o.previous_info=None;self.o.maintenance85();self.assertEqual(self.o.state85,'WAITING_BASELINE')

class TlsSelected85(unittest.TestCase):
 def setUp(self):
  f=fixture.OwnerBoundaries74();f.setUp();self.addCleanup(f.doCleanups);self.f=f;self.root=f.root;self.m=renew85.Manager85(self.root,self.root,'java');self.release=str(uuid.uuid4());write(self.root/'publication75',f.key,dict(schema='75',run=f.s['run'],label='r2',release=self.release));self.public=self.root/'releases/r2/public';self.public.mkdir(parents=True);(self.public/'offer.bin').write_bytes(b'fixture');self.config=dict(public='old',publicSeal='old',endpoint='https://localhost:8443/')
 def test_selects_new_pointer_not_old_expired_folder(self):
  with patch('first58.https78.public_info',return_value=dict(release=self.release,endpoint=self.config['endpoint'])):d=self.m.configuration(self.config)
  self.assertEqual(d['public'],str(self.public));self.assertEqual(d['endpoint'],self.config['endpoint'])
 def test_changed_endpoint_refused(self):
  with patch('first58.https78.public_info',return_value=dict(release=self.release,endpoint='https://other/')):
   with self.assertRaisesRegex(ValueError,'PUBLICATION'):self.m.configuration(self.config)
 def test_changed_release_refused(self):
  with patch('first58.https78.public_info',return_value=dict(release=str(uuid.uuid4()),endpoint=self.config['endpoint'])):
   with self.assertRaisesRegex(ValueError,'PUBLICATION'):self.m.configuration(self.config)
 def test_live_transport_not_restarted_by_pointer_only(self):
  self.m.state='RUNNING_TLS';self.m.token='keep';self.m.failed_token='keep'
  with patch('first58.https78.Manager78.tick'):self.m.tick()
  self.assertEqual(self.m.token,'keep')
 def test_failed_transport_retries_on_new_pointer(self):
  self.m.state='FAILED';self.m.token='old';self.m.failed_token='old';self.m.observed85='old'
  with patch('first58.https78.Manager78.tick'):self.m.tick()
  self.assertIsNone(self.m.token);self.assertIsNone(self.m.failed_token)
if __name__=='__main__':unittest.main()
