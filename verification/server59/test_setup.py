"""Setup contract tests with explicitly synthetic profiles; not Minecraft runtime tests."""
from filesystem_capabilities124 import fixture_symlink
from pathlib import Path
import copy
import json
import os
import socket
import sys
import tempfile
import unittest
import uuid
import zipfile
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
from first58 import wizard_model as model
from first58.runtime import core_classpath
from first58.common import json_read

class SetupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.server = self.base / 'any server location'
        self.client = self.base / 'client with spaces'
        for folder in (self.server, self.client):
            (folder / 'mods').mkdir(parents=True)
            self.jar(folder / 'mods/mared.jar', {
                'com/fixmer/mared/technology/server/firstlaunch/FirstLaunchServer58.class': b'fixture',
                'com/fixmer/mared/technology/server/firstlaunch/FirstLaunchClient58.class': b'fixture'})
        self.java = self.base / 'JDK/bin/java.exe'
        self.java.parent.mkdir(parents=True)
        self.java.write_bytes(b'fixture')
        (self.server / 'libraries/net/neoforged/neoforge/21.1.264').mkdir(parents=True)
        (self.server / 'libraries/net/neoforged/neoforge/21.1.264/win_args.txt').write_text('fixture')
        (self.server / 'user_jvm_args.txt').write_text('-Xmx2G')
        (self.server / 'eula.txt').write_text('eula=true\n')
        (self.server / 'server.properties').write_text('server-port=25565\nlevel-name=world\n')
        self.account = str(uuid.uuid4())
        self.argv = [str(self.java), '-cp', str(self.base / 'libraries.jar'), 'main.Client', '--gameDir', '{profile}', '--uuid', self.account.replace('-', ''), '--accessToken', 'test-token-not-real', '--fml.neoForgeVersion', '21.1.264']
        self.details = model.client_details(self.argv, self.client, self.account, '21.1.264')
    def jar(self, path, entries):
        with zipfile.ZipFile(path, 'w') as z:
            for k,v in entries.items(): z.writestr(k,v)
    def plan(self, *args):
        with patch.object(model, 'port_available'):
            return model.plan(self.server, self.details, *args)
    def test_arbitrary_paths_and_exact_version(self):
        spec, report = self.plan()
        self.assertEqual(spec['serverTemplate'], str(self.server))
        self.assertEqual(spec['neoforge'], '21.1.264')
        self.assertIn('@libraries/net/neoforged/neoforge/21.1.264/win_args.txt', spec['serverCommand'])
        self.assertEqual(spec['clientCommand'][spec['clientCommand'].index('--gameDir')+1], '{profile}')
        self.assertEqual(report['client'], {'added': [], 'removed': [], 'changed': []})
    def test_canonical_uuid_automatic(self):
        self.assertEqual(self.details['account'], self.account)
    def test_different_account_refused(self):
        with self.assertRaisesRegex(model.SetupError, 'Аккаунт'):
            model.client_details(self.argv, self.client, str(uuid.uuid4()), '21.1.264')
    def test_zero_uuid_refused(self):
        with self.assertRaises(model.SetupError): model.account_id('0'*32)
    def test_offline_token_refused(self):
        argv=list(self.argv);argv[argv.index('--accessToken')+1]='0'
        with self.assertRaisesRegex(model.SetupError,'online'):
            model.client_details(argv,self.client)
    def test_missing_uuid_not_guessed(self):
        argv=list(self.argv);i=argv.index('--uuid');del argv[i:i+2]
        with self.assertRaises(model.SetupError):model.client_details(argv,self.client)
    def test_duplicate_uuid_refused(self):
        with self.assertRaises(model.SetupError):model.client_details(self.argv+['--uuid',self.account],self.client)
    def test_agent_refused(self):
        with self.assertRaisesRegex(model.SetupError,'dev-запуск'):
            model.client_details(self.argv+['-javaagent:external.jar'],self.client)
    def test_loaded_version_mismatch(self):
        with self.assertRaisesRegex(model.SetupError,'отличается'):
            model.client_details(self.argv,self.client,neoforge='21.1.235')
    def test_server_version_mismatch(self):
        details=dict(self.details,neoforge='21.1.235')
        with self.assertRaisesRegex(model.SetupError,'должны совпадать'):
            model.server_details(self.server,details)
    def test_missing_server_not_created(self):
        missing=self.base/'missing'
        with self.assertRaises(model.SetupError):model.server_details(missing,self.details)
        self.assertFalse(missing.exists())
    def test_eula_never_accepted(self):
        path=self.server/'eula.txt';path.write_text('eula=false\n')
        with self.assertRaisesRegex(model.SetupError,'EULA'):self.plan()
        self.assertEqual(path.read_text(),'eula=false\n')
    def test_server_not_initialized(self):
        (self.server/'server.properties').unlink()
        with self.assertRaisesRegex(model.SetupError,'run.bat'):self.plan()
    def test_next_complete_inventory_and_removal_preview(self):
        self.jar(self.server/'mods/old.jar', {'old': b'old'})
        next_mods=self.base/'next mods';next_mods.mkdir()
        self.jar(next_mods/'mared.jar',{'com/fixmer/mared/technology/server/firstlaunch/FirstLaunchServer58.class':b'changed'})
        self.jar(next_mods/'extra.jar', {'extra': b'extra'})
        spec,delta=self.plan(str(next_mods))
        self.assertEqual(delta['server']['removed'],['old.jar'])
        self.assertEqual(delta['server']['changed'],['mared.jar'])
        self.assertEqual(delta['server']['added'],['extra.jar'])
        self.assertTrue((self.server/'mods/old.jar').exists())
    def test_adapter_missing_refused(self):
        (self.server/'mods/mared.jar').unlink();(self.server/'mods/other.jar').write_bytes(b'other')
        with self.assertRaisesRegex(model.SetupError, 'Повреждённый'):self.plan()
    def test_no_adapter_in_valid_jar(self):
        self.jar(self.server/'mods/mared.jar',{'other.class':b'fixture'})
        with self.assertRaisesRegex(model.SetupError,'нет MaRed'):self.plan()
    def test_bad_filename_refused(self):
        (self.server/'mods/bad name.jar').write_bytes(b'x')
        with self.assertRaisesRegex(model.SetupError,'латиницей'):self.plan()
    def test_case_collisions_refused(self):
        if (self.server/'mods/MARED.jar').exists():self.skipTest('Filesystem aliases MARED.jar and mared.jar; collision fixture is unavailable')
        (self.server/'mods/MARED.jar').write_bytes(b'x')
        with self.assertRaisesRegex(model.SetupError,'регистром'):self.plan()
    def test_symlink_refused(self):
        linked=self.base/'linked';fixture_symlink(linked,self.server,target_is_directory=True)
        with self.assertRaises(ValueError):model.server_details(linked,self.details)
    def test_invalid_port(self):
        with self.assertRaises(model.SetupError):self.plan('', '', 80)
    def test_occupied_port(self):
        with socket.socket() as sock:
            sock.bind(('0.0.0.0',0));sock.listen()
            with self.assertRaises(model.SetupError):model.port_available(sock.getsockname()[1])
    def test_new_root_each_time_and_acl_before_secret(self):
        spec,_=self.plan();parent=self.base/'generated';events=[]
        def secure(*args):events.append(('acl',args[-1]));self.assertFalse((args[-1]/'settings.json').exists())
        def initialize(project,root,settings):
            self.assertTrue(events)
            self.assertEqual(json_read(settings)['account'],self.account)
            events.append(('init',root));root.mkdir()
        with patch.object(model,'java',side_effect=secure),patch('first58.setup.initialize',side_effect=initialize),patch.object(model,'port_available'):
            one=model.prepare(self.base,spec,parent,stopped_check=lambda *a:None)
            two=model.prepare(self.base,spec,parent,stopped_check=lambda *a:None)
        self.assertNotEqual(one,two)
        self.assertEqual([e[0] for e in events],['acl','init','acl','init'])
    def test_live_template_refused_before_creating_state(self):
        spec,_=self.plan();parent=self.base/'generated'
        def stopped(*args):raise model.SetupError('running')
        with self.assertRaisesRegex(model.SetupError,'running'):model.prepare(self.base,spec,parent,stopped_check=stopped)
        self.assertFalse(parent.exists())
    def test_state_cannot_be_nested_in_template(self):
        spec,_=self.plan()
        with patch.object(model,'port_available'),self.assertRaisesRegex(model.SetupError,'отдельно'):
            model.prepare(self.base,spec,self.client/'state',stopped_check=lambda *args:None)
        self.assertFalse((self.client/'state').exists())
    def test_private_acl_failure_no_secret_written(self):
        spec,_=self.plan();parent=self.base/'generated'
        with patch.object(model,'java',side_effect=ValueError('ACL failed')),patch.object(model,'port_available'),self.assertRaises(ValueError):
            model.prepare(self.base,spec,parent,stopped_check=lambda *args:None)
        self.assertEqual(list(parent.rglob('settings.json')),[])
    def test_bundled_core_not_tied_to_source_checkout(self):
        core=self.base/'runtime/core.jar';core.parent.mkdir()
        self.jar(core,{'com/fixmer/genesis/technology/server/firstlaunch/FirstLaunchArtifacts.class':b'fixture', 'com/fixmer/genesis/technology/server/firstlaunch/FirstLaunchProbe.class':b'fixture'})
        self.assertEqual(core_classpath(self.base),core)
    def test_incomplete_core_refused(self):
        core=self.base/'runtime/core.jar';core.parent.mkdir();self.jar(core,{'other':b'fixture'})
        with self.assertRaisesRegex(ValueError,'incomplete'):core_classpath(self.base)
    def test_missing_core_refused(self):
        with self.assertRaisesRegex(ValueError,'missing'):core_classpath(self.base)

