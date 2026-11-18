"""Launch the selected NeoForge run.bat inside the server's existing lifetime Job.

Only the standard foreground NeoForge script is supported. START/CALL, detached
commands and profile overrides require an explicit launcher adapter, not guessing.
"""
from pathlib import Path
import hashlib
import os
import re
import subprocess
from .common import safe
from .windows_job import WindowsJob


def probe_java21(java):
    java = safe(java)
    if java.name.lower() != 'java.exe' or not java.is_file():
        return False
    env = {k: v for k, v in os.environ.items() if k.upper() in {
        'PATH', 'SYSTEMROOT', 'WINDIR', 'TEMP', 'TMP', 'USERPROFILE',
        'APPDATA', 'LOCALAPPDATA', 'PROGRAMDATA'}}
    try:
        result = subprocess.run([str(java), '-version'], capture_output=True,
                                text=True, timeout=15, env=env,
                                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0),
                                stdin=subprocess.DEVNULL)
        return result.returncode == 0 and bool(re.search(
            r'(?:java|openjdk) version "21(?:[.\"+-])', result.stdout + result.stderr))
    except (OSError, subprocess.SubprocessError):
        return False


def java21(preferred):
    """A BAT may be entered with Java25; its managed child still needs Java21."""
    preferred = safe(preferred)
    if probe_java21(preferred):
        return preferred
    homes = []
    for name in ('JAVA_HOME_21_X64', 'JDK21_HOME', 'JAVA_HOME'):
        if os.environ.get(name):
            homes.append(Path(os.environ[name]))
    homes.append(preferred.parent.parent)
    bases = [preferred.parent.parent.parent]
    for name in ('ProgramFiles', 'ProgramW6432'):
        if os.environ.get(name):
            bases.extend(Path(os.environ[name]) / suffix for suffix in (
                'Java', 'Eclipse Adoptium', 'Microsoft'))
    for base in bases:
        try:
            if base.is_dir():
                homes.extend(sorted(base.iterdir(), key=lambda p: p.name, reverse=True)[:64])
        except OSError:
            continue
    seen = set()
    for home in homes:
        try:
            home = safe(home)
            if home in seen:
                continue
            seen.add(home)
            release = safe(home / 'release')
            if not release.is_file() or release.stat().st_size > 32768:
                continue
            if not re.search(r'(?m)^JAVA_VERSION="21(?:[.\"+-])', release.read_text('utf-8')):
                continue
            candidate = home / 'bin/java.exe'
            if probe_java21(candidate):
                return candidate
        except (OSError, ValueError, UnicodeError):
            continue
    raise ValueError('SERVER104_JAVA21')


def batch(server, neoforge):
    script = safe(Path(server) / 'run.bat')
    if not script.is_file():
        raise ValueError('SERVER124_BATCH_REQUIRED')
    with script.open('rb') as source:
        raw = source.read(65537)
    if len(raw) > 65536:
        raise ValueError('SERVER124_BATCH_FORMAT')
    invocation = None
    for line in raw.decode('utf-8-sig', errors='replace').splitlines():
        text = line.strip()
        if not text or re.match(r'(?i)^@?rem(?:\s|$)', text) or text.startswith('::'):
            continue
        if re.fullmatch(r'(?i)@?echo\s+off|@?pause|@?cd\s+/d\s+"%~dp0"|@?exit\s+/b\s+%errorlevel%', text):
            continue
        normalized = text.replace('\\', '/')
        expected = (r'(?i)@?(?:java(?:\.exe)?|"%JAVA_HOME%/bin/java(?:\.exe)?")\s+'
                    r'@user_jvm_args\.txt\s+@libraries/net/neoforged/neoforge/' +
                    re.escape(neoforge) + r'/win_args\.txt(?:\s+(?:--)?nogui)?(?:\s+%\*)?')
        if invocation is not None or not re.fullmatch(expected, normalized):
            raise ValueError('SERVER124_BATCH_UNSUPPORTED')
        invocation = text
    if invocation is None:
        raise ValueError('SERVER124_BATCH_UNSUPPORTED')
    # CMD expands % even inside quotes; reject these filenames before building /C.
    if any(c in str(script) for c in '%"\r\n'):
        raise ValueError('SERVER124_BATCH_PATH')
    system = os.environ.get('SystemRoot', os.environ.get('SYSTEMROOT', r'C:\Windows'))
    cmd = safe(Path(system) / 'System32/cmd.exe')
    command = '""' + str(script) + '""'
    return dict(argv=[str(cmd), '/d', '/v:off', '/s', '/c', command],
                batchSha=hashlib.sha256(raw).hexdigest(), batch=str(script))


