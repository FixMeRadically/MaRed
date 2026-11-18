"""Recheck short-lived game-origin permits against current signed policy when accepting a command."""
import base64,contextlib,os,re,time,uuid
from .common import safe
from .bridge67 import read,write,verify
ROLES={'OBSERVER':{'PING'},'PREPARER':{'PING','STAGE'},'PUBLISHER':{'PING','STAGE','OFFER','CANCEL'},'RESTARTER':{'PING','COMMIT','CONTINUE_WAIT','CLOSE'},'MAINTAINER':{'PING','STAGE','OFFER','CANCEL','COMMIT','CONTINUE_WAIT','CLOSE'}}
ROOT={'PING','STAGE','OFFER','COMMIT','CANCEL','CONTINUE_WAIT','CLOSE','SHUTDOWN','LOCAL_TEST_ON','LOCAL_TEST_OFF'}
class AccessBusy88(ValueError):pass
FIELDS={'schema','run','revision','subjects','sequence','audit'}
@contextlib.contextmanager
def lock(root):
 p=safe(root/'access88.lock')
 with p.open('a+b') as f:
  if p.stat().st_size==0:f.write(b'0');f.flush()
  f.seek(0)
  if os.name=='nt':
   import msvcrt
   try:msvcrt.locking(f.fileno(),msvcrt.LK_NBLCK,1)
   except OSError:raise AccessBusy88('ACCESS88_BUSY') from None
  else:
   import fcntl
   try:fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB,1)
   except BlockingIOError:raise AccessBusy88('ACCESS88_BUSY') from None
  try:yield
  finally:
   f.seek(0)
   if os.name=='nt':msvcrt.locking(f.fileno(),msvcrt.LK_UNLCK,1)
   else:fcntl.lockf(f,fcntl.LOCK_UN,1)

def number(v):
 if not isinstance(v,str) or not re.fullmatch(r'0|[1-9][0-9]{0,12}',v) or int(v)>999999999999:raise ValueError('ACCESS88_STATE')
 return int(v)
def uid(v):
 if not isinstance(v,str) or str(uuid.UUID(v))!=v:raise ValueError('ACCESS88_IDENTITY')
 return v
def state(root,key,run):
 if read(root/'owner88',key)!=dict(schema='88',run=run):raise ValueError('ACCESS88_NEW_OWNER_REQUIRED')
 s=read(root/'access88.state',key)
 if set(s)!=FIELDS or s['schema']!='88' or s['run']!=run:raise ValueError('ACCESS88_STATE')
 number(s['revision']);number(s['sequence']);subjects={}
 if len(s['subjects'])>16000 or s['subjects'] and not s['subjects'].endswith('\n') or len(s['audit'])>24000 or len(s['audit'].splitlines())>64 or s['audit'] and not s['audit'].endswith('\n'):raise ValueError('ACCESS88_STATE')
 for line in s['subjects'].splitlines():
  who,role=line.split('=')
  if uid(who) in subjects or role not in ROLES:raise ValueError('ACCESS88_STATE')
  subjects[who]=role
 if len(subjects)>256 or ''.join(k+'='+v+'\n' for k,v in sorted(subjects.items()))!=s['subjects']:raise ValueError('ACCESS88_STATE')
 return s,subjects

def event(s,actor,action,outcome,nonce,label,now):
 n=number(s['sequence'])+1
 if n>999999999999:raise ValueError('ACCESS88_LIMIT')
 rows=s['audit'].splitlines();rows.append('|'.join((str(n),str(now),actor,actor,action,outcome,nonce,label)));s.update(sequence=str(n),audit='\n'.join(rows[-64:])+'\n')

def initialize(root,key,run):
 with lock(root):
  if (root/'access88.state').exists() or (root/'owner88').exists():state(root,key,run);return
  if (root/'owner88').exists() and read(root/'owner88',key)!=dict(schema='88',run=run):raise ValueError('ACCESS88_STATE')
  write(root/'owner88',key,dict(schema='88',run=run))
  write(root/'access88.state',key,dict(schema='88',run=run,revision='0',subjects='',sequence='0',audit=''))

def consume(root,key,run,d,now=None):
 now=int(time.time()*1000) if now is None else now
 with lock(root):
  s,subjects=state(root,key,run)
  if 'permit88' not in d:
   event(s,'LOCAL',d['action'],'ACCEPTED',d['request'],d['label'],now);write(root/'access88.state',key,s);return
  p=verify(key,base64.b64decode(d['permit88'],validate=True))
  if set(p)!={'schema','run','actor','root','revision','generation','request','action','label','issued','expires'} or p['schema']!='88' or p['root'] not in {'true','false'} or any(p[k]!=d[k] for k in ('run','generation','request','action','label')) or p['run']!=run:raise ValueError('ACCESS88_BINDING')
  actor=uid(p['actor']);issued=int(p['issued']);expires=int(p['expires']);allowed=ROOT if p['root']=='true' else ROLES.get(subjects.get(actor),set())
  valid=0<expires-issued<=15000 and issued-2000<=now<expires and p['revision']==s['revision'] and d['action'] in allowed
  event(s,actor,d['action'],'ACCEPTED' if valid else 'REJECTED',d['request'],d['label'],now);write(root/'access88.state',key,s)
  if not valid:raise ValueError('ACCESS88_REVOKED_OR_EXPIRED')