class ReplacementTests(unittest.TestCase):
    setUp = SetupTests.setUp
    jar = SetupTests.jar
    def small_mod(self, path, modid='samplemod', content='original'):
        self.jar(path, {'META-INF/neoforge.mods.toml': ('[[mods]]\nmodId="'+modid+'"\nversion="1"\n').encode(), 'content.txt': content.encode()})
    def test_single_jar_replaces_old_filename_by_modid(self):
        self.small_mod(self.server/'mods/sample-1.jar')
        new=self.base/'sample-2.jar';self.small_mod(new, content='updated')
        before,after=model.replacement_inventory(self.server/'mods',new)
        self.assertNotIn('sample-1.jar',after)
        self.assertIn('sample-2.jar',after)
        self.assertEqual(set(after),{'mared.jar','sample-2.jar'})
        self.assertTrue((self.server/'mods/sample-1.jar').exists())
    def test_stage_keeps_other_mods_and_originals(self):
        self.small_mod(self.server/'mods/sample-1.jar')
        new=self.base/'sample-2.jar';self.small_mod(new, content='updated')
        original=model.mod_files(self.server/'mods');dest=self.base/'staged'
        delta=model.stage_replacement(self.server/'mods',new,dest)
        self.assertEqual(delta['removed'],['sample-1.jar'])
        self.assertEqual(model.mod_files(self.server/'mods'),original)
        self.assertEqual((dest/'mared.jar').read_bytes(),(self.server/'mods/mared.jar').read_bytes())
    def test_modid_duplicates_refused(self):
        self.small_mod(self.server/'mods/sample-1.jar')
        self.small_mod(self.server/'mods/duplicate.jar')
        new=self.base/'new.jar';self.small_mod(new)
        with self.assertRaisesRegex(model.SetupError,'несколько JAR'):model.replacement_inventory(self.server/'mods',new)
    def test_core_replacement_refused(self):
        new=self.base/'new.jar';self.small_mod(new,'mared')
        with self.assertRaisesRegex(model.SetupError,'MaRed'):model.replacement_inventory(self.server/'mods',new)
    def test_metadata_required(self):
        new=self.base/'new.jar';self.jar(new,{'file':b'x'})
        with self.assertRaisesRegex(model.SetupError,'метаданных'):model.replacement_inventory(self.server/'mods',new)
    def test_conflicting_filename_not_removed(self):
        self.small_mod(self.server/'mods/new.jar','different')
        new=self.base/'new.jar';self.small_mod(new)
        with self.assertRaisesRegex(model.SetupError,'занято'):model.replacement_inventory(self.server/'mods',new)

if __name__=='__main__':unittest.main(verbosity=2)
