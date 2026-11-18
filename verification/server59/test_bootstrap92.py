from pathlib import Path
import sys,tempfile,unittest,zipfile,uuid,subprocess,io,contextlib
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
import recover_local92 as tool
class Wrapper92(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)/'project with spaces';self.root.mkdir();self.config=self.root/'run/client/config/mared/dev/supervisor.properties';self.config.parent.mkdir(parents=True);self.config.write_text('controlToken=PRIVATE_TOKEN');self.java=self.root/'jdk21 with spaces/bin/java.exe';self.java.parent.mkdir(parents=True);self.java.touch();self.jar=self.root/'build/supervisor/resource/mared/dev/supervisor-bootstrap.jar';self.jar.parent.mkdir(parents=True)
  with zipfile.ZipFile(self.jar,'w') as z:
   for name in tool.REQUIRED:z.writestr(name,b'compile fixture only')
 def test_arguments_are_not_shell_or_credentials(self):
  args=tool.command(self.root,java=self.java,status=True);self.assertIsInstance(args,list);self.assertEqual(args[0],str(self.java));self.assertIn(str(self.jar),args);self.assertNotIn('PRIVATE_TOKEN',str(args));self.assertNotIn('RESTORE',str(args))
 def test_ambiguous_profile_refused(self):
  other=self.root/'config/mared/dev/supervisor.properties';other.parent.mkdir(parents=True);other.touch()
  with self.assertRaises(ValueError):tool.config_path(self.root)
  self.assertEqual(tool.config_path(self.root,self.root/'run/client'),self.config)
 def test_no_capsule_refused_without_build(self):
  self.jar.unlink()
  with self.assertRaises(ValueError):tool.command(self.root,java=self.java)
 def test_old_capsule_refused(self):
  with zipfile.ZipFile(self.jar,'w') as z:z.writestr('old.class',b'old')
  with self.assertRaises(ValueError):tool.capsule(self.root)
 def test_action_is_bound_to_review(self):
  ticket=str(uuid.uuid4());args=tool.command(self.root,java=self.java,action='RESTORE',ticket=ticket,revision='a'*64);self.assertIn('--ticket='+ticket,args);self.assertIn('--revision='+'a'*64,args)
  for bad in ['-'*36,'bad',ticket+'\n']:
   with self.assertRaises(ValueError):tool.command(self.root,java=self.java,action='RESTORE',ticket=bad,revision='a'*64)
 def test_unsafe_revision_refused(self):
  with self.assertRaises(ValueError):tool.command(self.root,java=self.java,action='RESTORE',ticket=str(uuid.uuid4()),revision='$(untrusted)')
 def test_jdk17_does_not_launch_bootstrap(self):
  with patch.object(sys,'argv',['recover',str(self.root),'--java',str(self.java),'--status']),patch.object(tool.subprocess,'run',return_value=subprocess.CompletedProcess([],0,'','openjdk version "17.0.1"')) as run,contextlib.redirect_stdout(io.StringIO()):self.assertEqual(tool.main(),2);self.assertEqual(run.call_count,1)
 def test_jdk21_launches_only_named_bootstrap(self):
  with patch.object(sys,'argv',['recover',str(self.root),'--java',str(self.java),'--status']),patch.object(tool.subprocess,'run',side_effect=[subprocess.CompletedProcess([],0,'','openjdk version "21.0.12"'),subprocess.CompletedProcess([],0)]) as run:
   self.assertEqual(tool.main(),0);self.assertEqual(run.call_count,2);self.assertIn('com.fixmer.genesis.supervisor.RecoveryBootstrap92',run.call_args.args[0]);self.assertNotIn('shell',run.call_args.kwargs)
 def test_missing_owner_profile_has_no_offline_fallback(self):
  self.config.unlink()
  with self.assertRaises(ValueError):tool.command(self.root,java=self.java,action='RESTORE',ticket=str(uuid.uuid4()),revision='a'*64)
if __name__=='__main__':unittest.main()
