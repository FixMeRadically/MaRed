"""Cancel only unchanged, uncommitted preparation before bootstrap setup. No world/JAR replacement."""
import sys, re, socket
from pathlib import Path
from contextlib import ExitStack
from first58.inplace104 import identity, lease, old
from first58.changes104 import Changes104
from first58.bridge67 import read
from first58.common import safe, sha, inventory
from first58.folder118 import recover118, TERMINAL

def cancel(server, app, java):
    server = safe(server); app = safe(app); java = safe(java)
    root, key = identity(server)
    with ExitStack() as stack:
        for name in ('start104.lock', 'owner104.lock', 'game104.lock', 'prepare118.lock'):
            stack.enter_context(lease(root / name))
        props = old.properties(server)
        level = props['level-name']
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', level) or level.lower() in {'mods','config','libraries','.genesis'}:
            raise ValueError('SERVER104_WORLD_NAME')
        stack.enter_context(old.stopped_world(server, level))
        bind = props['server-ip'].strip()
        if bind and not re.fullmatch(r'[0-9a-fA-F:.]+', bind):
            raise ValueError('SERVER107_BIND_REVIEW')
        port = int(props['server-port'])
        if not 1 <= port <= 65535:
            raise ValueError('SERVER107_PORT')
        sock = stack.enter_context(socket.socket(socket.AF_INET6 if ':' in bind else socket.AF_INET))
        if sys.platform == 'win32':
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        sock.bind((bind or '0.0.0.0', port))
        changes = Changes104(server, root, key)
        loaded = changes.load()
        if not loaded:
            print('No pending change; nothing changed.')
            return
        d, plan, folder = loaded
        if d['phase'] != 'PREPARED':
            raise ValueError('RECOVERY_TRANSACTION_OR_PHASE_CHANGED')
        if plan['profile'] != str(server) or changes.inventory() != plan['before']:
            raise ValueError('RECOVERY_MODS_CHANGED')
        review = root / 'review104'
        archive = folder / 'review104-before-cancel'
        transition = False
        if review.exists():
            r = read(review, key)
            if r.get('schema') != '104' or r.get('reason') not in {'INTERRUPTED_CHANGE','FOLDER118_INTERRUPTED_ROUND','FOLDER118_TRANSITION_REVIEW'}:
                raise ValueError('RECOVERY_OTHER_REVIEW_REQUIRED')
            transition = r['reason'] == 'FOLDER118_TRANSITION_REVIEW'
        if review.exists() and archive.exists():
            raise ValueError('RECOVERY_REVIEW_ARCHIVE_EXISTS')
        if transition:
            rollout = read(root / 'rollout118', key)
            binding_path = root / 'rollout118.binding'
            if (rollout.get('schema'), rollout.get('phase'), rollout.get('reason')) != ('118','FAILED','FOLDER118_SOURCE_EXIT'):
                raise ValueError('RECOVERY_TRANSITION_NOT_SUPPORTED')
            if rollout.get('binding') != sha(binding_path, 16384):
                raise ValueError('FOLDER118_RECOVERY_BINDING')
            binding = read(binding_path, key)
            if binding.get('change') != d['id'] or binding.get('codeSeal') != d['plan'] or binding.get('oldInventory') != inventory(server):
                raise ValueError('FOLDER118_RECOVERY_BINDING')
            recover118(app, server, java)
        else:
            if (root / 'control73/active73').exists():
                state = old.core(app, java, 'inspect', root, timeout=15)
                if state.get('phase') not in {'PREPARING','CANCELLED','CLOSED'}:
                    raise ValueError('RECOVERY_COMMITTED_ROUND_REQUIRES_REVIEW')
                if not (root / 'rollout118').exists():
                    raise ValueError('RECOVERY_ROUND_BINDING_REQUIRED')
            if (root / 'rollout118').exists():
                rollout = read(root / 'rollout118', key)
                if rollout.get('schema') != '118':
                    raise ValueError('FOLDER118_RECOVERY_RECORD')
                if rollout.get('phase') not in TERMINAL:
                    if rollout.get('phase') != 'PREPARED':
                        raise ValueError('RECOVERY_ROUND_REQUIRES_REVIEW')
                    recover118(app, server, java)
                elif (root / 'control73/active73').exists():
                    raise ValueError('RECOVERY_ROUND_REQUIRES_REVIEW')
        changes.restore()
        changes.finish(False)
        if review.exists():
            archive = folder / 'review104-before-cancel'
            if archive.exists():
                raise ValueError('RECOVERY_REVIEW_ARCHIVE_EXISTS')
            review.rename(archive)
        print('PREPARED cancelled. Existing mods preserved; history retained.')

