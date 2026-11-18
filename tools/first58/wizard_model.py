"""Setup decisions, separate from Tk and from the managed lifecycle.
Only reads selected templates; staging and controller state live under a new root.
"""
from pathlib import Path
import ctypes as C
import json
import os
import re
import shutil
import socket
import subprocess
import uuid
import zipfile
from .common import safe, sha, json_write
from .setup import validate_spec, java

class SetupError(ValueError):
    pass

def option(argv, name):
    positions = [i for i, value in enumerate(argv) if value == name]
    if len(positions) != 1 or positions[0] + 1 >= len(argv):
        raise SetupError('Не удалось определить ' + name + '. Запусти обычный клиент через лаунчер и открой мастер снова.')
    return argv[positions[0] + 1]

def account_id(text):
    try:
        value = uuid.UUID(text)
    except (ValueError, TypeError, AttributeError):
        raise SetupError('Лаунчер не передал UUID аккаунта. Нужен клиент с входом в online-аккаунт.') from None
    if value.int == 0:
        raise SetupError('Пустой UUID аккаунта. Войди в online-аккаунт через лаунчер.')
    return str(value)

def client_details(argv, profile, account=None, neoforge=None, auth_mode="ONLINE", dev_mods=""):
    profile = safe(profile)
    if not profile.is_dir() or not (profile / 'mods').is_dir():
        raise SetupError('У клиента не найдена папка mods. Запусти установленный NeoForge через лаунчер.')
    if any(a.startswith(('-javaagent', '-agentlib', '-agentpath', '-Dfml.modFolders')) for a in argv):
        raise SetupError('Этот dev-запуск содержит agent или внешние папки модов. Открой мастер в обычном установленном клиенте.')
    if auth_mode not in {'ONLINE','LOCAL_OFFLINE'}:raise SetupError('Неизвестный режим авторизации.')
    if auth_mode=='ONLINE' and option(argv, '--accessToken').lower() in {'0', 'null', 'none', 'offline'}:
        raise SetupError('Нужен клиент с действующим входом в online-аккаунт через лаунчер.')
    captured_account = account_id(option(argv, '--uuid'))
    if account is not None and captured_account != account_id(account):
        raise SetupError('Аккаунт команды запуска отличается от аккаунта игры. Открой мастер заново.')
    neo = neoforge or option(argv, '--fml.neoForgeVersion')
    if not re.fullmatch(r'21\.1\.\d+', neo):
        raise SetupError('Нужен NeoForge 21.1.x для Minecraft 1.21.1.')
    if '--fml.neoForgeVersion' in argv and option(argv, '--fml.neoForgeVersion') != neo:
        raise SetupError('Версия NeoForge команды запуска отличается от загруженной.')
    if any(not a or '\x00' in a or '\r' in a or '\n' in a for a in argv):
        raise SetupError('Неподдерживаемая команда запуска клиента.')
    result={'profile': str(profile), 'account': captured_account, 'neoforge': neo, 'argv': list(argv)}
    if auth_mode!='ONLINE':result['authMode']=auth_mode
    if dev_mods:
        from .launch62 import dev_groups
        dev_groups(dev_mods)
        result['devModClasses']=dev_mods
    if '--launchTarget' in argv and option(argv,'--launchTarget').endswith('dev') and not dev_mods:
        raise SetupError('Не получен MOD_CLASSES. Пересобери MaRed с патчем62 и открой мастер заново из VSCode.')
    return result

def server_details(folder, client):
    folder = safe(folder)
    base = safe(folder / 'libraries/net/neoforged/neoforge')
    versions = sorted(p.name for p in base.iterdir() if p.is_dir() and (p / 'win_args.txt').is_file()) if base.is_dir() else []
    version = client['neoforge']
    if version not in versions:
        raise SetupError('Клиент: NeoForge ' + version + '. В выбранном сервере найдены: ' + (', '.join(versions) or 'ни одной версии') + '. Версии клиента и сервера должны совпадать.')
    eula = folder / 'eula.txt'
    if not eula.is_file() or not re.search(r'(?m)^eula=true\s*$', eula.read_text(encoding='utf-8-sig')):
        raise SetupError('Сначала прими EULA сервера самостоятельно и один раз запусти его через run.bat.')
    if not (folder / 'mods').is_dir() or not (folder / 'server.properties').is_file():
        raise SetupError('Сначала один раз запусти этот сервер через run.bat, затем останови командой stop.')
    if not (folder / 'user_jvm_args.txt').is_file():
        raise SetupError('Нет user_jvm_args.txt. Выбери корневую папку установленного NeoForge-сервера.')
    return [client['argv'][0], '@user_jvm_args.txt', '@libraries/net/neoforged/neoforge/' + version + '/win_args.txt', 'nogui']

