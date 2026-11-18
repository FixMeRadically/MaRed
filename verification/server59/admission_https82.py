#!/usr/bin/env python3
"""Actual shipped fixed route + TLS/Java/Python JOIN gate. Game/Job/loaded inventory are fixtures.
Requires JDK17+, keytool, openssl; never changes OS/global trust or launches Minecraft.
"""
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock,patch
import argparse,base64,http.server,os,shutil,ssl,subprocess,sys,tempfile,threading,urllib.error,urllib.request

def main():
 p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);p.add_argument('--classes',type=Path,required=True);p.add_argument('--harness',type=Path,required=True);a=p.parse_args();java=a.java.resolve();project=a.project.resolve();cp=str(a.classes.resolve())+os.pathsep+str(a.harness.resolve());sys.path.insert(0,str(project/'tools'))
 from first58.https_control73 import Routes73
 from first58.control73 import decode
 from first58.admission82 import Combined82
 def run(*args,ok=True):
  r=subprocess.run(list(map(str,args)),capture_output=True,text=True,timeout=30)
  assert (r.returncode==0)==ok,(r.returncode,r.stdout,r.stderr)
  return r
 with tempfile.TemporaryDirectory(prefix='genesis-join82-tls-') as temp:
  root=Path(temp);cert=root/'cert.pem';key=root/'key.pem';trust=root/'trust.p12';fixture=root/'fixture'
  run(shutil.which('openssl'),'req','-x509','-newkey','rsa:2048','-nodes','-keyout',key,'-out',cert,'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost')
  run(java.with_name('keytool.exe' if os.name=='nt' else 'keytool'),'-importcert','-noprompt','-alias','fixture82','-file',cert,'-keystore',trust,'-storetype','PKCS12','-storepass','fixture82-only')
  mode={'value':'normal','requests':[]};routes=None
  class Handler(http.server.BaseHTTPRequestHandler):
   def log_message(self,*args):pass
   def do_GET(self):
    mode['requests'].append(self.path)
    if mode['value']=='redirect':self.send_response(302);self.send_header('Location','/should-not-follow');self.end_headers();return
    if not routes.route(self):self.send_error(404)
  server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);server.daemon_threads=True;context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(cert,key);server.socket=context.wrap_socket(server.socket,server_side=True);thread=threading.Thread(target=server.serve_forever,daemon=True)
  try:
   endpoint=f'https://localhost:{server.server_port}/'
   run(java,'-cp',cp,'com.fixmer.mared.AdmissionChecks82','fixture',fixture,endpoint)
   routes=Routes73(fixture/'control73','/');thread.start();cfg=fixture/'client82.cfg';pub=fixture/'publisher73/public.der';values=decode(cfg.read_bytes());round_id=values['round'];proof=fixture/'control73/public'/f'{round_id}.ready82'
   props=['-Djavax.net.ssl.trustStore='+str(trust),'-Djavax.net.ssl.trustStorePassword=fixture82-only'];checks=0
   def pulse():run(java,'-cp',cp,'com.fixmer.mared.AdmissionChecks82','pulse',fixture)
   def poll(ok=True,ca=True,inv='d'*64,config=cfg):
    nonlocal checks
    pulse();r=run(java,*(props if ca else []),'-cp',cp,'com.fixmer.genesis.technology.server.remote.Admission82','poll',config,pub,inv,'1.21.1','21.1.264',ok=ok);checks+=1;return r
   poll(ok=False,ca=False);good=poll();assert b'JOIN_ONLY' in base64.b64decode(base64.b64decode(good.stdout).split(b'purpose=')[1].splitlines()[0]+b'==');checks+=1
   poll(ok=False,inv='e'*64)
   mode['value']='redirect';poll(ok=False);assert '/should-not-follow' not in mode['requests'];checks+=1;mode['value']='normal'
   pulse();original=proof.read_bytes();proof.write_bytes(original[:-3]+b'xx\n');run(java,*props,'-cp',cp,'com.fixmer.genesis.technology.server.remote.Admission82','poll',cfg,pub,'d'*64,'1.21.1','21.1.264',ok=False);checks+=1
   pulse();local=fixture/'admission82.local';raw=local.read_bytes();local.unlink();run(java,*props,'-cp',cp,'com.fixmer.genesis.technology.server.remote.Admission82','poll',cfg,pub,'d'*64,'1.21.1','21.1.264',ok=False);checks+=1;local.write_bytes(raw)
   tls=ssl.create_default_context(cafile=str(cert))
   for name in ('private.der','owner74.key','control73/auth73','control73/'+round_id+'.ready82?x=1'):
    try:urllib.request.urlopen(endpoint+name,context=tls,timeout=3);raise AssertionError('private/query route accepted')
    except urllib.error.HTTPError as e:assert e.code==404;checks+=1
   # Actual Java-signed state and proof travel through the shipped route and Python Combined82.
   w=SimpleNamespace(folder=root,spec={'clientCommand':[str(java)]},r={'neoforge':'21.1.264'})
   def status():
    result=run(java,*props,'-cp',cp,'com.fixmer.genesis.technology.server.remote.ControlHttp73','poll',cfg,pub);return decode(base64.b64decode(result.stdout))
   member=SimpleNamespace(w=w,cfg=values,file=cfg,key=pub,inventory='d'*64,check=status);public=SimpleNamespace(check=Mock(side_effect=ValueError('expired')));gate=Combined82(public,member);gate.runtime82=True
   pulse()
   env='-Djavax.net.ssl.trustStore="'+str(trust)+'" -Djavax.net.ssl.trustStorePassword=fixture82-only'
   with patch('first58.admission82.core_classpath',return_value=a.classes.resolve()),patch.dict(os.environ,{'JAVA_TOOL_OPTIONS':env}):assert gate.before_join()=='MEMBER82'
   public.check.assert_not_called();checks+=1
   # Close removes live round authority, even if old signed bytes still exist within TTL.
   active=fixture/'control73/active73';active.unlink();run(java,'-cp',cp,'com.fixmer.mared.AdmissionChecks82','pulse',fixture,ok=False);checks+=1
   print(f'Admission82 shipped HTTPS/TLS/Java/Python checks passed: {checks}. No game/native Job was launched; the temporary CA was used only by fixture processes.')
  finally:
   if thread.is_alive():server.shutdown();thread.join(timeout=5)
   server.server_close()
if __name__=='__main__':main()
