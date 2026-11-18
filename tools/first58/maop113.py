"""Fresh console-granted MaRed rights at controller consumption, bound by the existing owner HMAC."""
import hashlib,hmac,re,uuid
from pathlib import Path
from .common import safe
from .bridge67 import read,write
RIGHTS={
 'WORKSPACE':{'PING','WORKSPACE_CREATE'},
 'RESTARTER':{'PING','RESTART'},
 'PUBLISHER':{'PING','STAGE','OFFER','CANCEL'},
 'MAINTAINER':{'PING','RESTART','STAGE','OFFER','CANCEL','COMMIT','CONTINUE_WAIT','CLOSE','CHECKPOINT'},
 'ADMIN':{'PING','RESTART','STAGE','OFFER','COMMIT','CANCEL','CONTINUE_WAIT','CLOSE','SHUTDOWN','LOCAL_TEST_ON','LOCAL_TEST_OFF','WORKSPACE_CREATE','CHECKPOINT'},
}
def policy(path):
 path=safe(Path(path))
 with path.open('rb') as f:raw=f.read(20001)
 if len(raw)>20000:raise ValueError('MAOP113_STATE')
 try:
  text=raw.decode('ascii');lines=text.split('\n')
  if len(lines)<4 or lines[0]!='GENESIS-MAOP113' or not re.fullmatch(r'revision=(0|[1-9][0-9]{0,18})',lines[1]) or lines[-1]!='':raise ValueError()
  revision=int(lines[1][9:])
  if revision>9223372036854775807:raise ValueError()
  entries={}
  for line in lines[2:-2]:
   who,role=line.split('=')
   if str(uuid.UUID(who))!=who or uuid.UUID(who).int==0 or who in entries or role not in RIGHTS:raise ValueError()
   entries[who]=role
  if len(entries)>256:raise ValueError()
  # Java UUID.compareTo orders signed long halves. Require the same canonical order.
  def order(who):
   value=uuid.UUID(who).int;hi=value>>64;lo=value&((1<<64)-1);return (hi-(1<<64) if hi>=(1<<63) else hi,lo-(1<<64) if lo>=(1<<63) else lo)
  body='GENESIS-MAOP113\nrevision='+str(revision)+'\n'+''.join(who+'='+entries[who]+'\n' for who in sorted(entries,key=order))
  if body!='\n'.join(lines[:-2])+'\n':raise ValueError()
  if not re.fullmatch(r'sha256=[a-f0-9]{64}',lines[-2]) or not hmac.compare_digest(lines[-2][7:],hashlib.sha256(body.encode('ascii')).hexdigest()):raise ValueError()
  return revision,entries
 except (ValueError,UnicodeError,IndexError):raise ValueError('MAOP113_STATE') from None

def device153(root,key,run,actor,fingerprint):
 if not re.fullmatch('[a-f0-9]{64}',fingerprint):return False
 binding=read(root/'maop153.binding',key)
 if set(binding)!={'schema','run','profile','devices'} or binding['schema']!='153' or binding['run']!=run:raise ValueError('AUTH153_BINDING')
 profile=safe(Path(binding['profile']));target=safe(Path(binding['devices']))
 if not target.is_relative_to(profile) or target.name!='devices153':raise ValueError('AUTH153_BINDING')
 with target.open('rb') as f:raw=f.read(131073)
 if len(raw)>131072:raise ValueError('AUTH153_LIMIT')
 lines=raw.decode('ascii').split('\n')
 if lines[0]!='GENESIS-DEVICES153' or lines[-1]!='':raise ValueError('AUTH153_FORMAT')
 if any(not re.fullmatch('[a-f0-9-]{36}:[a-f0-9]{64}',line) for line in lines[1:-1]):raise ValueError('AUTH153_FORMAT')
 return actor+':'+fingerprint in lines[1:-1]

def authorize(root,key,run,actor,action,fingerprint=''):
 if fingerprint and not device153(root,key,run,actor,fingerprint):return False
 if not (root/'owner113').exists() and not (root/'maop113.binding').exists():return True # Only historical pre113 owners; new113 games always bind both.
 if read(root/'owner113',key)!=dict(schema='113',run=run):raise ValueError('MAOP113_BINDING')
 binding=read(root/'maop113.binding',key)
 if set(binding)!={'schema','run','profile','policy'} or binding['schema']!='113' or binding['run']!=run:raise ValueError('MAOP113_BINDING')
 profile=safe(Path(binding['profile']));target=safe(Path(binding['policy']))
 if not target.is_relative_to(profile) or target.name!='permissions113.txt':raise ValueError('MAOP113_BINDING')
 _,subjects=policy(target)
 return action in RIGHTS.get(subjects.get(actor),set())

def controller(root,key,run):
 # Written by a controller whose access88 consumer includes the113 policy recheck.
 write(root/'controller113',key,dict(schema='113',run=run))
 write(root/'owner153',key,dict(schema='153',run=run))
