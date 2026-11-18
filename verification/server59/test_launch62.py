"""Local dev/offline contracts using synthetic compiled roots, not a game runtime."""
from filesystem_capabilities124 import fixture_symlink
from pathlib import Path
import hashlib,json,sys,tempfile,unittest,uuid,zipfile
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58 import launch62
from first58.session61 import for_mode,offline_uuid,consume
from first58.setup import validate_spec
from first58.common import json_write
import test_setup as fixtures

class LaunchTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
 def test_argfile_spaces_backslashes_comments(self):
  text=r'''# comment
-cp "C:\\a b\\one.jar;C:\\two.jar" --username 'fix me'
"literal#hash" "" --gameDir .
'''
  self.assertEqual(launch62.argfile_tokens(text),['-cp','C:\\a b\\one.jar;C:\\two.jar','--username','fix me','literal#hash','','--gameDir','.'])
 def test_comment_discards_unquoted_partial_token_like_java(self):
  self.assertEqual(launch62.argfile_tokens('abc#comment\nnext'),['next'])
 def test_java_whitespace_is_ascii(self):
  self.assertEqual(launch62.argfile_tokens('a\u00a0b a\x0bb'),['a\u00a0b','a\x0bb'])
 def test_quoted_comment_prefix_matches_java(self):
  self.assertEqual(launch62.argfile_tokens('abc"def"suffix#comment\n next'),['abcdefnext'])
  self.assertEqual(launch62.argfile_tokens('"abc"#comment\n'),[])
 def test_continuation_skips_multiple_lines_like_java(self):
  text='"abc'+chr(92)+'\n\n  def"'
  self.assertEqual(launch62.argfile_tokens(text),['abcdef'])
 def test_argfile_unterminated_refused(self):
  with self.assertRaises(ValueError):launch62.argfile_tokens('"never closed')
 def test_argfile_continuation(self):
  self.assertEqual(launch62.argfile_tokens('"abc\\\n  def"'),['abcdef'])
 def test_expand_argfile_absolute_and_strip_debugger(self):
  f=self.root/'vm.args';f.write_text('-cp "some path"\n-agentlib:jdwp=transport=dt_socket,address=1234\n')
  self.assertEqual(launch62.expand_argfiles(['java','@'+str(f),'--gameDir','.']),['java','-cp','some path','--gameDir','.'])
 def test_expand_relative_uses_jvm_cwd(self):
  f=self.root/'vm.args';f.write_text('-Xmx2G')
  self.assertEqual(launch62.expand_argfiles(['java','@vm.args'],self.root),['java','-Xmx2G'])
 def test_no_cwd_never_guessed(self):
  with self.assertRaises(ValueError):launch62.expand_argfiles(['java','@vm.args'])
 def test_nested_refused(self):
  f=self.root/'vm.args';f.write_text('@other.args')
  with self.assertRaises(ValueError):launch62.expand_argfiles(['java','@'+str(f)])
 def test_mod_classes_absolute_grouping(self):
  a=self.root/'classes';a.mkdir();b=self.root/'resources';b.mkdir()
  self.assertEqual(launch62.dev_groups('MaRed%%'+str(a)+';MaRed%%'+str(b)),{'MaRed':[str(a),str(b)]})
 def test_mod_classes_unsafe_group(self):
  with self.assertRaises(ValueError):launch62.dev_groups('../bad%%'+str(self.root))
 def fixture(self):
  src=self.root/'IDE output';src.mkdir()
  adapter=src/'com/fixmer/mared/technology/server/firstlaunch/FirstLaunchClient58.class';adapter.parent.mkdir(parents=True);adapter.write_bytes(b'compiled fixture')
  (src/'resource.txt').write_text('stable')
  core=self.root/'core.jar';core.write_bytes(b'core fixture')
  legacy=self.root/'legacy.txt';legacy.write_text(str(src)+'\n'+str(self.root/'genesis-core-0.1.38.jar')+'\n')
  return src,core,legacy
 def test_snapshot_immutable_and_legacy_remapped(self):
  src,core,legacy=self.fixture();out=self.root/'snapshot'
  spec={'devModClasses':'MaRed%%'+str(src),'clientCommand':['java','-DlegacyClassPath.file='+str(legacy),'-cp',str(src),'main','--gameDir','{profile}']}
  frozen=launch62.snapshot(spec,out,core)
  self.assertEqual(frozen['roots'],[('MaRed','mods/MaRed')])
  self.assertIn('{profile}/.genesis-dev62/core.jar',frozen['legacy'])
  self.assertIn('{profile}/.genesis-dev62/mods/MaRed',frozen['argv'])
  (src/'resource.txt').write_text('changed later')
  self.assertEqual((out/'mods/MaRed/resource.txt').read_text(),'stable')
  self.assertEqual(launch62.tree_hash(out),frozen['hash'])
 def test_inline_class_and_module_paths_are_frozen(self):
  src,core,legacy=self.fixture()
  for option in ('--class-path=','--module-path=','-DlegacyClassPath='):
   with self.subTest(option=option):
    out=self.root/('snapshot-'+str(len(list(self.root.iterdir()))))
    frozen=launch62.snapshot({'devModClasses':'MaRed%%'+str(src),'clientCommand':['java',option+str(src),'Main']},out,core)
    self.assertEqual(frozen['argv'][1],option+'{profile}/.genesis-dev62/mods/MaRed')
 def test_inline_path_does_not_keep_relative_source_reference(self):
  src,core,legacy=self.fixture()
  with self.assertRaisesRegex(ValueError,'relative entry'):
   launch62.snapshot({'devModClasses':'MaRed%%'+str(src),'clientCommand':['java','--class-path=build/classes','Main']},self.root/'snapshot',core)
 def test_conflicting_merged_resource_refused(self):
  src,core,legacy=self.fixture();other=self.root/'other';other.mkdir();(other/'resource.txt').write_text('conflict')
  spec={'devModClasses':'MaRed%%'+str(src)+';MaRed%%'+str(other),'clientCommand':['java','main']}
  with self.assertRaisesRegex(ValueError,'Conflicting'):launch62.snapshot(spec,self.root/'snapshot',core)
 def test_edited_snapshot_refused(self):
  src,core,legacy=self.fixture();profile=self.root/'profile';profile.mkdir();out=profile/launch62.ROOT
  frozen=launch62.snapshot({'devModClasses':'MaRed%%'+str(src),'clientCommand':['java','main']},out,core)
  (out/'mods/MaRed/resource.txt').write_text('tampered')
  with self.assertRaisesRegex(ValueError,'changed'):launch62.verify_profile(profile,frozen['hash'])
 def test_runtime_binds_current_generation(self):
  src,core,legacy=self.fixture();profile=self.root/'client-1';profile.mkdir()
  frozen=launch62.snapshot({'devModClasses':'MaRed%%'+str(src),'clientCommand':['java','main']},profile/launch62.ROOT,core)
  frozen['legacy']=['{profile}/.genesis-dev62/core.jar']
  env=launch62.runtime(profile,{'devSnapshot':frozen})
  self.assertEqual(env['MOD_CLASSES'],'MaRed%%'+str(profile/launch62.ROOT/'mods/MaRed'))
  self.assertNotIn('{profile}',(profile/'client-legacy62.txt').read_text())
 def test_symlink_snapshot_refused(self):
  src,core,legacy=self.fixture();fixture_symlink(src/'linked',core)
  with self.assertRaises(ValueError):launch62.tree_hash(src)
 def test_long_command_uses_argfile_without_losing_arguments(self):
  argv=['java','main','x'*29000,'--gameDir',str(self.root)]
  wrapped=launch62.wrap_long(argv,self.root)
  self.assertEqual(launch62.expand_argfiles(wrapped),argv)

