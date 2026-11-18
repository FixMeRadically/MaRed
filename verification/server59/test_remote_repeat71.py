"""Real authenticated request/file checks and explicit models of sequential Job ownership."""
from pathlib import Path
import copy,json,secrets,sys,tempfile,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.repeat71 import packet,publish,matching,OwnedLease71,Cycle71,RepeatingWorker71,REQUEST_FIELDS
from first58.bridge67 import write,read
from first58.common import inventory
from first58.install70 import files
from test_remote_install70 import mod

class Channel71(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
  self.c=Mock();self.c.root=self.root;self.c.key=secrets.token_bytes(32);self.c.config=dict(run=str(uuid.uuid4()),account=str(uuid.uuid4()),inventory='a'*64)
  self.d={**self.c.config,'schema':'71','request':str(uuid.uuid4()),'action':'PREPARE','cache':str(self.root/'cache'),'candidate':str(self.root/'candidate')}
 def put(self,d=None):write(self.root/'repeat71.request',self.c.key,self.d if d is None else d)
 def test_round_trip_unicode_absolute_paths(self):
  self.d['candidate']=str(self.root/'a b'/'тест');self.put();self.assertEqual(packet(self.c),self.d)
 def test_missing_request_is_not_permission(self):self.assertIsNone(packet(self.c))
 def test_wrong_identity_fields(self):
  for key in ('run','account','inventory'):
   with self.subTest(key=key):
    d={**self.d,key:'b'*64 if key=='inventory' else str(uuid.uuid4())};self.put(d)
    with self.assertRaisesRegex(ValueError,'REQUEST'):packet(self.c)
 def test_wrong_protocol(self):
  self.put({**self.d,'schema':'70'})
  with self.assertRaises(ValueError):packet(self.c)
 def test_action_is_not_an_arbitrary_command(self):
  self.put({**self.d,'action':'EXEC'})
  with self.assertRaises(ValueError):packet(self.c)
 def test_unknown_fields_refused(self):
  self.put({**self.d,'pid':'123'})
  with self.assertRaises(ValueError):packet(self.c)
 def test_relative_candidate_refused(self):
  self.put({**self.d,'candidate':'../mods'})
  with self.assertRaisesRegex(ValueError,'PATH'):packet(self.c)
 def test_oversized_path_refused(self):
  self.put({**self.d,'candidate':'/'+'a'*4096})
  with self.assertRaisesRegex(ValueError,'PATH'):packet(self.c)
 def test_wrong_mac(self):
  self.put();p=self.root/'repeat71.request';data=p.read_bytes();p.write_bytes(data[:-2]+(b'0' if data[-2:-1]!=b'0' else b'1')+data[-1:])
  with self.assertRaises(ValueError):packet(self.c)
 def test_arm_cannot_change_candidate_of_ready_request(self):self.assertFalse(matching(self.d,{**self.d,'action':'ARM','candidate':str(self.root/'elsewhere')}))
 def test_arm_must_keep_nonce(self):self.assertFalse(matching(self.d,{**self.d,'action':'ARM','request':str(uuid.uuid4())}))
 def test_initial_unsolicited_arm_is_not_match(self):self.assertFalse(matching({},self.d))
 def test_status_bound_to_current_identity(self):
  publish(self.c,self.d['request'],'READY',None,'safe reason');d=read(self.root/'repeat71.status',self.c.key)
  self.assertEqual(set(d),{'schema','run','account','inventory','request','phase','at','reason','delta','reports'});self.assertEqual(d['run'],self.c.config['run']);self.assertEqual(d['phase'],'READY');self.assertEqual(json.loads(d['delta']),{})

class Lease71(unittest.TestCase):
 def setUp(self):self.job=Mock();self.job.status.return_value={'rootExited':True,'exitCode':0,'logFailed':False};self.job.empty.return_value=True;self.lease=OwnedLease71(self.job)
 def test_normal_owned_exit(self):self.assertTrue(self.lease.normal())
 def test_root_alive_not_normal(self):self.job.status.return_value['rootExited']=False;self.assertFalse(self.lease.normal())
 def test_descendant_alive_not_normal(self):self.job.empty.return_value=False;self.assertFalse(self.lease.normal())
 def test_nonzero_not_normal(self):self.job.status.return_value['exitCode']=1;self.assertFalse(self.lease.normal())
 def test_log_failure_not_normal(self):self.job.status.return_value['logFailed']=True;self.assertFalse(self.lease.normal())
 def test_lease_never_kills_or_adopts(self):self.lease.close();self.job.close.assert_not_called();self.job.stop.assert_not_called()

class Prepare71(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.folder=Path(self.temp.name);self.profile=self.folder/'old/generations/client-1';(self.profile/'mods').mkdir(parents=True);mod(self.profile/'mods/mared.jar','mared',adapter=True)
  self.current=Mock();self.current.folder=self.folder;self.current.root=self.folder/'old';self.current.key=b'k'*32;self.current.config={'run':str(uuid.uuid4()),'account':str(uuid.uuid4()),'inventory':inventory(self.profile)};self.current.spec={'clientCommand':['java','--gameDir','{profile}']};self.current.account=self.current.config['account'];self.current.r={'cache':str(self.folder/'cache'),'neoforge':'21.1.264'};self.current.info={'game':'localhost:25565','endpoint':'https://localhost/update/','distribution':str(uuid.uuid4()),'release':str(uuid.uuid4()),'archiveSha256':'a'*64};self.current.job.status.return_value={'rootExited':False,'exitCode':None,'logFailed':False}
  self.offer={**self.current.config,'schema':'71','request':str(uuid.uuid4()),'action':'PREPARE','cache':self.current.r['cache'],'candidate':str(self.folder/'cache/downloads/x')}
  write(self.current.root/'repeat71.request',self.current.key,self.offer);self.cycle=Cycle71(self.current,self.offer);self.cycle.verify_remote=Mock(return_value={**self.current.info,'release':str(uuid.uuid4()),'archiveSha256':'b'*64})
 def test_does_not_launch_new_controller_during_prepare(self):self.assertIsNone(self.cycle.job);self.assertIs(self.cycle.lease.job,self.current.job)
 def test_baseline_changed_refused(self):
  mod(self.profile/'mods/extra.jar')
  with self.assertRaisesRegex(ValueError,'SOURCE_CHANGED'):self.cycle.prepare()
  self.cycle.verify_remote.assert_not_called()
 def test_origin_fields_are_immutable(self):
  for key in ('game','distribution','endpoint'):
   with self.subTest(key=key):
    self.cycle.verify_remote.return_value={**self.current.info,'release':str(uuid.uuid4()),'archiveSha256':'b'*64,key:'different'}
    with self.assertRaisesRegex(ValueError,'ORIGIN'):self.cycle.prepare()
 def test_cache_root_cannot_be_replaced(self):
  self.cycle.r['cache']=str(self.folder/'other')
  with self.assertRaisesRegex(ValueError,'ORIGIN'):self.cycle.prepare()
 def test_same_release_refused(self):
  self.cycle.verify_remote.return_value={**self.current.info,'archiveSha256':'b'*64}
  with self.assertRaisesRegex(ValueError,'SAME_RELEASE'):self.cycle.prepare()
 def test_same_archive_refused(self):
  self.cycle.verify_remote.return_value={**self.current.info,'release':str(uuid.uuid4())}
  with self.assertRaisesRegex(ValueError,'SAME_RELEASE'):self.cycle.prepare()
 def test_cancel_before_prepare(self):
  write(self.current.root/'repeat71.request',self.current.key,{**self.offer,'action':'CANCEL'})
  with self.assertRaises(InterruptedError):self.cycle.prepare()
  self.cycle.verify_remote.assert_not_called()

class Sequential71(unittest.TestCase):
 def setup_owner(self):
  owner=RepeatingWorker71.__new__(RepeatingWorker71);owner.seen71=set();owner.active71=owner;owner.job=Mock();owner.job.status.return_value={'rootExited':False,'exitCode':None,'logFailed':False};owner.config={'run':str(uuid.uuid4()),'account':str(uuid.uuid4()),'inventory':'a'*64};owner.key=b'k'*32;owner.root=Path('/modeled-owned-root');owner.stop=Mock();owner.state=Mock();return owner
 def test_two_updates_keep_one_owner_and_stop_before_spawn(self):
  owner=self.setup_owner();events=[];cycles=[]
  for i in range(2):
   c=Mock();c.root=Path('/modeled-cycle-'+str(i));c.config={**owner.config,'run':str(uuid.uuid4())};c.key=b'x'*32;c.job=Mock();c.job.status.return_value={'rootExited':False,'exitCode':None,'logFailed':False};c.lease.normal.return_value=True;c.info={'release':str(uuid.uuid4())};c.verify_remote.return_value=c.info;c.join.return_value=True;c.cancelled.return_value=False
   c.final_check.side_effect=lambda:events.append('check');c.build.side_effect=lambda:events.append('build');c.start.side_effect=lambda:events.append('spawn');cycles.append(c)
  owner.begin=Mock(side_effect=cycles);owner.stop.side_effect=lambda:events.append('stop0');cycles[0].stop.side_effect=lambda:events.append('stop1')
  offers=[dict(schema='71',run=owner.config['run'],account=owner.config['account'],inventory='a'*64,request=str(uuid.uuid4()),action='PREPARE',cache='/cache',candidate='/candidate') for _ in range(2)]
  packets=[offers[0],{**offers[0],'action':'ARM'},offers[1],{**offers[1],'action':'ARM'}]
  def next_packet(current):
   if packets:return packets.pop(0)
   cycles[1].job.status.return_value={'rootExited':True,'exitCode':0,'logFailed':False};return None
  with patch('first58.repeat71.packet',side_effect=next_packet),patch('first58.repeat71.publish'),patch('first58.repeat71.time.sleep'):
   owner.watch(Mock(),lambda:False,Mock())
  self.assertEqual(events,['check','stop0','build','spawn','check','stop1','build','spawn']);self.assertIs(owner.active71,cycles[1]);self.assertEqual(owner.begin.call_count,2)
 def test_unsolicited_arm_never_stops_current_game(self):
  owner=self.setup_owner();d=dict(schema='71',run=owner.config['run'],account=owner.config['account'],inventory='a'*64,request=str(uuid.uuid4()),action='ARM',cache='/cache',candidate='/candidate')
  def next_packet(current):owner.job.status.return_value={'rootExited':True,'exitCode':0,'logFailed':False};return d
  with patch('first58.repeat71.packet',side_effect=next_packet),patch('first58.repeat71.publish'),patch('first58.repeat71.time.sleep'):owner.watch(Mock(),lambda:False,Mock())
  # Only ordinary exit cleanup, never a handoff or a new cycle.
  owner.begin=Mock();self.assertEqual(owner.stop.call_count,1);self.assertEqual(owner.seen71,set())


class BarrierFailures71(unittest.TestCase):
 def exercise(self,fail_at):
  owner=Sequential71().setup_owner();c=Mock();c.root=Path('/explicit-cycle-model');c.key=b'z'*32;c.config=owner.config;c.info={'release':'new'};c.verify_remote.return_value=c.info;c.cancelled.return_value=False;c.lease.normal.return_value=fail_at!='exit';c.join.return_value=True
  d={**owner.config,'schema':'71','request':str(uuid.uuid4()),'action':'PREPARE','cache':'/cache','candidate':'/candidate'};owner.begin=Mock(return_value=c)
  if fail_at=='preflight':c.final_check.side_effect=ValueError('REMOTE70_CANDIDATE')
  if fail_at=='prepare':c.prepare.side_effect=ValueError('REMOTE70_CANDIDATE')
  commands=[d,{**d,'action':'ARM'}]
  def packet_model(current):
   if commands:return commands.pop(0)
   owner.job.status.return_value={'rootExited':True,'exitCode':0,'logFailed':False};return None
  def notify(value):
   if fail_at=='cover' and value=='COVER':raise RuntimeError('explicit cover refusal')
  with patch('first58.repeat71.packet',side_effect=packet_model),patch('first58.repeat71.publish'),patch('first58.repeat71.json_write'),patch('first58.repeat71.time.sleep'):
   if fail_at=='exit':
    with self.assertRaises(Exception):owner.watch(notify,lambda:False,Mock())
   else:owner.watch(notify,lambda:False,Mock())
  return owner,c
 def test_prepare_failure_no_build_or_spawn(self):
  o,c=self.exercise('prepare');c.build.assert_not_called();c.start.assert_not_called();c.final_check.assert_not_called()
 def test_final_recheck_failure_no_build_or_spawn(self):
  o,c=self.exercise('preflight');c.build.assert_not_called();c.start.assert_not_called();c.lease.normal.assert_not_called()
 def test_cover_failure_no_build_or_spawn(self):
  o,c=self.exercise('cover');c.build.assert_not_called();c.start.assert_not_called();c.lease.normal.assert_not_called()
 def test_abnormal_exit_never_builds_or_spawns(self):
  o,c=self.exercise('exit');o.stop.assert_called_once();c.build.assert_not_called();c.start.assert_not_called();o.job.close.assert_not_called()

class Limit71(unittest.TestCase):
 def test_limit_replies_to_the_new_request_without_starting_a_cycle(self):
  owner=Sequential71().setup_owner();owner.seen71={str(uuid.uuid4()) for _ in range(128)}
  d={**owner.config,'schema':'71','request':str(uuid.uuid4()),'action':'PREPARE','cache':'/cache','candidate':'/candidate'}
  def packet_model(current):
   owner.job.status.return_value={'rootExited':True,'exitCode':0,'logFailed':False};return d
  with patch('first58.repeat71.packet',side_effect=packet_model),patch('first58.repeat71.publish') as report,patch('first58.repeat71.time.sleep'):
   owner.watch(Mock(),lambda:False,Mock())
  self.assertEqual(report.call_args.args[1:3],(d['request'],'FAILED'));self.assertEqual(len(owner.seen71),128)


class FailureReports165(unittest.TestCase):
 def test_actual_failure_report_is_safe_and_in_displayed_folder(self):
  from first58.repeat71 import record_prepare_failure
  from types import SimpleNamespace
  with tempfile.TemporaryDirectory() as temporary:
   root=Path(temporary);owner=SimpleNamespace(root=root);cycle=SimpleNamespace(phase='VALIDATING')
   try:raise ValueError('REMOTE70_CANDIDATE')
   except ValueError as error:reason=record_prepare_failure(owner,cycle,error)
   report=json.loads((root/'failure71.json').read_text())
   self.assertEqual(report['last_phase'],'VALIDATING');self.assertEqual(report['code'],'REMOTE70_CANDIDATE')
   self.assertEqual(report['reason'],reason);self.assertTrue(report['frames'])
   record_prepare_failure(owner,cycle,ValueError('--accessToken PRIVATE_TEST_SECRET'))
   self.assertNotIn('PRIVATE_TEST_SECRET',(root/'failure71.json').read_text())
 def test_unwritable_diagnostics_does_not_replace_original_reason(self):
  from first58.repeat71 import record_prepare_failure,reason_for
  from types import SimpleNamespace
  error=ValueError('REMOTE70_CANDIDATE')
  with patch('first58.repeat71.json_write',side_effect=PermissionError()):
   self.assertEqual(record_prepare_failure(SimpleNamespace(root=Path('/fixture')),None,error),reason_for(error))

if __name__=='__main__':unittest.main()
