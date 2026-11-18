"""Real files/HMAC boundaries with explicit process/READY models. Does not start Minecraft or Windows Jobs."""
from filesystem_capabilities124 import fixture_symlink
from pathlib import Path
import json,os,sys,tempfile,unittest,uuid,zipfile,copy
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.dedicated74 import *
from test_remote_install70 import mod

class Files74(unittest.TestCase):
 def setUp(self):
  t=tempfile.TemporaryDirectory();self.addCleanup(t.cleanup);self.root=Path(t.name);self.source=self.root/'source';self.source.mkdir();(self.source/'mods').mkdir();(self.source/'world').mkdir();(self.source/'world/level.dat').write_bytes(b'latest world');(self.source/'world/session.lock').write_bytes(b'locked');(self.source/'config').mkdir();(self.source/'config/options.txt').write_text('latest config');(self.source/'server.properties').write_text('level-name=world\nserver-port=25565\nonline-mode=false\nserver-ip=127.0.0.1\n');(self.source/'user_jvm_args.txt').write_text('-Xmx2G\n')
 def test_live_code_copy_does_not_read_world(self):
  result=copy_tree(self.source,self.root/'stage.building','world',True);self.assertNotIn('world/level.dat',result);self.assertFalse((self.root/'stage.building/world').exists());self.assertFalse((self.root/'stage.building/config').exists())
 def test_code_snapshot_changes_when_library_changes(self):
  before=digest(tree(self.source,'world',True));(self.source/'library.jar').write_bytes(b'changed');self.assertNotEqual(before,digest(tree(self.source,'world',True)))
 def test_runtime_world_mutations_are_excluded_from_code_seal(self):
  before=digest(tree(self.source,'world',True));(self.source/'world/level.dat').write_bytes(b'new');(self.source/'config/options.txt').write_text('new');self.assertEqual(before,digest(tree(self.source,'world',True)))
 def test_copy_does_not_touch_original(self):
  before=tree(self.source,'world',raw=True);copy_tree(self.source,self.root/'copy','world');self.assertEqual(before,tree(self.source,'world',raw=True))
 def test_world_copy_preserves_arbitrary_named_data(self):
  for name in ('logs','backups','config','mods'):(self.source/'world'/name).mkdir();(self.source/'world'/name/'data').write_bytes(name.encode())
  copy_tree(self.source/'world',self.root/'world-copy','unused',raw=True)
  for name in ('logs','backups','config','mods'):self.assertEqual((self.root/'world-copy'/name/'data').read_bytes(),name.encode())
  self.assertFalse((self.root/'world-copy/session.lock').exists())
 def test_changed_during_copy_rejected(self):
  def mutate():(self.source/'user_jvm_args.txt').write_text('changed')
  with self.assertRaisesRegex(ValueError,'CHANGED_DURING_COPY'):copy_tree(self.source,self.root/'stage','world',True,mutate)
 def test_live_destination_refused(self):
  with self.assertRaisesRegex(ValueError,'NEW_COPY'):copy_tree(self.source,self.source,'world')
 def test_nested_destination_refused(self):
  with self.assertRaises(ValueError):copy_tree(self.source,self.source/'sub','world')
 def test_symlink_refused(self):
  fixture_symlink(self.source/'linked',self.source/'world',target_is_directory=True)
  with self.assertRaises(ValueError):tree(self.source,'world',True)
 def test_unsafe_world_name(self):
  (self.source/'server.properties').write_text('level-name=../outside\n')
  with self.assertRaises(ValueError):properties(self.source)
 def test_duplicate_property(self):
  with (self.source/'server.properties').open('a') as f:f.write('level-name=other\n')
  with self.assertRaisesRegex(ValueError,'DUPLICATE'):properties(self.source)
 def test_source_world_lock_required(self):
  (self.source/'world/session.lock').unlink()
  with self.assertRaisesRegex(ValueError,'LOCK_REQUIRED'):
   with stopped_world(self.source,'world'):pass
 def test_stopped_lock_released(self):
  with stopped_world(self.source,'world'):pass
  with stopped_world(self.source,'world'):pass
 def test_argfile_redirect_refused(self):
  (self.source/'user_jvm_args.txt').write_text('-Duser.dir=C:/other\n')
  with self.assertRaisesRegex(ValueError,'REDIRECT'):validate_argv(['java','@user_jvm_args.txt'],self.source)
 def test_argfile_nested_refused(self):
  (self.source/'user_jvm_args.txt').write_text('@other.txt\n')
  with self.assertRaisesRegex(ValueError,'NESTED'):validate_argv(['java','@user_jvm_args.txt'],self.source)
 def test_label_is_not_path_or_shell(self):
  for text in ('../r1','r1;stop','r1 other','', 'x'*41):
   with self.assertRaises(ValueError):label(text)