def properties(folder):
    out = {}
    for line in (safe(folder) / 'server.properties').read_text(encoding='utf-8-sig').splitlines():
        if not line.lstrip().startswith(('#', '!')) and '=' in line:
            key, value = line.split('=', 1)
            out[key.strip()] = value.strip()
    return out

def port_available(port):
    try:
        with socket.socket() as sock:
            if os.name == 'nt':
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            sock.bind(('0.0.0.0', port))
    except OSError:
        raise SetupError('Порт ' + str(port) + ' занят. Останови исходный сервер командой stop и повтори.') from None

def check_stopped(server, client_profile):
    data = properties(server)
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', data.get('level-name', 'world')):
        raise SetupError('Для первого теста имя папки мира должно состоять из латинских букв, цифр, _ или -.')
    try:
        port = int(data.get('server-port', '25565'))
        if not 1 <= port <= 65535:
            raise ValueError()
    except ValueError:
        raise SetupError('Неправильный server-port в server.properties.') from None
    port_available(port)
    # Existing Minecraft world locks must be obtainable, never removed or truncated.
    if os.name == 'nt':
        import msvcrt
        roots = [safe(server) / data.get('level-name', 'world'), safe(client_profile) / 'saves']
        for root in roots:
            if root.is_dir():
                for lock in root.rglob('session.lock'):
                    lock = safe(lock)
                    try:
                        with lock.open('r+b') as stream:
                            msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
                            msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
                    except OSError:
                        raise SetupError('Мир ещё используется игрой или сервером. Закрой их штатно и повтори.') from None

def mod_files(folder, allow_empty=False):
    folder = safe(folder)
    if not folder.is_dir():
        raise SetupError('Папка модов не найдена.')
    jars = {}
    for p in folder.iterdir():
        if p.suffix.lower() != '.jar':
            continue
        p = safe(p)
        if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]{0,119}\.jar', p.name) or '..' in p.name:
            raise SetupError('Переименуй JAR латиницей без пробелов: ' + p.name)
        if not p.is_file():
            raise SetupError('В наборе модов найден неподдерживаемый файл.')
        if p.name.lower() in {name.lower() for name in jars}:
            raise SetupError('Два JAR различаются только регистром имени. Оставь один файл.')
        jars[p.name] = sha(p, 64 * 1024 * 1024)
    if (not jars and not allow_empty) or len(jars) > 256 or sum((folder / n).stat().st_size for n in jars) > 256 * 1024 * 1024:
        raise SetupError('Набор должен содержать 1–256 JAR, до 64 МиБ каждый и до 256 МиБ суммарно.')
    return jars

def has_adapter(folder, role, require62=False, require67=False):
    needle = 'com/fixmer/mared/technology/server/firstlaunch/FirstLaunch' + ('Server' if role == 'server' else 'Client') + '58.class'
    found = False
    for p in safe(folder).glob('*.jar'):
        try:
            with zipfile.ZipFile(safe(p)) as z:
                if needle in z.namelist():
                    if z.getinfo(needle).file_size>4*1024*1024:raise SetupError('Слишком большой класс адаптера MaRed.')
                    if require62 and b'verifyServerMode' not in z.read(needle):
                        raise SetupError('Обнови MaRed JAR в серверном наборе до сборки с патчем62: старый адаптер требует online-mode=true.')
                    if require67 and b'OFFER67' not in z.read(needle):raise SetupError('Обнови MaRed JAR в сервере до сборки с патчем67: нужен адаптер повторных обновлений.')
                    found = True
        except zipfile.BadZipFile:
            raise SetupError('Повреждённый JAR в наборе модов: ' + p.name) from None
    if not found:
        raise SetupError('В ' + role + ' наборе нет MaRed с поддержкой обновления. Добавь свежий собранный JAR MaRed.')
    return True

def changes(previous, following):
    return {'added': sorted(following.keys() - previous.keys()),
            'removed': sorted(previous.keys() - following.keys()),
            'changed': sorted(n for n in previous.keys() & following.keys() if previous[n] != following[n])}

