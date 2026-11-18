from pathlib import Path
import importlib.util
import json
import shutil
import subprocess
import sys
import tempfile
from unittest.mock import patch

root = Path(__file__).resolve().parents[2]
package = root / 'artifacts/Genesis-system-bindings-123'
installer = package / 'patch/apply_changes.py'
manifest = json.loads((package / 'patch/manifest.json').read_text('utf-8'))
checks = 0

def check(value):
    global checks
    checks += 1
    if not value:
        raise AssertionError(f'installer123 #{checks}')

def run(project, *args, success=True):
    result = subprocess.run([sys.executable, str(installer), str(project), *map(str,args)], capture_output=True, text=True)
    check((result.returncode == 0) == success)
    if success and result.returncode:
        raise AssertionError(result.stderr)
    return result

def state(project):
    return {e['path']: (project / e['path']).read_bytes() if (project / e['path']).is_file() else None for e in manifest['files']}

run(root, '--check')
with tempfile.TemporaryDirectory(prefix='genesis123-installer-') as temporary:
    project = Path(temporary) / 'MaRed'
    project.mkdir()
    for e in manifest['required']:
        dest = project / e['path']; dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root / e['path'], dest)
    for e in manifest['files']:
        before = root / '.genesis-backups/audit123/before' / e['path']
        if before.is_file():
            dest = project / e['path']; dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(before, dest)
    baseline = state(project)
    run(project, '--check')
    check(state(project) == baseline)
    run(project)
    for e in manifest['files']:
        check((project / e['path']).read_bytes() == (package / 'patch/files' / e['path']).read_bytes())
    backups = list((project / '.genesis-backups/patch123').glob('*/backup.json'))
    check(len(backups) == 1)
    run(project)
    check(len(list((project / '.genesis-backups/patch123').glob('*/backup.json'))) == 1)
    # A newer manual edit must block restoration before touching any other file.
    changed = project / 'build.gradle'
    changed.write_bytes(changed.read_bytes() + b'\n// later manual edit\n')
    observed = state(project)
    run(project, '--restore', backups[0].parent, success=False)
    check(state(project) == observed)
    shutil.copyfile(package / 'patch/files/build.gradle', changed)
    run(project, '--restore', backups[0].parent)
    check(state(project) == baseline)
    # Unknown bases fail the whole preflight without modifying known ones.
    changed.write_bytes(changed.read_bytes() + b'\n// unknown base\n')
    observed = state(project)
    run(project, '--check', success=False)
    check(state(project) == observed)
    changed.write_bytes(baseline['build.gradle'])
    # Inject a real failure after one successful write and verify complete rollback.
    spec = importlib.util.spec_from_file_location('patch123_installer', installer)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    original = module.write
    calls = 0
    def interrupted(path, data):
        global calls
        calls += 1
        if calls == 2:
            raise OSError('injected write failure')
        return original(path, data)
    with patch.object(module, 'write', interrupted), patch.object(sys, 'argv', [str(installer), str(project)]):
        try:
            module.main()
            raise AssertionError('Injected error was ignored')
        except OSError:
            pass
    check(calls >= 3)
    check(state(project) == baseline)

print(f'Installer123 passed: {checks}; check/apply/idempotence/restore/changed-source refusal/injected IO rollback; {len(manifest["files"])} payloads.')
