from pathlib import Path
import os
import tempfile
import unittest
from unittest.mock import Mock, patch
from first58.batch124 import batch, java21, WindowsServerJob124


class Batch124(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='Genesis124 batch & spaces ')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.script = self.root / 'run.bat'
        self.standard = ('@echo off\r\nREM NeoForge standard script\r\n'
                         'java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.264/win_args.txt %*\r\npause\r\n')
        self.script.write_bytes(self.standard.encode())

    def test_actual_bat_path_and_foreground_command(self):
        result = batch(self.root, '21.1.264')
        self.assertEqual(result['batch'], str(self.script))
        self.assertEqual(result['argv'][1:5], ['/d', '/v:off', '/s', '/c'])
        self.assertEqual(result['argv'][-1], '""' + str(self.script) + '""')
        self.assertEqual(self.script.read_bytes(), self.standard.encode())

    def test_explicit_nogui_inside_user_script_is_preserved(self):
        content=self.standard.replace(' %*',' nogui %*');self.script.write_bytes(content.encode())
        self.assertNotIn('nogui',batch(self.root,'21.1.264')['argv'][-1])
        self.assertEqual(self.script.read_bytes(),content.encode())

    def test_unsupported_script_is_not_approximated(self):
        for command in ('start java ignored', 'call evil.bat', 'java ignored',
                        'set JAVA_HOME=wrong', 'java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.264/win_args.txt & echo extra'):
            self.script.write_text(command)
            with self.assertRaisesRegex(ValueError, 'BATCH_UNSUPPORTED'):
                batch(self.root, '21.1.264')

    def test_missing_bat_and_runtime_mismatch(self):
        with self.assertRaisesRegex(ValueError, 'BATCH_UNSUPPORTED'):
            batch(self.root, '21.1.263')
        self.script.unlink()
        with self.assertRaisesRegex(ValueError, 'BATCH_REQUIRED'):
            batch(self.root, '21.1.264')

    def test_windows_path_and_java_home_command(self):
        self.script.write_text(self.standard.replace('java @', '"%JAVA_HOME%\\bin\\java.exe" @').replace('libraries/net/neoforged/neoforge/', 'libraries\\net\\neoforged\\neoforge\\'))
        self.assertTrue(batch(self.root, '21.1.264')['batchSha'])

    def test_cmd_quotes_are_not_c_runtime_escaped(self):
        command = batch(self.root, '21.1.264')
        system = self.root / 'Windows/System32'
        system.mkdir(parents=True)
        (system / 'cmd.exe').write_bytes(b'fixture')
        with patch.dict(os.environ, {'SystemRoot': str(system.parent)}):
            command = batch(self.root, '21.1.264')
            env = {'GENESIS_SERVER_NEO124': '21.1.264', 'GENESIS_SERVER_BATCH124': command['batchSha']}
            job = object.__new__(WindowsServerJob124)
            module, raw = job.launch_command(command['argv'], self.root, env)
            self.assertEqual(module, system / 'cmd.exe')
            self.assertTrue(raw.endswith(command['argv'][-1]))
            self.assertNotIn('\\"', raw)
            self.script.write_text(self.standard + 'REM changed after review\n')
            with self.assertRaisesRegex(ValueError, 'BATCH_CHANGED'):
                job.launch_command(command['argv'], self.root, env)

    def test_java25_falls_back_to_probed_java21(self):
        vendor = self.root / 'Java'
        old = vendor / 'jdk-25/bin/java.exe'
        good = vendor / 'jdk-21/bin/java.exe'
        for exe, version in ((old, '25.0.2'), (good, '21.0.12')):
            exe.parent.mkdir(parents=True)
            exe.write_bytes(b'fixture')
            (exe.parent.parent / 'release').write_text('JAVA_VERSION="' + version + '"\n')
        def probe(argv, **kwargs):
            self.assertNotIn('JAVA_TOOL_OPTIONS', kwargs['env'])
            return Mock(returncode=0, stdout='', stderr='java version "' + ('21.0.12' if Path(argv[0]) == good else '25.0.2') + '"')
        with patch('first58.batch124.subprocess.run', side_effect=probe):
            self.assertEqual(java21(old), good)

    def test_java21_kept_and_wrong_java_rejected(self):
        java = self.root / 'java.exe'
        java.write_bytes(b'fixture')
        with patch('first58.batch124.subprocess.run', return_value=Mock(returncode=0, stdout='', stderr='openjdk version "21.0.12"')):
            self.assertEqual(java21(java), java)
        with patch('first58.batch124.subprocess.run', return_value=Mock(returncode=0, stdout='', stderr='openjdk version "25.0.2"')):
            with self.assertRaisesRegex(ValueError, 'JAVA21'):
                java21(java)


if __name__ == '__main__':
    unittest.main()
