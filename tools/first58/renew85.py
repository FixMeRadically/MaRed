"""Read-only Java release queries and HTTPS selection recovery. Does not alter certificate trust."""
from pathlib import Path
from . import publication83 as pub,dedicated74 as base
from .bridge67 import read
from .common import safe,sha
from .https78 import Manager78

def head_is_current(project,root,pair,checkpoint,ignored="none"):return pub.command(project,root,'com.fixmer.genesis.technology.server.remote.Renewal85','head',root/'publisher73',pair,root/'releases',ignored,checkpoint=checkpoint)['current'] is True

class Manager85(Manager78):
 def __init__(self,*args,**kwargs):super().__init__(*args,**kwargs);self.observed85=None
 def configuration(self,d):
  p=self.root/'publication75'
  if not p.exists():return d
  selected=read(p,self.key)
  if set(selected)!={'schema','run','label','release'} or selected['schema']!='75' or selected['run']!=self.s['run']:raise ValueError('HTTPS78_PUBLICATION')
  folder=safe(self.root/'releases'/base.label(selected['label'])/'public')
  # check() below verifies the signed publication, own publisher, game/version/address,
  # certificate SAN/date/keypair and every pinned TLS field. Only the public folder changes.
  from .https78 import public_info
  info=public_info(self.project,self.java,folder,historical=True) if (self.root/'owner118').exists() else public_info(self.project,self.java,folder)
  if info['release']!=selected['release'] or info['endpoint']!=d['endpoint']:raise ValueError('HTTPS78_PUBLICATION')
  return dict(d,public=str(folder),publicSeal=base.digest(base.tree(folder,'unused')))
 def tick(self,force=False):
  p=self.root/'publication75'
  try:token=sha(p,16384) if p.exists() else 'none'
  except Exception:token='invalid'
  if self.state=='FAILED' and token!=self.observed85:self.token=None;self.failed_token=None;self.due=0
  self.observed85=token
  return super().tick(force)