def plan(server, client, next_server='', next_client='', port=27690):
    server = safe(server)
    command = server_details(server, client)
    if not 1024 <= port <= 65535:
        raise SetupError('Выбери порт от 1024 до 65535.')
    port_available(port)
    sources = {'server': safe(next_server) if next_server else server / 'mods',
               'client': safe(next_client) if next_client else safe(client['profile']) / 'mods'}
    reports = {}
    for role in ('server', 'client'):
        baseline = server / 'mods' if role == 'server' else safe(client['profile']) / 'mods'
        dev = role=='client' and bool(client.get('devModClasses'))
        old = mod_files(baseline,allow_empty=dev)
        new = mod_files(sources[role],allow_empty=dev)
        if dev:
            from .launch62 import dev_groups
            if not any((Path(source)/'com/fixmer/mared/technology/server/firstlaunch/FirstLaunchClient58.class').is_file() for group in dev_groups(client['devModClasses']).values() for source in group):
                raise SetupError('В dev-классах отсутствует скомпилированный адаптер MaRed.')
            for folder in (baseline,sources[role]):
                for jar in folder.glob('*.jar'):
                    if 'mared' in mod_ids(jar):raise SetupError('Убери второй MaRed JAR из клиентского mods: MaRed уже загружается из dev-классов.')
        else:
            required=role=='server' and client.get('authMode')=='LOCAL_OFFLINE'
            has_adapter(baseline, role, require62=required)
            has_adapter(sources[role], role, require62=required)
        reports[role] = changes(old, new)
    spec = {'serverTemplate': str(server), 'clientTemplate': client['profile'],
            'nextServerMods': str(sources['server']), 'nextClientMods': str(sources['client']),
            'serverCommand': command, 'clientCommand': client['argv'], 'account': client['account'],
            'minecraft': '1.21.1', 'neoforge': client['neoforge'], 'port': port}
    for field in ('authMode','devModClasses'):
        if field in client:spec[field]=client[field]
    validate_spec(spec)
    return spec, reports

def workspace_parent():
    if os.name != 'nt' or not os.environ.get('LOCALAPPDATA'):
        raise SetupError('Мастер первого запуска поддерживает Windows x64.')
    return safe(Path(os.environ['LOCALAPPDATA']) / 'Genesis/server-tests')

def prepare(project, spec, parent, stopped_check=check_stopped, replacements=None):
    """Every attempt gets a new private root; never reuses an interrupted run."""
    from .setup import initialize
    project = safe(project)
    spec = dict(spec)
    validate_spec(spec)
    stopped_check(spec['serverTemplate'], spec['clientTemplate'])
    port_available(spec['port'])
    parent = safe(parent)
    # Before creating staging data, reject nesting into any source profile/mod set.
    for field in ('serverTemplate', 'clientTemplate', 'nextServerMods', 'nextClientMods'):
        source = safe(spec[field])
        if source == parent or source in parent.parents or parent in source.parents:
            raise SetupError('Папка тестов должна находиться отдельно от профилей и наборов модов.')
    parent.mkdir(parents=True, exist_ok=True)
    attempt = parent / ('test-' + uuid.uuid4().hex)
    attempt.mkdir()
    java(project, spec['serverCommand'][0], 'private', attempt)
    for role, replacement in (replacements or {}).items():
        if role not in ('server', 'client'):
            raise SetupError('Unknown replacement side')
        destination = attempt / ('next-' + role + '-mods')
        stage_replacement(spec['next' + role.title() + 'Mods'], replacement, destination)
        spec['next' + role.title() + 'Mods'] = str(destination)
    # Secrets are only written after the private ACL succeeded.
    if spec.get('devModClasses'):
        from .launch62 import snapshot
        from .runtime import core_classpath
        core=core_classpath(project)
        if not core.is_file():raise SetupError('Открой мастер из игры: для dev-снимка нужен комплект с core.jar.')
        source=attempt/'dev-snapshot'
        frozen=snapshot(spec,source,core)
        spec['clientCommand']=frozen.pop('argv')
        spec['devSnapshot']={**frozen,'source':str(source)}
    launch = attempt / 'settings.json'
    json_write(launch, spec, new=True)
    root = attempt / 'run'
    initialize(project, root, launch)
    return root

