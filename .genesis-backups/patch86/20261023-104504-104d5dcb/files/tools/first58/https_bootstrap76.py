"""Fixed signed state and versioned four-file routes. No profile/capability/account endpoint."""
import re,time,uuid,threading
from .common import safe
from .bridge67 import read
from .bootstrap_wire76 import decode
from . import dedicated74 as old
FIELDS={'schema','owner','generation','phase','serial','issued','expires','game','endpoint','distribution','release','archive','signer','minecraft','neoforge','inventory','offer','offerSig'}
class Routes76:
 def __init__(self,root,prefix,publication,verify_public):
  self.root=safe(root);self.s,self.key=old.settings(self.root);self.prefix=prefix+'bootstrap76/';self.publication=publication;self.verify_public=verify_public;self.cache=None;self.selected=None;self.lock=threading.Lock()
  if read(self.root/'owner76',self.key)!={'schema':'76','run':self.s['run']}:raise ValueError('BOOTSTRAP76_OWNER')
 def local(self):
  d=read(self.root/'bootstrap76.local',self.key)
  if set(d)!={'schema','run','at','serial','phase','label','generation','release'} or d['schema']!='76' or d['run']!=self.s['run'] or not -30000<=int(time.time()*1000)-int(d['at'])<=5000 or d['phase'] not in {'READY','TRANSITION','UNAVAILABLE'} or str(uuid.UUID(d['release']))!=d['release']:raise ValueError('BOOTSTRAP76_LOCAL')
  old.label(d['label']);return d
 def route(self,handler):
  if not handler.path.startswith(self.prefix):return False
  if handler.command!='GET':handler.send_error(404);return True
  name=handler.path[len(self.prefix):]
  try:
   if name=='state':
    self.local();file=safe(self.root/'bootstrap76.state')
    if file.stat().st_size>32768:raise ValueError('BOOTSTRAP76_SIZE')
    data=file.read_bytes();body,sep,sig=data.rpartition(b'sig=');d=decode(body)
    if not sep or set(d)!=FIELDS or d['schema']!='76' or d['owner']!=self.s['run'] or int(d['expires'])<int(time.time()*1000):raise ValueError('BOOTSTRAP76_STATE')
   else:
    match=re.fullmatch(r'([0-9a-f-]{36})/(public\.der|offer\.bin|offer\.sig|client-[a-f0-9]{64}\.zip)',name)
    if not match:handler.send_error(404);return True
    d=self.local()
    if d['phase']=='UNAVAILABLE' or match[1]!=d['release']:handler.send_error(404);return True
    with self.lock:
     token=(d['label'],d['release'])
     if token!=self.cache:
      folder=safe(self.root/'releases'/d['label']/'public');info=self.verify_public(folder)
      if info['release']!=d['release'] or info['endpoint']!=self.publication.endpoint or safe(folder/'public.der').read_bytes()!=self.publication.publisher:raise ValueError('BOOTSTRAP76_BINDING')
      allowed={'public.der','offer.bin','offer.sig',info['archive']}
      if {p.name for p in folder.iterdir()}!=allowed:raise ValueError('BOOTSTRAP76_FILES')
      self.selected=(folder,allowed);self.cache=token
     folder,allowed=self.selected
    if match[2] not in allowed:handler.send_error(404);return True
    file=safe(folder/match[2]);data=None
   length=len(data) if data is not None else file.stat().st_size
   with file.open('rb') as stream:
    handler.send_response(200);handler.send_header('Content-Length',str(length));handler.send_header('Content-Type','application/octet-stream');handler.send_header('Cache-Control','no-store');handler.end_headers()
    if data is not None:handler.wfile.write(data)
    else:
     import shutil
     shutil.copyfileobj(stream,handler.wfile,65536)
  except Exception:handler.send_error(503)
  return True
