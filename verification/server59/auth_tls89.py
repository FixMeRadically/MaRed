#!/usr/bin/env python3
"""Production bounded HTTP parser over real local TLS plus Java/Python private current-session handoff.
Fixed-origin connection is redirected ONLY by an explicit verification shim, not a production config option.
No real Minecraft token or Microsoft service is used.
"""
from pathlib import Path
from types import SimpleNamespace
import argparse,http.client,http.server,json,os,ssl,subprocess,sys,tempfile,threading,uuid
from unittest.mock import Mock,patch
p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);p.add_argument('--classes',type=Path,required=True);p.add_argument('--fixtures',type=Path,required=True);a=p.parse_args();tools=a.project.resolve()/'tools';sys.path.insert(0,str(tools))
from first58 import auth89 as auth
from first58.bridge67 import write
with tempfile.TemporaryDirectory(prefix='genesis-auth89-') as temp:
 root=Path(temp);cert=root/'cert.pem';key=root/'key.pem';subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost','-keyout',str(key),'-out',str(cert)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 identity=dict(account=str(uuid.uuid4()),name='TestPlayer',accessToken='private-fixture89',userType='msa');state=dict(status=200,body=json.dumps(dict(id=identity['account'].replace('-',''),name='TestPlayer')).encode(),headers={},requests=[])
 class Handler(http.server.BaseHTTPRequestHandler):
  def log_message(self,*args):pass
  def do_GET(self):
   state['requests'].append((self.path,self.headers.get('Authorization')));self.send_response(state['status'])
   for k,v in state['headers'].items():self.send_header(k,v)
   self.send_header('Content-Length',str(len(state['body'])));self.end_headers();self.wfile.write(state['body'])
 server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);server.daemon_threads=True;ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);ctx.load_cert_chain(cert,key);server.socket=ctx.wrap_socket(server.socket,server_side=True);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start();real=http.client.HTTPSConnection;trust=ssl.create_default_context(cafile=str(cert));checks=0
 def shim(host,*args,**kw):
  assert host=='api.minecraftservices.com';assert isinstance(kw.get('context'),ssl.SSLContext);return real('localhost',server.server_port,timeout=8,context=trust)
 def call(expected=None):
  with patch.object(auth,'_HTTPS89',side_effect=shim):
   if expected:
    try:auth.online(identity);raise AssertionError('invalid session response accepted')
    except auth.AuthError as e:assert str(e)==expected
   else:assert auth.online(identity)=='AUTH89_OK'
 try:
  call();assert state['requests'][-1]==('/minecraft/profile','Bearer private-fixture89');checks+=2
  for code,expected in ((401,'AUTH89_LOGIN_REQUIRED'),(403,'AUTH89_LOGIN_REQUIRED'),(404,'AUTH89_PROFILE_REQUIRED'),(429,'AUTH89_UNAVAILABLE'),(503,'AUTH89_UNAVAILABLE')):state['status']=code;call(expected);checks+=1
  state['status']=302;state['headers']={'Location':'https://untrusted.example/token'};before=len(state['requests']);call('AUTH89_UNAVAILABLE');assert len(state['requests'])==before+1;checks+=2
  state.update(status=200,headers={},body=json.dumps(dict(id=uuid.uuid4().hex,name='TestPlayer')).encode());call('AUTH89_ACCOUNT_CHANGED');checks+=1
  state['body']=b'{"id":"a","id":"b"}';call('AUTH89_RESPONSE');checks+=1
  state['body']=b'x'*32769;call('AUTH89_RESPONSE');checks+=1
  state['body']=json.dumps(dict(id=identity['account'],name='TestPlayer')).encode();state['headers']={'Content-Encoding':'gzip'};call('AUTH89_RESPONSE');checks+=1
  state['headers']={}
  def untrusted(host,*args,**kw):return real('localhost',server.server_port,timeout=8,context=ssl.create_default_context())
  with patch.object(auth,'_HTTPS89',side_effect=untrusted):
   try:auth.online(identity);raise AssertionError('untrusted fixture CA accepted')
   except auth.AuthError as e:assert str(e)=='AUTH89_UNAVAILABLE';checks+=1
  # Actual fresh challenge: Python request -> Java core answer -> Python bind/argv -> actual TLS check.
  peer=root/'peer';peer.mkdir();bridge=os.urandom(32);(peer/'channel.key').write_bytes(bridge);w=SimpleNamespace(root=peer,key=bridge,id=str(uuid.uuid4()),source=root/'source',account=identity['account'],r=dict(auth89=True,account=identity['account'],authMode='ONLINE',pid=123,birth=456),spec=dict(clientCommand=['java','--uuid',identity['account'],'--username','TestPlayer','--accessToken','old-fixture89','--userType','msa']),cancelled=Mock(return_value=False),require_source=Mock(),phase='ARMING',state=Mock(),config=None)
  _,_,expected=auth.peer(w);write(peer/'fixture89.context',bridge,expected);cp=str(a.classes.resolve())+os.pathsep+str(a.fixtures.resolve())
  def answer(n):subprocess.run([str(a.java.resolve()),'-cp',cp,'SessionProbe89',str(peer)],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=10)
  def online_check(s):
   with patch.object(auth,'_HTTPS89',side_effect=shim):auth.online(s)
  with patch.object(auth.time,'sleep',side_effect=answer),patch.object(auth,'probe',side_effect=online_check):auth.check(w)
  assert w.auth89['stage']=='VERIFIED';assert auth.session(w.spec['clientCommand'])['accessToken']=='private-fixture89';assert not (peer/'auth89.response').exists();assert not (peer/'auth89.request').exists();assert 'private-fixture89' not in json.dumps(w.auth89);checks+=5
  # Real isolated helper starts and rejects a placeholder BEFORE any network request.
  before=len(state['requests'])
  try:auth.probe(dict(identity,accessToken='0'));raise AssertionError('placeholder accepted')
  except auth.AuthError as e:assert str(e)=='AUTH89_LOGIN_REQUIRED'
  assert len(state['requests'])==before;checks+=2
  print(f'Auth89 actual local TLS/parser/no-redirect/CA and Java-Python handoff checks passed: {checks}; connection shim and user are fixtures, no Microsoft/Minecraft runtime.')
 finally:server.shutdown();server.server_close();thread.join(timeout=5)
