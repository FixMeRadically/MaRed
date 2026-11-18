"""Read-only switching of the four public files; owner paths and keys are never routes."""
import re,threading,uuid
from .common import safe
from .bridge67 import read,verify
from . import dedicated74 as old

class Publication75:
 def __init__(self,root,initial,verify_public,initial_info=None):
  self.root=safe(root);self.settings,self.key=old.settings(self.root)
  if read(self.root/'owner75',self.key)!={'schema':'75','run':self.settings['run']}:raise ValueError('PUBLICATION75_OWNER')
  self.initial=safe(initial);self.verify_public=verify_public;info=initial_info if initial_info is not None else verify_public(self.initial);self.endpoint=info['endpoint'];self.publisher=safe(self.root/'publisher73/public.der').read_bytes()
  if safe(self.initial/'public.der').read_bytes()!=self.publisher:raise ValueError('PUBLICATION75_SIGNER')
  self.cached=None;self.result=None;self.lock=threading.Lock()
 def snapshot(self):
  with self.lock:
   pointer=safe(self.root/'publication75');d=None
   if not pointer.exists():folder=self.initial;token=b'initial'
   else:
    if pointer.stat().st_size>16384:raise ValueError('PUBLICATION75_SIZE')
    token=pointer.read_bytes();d=verify(self.key,token)
    if set(d)!={'schema','run','label','release'} or d['schema']!='75' or d['run']!=self.settings['run'] or str(uuid.UUID(d['release']))!=d['release'] or d['release']==str(uuid.UUID(int=0)):raise ValueError('PUBLICATION75_BINDING')
    folder=safe(self.root/'releases'/old.label(d['label'])/'public')
   if token==self.cached:return self.result
   info=self.verify_public(folder)
   if d is not None and info['release']!=d['release']:raise ValueError('PUBLICATION75_RELEASE')
   if info['endpoint']!=self.endpoint or safe(folder/'public.der').read_bytes()!=self.publisher or not re.fullmatch('client-[a-f0-9]{64}\\.zip',info['archive']):raise ValueError('PUBLICATION75_IDENTITY')
   allowed={'public.der','offer.bin','offer.sig',info['archive']}
   if {p.name for p in folder.iterdir()}!=allowed:raise ValueError('PUBLICATION75_FILES')
   self.result=(folder,frozenset(allowed));self.cached=token;return self.result