class ClientLease:
    """Retain an identity-bound, read-only wait handle; never stop/adopt this process."""
    def __init__(self, pid):
        if os.name != 'nt':
            raise SetupError('Нужна Windows x64.')
        from ctypes import wintypes as W
        self.kernel = C.WinDLL('kernel32', use_last_error=True)
        self.kernel.OpenProcess.argtypes = [W.DWORD, W.BOOL, W.DWORD]
        self.kernel.OpenProcess.restype = W.HANDLE
        self.kernel.WaitForSingleObject.argtypes = [W.HANDLE, W.DWORD]
        self.kernel.WaitForSingleObject.restype = W.DWORD
        self.kernel.CloseHandle.argtypes = [W.HANDLE]
        self.kernel.CloseHandle.restype = W.BOOL
        self.handle = self.kernel.OpenProcess(0x100000, False, pid)
        if not self.handle:
            raise SetupError('Исходный клиент уже закрыт. Открой мастер из игры заново.')
    def closed(self):
        result = self.kernel.WaitForSingleObject(self.handle, 0)
        if result not in (0, 258):
            raise SetupError('Не удалось проверить завершение исходного клиента.')
        return result == 0
    def close(self):
        if self.handle:
            self.kernel.CloseHandle(self.handle)
            self.handle = None

def mod_ids(jar):
    """Identify a replacement by declared mod IDs, never by a guessed filename."""
    try:
        import tomllib
    except ImportError:
        raise SetupError('Для выбора одного JAR требуется Python 3.11 или новее.') from None
    with zipfile.ZipFile(safe(jar)) as archive:
        name = next((n for n in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml') if n in archive.namelist()), None)
        if name is None:
            return frozenset()
        if archive.getinfo(name).file_size > 1024 * 1024:
            raise SetupError('Слишком большой файл метаданных мода.')
        try:
            data = tomllib.loads(archive.read(name).decode('utf-8-sig'))
        except (ValueError, UnicodeError):
            raise SetupError('Не удалось прочитать метаданные мода: ' + Path(jar).name) from None
    mods = data.get('mods', [])
    if not isinstance(mods, list):
        raise SetupError('Некорректный список mods в метаданных JAR.')
    ids = frozenset(m.get('modId') for m in mods if isinstance(m, dict))
    if any(not isinstance(i, str) or not re.fullmatch('[a-z][a-z0-9_]{1,63}', i) for i in ids):
        raise SetupError('Некорректный modId в метаданных JAR.')
    return ids

def replacement_inventory(folder, replacement):
    before = mod_files(folder,allow_empty=True)
    replacement = safe(replacement)
    if not replacement.is_file() or replacement.suffix != '.jar' or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]{0,119}\.jar', replacement.name) or '..' in replacement.name:
        raise SetupError('Выбери обычный JAR с именем латиницей без пробелов.')
    new_ids = mod_ids(replacement)
    if not new_ids:
        raise SetupError('У выбранного JAR нет метаданных NeoForge-мода.')
    if new_ids & {'mared', 'minecraft', 'neoforge'}:
        raise SetupError('В первом тесте выбери небольшой собственный мод. Обновление MaRed или загрузчика здесь не поддерживается.')
    matches = []
    for name in before:
        ids = mod_ids(safe(folder) / name)
        if new_ids & ids:
            if ids != new_ids:
                raise SetupError('JAR меняет состав modId. Используй явно подготовленный полный набор модов.')
            matches.append(name)
    if len(matches) > 1:
        raise SetupError('Найдены несколько JAR одного мода. Удали дубликаты в остановленном исходном профиле.')
    after = dict(before)
    for name in matches:
        del after[name]
    if replacement.name.lower() in {n.lower() for n in after}:
        raise SetupError('Имя нового JAR занято другим модом. Переименуй новый файл.')
    after[replacement.name] = sha(replacement, 64 * 1024 * 1024)
    return before, after

def stage_replacement(folder, replacement, destination):
    before, after = replacement_inventory(folder, replacement)
    destination = safe(destination)
    destination.mkdir()
    for name in after:
        source = safe(replacement) if name == Path(replacement).name else safe(folder) / name
        with source.open('rb') as stream, (destination / name).open('xb') as out:
            shutil.copyfileobj(stream, out, 1024 * 1024)
    if mod_files(destination,allow_empty=True) != after or mod_files(folder,allow_empty=True) != before:
        raise SetupError('Набор модов изменился во время подготовки. Исходные файлы не заменялись; повтори с остановленной сборкой.')
    return changes(before, after)
