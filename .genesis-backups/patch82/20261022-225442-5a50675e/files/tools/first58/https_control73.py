"""Optional, bounded control routes on the existing TLS host. No public keys/tokens/private files served."""
import hashlib,re,uuid
from pathlib import Path
from .common import safe,atomic
from .bridge67 import verify
from .control73 import decode

class Routes73:
 def __init__(self,root,prefix):self.root=safe(root);self.prefix=prefix+'control73/'
 def route(self,handler):
  path=handler.path
  if not path.startswith(self.prefix):return False
  name=path[len(self.prefix):]
  if handler.command=='GET' and re.fullmatch(r'[0-9a-f-]{36}\.state',name):
   try:
    if str(uuid.UUID(name[:-6]))!=name[:-6]:raise ValueError()
    file=safe(self.root/'public'/name)
    if not file.is_file() or file.stat().st_size>131072:raise ValueError()
    with file.open('rb') as stream:data=stream.read(131073)
    if len(data)>131072:raise ValueError()
    unsigned,sep,signature=data.rpartition(b'sig=')
    state=decode(unsigned)
    if not sep or set(state)!={'schema','instance','round','release','archive','distribution','game','endpoint','signer','phase','sequence','expires','needed','installed','admission','clients'}:raise ValueError()
    for row in state['clients'].split(';'):
     if len(row.split(','))!=3:raise ValueError()
    handler.send_response(200);handler.send_header('Content-Length',str(len(data)));handler.send_header('Cache-Control','no-store');handler.end_headers();handler.wfile.write(data)
   except (OSError,ValueError):handler.send_error(404)
   return True
  if handler.command=='POST' and name=='receipt':
   try:
    if handler.headers.get('Transfer-Encoding') is not None:raise ValueError()
    size=int(handler.headers.get('Content-Length','0'))
    if not 1<=size<=16384:raise ValueError()
    data=handler.rfile.read(size)
    if len(data)!=size:raise ValueError()
    auth=decode(safe(self.root/'auth73').read_bytes());client=handler.headers.get('X-Genesis-Client','')
    if str(uuid.UUID(client))!=client:raise ValueError()
    keys=dict(row.split(':') for row in auth['clients'].split(';') if row)
    if client not in keys:raise ValueError()
    packet=verify(bytes.fromhex(keys[client]),data)
    if packet.get('client')!=client or packet.get('round')!=auth['round'] or packet.get('schema')!='73' or set(packet)!={'schema','round','client','request','action','archive','inventory'} or packet['action'] not in {'PREPARED','INSTALLED','JOINED','ABORT'}:raise ValueError()
    if str(uuid.UUID(packet['request']))!=packet['request'] or packet['request']==str(uuid.UUID(int=0)):raise ValueError()
    inbox=safe(self.root/'inbox')
    # Per-capability fixed slot; no unlimited inbox/file creation, even with valid credentials.
    target=inbox/(client+'.packet')
    if target.exists() and target.read_bytes()!=data:handler.send_error(409);return True
    atomic(target,data);handler.send_response(202);handler.send_header('Content-Length','0');handler.end_headers()
   except (OSError,ValueError,KeyError):handler.send_error(403)
   return True
  handler.send_error(404);return True
