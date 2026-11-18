"""Local publication from two complete mod sets; read-only preview before signing."""
from pathlib import Path
import json,re,subprocess
from . import dedicated74 as base
from .bridge67 import read
from .common import safe
from .runtime import core_classpath

def baseline(root,key,fallback=None,project=None):
 # Prefer the exact owner-selected publication; never search for arbitrary unsigned folders.
 if (root/'publication75').is_file():
  d=read(root/'publication75',key);s,_=base.settings(root)
  if set(d)!={'schema','run','label','release'} or d['schema']!='75' or d['run']!=s['run']:raise ValueError('PUBLICATION79_BASELINE')
  return safe(root/'releases'/base.label(d['label'])/'public'),d['release']
 if (root/'recovery77').is_file():
  d=read(root/'recovery77',key);s,_=base.settings(root)
  if d.get('schema')!='77' or d.get('run')!=s['run']:raise ValueError('PUBLICATION79_BASELINE')
  if d['label']!='none':return safe(root/'releases'/base.label(d['label'])/'public'),d['release']
 from .https78 import load
 _,_,config=load(root)
 if config:return safe(Path(config['public'])),None
 if fallback:return safe(Path(fallback)),None
 if project is not None and (root/'publisher73/identity83').is_file():
  from .publication83 import call
  result=call(project,root,'baseline',root/'publisher73',root/'releases')
  if result['path']!='none':return safe(Path(result['path'])/'public'),None
 raise ValueError('PUBLICATION79_BASELINE_REQUIRED')

def available(root,key):
 if (root/'recovered77').exists():raise ValueError('PUBLICATION79_RECOVERED_ROOT')
 if (root/'import77.pending').exists():raise ValueError('PUBLICATION79_INCOMPLETE_ROOT')
 if not (root/'view75').exists():return
 d=read(root/'view75',key)
 try:control=json.loads(d.get('control','{}'))
 except ValueError:raise ValueError('PUBLICATION79_OWNER_VIEW') from None
 if d.get('busy')=='true' or control.get('phase')=='COMMITTED':raise ValueError('PUBLICATION79_UPDATE_BUSY')

def run(project,root,client,server,label,ttl,ticket=None,seed_override=None):
 root=safe(root);s,key=base.settings(root);available(root,key)
 base.label(label);seed,release=baseline(root,key,seed_override,project)
 client=safe(client);server=safe(server)
 # Do not use a live managed generation as the editable next-mods source.
 for folder in (client,server):
  if root/'generations'==folder or root/'generations' in folder.parents:raise ValueError('PUBLICATION79_LIVE_SOURCE')
 if ticket and not re.fullmatch('[a-f0-9]{64}',ticket):raise ValueError('PUBLICATION79_TICKET')
 args=[s['java'],'-XX:-UsePerfData','-cp',str(core_classpath(project)),'com.fixmer.genesis.technology.server.remote.Publication79','author' if ticket else 'preview',str(root/'publisher73'),str(seed),str(client),str(server),str(root/'releases'),label,str(ttl)]
 if ticket:
  args.append(ticket)
 # Verify baseline binding/version/address BEFORE consuming a publication number.
 proof=subprocess.run([s['java'],'-XX:-UsePerfData','-cp',str(core_classpath(project)),'com.fixmer.genesis.technology.server.remote.PublicationStatus79',str(seed)],capture_output=True,text=True,timeout=180,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
 if proof.returncode:raise ValueError('PUBLICATION79_BASELINE')
 info=json.loads(proof.stdout)
 if release is not None and info['release']!=release or info['minecraft']!=s['minecraft'] or info['neoforge']!=s['neoforge'] or int(info['game'].rsplit(':',1)[1])!=int(s['port']):raise ValueError('PUBLICATION79_BASELINE')
 if s['auth']=='false' and info['game'].rsplit(':',1)[0] not in {'localhost','127.0.0.1','[::1]'}:raise ValueError('PUBLICATION79_OFFLINE_LOOPBACK')
 available(root,key)
 r=subprocess.run(args,capture_output=True,text=True,timeout=300,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
 if r.returncode:
  code=r.stderr.strip();raise ValueError(code if re.fullmatch('PUBLICATION79_[A-Z_]{1,60}',code) else 'PUBLICATION79_REJECTED')
 result=json.loads(r.stdout)
 if not ticket:
  for side in ('client','server'):
   result[side+'Count']=len(result[side]);result[side+'Bytes']=sum(x['bytes'] for x in result[side]);result[side]=result[side][:20]
 return result
