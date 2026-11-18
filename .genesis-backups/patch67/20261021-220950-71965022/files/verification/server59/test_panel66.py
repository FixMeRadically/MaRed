"""Actual file snapshots and modelled private handoff; no Windows/Minecraft certification."""
from pathlib import Path
import contextlib,json,sys,tempfile,unittest,uuid
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.desktop66 import live_client_snapshot
from first58.panel66 import Worker,load_request,command
from first58.common import json_write

class SnapshotTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.source=self.root/'source';(self.source/'mods').mkdir(parents=True);(self.source/'mods/a.jar').write_bytes(b'jar fixture');(self.source/'options.txt').write_text('original');self.dest=self.root/'snapshot'
 def test_worlds_locks_and_logs_never_copied(self):
  for name in ['saves/world','logs','backups','screenshots','crash-reports']:
   f=self.source/name/'private';f.parent.mkdir(parents=True,exist_ok=True);f.write_text('not copied')
  (self.source/'session.lock').write_text('lock')
  result=live_client_snapshot(self.source,self.dest)
  self.assertFalse(result['localWorldsCopied']);self.assertEqual((self.dest/'options.txt').read_text(),'original');self.assertFalse((self.dest/'saves').exists());self.assertFalse((self.dest/'logs').exists());self.assertFalse((self.dest/'session.lock').exists())
 def test_source_never_written_and_snapshot_independent(self):
  before={p.relative_to(self.source):p.read_bytes() for p in self.source.rglob('*') if p.is_file()}
  live_client_snapshot(self.source,self.dest);(self.dest/'mods/a.jar').write_bytes(b'changed copy')
  self.assertEqual(before,{p.relative_to(self.source):p.read_bytes() for p in self.source.rglob('*') if p.is_file()})
 def test_nested_destination_refused_before_mutation(self):
  with self.assertRaises(ValueError):live_client_snapshot(self.source,self.source/'nested')
  self.assertFalse((self.source/'nested').exists())
 def test_linked_source_file_refused(self):
  (self.source/'link').symlink_to(self.source/'options.txt')
  with self.assertRaises(ValueError):live_client_snapshot(self.source,self.dest)
 def test_cancel_during_copy(self):
  with self.assertRaises(InterruptedError):live_client_snapshot(self.source,self.dest,lambda:True)
 def test_concurrent_file_change_detected(self):
  import shutil
  original=shutil.copyfileobj
  def changed(src,out,*args):
   original(src,out,*args)
   if Path(src.name).name=='options.txt':Path(src.name).write_text('changed')
  with patch('first58.desktop66.shutil.copyfileobj',side_effect=changed):
   with self.assertRaisesRegex(ValueError,'changed during'):live_client_snapshot(self.source,self.dest)
 def test_added_mod_not_silently_accepted(self):
  import shutil
  original=shutil.copyfileobj
  def added(src,out,*args):
   original(src,out,*args);(self.source/'mods/late.jar').write_bytes(b'late')
  with patch('first58.desktop66.shutil.copyfileobj',side_effect=added):
   with self.assertRaisesRegex(ValueError,'changed during'):live_client_snapshot(self.source,self.dest)
 def test_preexisting_snapshot_refused(self):
  self.dest.mkdir()
  with self.assertRaises(ValueError):live_client_snapshot(self.source,self.dest)

class PanelTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.id=str(uuid.uuid4())
  self.r={'schema':66,'request':self.id,'pid':123,'profile':str(self.root/'client'),'account':str(uuid.uuid4()),'neoforge':'21.1.264','server':str(self.root/'server'),'jar':'','side':'BOTH','authMode':'LOCAL_OFFLINE','port':27690,'operation':'PREPARE'}
  json_write(self.root/'request66.json',self.r)
 def send(self,action,request=None):json_write(self.root/'command66.json',{'schema':66,'request':request or self.id,'action':action})
 def worker(self):
  w=Worker(self.root);w.lease=Mock();w.lease.closed.return_value=False;w.root=self.root/'run';w.root.mkdir();return w
 def test_request_valid_and_bounded(self):self.assertEqual(load_request(self.root),self.r)
 def test_unknown_operation_refused(self):
  self.r['operation']='SHELL';json_write(self.root/'request66.json',self.r)
  with self.assertRaises(ValueError):load_request(self.root)
 def test_extra_private_field_refused(self):
  self.r['token']='never allowed';json_write(self.root/'request66.json',self.r)
  with self.assertRaises(ValueError):load_request(self.root)
 def test_wrong_request_cannot_arm(self):
  self.send('ARM',str(uuid.uuid4()))
  with self.assertRaises(ValueError):command(self.root,self.id)
 def test_unknown_command_refused(self):
  self.send('RUN_POWERSHELL')
  with self.assertRaises(ValueError):command(self.root,self.id)
 def test_cancel_while_ready_does_not_close_source(self):
  w=self.worker();self.send('CANCEL')
  with self.assertRaises(InterruptedError):w.wait_action({'ARM'},1)
  w.lease.close.assert_not_called()
 def test_user_source_exit_stops_preparation(self):
  w=self.worker();w.lease.closed.return_value=True
  with self.assertRaises(InterruptedError):w.require_open()
 def test_doctor_failure_does_not_arm_or_launch_game(self):
  w=self.worker();self.send('ARM')
  with patch('first_launch.ownership',return_value=contextlib.nullcontext()),patch('first58.panel66.doctor',side_effect=ValueError('probe failed')),patch('first58.ui.run_cover') as cover:
   with self.assertRaises(ValueError):w.launch()
   cover.assert_not_called()
  self.assertEqual(json.loads((self.root/'state66.json').read_text())['phase'],'ARMING');w.lease.close.assert_not_called()
 def test_cover_ack_then_commit_then_source_exit_then_managed_launch(self):
  w=self.worker();self.send('ARM');events=[];original=w.state
  def state(phase):
   original(phase);events.append(phase)
   if phase=='ARMED':
    self.assertIn('COVER_ACK',events);self.send('COMMIT');w.lease.closed.return_value=True
  w.state=state
  def cover(operation,profile):operation(lambda msg:events.append(msg+'_ACK'),lambda:False,lambda _:False)
  def launched(*args,**kwargs):
   self.assertTrue(w.lease.closed());events.append('MANAGED_LAUNCH');return Mock()
  with patch('first_launch.ownership',side_effect=lambda _:contextlib.nullcontext()),patch('first58.panel66.doctor'),patch('first58.ui.run_cover',side_effect=cover),patch('first58.controller.Pilot',side_effect=launched):w.launch()
  self.assertLess(events.index('COVER_ACK'),events.index('ARMED'));self.assertLess(events.index('STARTING'),events.index('MANAGED_LAUNCH'));self.assertEqual(events[-1],'COMPLETE')
 def test_state_has_no_launch_credentials(self):
  w=self.worker();w.state('READY');d=json.loads((self.root/'state66.json').read_text())
  self.assertEqual(set(d),{'schema','request','phase','reportDirectory','delta'})
 def test_invalid_port_refused(self):
  self.r['port']=0;json_write(self.root/'request66.json',self.r)
  with self.assertRaises(ValueError):load_request(self.root)
 def test_prepare_failure_does_not_call_launch_or_leak_message(self):
  lease=Mock();w=Worker(self.root,lambda _:lease);w.prepare=Mock(side_effect=ValueError('--accessToken SECRET'));w.launch=Mock()
  with self.assertRaises(ValueError):w.run()
  w.launch.assert_not_called();lease.close.assert_called_once();self.assertNotIn('SECRET',(self.root/'panel-report66.json').read_text())

if __name__=='__main__':unittest.main(verbosity=2)
