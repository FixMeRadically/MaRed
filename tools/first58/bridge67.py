"""Canonical authenticated local messages; format shared with pure-JDK LocalApply67."""
import base64,hashlib,hmac,re
from .common import safe,atomic

def body(values):
 if not isinstance(values,dict) or not 1<=len(values)<=16:raise ValueError('Apply67 fields')
 rows=[]
 for k,v in sorted(values.items()):
  if not isinstance(k,str) or not re.fullmatch('[A-Za-z][A-Za-z0-9]{0,31}',k) or k=='mac' or not isinstance(v,str) or len(v.encode('utf-8'))>65536:raise ValueError('Apply67 field')
  rows.append(k+'='+base64.urlsafe_b64encode(v.encode()).decode().rstrip('=')+'\n')
 data=''.join(rows).encode('ascii')
 if len(data)>130000:raise ValueError('Apply67 size')
 return data

def sign(key,values):
 if len(key)!=32:raise ValueError('Apply67 key')
 data=body(values);return data+b'mac='+hmac.new(key,data,hashlib.sha256).hexdigest().encode()+b'\n'

def verify(key,data):
 if len(data)>131072 or len(key)!=32:raise ValueError('Apply67 size')
 raw,sep,signature=data.rpartition(b'mac=')
 if not sep or raw and not raw.endswith(b'\n') or not re.fullmatch(b'[a-f0-9]{64}\n',signature) or not hmac.compare_digest(signature[:-1],hmac.new(key,raw,hashlib.sha256).hexdigest().encode()):raise ValueError('Apply67 authentication')
 values={}
 for line in raw.decode('ascii').splitlines():
  k,v=line.split('=',1)
  if k in values:raise ValueError('Apply67 duplicate')
  decoded=base64.b64decode(v+'='*((-len(v))%4),altchars=b'-_',validate=True)
  if base64.urlsafe_b64encode(decoded).decode().rstrip('=')!=v:raise ValueError('Apply67 canonical')
  values[k]=decoded.decode('utf-8')
 if body(values)!=raw:raise ValueError('Apply67 canonical')
 return values

def read(path,key):
 p=safe(path)
 if not p.is_file() or p.stat().st_size>131072:raise ValueError('Apply67 message limit')
 with p.open('rb') as stream:return verify(key,stream.read(131073))

def write(path,key,values):atomic(path,sign(key,values))
