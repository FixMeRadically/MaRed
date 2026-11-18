"""Actual owner + Changes104 files and HMAC packets. Java game/Job/save/READY are explicit fixtures."""
from pathlib import Path
import contextlib,json,sys,tempfile,unittest,uuid,zipfile
from unittest.mock import patch,Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import update115
from first58.inplace104 import Owner104,root_for
from first58.bridge67 import read,write
from first58.common import inventory,sha

from approval_fixture120 import stage as fixture_stage120

class OwnerUpdate115(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.base=Path(self.temp.name);self.server=self.base/'server';(self.server/'mods').mkdir(parents=True);(self.server/'world').mkdir();(self.server/'world/level.dat').write_bytes(b'saved world unchanged')
  self.root=root_for(self.server);self.root.mkdir(parents=True);self.key=b'k'*32;(self.root/'owner74.key').write_bytes(self.key);self.app=self.base/'app';self.app.mkdir();self.snapshot=self.base/'snapshot';self.snapshot.mkdir()
  self.old=self.jar(self.server/'mods/demo-old.jar',b'old');self.untouched=self.jar(self.server/'mods/keep.jar',b'keep');self.new=self.jar(self.snapshot/'artifact.jar',b'new');self.old_bytes=self.old.read_bytes();self.keep_bytes=self.untouched.read_bytes()
  c=dict(argv=['fixture-java'],java='fixture-java',neoforge='21.1.264',minecraft='1.21.1',level='world',port='25565',bind='',auth='false')
  for target,kwargs in [('first58.inplace104.config',dict(return_value=c)),('first58.inplace104.old.environment',dict(return_value={})),('first58.inplace104.old.stopped_world',dict(side_effect=lambda *a:contextlib.nullcontext())),('first58.update115.verify_snapshot',dict(return_value=None)),('first58.inplace104.time.sleep',dict(return_value=None))]:
   p=patch(target,**kwargs);p.start();self.addCleanup(p.stop)
  self.owner=Owner104(self.server,self.app,Path('unused'),self.factory);self.o=self.owner;self.o.generation=1;self.o.launch=str(uuid.uuid4());self.o.channel=self.root/'runs'/self.o.launch;self.o.channel.mkdir();(self.o.channel/'world104').write_bytes(b'previous saved generation');self.o.expected=inventory(self.server);self.started=[];self.mode='success';self.o.job=self.job(self.o.channel,1);self.o.report('RUNNING')
  self.d=dict(schema='115',run=self.o.run,generation='1',request=str(uuid.uuid4()),action='APPLY115',label='r1',snapshot=str(self.snapshot),seal='a'*64,inventory=self.o.expected,target='demo-old.jar',sha256=sha(self.new),previous=sha(self.old))
 def jar(self,p,payload):
  with zipfile.ZipFile(p,'w') as z:z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\n[[mods]]\nmodId="demo"\nversion="1.0.0"\n');z.writestr('data',payload)
  return p
 def job(self,channel,generation,failed=False):
  j=Mock();j.dead=failed;j.code=7 if failed else 0
  def status():
   command=channel/f'server-{generation}.command'
   if command.exists() and not j.dead:
    d=read(command,self.key);j.dead=True;write(channel/f'server-{generation}.status',self.key,dict(schema='74',run=d['run'],generation=d['generation'],inventory=d['inventory'],sequence=d['sequence'],request=d['request'],phase='STOPPED_SAVED',round=d['round'],at='1'))
    return dict(rootExited=False,active=1,exitCode=None) # receipt is observed before the next exit observation
   return dict(rootExited=j.dead,active=0 if j.dead else 1,exitCode=j.code if j.dead else None)
  j.status.side_effect=status;return j
 def factory(self,argv,cwd,env,log):
  self.assertEqual(cwd,self.server);data=self.old.read_bytes();self.started.append(data);failed=data==self.new.read_bytes() and self.mode in ('crash','world','both-fail') or data==self.old_bytes and self.mode=='both-fail'
  log.write_text('candidate crash fixture' if failed else 'running fixture')
  if self.mode=='world' and failed:(self.o.channel/'world104').write_bytes(b'explicit world-loading fixture')
  j=self.job(self.o.channel,self.o.generation,failed)
  if not failed:write(self.o.channel/f'server-{self.o.generation}.status',self.key,dict(schema='74',run=self.o.run,generation=str(self.o.generation),inventory=self.o.expected,sequence='0',request=str(uuid.uuid4()),phase='READY',round='none',at='1'))
  return j
 def execute(self):
  self.o.request_id=self.d['request'];return update115.execute(self.o,self.d)
 def report(self):return json.loads((self.root/'logs/update115.json').read_text())
 def unchanged(self):
  self.assertEqual(self.untouched.read_bytes(),self.keep_bytes);self.assertEqual((self.server/'world/level.dat').read_bytes(),b'saved world unchanged');self.assertFalse((self.root/'generations').exists())
 def test_apply_calls_real_owner_save_start_and_commits(self):
  self.assertTrue(self.execute());self.assertEqual(self.old.read_bytes(),self.new.read_bytes());self.assertIsNone(self.o.changes.load());self.assertEqual(self.report()['status'],'APPLIED');self.assertEqual(self.o.phase,'RUNNING');self.assertEqual(self.started,[self.new.read_bytes()]);self.unchanged()
 def test_diagnostic_write_failure_does_not_stop_applied_server(self):
  with patch('first58.update115.json_write',side_effect=OSError('fixture logs unavailable')),patch.object(self.o,'hold_live') as hold:
   self.assertTrue(self.execute())
  hold.assert_not_called()
  self.assertEqual(self.o.request_result,'COMPLETED')
  self.assertEqual(self.old.read_bytes(),self.new.read_bytes())

 def test_early_failure_restores_and_starts_old_exactly_once(self):
  self.mode='crash';self.assertTrue(self.execute());self.assertEqual(self.started,[self.new.read_bytes(),self.old_bytes]);self.assertEqual(self.old.read_bytes(),self.old_bytes);self.assertEqual(self.report()['status'],'RESTORED');self.assertEqual(self.o.request_result,'ROLLED_BACK');self.assertEqual(self.o.error,'SERVER115_RESTORED');self.assertTrue((self.root/'logs'/('server-'+self.report()['candidateLaunch']+'.log')).exists());self.unchanged()
 def test_return_failure_has_no_retry_loop(self):
  self.mode='both-fail';self.assertFalse(self.execute());self.assertEqual(len(self.started),2);self.assertEqual(self.o.phase,'RECOVERY_REQUIRED');self.assertTrue((self.root/'review104').exists());self.assertEqual(self.report()['status'],'RETURN_FAILED');self.unchanged()
 def test_new_world_loading_requires_review_without_downgrade(self):
  self.mode='world';self.assertFalse(self.execute());self.assertEqual(self.old.read_bytes(),self.new.read_bytes());self.assertEqual(len(self.started),1);self.assertEqual(self.report()['status'],'REVIEW_REQUIRED');self.assertIsNotNone(self.o.changes.load());self.unchanged()
 def test_verifier_rejection_never_stops_old_server(self):
  with patch('first58.update115.verify_snapshot',side_effect=ValueError('SERVER115_REVIEW_CHANGED')):self.assertTrue(self.execute())
  self.assertEqual(self.old.read_bytes(),self.old_bytes);self.assertEqual(self.started,[]);self.assertFalse(self.o.job.dead);self.assertEqual(self.report()['status'],'REJECTED')
 def test_stale_inventory_rejected_before_stop(self):
  self.jar(self.server/'mods/foreign.jar',b'foreign');self.assertTrue(self.execute());self.assertEqual(self.started,[]);self.assertFalse(self.o.job.dead);self.assertIsNone(self.o.changes.load())
 def test_unrelated_prepared_transaction_is_not_cancelled(self):
  change=fixture_stage120(self.o.changes,{'demo-old.jar':self.new});self.assertTrue(self.execute());self.assertEqual(self.o.changes.load()[0]['id'],change);self.assertFalse(self.o.job.dead)
 def test_configuration_change_requires_separate_restart(self):
  with patch('first58.inplace104.config',return_value=dict(self.o.c,port='25566')):self.assertTrue(self.execute())
  self.assertEqual(self.o.error,'SERVER115_CONFIG_CHANGED');self.assertFalse(self.o.job.dead);self.assertIsNone(self.o.changes.load())
 def test_changed_candidate_after_review_rejected_before_stop(self):
  self.jar(self.new,b'changed');self.assertTrue(self.execute());self.assertEqual(self.started,[]);self.assertIsNone(self.o.changes.load());self.assertFalse(self.o.job.dead)
 def test_stage_copy_race_is_cancelled_without_stop(self):
  original=self.o.changes.stage
  def mutate_then_stage(updates,*a,**kw):self.jar(self.new,b'moved after verifier');return original(updates,*a,**kw)
  with patch.object(self.o.changes,'stage',side_effect=mutate_then_stage):self.assertTrue(self.execute())
  self.assertEqual(self.old.read_bytes(),self.old_bytes);self.assertFalse(self.o.job.dead);self.assertIsNone(self.o.changes.load())
 def test_failed_save_never_applies(self):
  with patch.object(self.o,'stop_game',side_effect=ValueError('SERVER104_SAVE_UNCONFIRMED')):
   # Liveness is retained until an explicit fixture stop, not assumed from the failed save.
   def stop(reason):self.assertEqual(self.old.read_bytes(),self.old_bytes);self.o.job.dead=True
   with patch.object(self.o,'hold_live',side_effect=stop):self.assertFalse(self.execute())
  self.assertEqual(self.old.read_bytes(),self.old_bytes);self.assertEqual(self.started,[]);self.assertEqual(self.o.phase,'RECOVERY_REQUIRED')
 def test_failed_save_does_not_mark_a_candidate_world_as_loaded(self):
  with patch.object(self.o,'stop_game',side_effect=ValueError('SERVER104_SAVE_UNCONFIRMED')):
   with patch.object(self.o,'hold_live',side_effect=lambda reason:setattr(self.o.job,'dead',True)):
    self.assertFalse(self.execute())
  self.assertEqual(read(self.root/'review104',self.key)['reason'],'UPDATE115_SAVE_UNCONFIRMED')
  self.assertEqual(self.o.changes.load()[0]['phase'],'PREPARED')

 def test_partial_apply_failure_restores_before_start(self):
  actual=self.o.changes.apply
  def fail():actual();raise ValueError('SERVER104_CHANGE_INCOMPLETE')
  with patch.object(self.o.changes,'apply',side_effect=fail):self.assertTrue(self.execute())
  self.assertEqual(self.started,[self.old_bytes]);self.assertEqual(self.report()['status'],'RESTORED');self.unchanged()
 def test_foreign_edit_after_save_not_overwritten(self):
  actual=self.o.stop_game
  def changed():actual();self.jar(self.old,b'foreign writer')
  with patch.object(self.o,'stop_game',side_effect=changed):self.assertFalse(self.execute())
  self.assertEqual(self.started,[]);self.assertNotEqual(self.old.read_bytes(),self.old_bytes);self.assertEqual(self.o.phase,'RECOVERY_REQUIRED')
 def test_hung_candidate_stays_owned_before_restore(self):
  calls=[]
  def start():
   calls.append(1)
   if len(calls)==1:
    self.o.launch=str(uuid.uuid4());self.o.channel=self.root/'runs'/self.o.launch;self.o.channel.mkdir();self.o.job=self.job(self.o.channel,2);raise ValueError('SERVER104_STARTUP_TIMEOUT')
   self.o.changes.finish(True) if self.o.changes.load() else None
  def held(reason):self.assertEqual(self.old.read_bytes(),self.new.read_bytes());self.assertFalse(self.o.job.dead);self.o.job.dead=True
  with patch.object(self.o,'start_game',side_effect=start),patch.object(self.o,'hold_live',side_effect=held):self.assertTrue(self.execute())
  self.assertEqual(self.old.read_bytes(),self.old_bytes);self.assertEqual(len(calls),2)
 def test_packet_accepted_by_actual_local_queue_consumer(self):
  write(self.root/'request74',self.key,self.d);self.assertEqual(self.o.receive(),'APPLY115');self.assertEqual(self.o.update_request115,self.d);self.assertIsNone(self.o.receive())
 def test_remote_permit_cannot_smuggle_new_action(self):
  write(self.root/'request74',self.key,dict(self.d,permit88='fake'))
  with self.assertRaisesRegex(ValueError,'SERVER115_REQUEST'):self.o.receive()
 def test_invalid_request_fields_rejected(self):
  for field,value in [('snapshot','relative'),('target','../bad.jar'),('generation','0'),('sha256','bad'),('request',str(uuid.UUID(int=0))),('schema','75')]:
   with self.subTest(field=field),self.assertRaises(ValueError):update115.packet(dict(self.d,**{field:value}))
 def test_run_or_generation_replay_cannot_stop_server(self):
  for field,value in [('run',str(uuid.uuid4())),('generation','2')]:
   with self.subTest(field=field):
    write(self.root/'request74',self.key,dict(self.d,**{field:value},request=str(uuid.uuid4())))
    with self.assertRaisesRegex(ValueError,'STALE'):self.o.receive()
  self.assertFalse(self.o.job.dead)
 def test_waiting_for_stop_rejects_update(self):
  self.o.report('WAITING_FOR_STOP');write(self.root/'request74',self.key,self.d)
  with self.assertRaisesRegex(ValueError,'STILL_ACTIVE'):self.o.receive()
 def test_restart_loop_dispatches_actual_update(self):
  self.o.bootstrap=Mock();self.o.start_game=Mock();real_receive=self.o.receive;write(self.root/'request74',self.key,self.d)
  # Explicit terminal return fixture lets the outer loop exit without killing a live mock Job.
  with patch('first58.update115.execute',return_value=False) as handler:self.o.run_loop()
  handler.assert_called_once_with(self.o,self.d)

if __name__=='__main__':unittest.main()
