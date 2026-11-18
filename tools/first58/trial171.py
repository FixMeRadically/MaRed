"""One-run server world and signed return publication, journaled under the folder owner."""
import contextlib
import re
import time
import uuid
from pathlib import Path

from . import dedicated74 as base, publication83
from .bridge67 import read, write
from .changes104 import Changes104
from .common import atomic, inventory, safe, sha
from .install70 import policy

MARKER = 'trial171'

def running(root):
    return (root / MARKER).is_file()

def switch_level(server, expected, target):
    path = safe(server / 'server.properties')
    original = path.read_bytes()
    text = original.decode('utf-8-sig')
    if base.properties(server)['level-name'] != expected:
        raise ValueError('TRIAL171_WORLD_CHANGED')
    pattern = r'(?m)^([ \t]*level-name[ \t]*=[ \t]*)[^\r\n]*'
    text, matches = re.subn(pattern, lambda m: m[1] + target, text)
    if matches == 0:
        text = text.rstrip('\r\n') + '\nlevel-name=' + target + '\n'
    if matches > 1 or path.read_bytes() != original:
        raise ValueError('TRIAL171_WORLD_CHANGED')
    encoded = text.encode('utf-8-sig' if original.startswith(b'\xef\xbb\xbf') else 'utf-8')
    atomic(path, encoded)

def begin(owner, plan):
    server, root, key = owner.server, owner.root, owner.key
    old_level = owner.c['level']
    if not plan['change'] or not plan['prior'] or not (server / old_level).is_dir():
        raise ValueError('TRIAL171_SERVER_MOD_AND_WORLD_REQUIRED')
    if any((server / (old_level + suffix)).exists() for suffix in ('_nether', '_the_end')):
        raise ValueError('TRIAL171_SPLIT_WORLD')
    token = uuid.uuid4().hex
    level, label = 'trial-' + token, 'return-' + token
    releases = root / 'releases'
    baseline = releases / base.label(plan['prior']['label'])
    info = base.core(owner.app, owner.c['java'], 'verify-history', baseline, root / 'publisher73/public.der')
    verify_current(server, baseline, info, plan['prior']['release'])
    write(root / MARKER, key, dict(schema='171', profile=str(server), run=owner.run,
        original=old_level, trial=level, returnLabel=label,
        priorLabel=plan['prior']['label'], trialLabel=plan['label'],
        oldInventory=plan['old'], newInventory=plan['new'], change=plan['change'], seal=plan['codeSeal']))
    base.copy_tree(server / old_level, server / level, '__trial__', checkpoint=owner.report, raw=True)
    switch_level(server, old_level, level)
    owner.c['level'] = level
    set_level(root, key, level)

def set_level(root, key, level):
    record = read(root / 'settings118', key)
    write(root / 'settings118', key, dict(record, level=level))

def verify_current(server, pair, info, release):
    current = policy(server / 'mods', pair / 'server.zip', info)
    if info['release'] != release or current['before'] != current['after']:
        raise ValueError('TRIAL171_RETURN_INVENTORY')

def restore(server, root, key, app, java):
    # Call with the owner and game leases held and no retained game process.
    m = read(root / MARKER, key)
    if (m.get('schema') != '171' or m.get('profile') != str(server) or
        not re.fullmatch(r'trial-[0-9a-f]{32}', m.get('trial', '')) or
        m['returnLabel'] != 'return-' + m['trial'][6:] or
        str(uuid.UUID(m['change'])) != m['change'] or
        not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', m.get('original', ''))):
        raise ValueError('TRIAL171_JOURNAL')
    level = base.properties(server)['level-name']
    if level not in {m['original'], m['trial']} or not safe(server / m['original']).is_dir():
        raise ValueError('TRIAL171_WORLD_CHANGED')
    review = root / 'review104'
    if review.exists():
        record = read(review, key)
        if record.get('schema') != '104' or record.get('reason') not in {
            'FOLDER118_INTERRUPTED_ROUND', 'FOLDER118_TRANSITION_REVIEW', 'TRIAL171_REVIEW'}:
            raise ValueError('TRIAL171_REVIEW')
    owner = read(root / 'owner104', key)
    if owner.get('run') != m['run']:
        raise ValueError('TRIAL171_OWNER_CHANGED')
    releases = root / 'releases'
    result = publication83.command(app, root,
        'com.fixmer.genesis.technology.server.remote.Renewal85', 'trial-return',
        root / 'publisher73', releases / base.label(m['priorLabel']),
        releases / base.label(m['trialLabel']), releases, m['returnLabel'])
    release = result['release']
    pair = releases / m['returnLabel']
    info = base.core(app, java, 'verify-history', pair, root / 'publisher73/public.der')
    if info['release'] != release:
        raise ValueError('TRIAL171_RETURN_INVENTORY')
    changes = Changes104(server, root, key)
    with contextlib.ExitStack() as locks:
        locks.enter_context(base.stopped_world(server, level))
        if level!=m['original']:
            locks.enter_context(base.stopped_world(server, m['original']))
        loaded = changes.load()
        if loaded:
            if (loaded[0]['id'], loaded[0]['plan']) != (m['change'], m['seal']):
                raise ValueError('TRIAL171_CHANGE')
            if loaded[0]['phase'] == 'COMMITTED':
                changes.clear_terminal()
                loaded = None
        if not loaded and inventory(server) != m['oldInventory']:
            folder = safe(root / 'changes' / m['change'])
            if (inventory(server) != m['newInventory'] or sha(folder / 'plan.json', 262144) != m['seal'] or
                (folder / 'result104').read_bytes() != b'COMMITTED\n'):
                raise ValueError('TRIAL171_CHANGE')
            write(root / 'change104', key, dict(schema='104', id=m['change'], plan=m['seal'], phase='TRIAL'))
            loaded = changes.load()
        if loaded:
            if loaded[0]['phase'] != 'RESTORED':
                changes.restore()
            changes.finish(False)
        if inventory(server) != m['oldInventory']:
            raise ValueError('TRIAL171_RETURN_INVENTORY')
        verify_current(server, pair, info, release)
        if (root / 'control73/active73').exists():
            base.core(app, java, 'close', root, timeout=15)
        if level != m['original']:
            switch_level(server, m['trial'], m['original'])
        set_level(root, key, m['original'])
        write(root / 'publication75', key, dict(schema='75', run=m['run'],
              label=m['returnLabel'], release=release))
        write(root / 'rollout118', key, dict(schema='118', run=m['run'],
              phase='CLOSED', reason='TRIAL171_RETURNED', label=m['returnLabel'],
              serial='0', at=str(int(time.time()*1000))))
        for name in ('prepared74', 'clientonly118', 'admission82.local', 'bootstrap76.local', 'bootstrap76.state'):
            (root / name).unlink(missing_ok=True)
        review.unlink(missing_ok=True)
        (root / MARKER).unlink()
    return m['original']
