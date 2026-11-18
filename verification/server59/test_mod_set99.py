from pathlib import Path
import contextlib,io,json,subprocess,sys,tempfile,unittest,zipfile
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
import repair_set99 as repair
import recover_local92 as local
class SetWrapper99(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)/'project space';self.config=self.root/'run/client/config/mared/dev/supervisor.properties';self.config.parent.mkdir(parents=True);self.config.write_text('controlToken=PRIVATE_TOKEN');self.java=self.root/'jdk/bin/java.exe';self.java.parent.mkdir(parents=True);self.java.touch();self.jar=self.root/'build/supervisor/resource/mared/dev/supervisor-bootstrap.jar';self.jar.parent.mkdir(parents=True)
  with zipfile.ZipFile(self.jar,'w') as z:
   for name in local.REQUIRED|repair.REQUIRED:z.writestr(name,b'fixture signatures, not Java')
 def test_default_inspects_only(self):
  args=repair.command(self.root,java=self.java);self.assertEqual(args[-1],'--inspect');self.assertNotIn('PRIVATE_TOKEN',str(args));self.assertNotIn('--recover-cold94',args);self.assertFalse(any('handoff' in s for s in args))
 def test_restore_binds_fresh_revision(self):
  args=repair.command(self.root,java=self.java,action='restore',revision='a'*64);self.assertEqual(args[-2:],['--restore','--revision='+'a'*64])
 def test_cancel_binds_fresh_revision(self):
  args=repair.command(self.root,java=self.java,action='cancel-pending',revision='b'*64);self.assertEqual(args[-2],'--cancel-pending')
 def test_mutation_without_revision_refused(self):
  for action in ['restore','cancel-pending']:
   with self.assertRaises(ValueError):repair.command(self.root,java=self.java,action=action)
 def test_unknown_action_refused(self):
  with self.assertRaises(ValueError):repair.command(self.root,java=self.java,action='force')
 def test_old_capsule_refused(self):
  with zipfile.ZipFile(self.jar,'w') as z:
   for name in local.REQUIRED:z.writestr(name,b'old')
  with self.assertRaises(ValueError):repair.command(self.root,java=self.java)
 def test_profile_is_explicit(self):
  args=repair.command(self.root,java=self.java,profile=self.root/'run/client');self.assertIn('--config='+str(self.config),args)
 def test_jdk17_refused(self):
  with patch.object(sys,'argv',['repair',str(self.root),'--java',str(self.java)]),patch.object(repair.subprocess,'run',return_value=subprocess.CompletedProcess([],0,'','openjdk version "17.0.1"')) as run,contextlib.redirect_stdout(io.StringIO()):self.assertEqual(repair.main(),2);self.assertEqual(run.call_count,1)
 def test_jdk21_never_uses_shell_or_auto_restore(self):
  with patch.object(sys,'argv',['repair',str(self.root),'--java',str(self.java)]),patch.object(repair.subprocess,'run',side_effect=[subprocess.CompletedProcess([],0,'','openjdk version "21.0.12"'),subprocess.CompletedProcess([],0)]) as run:self.assertEqual(repair.main(),0);self.assertIsInstance(run.call_args.args[0],list);self.assertNotIn('shell',run.call_args.kwargs);self.assertEqual(run.call_args.args[0][-1],'--inspect')
 def test_localizations_match(self):
  root=Path(__file__).resolve().parents[2];ru=json.loads((root/'src/main/resources/assets/mared/lang/ru_ru.json').read_text(encoding='utf-8'));en=json.loads((root/'src/main/resources/assets/mared/lang/en_us.json').read_text(encoding='utf-8'));keys={k for k in ru if k.startswith('mared.scripts.set99.')};self.assertTrue(keys);self.assertEqual(keys,{k for k in en if k.startswith('mared.scripts.set99.')});self.assertTrue(all(ru[k].strip() and en[k].strip() for k in keys))
if __name__=='__main__':unittest.main()