class WindowsServerJob124(WindowsJob):
    """A fixed /C invocation, with the entire CMD -> Java tree in one Job."""
    def __init__(self, argv, cwd, env, log):
        self.server_process124 = None
        self.batch124 = Path(argv[0]).name.lower() == 'cmd.exe'
        self.java124 = os.path.normcase(str(Path(env.get('JAVA_HOME', '')) / 'bin/java.exe'))
        super().__init__(argv, cwd, env, log)

    def launch_command(self, argv, cwd, env):
        if Path(argv[0]).name.lower() == 'java.exe':
            return super().launch_command(argv, cwd, env)
        expected = batch(cwd, env.get('GENESIS_SERVER_NEO124', ''))
        if argv != expected['argv'] or env.get('GENESIS_SERVER_BATCH124') != expected['batchSha']:
            raise ValueError('SERVER124_BATCH_CHANGED')
        module = safe(argv[0])
        if not module.is_file():
            raise ValueError('SERVER124_CMD_REQUIRED')
        # list2cmdline escapes inner quotes for the C runtime, not CMD's /S /C.
        command = subprocess.list2cmdline(argv[:-1]) + ' ' + argv[-1]
        return module, command

    def observe_java124(self):
        """Retain a HANDLE only after proving membership in our own newly created Job.

        CMD's PAUSE resets ERRORLEVEL. The wrapper's exit code therefore cannot
        attest successful JVM exit. A missed/ambiguous JVM fails closed instead.
        """
        import ctypes as C
        from ctypes import wintypes as W
        if self.server_process124 is not None:
            return
        self.k.OpenProcess.restype = W.HANDLE
        self.k.OpenProcess.argtypes = [W.DWORD, W.BOOL, W.DWORD]
        self.k.QueryFullProcessImageNameW.restype = W.BOOL
        self.k.QueryFullProcessImageNameW.argtypes = [W.HANDLE, W.DWORD, W.LPWSTR, C.POINTER(W.DWORD)]
        class ProcessList(C.Structure):
            _fields_ = [('assigned', W.DWORD), ('count', W.DWORD), ('pids', C.c_size_t * 4096)]
        owned = ProcessList()
        self.ok(self.k.QueryInformationJobObject(self.job, 3, C.byref(owned), C.sizeof(owned), None))
        if owned.count > 4096 or owned.assigned != owned.count:
            raise ValueError('SERVER124_JOB_PROCESS_LIMIT')
        matches = []
        try:
            for pid in owned.pids[:owned.count]:
                if pid == self.pid:
                    continue
                handle = self.k.OpenProcess(0x1000 | 0x100000, False, pid)
                if not handle:
                    continue
                keep = False
                try:
                    member = W.BOOL()
                    self.ok(self.k.IsProcessInJob(handle, self.job, C.byref(member)))
                    if not member.value:
                        continue
                    image = C.create_unicode_buffer(32768); size = W.DWORD(len(image))
                    self.ok(self.k.QueryFullProcessImageNameW(handle, 0, image, C.byref(size)))
                    if os.path.normcase(image.value) == self.java124:
                        matches.append(handle); keep = True
                finally:
                    if not keep:
                        self.k.CloseHandle(handle)
            if len(matches) > 1:
                raise ValueError('SERVER124_JAVA_AMBIGUOUS')
            if matches:
                self.server_process124 = matches.pop()
        finally:
            for handle in matches:
                self.k.CloseHandle(handle)

    def status(self):
        if self.closed:
            raise RuntimeError('Owner closed')
        if not self.batch124:
            return super().status()
        self.observe_java124()
        state = super().status()
        state['wrapperExitCode'] = state['exitCode']
        if self.server_process124 is None:
            state['exitCode'] = -1 if state['rootExited'] else None
        elif state['rootExited']:
            code = self.W.DWORD()
            self.ok(self.k.GetExitCodeProcess(self.server_process124, __import__('ctypes').byref(code)))
            state['exitCode'] = code.value
        return state

    def close(self):
        try:
            super().close()
        finally:
            if self.server_process124 is not None:
                self.k.CloseHandle(self.server_process124)
                self.server_process124 = None