class OfflineTests(unittest.TestCase):
 jar=fixtures.SetupTests.jar
 def setUp(self):
  fixtures.SetupTests.setUp(self)
  self.jar(self.server/'mods/mared.jar', {'com/fixmer/mared/technology/server/firstlaunch/FirstLaunchServer58.class':b'compiled fixture verifyServerMode'})
 def session(self):return {'account':self.account,'name':'fix','accessToken':'0','userType':'legacy'}
 def test_offline_uuid_java_algorithm(self):
  self.assertEqual(offline_uuid('fix'),str(uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:fix').digest(),version=3)))
 def test_case_sensitive_identity(self):self.assertNotEqual(offline_uuid('fix'),offline_uuid('Fix'))
 def test_local_mode_has_no_online_token(self):
  argv,account=for_mode(self.argv,self.session(),'LOCAL_OFFLINE')
  self.assertEqual(argv[argv.index('--accessToken')+1],'0');self.assertEqual(account,offline_uuid('fix'))
 def test_no_automatic_online_fallback(self):
  with self.assertRaises(ValueError):for_mode(self.argv,self.session(),'ONLINE')
 def test_offline_spec_uuid_name_binding(self):
  from first58.wizard_model import client_details,plan
  argv,account=for_mode(self.argv,self.session(),'LOCAL_OFFLINE')
  detail=client_details(argv,self.client,account,'21.1.264',auth_mode='LOCAL_OFFLINE')
  with patch('first58.wizard_model.port_available'):spec,_=plan(self.server,detail)
  self.assertEqual(spec['authMode'],'LOCAL_OFFLINE');validate_spec(spec)
  spec['account']=str(uuid.uuid4())
  with self.assertRaisesRegex(ValueError,'UUID'):validate_spec(spec)
 def test_offline_snapshot_consumption_without_online_credentials(self):
  path=self.base/'client-session61.json'
  data={**self.session(),'schema':62,'pid':123,'profile':str(self.client),'issuedAt':1000000,'devMods':'','cwd':str(self.base)}
  json_write(path,data,new=True)
  self.assertEqual(consume(self.base,path,123,self.client,self.account,now=1000001,allow_offline=True)['accessToken'],'0')
  self.assertFalse(path.exists())
 def test_empty_dev_mod_inventory_is_accepted_without_second_mared_jar(self):
  from first58.wizard_model import client_details,plan
  (self.client/'mods/mared.jar').unlink()
  dev=self.base/'bin/main';adapter=dev/'com/fixmer/mared/technology/server/firstlaunch/FirstLaunchClient58.class';adapter.parent.mkdir(parents=True);adapter.write_bytes(b'fixture')
  argv,account=for_mode(self.argv,self.session(),'LOCAL_OFFLINE');argv+=['--launchTarget','forgeclientdev']
  detail=client_details(argv,self.client,account,'21.1.264',auth_mode='LOCAL_OFFLINE',dev_mods='MaRed%%'+str(dev))
  with patch('first58.wizard_model.port_available'):spec,delta=plan(self.server,detail)
  self.assertIn('devModClasses',spec)
 def test_old_server_adapter_rejected_before_launch(self):
  from first58.wizard_model import has_adapter
  self.jar(self.server/'mods/mared.jar', {'com/fixmer/mared/technology/server/firstlaunch/FirstLaunchServer58.class':b'old adapter'})
  with self.assertRaisesRegex(ValueError,'патчем62'):has_adapter(self.server/'mods','server',require62=True)
 def test_missing_dev_environment_refused(self):
  from first58.wizard_model import client_details
  with self.assertRaisesRegex(ValueError,'MOD_CLASSES'):client_details(self.argv+['--launchTarget','forgeclientdev'],self.client)

if __name__=='__main__':unittest.main(verbosity=2)
