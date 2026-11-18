"""Local Script draft -> complete paired release. No game/network path authorizes host filesystem writes."""
from pathlib import Path
import re
from . import dedicated74 as base,publication79,publication83
from .common import safe

def run(project,root,action,input,side=None,label=None,ttl=None,ticket=None,fallback=None):
 root=safe(root);settings,key=base.settings(root);publication79.available(root,key)
 seed,release=publication79.baseline(root,key,fallback,project);pair=safe(seed.parent)
 # Keep owner-version/address/auth checks from the normal publication route, before any signing.
 info=publication83.command(project,root,'com.fixmer.genesis.technology.server.remote.PublicationStatus79',seed)
 if release is not None and info['release']!=release or info['minecraft']!=settings['minecraft'] or info['neoforge']!=settings['neoforge'] or int(info['game'].rsplit(':',1)[1])!=int(settings['port']):raise ValueError('PUBLICATION79_BASELINE')
 if settings['auth']=='false':
  from urllib.parse import urlsplit
  if info['game'].rsplit(':',1)[0] not in {'localhost','127.0.0.1','[::1]'} or urlsplit(info['endpoint']).hostname not in {'localhost','127.0.0.1','::1'}:raise ValueError('PUBLICATION79_OFFLINE_LOOPBACK')
 input=safe(input)
 if root/'generations'==input or root/'generations' in input.parents:raise ValueError('PUBLICATION79_LIVE_SOURCE')
 if action=='script-prepare':
  if side not in {'CLIENT','SERVER','BOTH'}:raise ValueError('SCRIPT84_SIDE_REQUIRED')
  args=['prepare',root/'publisher73',pair,input,root/'drafts84',side]
 elif action in {'script-preview','script-author'}:
  if input.parent!=root/'drafts84':raise ValueError('SCRIPT84_DRAFT_ROOT')
  base.label(label)
  if not isinstance(ttl,int) or not 60<=ttl<=604800:raise ValueError('SCRIPT84_TTL')
  args=['publication',root/'publisher73',pair,input,root/'releases',label,ttl]
  if action=='script-author':
   if not isinstance(ticket,str) or not re.fullmatch('[a-f0-9]{64}',ticket):raise ValueError('SCRIPT84_PREVIEW_REQUIRED')
   args.append(ticket)
 else:raise ValueError('SCRIPT84_ACTION')
 publication79.available(root,key)
 result=publication83.command(project,root,'com.fixmer.genesis.technology.server.remote.ScriptRelease84',*args)
 if action=='script-preview':
  for s in ('client','server'):
   result[s+'Count']=len(result[s]);result[s+'Bytes']=sum(e['bytes'] for e in result[s]);result[s]=result[s][:20]
 return result
