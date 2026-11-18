"""Actual disk/HMAC/next-generation restarts; save and OS Job signals are explicit fixtures."""
from pathlib import Path
import sys,unittest,uuid,tempfile
from unittest.mock import Mock,patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.dedicated103 import Owner103,launcher
from first58 import dedicated74 as b
from first58.bridge67 import write,read
from first58.common import inventory
import test_dedicated74 as f

class Restart103(unittest.TestCase):
 def setUp(self):
  base=f.OwnerBoundaries74();base.setUp();self.addCleanup(base.doCleanups);self.base=base;self.root=base.root;self.source=base.source;self.key=base.key
  o=Owner103.__new__(Owner103);o.__dict__.update(base.owner.__dict__);self.owner=o;o.busy75=False;o.previous_info=None;o.current_inventory=inventory(self.source);(self.root/'stages').mkdir();self.started=[]
  o.report=lambda phase=None,reason='':setattr(o,'phase',phase or o.phase);o.event=Mock();o.checkpoint=Mock();o.health=Mock()
  def start(g):
   self.started.append(g);self.assertFalse((self.root/f'generations/server-{g}/world/session.lock').exists());(self.root/f'generations/server-{g}/world/session.lock').write_bytes(b'NEW_RUNTIME_LOCK');self.assertEqual(read(self.root/f'server-{g}.session',self.key)['inventory'],inventory(self.root/f'generations/server-{g}'));o.jobs[g]=Mock();o.jobs[g].status.return_value={"rootExited":False,"exitCode":None,"logFailed":False};o.jobs[g].empty.return_value=False;o.current=g
  o.start=start;o.stop=self.stop
 def stop(self,g,action,round_id):
  self.assertEqual(action,'SAVE_STOP');self.assertTrue(all(not (p/'world').exists() for p in (self.root/'stages').iterdir()))
  source=self.root/f'generations/server-{g}';(source/'world/level.dat').write_bytes(('LATEST_SAVED_AFTER_STOP_'+str(g)).encode());(source/'config/latest.txt').write_text('latest configuration after save')
  cmd=b.Owner74.command(self.owner,g,action,round_id);self.owner.jobs[g].empty.return_value=True;self.owner.jobs[g].status.return_value={'rootExited':True,'exitCode':0,'logFailed':False}
  d={k:cmd[k] for k in ('schema','run','generation','inventory','sequence','request','round')};d.update(phase='STOPPED_SAVED',at='1');write(self.root/f'server-{g}.status',self.key,d);return cmd
 def test_latest_saved_world_and_config_are_copied_only_after_empty_saved_job(self):
  oldmod=(self.source/'mods/mared.jar').read_bytes();self.owner.restart103();target=self.root/'generations/server-1'
  self.assertEqual(self.started,[1]);self.assertEqual((target/'world/level.dat').read_bytes(),b'LATEST_SAVED_AFTER_STOP_0');self.assertEqual((target/'world/session.lock').read_bytes(),b'NEW_RUNTIME_LOCK');self.assertEqual((target/'config/latest.txt').read_text(),'latest configuration after save');self.assertEqual((target/'mods/mared.jar').read_bytes(),oldmod);self.assertTrue((self.source/'world').is_dir());self.base.job.close.assert_called_once()
 def test_two_restarts_use_last_saved_world_and_keep_old_generations(self):
  self.owner.restart103();self.owner.restart103();self.assertEqual(self.started,[1,2]);self.assertEqual((self.root/'generations/server-2/world/level.dat').read_bytes(),b'LATEST_SAVED_AFTER_STOP_1');self.assertTrue((self.root/'generations/server-0/world').is_dir());self.assertTrue((self.root/'generations/server-1/world').is_dir())
 def test_preparation_io_failure_keeps_running_owner(self):
  from first58.dedicated88 import Owner88
  with patch.object(Owner88,'receive',return_value={'action':'RESTART'}),patch('first58.dedicated103.base.copy_tree',side_effect=OSError('disk error')):
   self.assertIsNone(self.owner.receive())
  self.assertFalse(self.started);self.assertFalse(self.base.job.status()['rootExited']);self.assertEqual(self.owner.phase,'PREPARATION_FAILED');self.assertEqual(self.owner.failure_code,'SERVER103_PREPARATION_FAILED')
 def test_pending_update_cannot_be_overlapped(self):
  for key,value in [('pending',{}),('external',True),('busy75',True),('current',32)]:
   original=getattr(self.owner,key);setattr(self.owner,key,value if key!='pending' else {'release':'x'})
   with self.assertRaisesRegex(ValueError,'UPDATE_ACTIVE|GENERATION_LIMIT'):self.owner.restart103()
   setattr(self.owner,key,original)
  self.assertFalse(self.started)
 def test_foreign_mod_change_rejected_before_stop(self):
  self.owner.stop=Mock();(self.source/'mods/mared.jar').write_bytes(b'foreign')
  with self.assertRaisesRegex(ValueError,'CODE_CHANGED'):self.owner.restart103()
  self.owner.stop.assert_not_called();self.assertFalse(self.started)
 def test_existing_generation_is_never_overwritten(self):
  target=self.root/'generations/server-1';target.mkdir();(target/'foreign').write_text('KEEP')
  with self.assertRaisesRegex(ValueError,'GENERATION_EXISTS'):self.owner.restart103()
  self.assertEqual((target/'foreign').read_text(),'KEEP');self.assertFalse(self.started)
 def reject_saved(self,alter):
  def stop(*args):
   cmd=self.stop(*args);alter();return cmd
  self.owner.stop=stop
  with self.assertRaisesRegex(ValueError,'SAVE_UNCONFIRMED|CODE_CHANGED'):self.owner.restart103()
  self.assertFalse(self.started);self.assertFalse((self.root/'generations/server-1').exists());self.assertFalse(any((p/'world').exists() for p in (self.root/'stages').iterdir()))
 def test_live_job_cannot_authorize_copy(self):self.reject_saved(lambda:setattr(self.base.job.empty,'return_value',False))
 def test_exit_nonzero_cannot_authorize_copy(self):self.reject_saved(lambda:setattr(self.base.job.status,'return_value',{'rootExited':True,'exitCode':7,'logFailed':False}))
 def test_root_still_alive_cannot_authorize_copy(self):self.reject_saved(lambda:setattr(self.base.job.status,'return_value',{'rootExited':False,'exitCode':0,'logFailed':False}))
 def test_wrong_save_nonce_cannot_authorize_copy(self):
  def alter():
   p=self.root/'server-0.status';d=read(p,self.key);d['round']=str(uuid.uuid4());write(p,self.key,d)
  self.reject_saved(alter)
 def test_mod_change_after_stop_cannot_be_installed(self):self.reject_saved(lambda:(self.source/'mods/mared.jar').write_bytes(b'foreign'))
 def test_bad_receipt_hmac_cannot_authorize_copy(self):
  def alter():(self.root/'server-0.status').write_bytes(b'forged')
  self.owner.stop=lambda *args:(self.stop(*args),alter())[0]
  with self.assertRaises(Exception):self.owner.restart103()
  self.assertFalse(self.started)
 def test_launcher_is_idempotent_and_does_not_replace_custom_file(self):
  path=launcher(self.root,self.root);old=path.read_bytes();self.assertEqual(launcher(self.root,self.root).read_bytes(),old);path.write_text('CUSTOM')
  with self.assertRaisesRegex(ValueError,'LAUNCHER_CHANGED'):launcher(self.root,self.root)
  self.assertEqual(path.read_text(),'CUSTOM')
 def test_generated_launcher_can_refresh_only_with_matching_signed_old_hash(self):
  path=launcher(self.root,self.root);before=path.read_bytes();other=self.root/'other bundle';other.mkdir();launcher(other,self.root);self.assertNotEqual(path.read_bytes(),before);self.assertIn(b'other bundle',path.read_bytes())
 def test_launcher_rejects_shell_expansion(self):
  root=self.root/'bad%root';root.mkdir()
  with patch('first58.dedicated103.base.settings',return_value=({},b'')):
   with self.assertRaisesRegex(ValueError,'LAUNCHER_PATH'):launcher(self.root,root)
  self.assertFalse((root/'Genesis-server.cmd').exists())
if __name__=='__main__':unittest.main()
