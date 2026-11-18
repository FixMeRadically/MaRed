"""Fresh current-client handoff and bounded account probe. No Microsoft refresh tokens, JWT trust or redirects."""
from pathlib import Path
import hashlib,http.client,json,os,re,ssl,subprocess,sys,time,uuid
from .common import safe
from .bridge67 import read,write
from .session61 import apply,for_mode

_HTTPS89=http.client.HTTPSConnection
CONTEXT={'sourceRun','profile','account','mode','scope','pid','birth'}
CODES={'AUTH89_SESSION_CHANGED','AUTH89_LOGIN_REQUIRED','AUTH89_PROFILE_REQUIRED','AUTH89_ACCOUNT_CHANGED','AUTH89_SESSION','AUTH89_UNAVAILABLE','AUTH89_RESPONSE','AUTH89_HANDOFF_TIMEOUT','AUTH89_BINDING','AUTH89_NEW_OWNER_REQUIRED'}
class AuthError(ValueError):pass

def enabled(w):return getattr(w,'r',{}).get('auth89') is True

def session(argv):
 values={};i=0
 while i<len(argv):
  value=argv[i];key=value.split('=',1)[0]
  if key not in {'--uuid','--username','--accessToken','--userType'}:i+=1;continue
  if key in values:raise AuthError('AUTH89_SESSION')
  if '=' in value:values[key]=value.split('=',1)[1];i+=1
  else:
   if i+1>=len(argv):raise AuthError('AUTH89_SESSION')
   values[key]=argv[i+1];i+=2
 if set(values)!={'--uuid','--username','--accessToken','--userType'}:raise AuthError('AUTH89_SESSION')
 return dict(account=values['--uuid'],name=values['--username'],accessToken=values['--accessToken'],userType=values['--userType'])

def validate_identity(s):
 try:
  if set(s)!={'account','name','accessToken','userType'} or any(not isinstance(v,str) for v in s.values()):raise ValueError()
  if str(uuid.UUID(s['account']))!=s['account'] or not re.fullmatch(r'[A-Za-z0-9_]{1,16}',s['name']) or len(s['accessToken'])>8192 or any(ord(c)<33 or ord(c)>126 for c in s['accessToken']) or s['userType'] not in {'msa','mojang','legacy'}:raise ValueError()
 except Exception:raise AuthError('AUTH89_SESSION') from None

def strict_json(data):
 def pairs(items):
  d={}
  for k,v in items:
   if k in d:raise ValueError()
   d[k]=v
  return d
 return json.loads(data,object_pairs_hook=pairs)

def https_profile(token):
 # Fixed origin/path, default TLS trust, direct connection. No updater endpoint/proxy/redirect receives the token.
 c=_HTTPS89('api.minecraftservices.com',timeout=8,context=ssl.create_default_context())
 try:
  c.request('GET','/minecraft/profile',headers={'Authorization':'Bearer '+token,'Accept':'application/json','Accept-Encoding':'identity','User-Agent':'Genesis-session-check89'})
  response=c.getresponse();status=response.status
  if status!=200:return status,b''
  if response.getheader('Content-Encoding','identity').lower()!='identity':raise AuthError('AUTH89_RESPONSE')
  raw=response.read(32769)
  if len(raw)>32768:raise AuthError('AUTH89_RESPONSE')
  return status,raw
 finally:c.close()

def online(s,transport=https_profile):
 validate_identity(s);token=s['accessToken']
 if s['userType'] not in {'msa','mojang'} or not token or token.lower() in {'0','null','none','offline','invalid'} or '${' in token:raise AuthError('AUTH89_LOGIN_REQUIRED')
 try:
  status,body=transport(token)
  if status in {401,403}:raise AuthError('AUTH89_LOGIN_REQUIRED')
  if status==404:raise AuthError('AUTH89_PROFILE_REQUIRED')
  if status!=200:raise AuthError('AUTH89_UNAVAILABLE')
  if not isinstance(body,bytes) or len(body)>32768:raise AuthError('AUTH89_RESPONSE')
  data=strict_json(body.decode('utf8'))
  if not isinstance(data,dict) or not isinstance(data.get('id'),str) or not re.fullmatch('[a-fA-F0-9]{32}|[a-fA-F0-9-]{36}',data['id']) or not isinstance(data.get('name'),str):raise AuthError('AUTH89_RESPONSE')
  if uuid.UUID(data['id'])!=uuid.UUID(s['account']) or data['name']!=s['name']:raise AuthError('AUTH89_ACCOUNT_CHANGED')
 except AuthError:raise
 except OSError:raise AuthError('AUTH89_UNAVAILABLE') from None
 except (ValueError,UnicodeError,TypeError):raise AuthError('AUTH89_RESPONSE') from None
 except Exception:raise AuthError('AUTH89_UNAVAILABLE') from None
 return 'AUTH89_OK'

