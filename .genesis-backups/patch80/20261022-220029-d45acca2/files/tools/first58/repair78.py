"""Finish ONLY the final registration of an already complete, claimed77 copy.
Never recopy a world, replay a commit, create a second successor or start any process.
"""
from pathlib import Path
import contextlib
from . import dedicated74 as base,dedicated77 as old
from .common import safe,sha,inventory,json_write
from .bridge67 import read,write
from .dedicated75 import History75
from .install70 import policy,files


def inspect(root):
 root=safe(root);s,key=base.settings(root)
 if not (root/'recovered77').exists():return False
 p=read(root/'recovered77',key)
 if p.get('run')!=s['run'] or p.get('schema')!='77' or not p.get('targetRoot'):return False
 target=safe(Path(p['targetRoot']))
 return (target/'import77.pending').is_file() and not (target/'started74').exists()


def repair(project,root,java):
 root=safe(root);project=safe(project)
 with old.owner_lease(root):
  s,key=base.settings(root);claim=read(root/'recovered77',key)
  if set(claim)!={'schema','run','targetRun','targetRoot','generation','ticket'} or claim['schema']!='77' or claim['run']!=s['run']:raise ValueError('RECOVERY78_CLAIM')
  target=safe(Path(claim['targetRoot']))
  if root==target or root in target.parents or target in root.parents:raise ValueError('RECOVERY78_ROOTS')
  with old.owner_lease(target):
   if (target/'started74').exists():raise ValueError('RECOVERY78_ALREADY_STARTED')
   ns,nk=base.settings(target)
   for version in (75,76,77):
    if read(target/f'owner{version}',nk)!=dict(schema=str(version),run=ns['run']):raise ValueError('RECOVERY78_OWNER')
   if (target/'control73').exists() or list(target.glob('server-*.status')):raise ValueError('RECOVERY78_RUNTIME_HISTORY')
   p=read(target/'recovery77',nk);g=int(claim['generation'])
   if set(p)!={'schema','run','sourceRun','generation','release','label','ticket','inventory','profile','kind'} or p['schema']!='77' or p['run']!=ns['run'] or claim['targetRun']!=ns['run'] or p['sourceRun']!=s['run'] or any(p[k]!=claim[k] for k in ('generation','ticket')) or ns.get('initialGeneration',0)!=g or ns['source']!=str(root/f'generations/server-{g}'):raise ValueError('RECOVERY78_BINDING')
   selected,kind=old.selection(root,s,key,g)
   if kind!=p['kind'] or selected['profile']!=p['profile'] or selected['release']!=p['release'] or selected['label']!=p['label'] or old.ticket(root,s,key,g,old.generations(root),selected)!=p['ticket']:raise ValueError('RECOVERY78_SOURCE_CHANGED')
   if old.generations(target)!=[g]:raise ValueError('RECOVERY78_GENERATIONS')
   profile=target/f'generations/server-{g}';source=root/f'generations/server-{g}'
   with base.stopped_world(source,s['level']):
    with base.stopped_world(profile,ns['level']) if (profile/ns['level']/'session.lock').exists() else contextlib.nullcontext():
     # No started74 marker and a complete copy are mandatory; an absent lock file in an unstarted copy is normal.
     if base.digest(base.tree(source,s['level']))!=p['profile'] or base.digest(base.tree(profile,ns['level']))!=p['profile'] or inventory(profile)!=p['inventory'] or ns['initialInventory']!=p['inventory']:raise ValueError('RECOVERY78_COPY_CHANGED')
     session=read(target/f'server-{g}.session',nk)
     if session!=dict(schema='74',run=ns['run'],generation=str(g),inventory=p['inventory'],minecraft=ns['minecraft'],neoforge=ns['neoforge'],auth=ns['auth'],bind=ns['bind'],port=ns['port']):raise ValueError('RECOVERY78_SESSION')
     for name in ('public.der','private.der'):
      if sha(target/'publisher73'/name,256)!=sha(root/'publisher73'/name,256):raise ValueError('RECOVERY78_IDENTITY')
     history=History75(target,nk,ns['run'])
     if len(history.rows)!=1 or any(history.rows[0][k]!=v for k,v in dict(phase='RECOVERED_COPY',source=str(g),target=str(g),release=p['release'],inventory=p['inventory']).items()):raise ValueError('RECOVERY78_HISTORY')
     if p['release']!='none':
      folder=target/'releases'/base.label(p['label']);info=base.core(project,java,'verify',folder,target/'publisher73/public.der')
      if info['release']!=p['release'] or base.digest(base.tree(folder,'unused'))!=selected['pair'] or policy(profile/'mods',folder/'server.zip',info)['after']!=files(profile/'mods'):raise ValueError('RECOVERY78_RELEASE')
      if read(target/'publication75',nk)!=dict(schema='75',run=ns['run'],label=p['label'],release=p['release']):raise ValueError('RECOVERY78_PUBLICATION')
     ready=dict(schema='77',run=ns['run'],ticket=p['ticket'])
     if (target/'recovery77.ready').exists() and read(target/'recovery77.ready',nk)!=ready:raise ValueError('RECOVERY78_READY_CONFLICT')
     from .https78 import inherit
     inherit(root,target)
     write(target/'recovery77.ready',nk,ready)
     json_write(target/'recovery77-report.json',dict(schema=78,phase='READY_TO_START',root=str(target),generation=g,sourceProfileModified=False,oldCohortCopied=False,repairedRegistration=True))
     (target/'import77.pending').unlink(missing_ok=True)
     return dict(schema=78,root=str(target),generation=g,release=p['release'],started=False,repairedRegistration=True)