class OwnerBoundaries74(unittest.TestCase):
 def setUp(self):
  t=tempfile.TemporaryDirectory();self.addCleanup(t.cleanup);self.root=Path(t.name);self.source=self.root/'generations/server-0';(self.source/'mods').mkdir(parents=True);(self.source/'world').mkdir();(self.source/'world/level.dat').write_bytes(b'last saved world');(self.source/'world/session.lock').write_bytes(b'');(self.source/'config').mkdir();(self.source/'config/latest.txt').write_text('latest');(self.source/'server.properties').write_text('level-name=world\nserver-port=25565\nonline-mode=false\nserver-ip=127.0.0.1\n');(self.source/'eula.txt').write_text('eula=true\n');mod(self.source/'mods/mared.jar','mared',adapter=True)
  self.s=dict(schema=74,run=str(uuid.uuid4()),source=str(self.root/'original'),java='java',argv=['java'],minecraft='1.21.1',neoforge='21.1.264',auth='false',bind='127.0.0.1',port='25565',level='world',initialInventory=inventory(self.source));self.key=b'k'*32;(self.root/'owner74.key').write_bytes(self.key);json_write(self.root/'settings.json',self.s);write(self.root/'settings74',self.key,{'schema':'74','sha':sha(self.root/'settings.json',65536)});session(self.root,self.s,self.key,0,self.s['initialInventory']);self.owner=Owner74(self.root,self.root);self.job=Mock();self.job.status.return_value={'rootExited':False,'exitCode':None,'logFailed':False};self.job.empty.return_value=False;self.owner.jobs={0:self.job}
 def prep(self):
  stage=self.root/'stages/one.building';baseline=copy_tree(self.source,stage,'world',True);info=dict(release=str(uuid.uuid4()),client='a'*64,server='b'*64,game='localhost:25565',minecraft='1.21.1',neoforge='21.1.264',endpoint='https://localhost/genesis/',files=[]);self.owner.pending=dict(info=info,path=stage,release=self.root/'release',approved={'delta':{}},sourceSeal=digest(baseline),targetSeal=digest(tree(stage,'world',True)),label='r1');self.owner.tool=Mock(return_value=info);write(self.root/'prepared74',self.key,{'newInventory':inventory(stage)});return stage
 def saved(self):
  self.job.empty.return_value=True;self.job.status.return_value={'rootExited':True,'exitCode':0,'logFailed':False};cmd=self.owner.command(0,'SAVE_STOP',str(uuid.uuid4()));c=read(self.root/'server-0.session',self.key);d={k:c[k] for k in ('schema','run','generation','inventory')};d.update(sequence=cmd['sequence'],request=cmd['request'],phase='STOPPED_SAVED',round=cmd['round'],at='1');write(self.root/'server-0.status',self.key,d);return d
 def test_no_copy_when_world_owner_alive(self):
  self.prep()
  with self.assertRaisesRegex(ValueError,'WORLD_OWNER_ACTIVE'):self.owner.copy_saved()
 def test_normal_exit_without_receipt_not_enough(self):
  self.prep();self.job.empty.return_value=True
  with self.assertRaisesRegex(ValueError,'SAVE_RECEIPT'):self.owner.copy_saved()
 def test_saved_generation_contains_latest_world_and_config(self):
  self.prep();self.saved();self.owner.copy_saved();target=self.root/'generations/server-1';self.assertEqual((target/'world/level.dat').read_bytes(),b'last saved world');self.assertEqual((target/'config/latest.txt').read_text(),'latest');self.assertFalse((target/'world/session.lock').exists());self.assertTrue((self.source/'world/level.dat').is_file());self.job.close.assert_not_called()
 def test_wrong_nonce_refuses_world_transfer(self):
  self.prep();d=self.saved();d['request']=str(uuid.uuid4());write(self.root/'server-0.status',self.key,d)
  with self.assertRaisesRegex(ValueError,'SAVE_RECEIPT'):self.owner.copy_saved()
 def test_wrong_round_refuses_world_transfer(self):
  self.prep();d=self.saved();d['round']=str(uuid.uuid4());write(self.root/'server-0.status',self.key,d)
  with self.assertRaises(ValueError):self.owner.copy_saved()
 def test_wrong_inventory_refuses_world_transfer(self):
  self.prep();d=self.saved();d['inventory']='e'*64;write(self.root/'server-0.status',self.key,d)
  with self.assertRaisesRegex(ValueError,'STATUS_BINDING'):self.owner.copy_saved()
 def test_still_active_descendant_refuses_world_transfer(self):
  self.prep();self.saved();self.job.empty.return_value=False
  with self.assertRaises(ValueError):self.owner.copy_saved()
 def test_staged_code_changed_refuses_transfer(self):
  stage=self.prep();self.saved();(stage/'changed.jar').write_bytes(b'changed')
  with self.assertRaisesRegex(ValueError,'STAGE_CHANGED'):self.owner.copy_saved()
 def test_source_code_changed_refuses_transfer(self):
  self.prep();self.saved();(self.source/'changed.jar').write_bytes(b'changed')
  with self.assertRaisesRegex(ValueError,'STAGE_CHANGED'):self.owner.copy_saved()
 def test_world_already_in_stage_refused(self):
  stage=self.prep();self.saved();(stage/'world').mkdir()
  with self.assertRaisesRegex(ValueError,'WORLD_PREEXISTED'):self.owner.copy_saved()
 def test_config_refresh_after_save(self):
  self.prep();self.saved();(self.source/'config/latest.txt').write_text('saved setting');self.owner.copy_saved();self.assertEqual((self.root/'generations/server-1/config/latest.txt').read_text(),'saved setting')
 def test_wrong_release_cannot_quiesce_or_stop(self):
  self.prep();self.owner.command=Mock();state=dict(phase='COMMITTED',release=str(uuid.uuid4()),client='a'*64,round=str(uuid.uuid4()),admission=False)
  with self.assertRaisesRegex(ValueError,'COMMIT_BINDING'):self.owner.activate(state)
  self.owner.command.assert_not_called()
 def test_no_second_process_while_old_job_handle_retained(self):
  with self.assertRaisesRegex(ValueError,'OLD_JOB_STILL_HELD'):self.owner.start(1)
 def test_no_install_ready_from_port_or_pid(self):self.assertFalse(self.owner.ready_generation1);self.assertFalse(self.owner.admit)
 def test_settings_tampering(self):
  (self.root/'settings.json').write_text('{}')
  with self.assertRaisesRegex(ValueError,'SETTINGS_CHANGED'):settings(self.root)
 def test_hmac_forged_request(self):
  write(self.root/'request74',b'x'*32,dict(schema='74',run=self.s['run'],request=str(uuid.uuid4()),action='SHUTDOWN',label='r1'))
  with self.assertRaises(ValueError):self.owner.receive()
 def test_request_ack_prevents_overwriting_unread_command(self):
  submit(self.root,'STAGE');
  with self.assertRaisesRegex(ValueError,'REQUEST_PENDING'):submit(self.root,'OFFER')
  self.owner.receive();submit(self.root,'OFFER')
 def test_request_action_is_allowlist(self):
  with self.assertRaises(ValueError):submit(self.root,'EXEC')
 def test_old_server_stays_alive_until_all_installed(self):
  self.owner.last_pump={'total':8,'installed':2};self.owner.receive=Mock(return_value={'action':'SHUTDOWN'});self.owner.stop=Mock()
  with self.assertRaises(InterruptedError):self.owner.wait_all_installed()
  self.owner.stop.assert_not_called();self.job.close.assert_not_called()
 def test_all_installed_releases_save_barrier(self):
  self.owner.last_pump={'total':8,'installed':8};self.owner.receive=Mock();self.owner.wait_all_installed();self.owner.receive.assert_not_called();self.job.close.assert_not_called()
 def test_argfile_external_drive_refused(self):
  (self.source/'user_jvm_args.txt').write_text('-p C:/external/libraries\n')
  with self.assertRaisesRegex(ValueError,'EXTERNAL_RUNTIME_PATH'):validate_argv(['java','@user_jvm_args.txt'],self.source)
 def test_normal_exit_does_not_claim_saved_without_receipt_on_cleanup(self):
  self.job.status.return_value={'rootExited':True,'exitCode':0,'logFailed':False};self.job.empty.return_value=True;self.owner.stop_requested=True;self.owner.cleanup();self.assertEqual(self.owner.phase,'STOP_NOT_CONFIRMED');self.job.close.assert_called_once()
 def test_matching_save_receipt_allows_clean_shutdown_report(self):
  self.saved();self.owner.stop_requested=True;self.owner.cleanup();self.assertEqual(self.owner.phase,'STOPPED_SAVED');self.job.close.assert_called_once()
 def test_cannot_stop_unknown_process(self):self.assertIsNone(self.owner.stop(123));self.job.close.assert_not_called()
if __name__=='__main__':unittest.main()