def probe(s):
 # Token only on private stdin, never in argv/URL/temp file. Timeout kills only this helper.
 payload=json.dumps(s,separators=(',',':')).encode('utf8')
 executable=Path(sys.executable)
 if executable.name.lower()=='pythonw.exe' and executable.with_name('python.exe').is_file():executable=executable.with_name('python.exe')
 bootstrap="import runpy,sys;sys.path.insert(0,sys.argv[1]);runpy.run_module('first58.auth89',run_name='__main__')"
 try:r=subprocess.run([str(executable),'-I','-c',bootstrap,str(Path(__file__).resolve().parents[1])],input=payload,capture_output=True,timeout=16,cwd=Path(__file__).resolve().parents[1],creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
 except (OSError,subprocess.TimeoutExpired):raise AuthError('AUTH89_UNAVAILABLE') from None
 code=r.stdout.decode('ascii','replace').strip()
 if r.returncode==0 and code=='AUTH89_OK':return
 raise AuthError(code if code in CODES else 'AUTH89_UNAVAILABLE')

def peer(w,joining=False):
 if joining:
  if not w.config or 'authMode' not in w.config:raise AuthError('AUTH89_NEW_OWNER_REQUIRED')
  return w.root,w.key,dict(sourceRun=w.config['run'],profile=str(w.root/'generations/client-1'),account=w.account,mode=w.r['authMode'],scope='MANAGED',pid='owned',birth='owned')
 previous=getattr(w,'previous',None)
 if previous is not None:return previous.root,previous.key,dict(sourceRun=previous.config['run'],profile=str(w.source),account=previous.account,mode=w.r['authMode'],scope='MANAGED',pid='owned',birth='owned')
 return w.root,w.key,dict(sourceRun=w.id,profile=str(w.source),account=w.r['account'],mode=w.r['authMode'],scope='INITIAL',pid=str(w.r['pid']),birth=str(w.r['birth']))

def refresh(w,joining=False):
 root,key,context=peer(w,joining);root=safe(root);nonce=str(uuid.uuid4());request=dict(schema='89',request=w.id,nonce=nonce,issued=str(int(time.time()*1000)),**context);response=root/'auth89.response';challenge=root/'auth89.request'
 # Old proof never substitutes for a fresh challenge; only known files in this private peer root.
 response.unlink(missing_ok=True);write(challenge,key,request);end=time.monotonic()+7
 try:
  while time.monotonic()<end:
   if w.cancelled():raise InterruptedError('REMOTE70_CANCELLED')
   if not joining:w.require_source()
   elif w.job.status()['rootExited']:raise AuthError('AUTH89_HANDOFF_TIMEOUT')
   if response.exists():
    answer=read(response,key)
    if any(answer.get(k)!=v for k,v in request.items()):raise AuthError('AUTH89_BINDING')
    now=int(time.time()*1000);answered=int(answer.get('answered','0'))
    if now<answered or now-answered>10000 or answered<int(request['issued']):raise AuthError('AUTH89_HANDOFF_TIMEOUT')
    if 'error' in answer:
     if set(answer)!=set(request)|{'answered','error'}:raise AuthError('AUTH89_BINDING')
     raise AuthError(answer['error'] if answer['error'] in CODES else 'AUTH89_SESSION')
    if set(answer)!=set(request)|{'answered','name','accessToken','userType'}:raise AuthError('AUTH89_BINDING')
    s=dict(account=context['account'],name=answer['name'],accessToken=answer['accessToken'],userType=answer['userType']);validate_identity(s)
    old=session(w.spec['clientCommand'])
    if old['name']!=s['name']:raise AuthError('AUTH89_ACCOUNT_CHANGED')
    effective=s
    if w.r['authMode']=='LOCAL_OFFLINE':
     if old['account']!=w.account:raise AuthError('AUTH89_ACCOUNT_CHANGED')
     effective=dict(s,account=old['account'])
    argv,account=for_mode(w.spec['clientCommand'],effective,w.r['authMode'])
    if account!=w.account:raise AuthError('AUTH89_ACCOUNT_CHANGED')
    return argv,session(argv)
   time.sleep(.1)
  raise AuthError('AUTH89_HANDOFF_TIMEOUT')
 except (AuthError,InterruptedError):raise
 except Exception:raise AuthError('AUTH89_BINDING') from None
 finally:
  challenge.unlink(missing_ok=True);response.unlink(missing_ok=True)

def check(w,joining=False):
 if not enabled(w):return
 cache=getattr(w,'auth_cache89',None)
 if joining and cache and 0<=time.monotonic()-cache[0]<30:return
 def status(stage,code=''):
  w.auth89=dict(schema=89,stage=stage,checkedAt=int(time.time()*1000),code=code);w.state(w.phase)
 status('CHECKING')
 try:
  argv,s=refresh(w,joining)
  if w.r['authMode']=='ONLINE':
   probe(s)
   latest_argv,latest=refresh(w,joining)
   if latest!=s:raise AuthError('AUTH89_SESSION_CHANGED')
   argv=latest_argv
  elif w.r['authMode']!='LOCAL_OFFLINE':raise AuthError('AUTH89_SESSION')
  if w.cancelled():raise InterruptedError('REMOTE70_CANCELLED')
  if not joining:w.require_source()
  w.spec['clientCommand']=argv;w.auth_cache89=(time.monotonic(),);status('VERIFIED' if w.r['authMode']=='ONLINE' else 'OFFLINE')
 except AuthError as e:status('REFUSED',str(e));raise

def main():
 try:
  raw=sys.stdin.buffer.read(16385)
  if len(raw)>16384:raise AuthError('AUTH89_SESSION')
  s=strict_json(raw.decode('utf8'));online(s);sys.stdout.write('AUTH89_OK\n');return 0
 except Exception as e:
  code=str(e) if isinstance(e,AuthError) and str(e) in CODES else 'AUTH89_UNAVAILABLE';sys.stdout.write(code+'\n');return 2
if __name__=='__main__':raise SystemExit(main())
