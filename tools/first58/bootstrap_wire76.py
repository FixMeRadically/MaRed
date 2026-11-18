"""Bounded canonical PUBLIC76 map, compatible with ControlWire73 (32 fields).
The private Apply67 HMAC format keeps its original16-field limit unchanged.
"""
import base64,re

def decode(data):
 if not isinstance(data,bytes) or not 1<=len(data)<=32768:raise ValueError('BOOTSTRAP76_SIZE')
 out={}
 for line in data.decode('ascii').splitlines():
  k,v=line.split('=',1)
  if not re.fullmatch('[A-Za-z][A-Za-z0-9]{0,31}',k) or k in out or k in {'mac','sig'} or len(out)>=32:raise ValueError('BOOTSTRAP76_FIELDS')
  raw=base64.b64decode(v+'='*((-len(v))%4),altchars=b'-_',validate=True)
  if base64.urlsafe_b64encode(raw).decode().rstrip('=')!=v:raise ValueError('BOOTSTRAP76_CANONICAL')
  out[k]=raw.decode('utf8')
 canonical=''.join(k+'='+base64.urlsafe_b64encode(v.encode('utf8')).decode().rstrip('=')+'\n' for k,v in sorted(out.items())).encode('ascii')
 if not out or canonical!=data:raise ValueError('BOOTSTRAP76_CANONICAL')
 return out
