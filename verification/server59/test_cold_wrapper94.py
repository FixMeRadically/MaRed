from pathlib import Path
import contextlib,io,subprocess,sys,tempfile,unittest,zipfile
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
import recover_after_owner94 as cold
import recover_local92 as local

class ColdWrapper94(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)/'project with spaces';self.root.mkdir();self.config=self.root/'run/client/config/mared/dev/supervisor.properties';self.config.parent.mkdir(parents=True);self.config.write_text('controlToken=PRIVATE_TOKEN');self.java=self.root/'jdk/bin/java.exe';self.java.parent.mkdir(parents=True);self.java.touch();self.jar=self.root/'build/supervisor/resource/mared/dev/supervisor-bootstrap.jar';self.jar.parent.mkdir(parents=True)
  with zipfile.ZipFile(self.jar,'w') as z:
   for name in local.REQUIRED:z.writestr(name,b'compile fixture only')
 def test_check_has_no_action_or_game_command(self):
  args=cold.command(self.root,java=self.java,check=True);self.assertIn('com.fixmer.genesis.supervisor.ColdOwner94',args);self.assertIn('--check',args);self.assertNotIn('--recover-cold94',args);self.assertNotIn('PRIVATE_TOKEN',str(args))
 def test_default_starts_only_explicit_owner_recovery_mode(self):
  args=cold.command(self.root,java=self.java);self.assertIn('com.fixmer.genesis.supervisor.Supervisor',args);self.assertEqual(args[-1],'--recover-cold94');self.assertFalse(any('handoff-pid' in s or '--action' in s for s in args))
 def test_explicit_profile_is_bound(self):self.assertIn('--config='+str(self.config),cold.command(self.root,profile=self.root/'run/client',java=self.java))
 def test_unknown_profile_refused(self):
  with self.assertRaises(ValueError):cold.command(self.root,profile=self.root/'unknown',java=self.java)
 def test_old_bootstrap_refused(self):
  with zipfile.ZipFile(self.jar,'w') as z:z.writestr('old.class',b'old')
  with self.assertRaises(ValueError):cold.command(self.root,java=self.java)
 def test_no_auto_build(self):
  self.jar.unlink()
  with self.assertRaises(ValueError):cold.command(self.root,java=self.java)
 def test_missing_jdk_refused(self):
  with self.assertRaises(ValueError):cold.command(self.root,java=self.root/'missing-java')
 def test_jdk17_cannot_start_owner(self):
  with patch.object(sys,'argv',['cold',str(self.root),'--java',str(self.java)]),patch.object(cold.subprocess,'run',return_value=subprocess.CompletedProcess([],0,'','openjdk version "17.0.1"')) as run,contextlib.redirect_stdout(io.StringIO()):self.assertEqual(cold.main(),2);self.assertEqual(run.call_count,1)
 def test_jdk21_check_uses_argument_list_no_shell(self):
  with patch.object(sys,'argv',['cold',str(self.root),'--java',str(self.java),'--check']),patch.object(cold.subprocess,'run',side_effect=[subprocess.CompletedProcess([],0,'','openjdk version "21.0.12"'),subprocess.CompletedProcess([],2)]) as run:
   self.assertEqual(cold.main(),2);self.assertIsInstance(run.call_args.args[0],list);self.assertNotIn('shell',run.call_args.kwargs);self.assertIn('--check',run.call_args.args[0]);self.assertNotIn('--recover-cold94',run.call_args.args[0])
 def test_jdk21_default_never_sends_restore(self):
  with patch.object(sys,'argv',['cold',str(self.root),'--java',str(self.java)]),patch.object(cold.subprocess,'run',side_effect=[subprocess.CompletedProcess([],0,'','openjdk version "21.0.12"'),subprocess.CompletedProcess([],0)]) as run:self.assertEqual(cold.main(),0);self.assertNotIn('RESTORE',str(run.call_args));self.assertIn('--recover-cold94',run.call_args.args[0])
if __name__=='__main__':unittest.main()
