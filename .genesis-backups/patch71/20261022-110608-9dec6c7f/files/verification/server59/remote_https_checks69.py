#!/usr/bin/env python3
"""Actual localhost HTTPS/hostname/signature tests. No Minecraft, installs or public network.
Requires a full JDK (17+), openssl and Python 3.11+. Its CA exists only in a temporary JDK truststore.
"""
from pathlib import Path
import argparse,http.server,json,os,re,shutil,ssl,subprocess,tempfile,threading,time,urllib.request,urllib.error,zipfile,sys

def main():
 p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);p.add_argument('--classes',type=Path);a=p.parse_args();project=a.project.resolve();java=a.java.resolve();keytool=java.with_name('keytool.exe' if os.name=='nt' else 'keytool');openssl=shutil.which('openssl')
 if not openssl or not keytool.is_file():raise SystemExit('A full JDK/keytool and openssl are required for this optional TLS fixture.')
 with tempfile.TemporaryDirectory(prefix='genesis-https69-') as temp:
  root=Path(temp);classes=a.classes.resolve() if a.classes else root/'core';classes.mkdir(exist_ok=True)
  def run(cmd):return subprocess.run(list(map(str,cmd)),capture_output=True,text=True,timeout=120,check=True)
  if not a.classes:
   sources=list((project/'technologies/core/src/main/java').rglob('*.java'));argfile=root/'sources.args';argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in sources))
   run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-d',classes,'@'+str(argfile)])
  harness=root/'harness';harness.mkdir();extra=project/'verification/server59/RemoteSessionChecks70.java';run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','-cp',classes,'-d',harness,project/'verification/server59/RemoteChecks69.java',*([extra] if extra.is_file() else [])]);cp=str(harness)+os.pathsep+str(classes)
  cert=root/'server.crt';key=root/'server.key';trust=root/'trust.p12'
  run([openssl,'req','-x509','-newkey','rsa:2048','-nodes','-keyout',key,'-out',cert,'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'])
  run([keytool,'-importcert','-noprompt','-alias','genesis-fixture-only','-file',cert,'-keystore',trust,'-storetype','PKCS12','-storepass','fixture-only69'])
  state={'root':None,'mode':'normal','requests':[]}
  class Handler(http.server.BaseHTTPRequestHandler):
   def log_message(self,*args):pass
   def do_GET(self):
    state['requests'].append(self.path)
    if state['mode']=='redirect':self.send_response(302);self.send_header('Location','/should-not-follow');self.end_headers();return
    if not re.fullmatch(r'/genesis/(public\.der|offer\.bin|offer\.sig|client-[a-f0-9]{64}\.zip)',self.path):self.send_error(404);return
    file=state['root']/self.path.rsplit('/',1)[1]
    if not file.is_file():self.send_error(404);return
    data=file.read_bytes();is_zip=file.suffix=='.zip'
    if state['mode']=='signature' and file.name=='offer.sig':data=bytes([data[0]^1])+data[1:]
    if state['mode']=='metadata-limit' and file.name=='offer.bin':self.send_response(200);self.send_header('Content-Length','8193');self.end_headers();return
    if state['mode']=='changed' and is_zip:state['root']=root/'public2'
    if state['mode']=='withdraw' and is_zip:state['root']=root/'wrong-key'
    if state['mode']=='archive-hash' and is_zip:data=bytes([data[0]^1])+data[1:]
    self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers()
    try:self.wfile.write(data[:len(data)//2] if state['mode']=='truncated' and is_zip else data)
    except (BrokenPipeError,ConnectionResetError,ssl.SSLError):pass
  server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);server.daemon_threads=True;context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(cert,key);server.socket=context.wrap_socket(server.socket,server_side=True);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
  endpoint=f'https://localhost:{server.server_port}/genesis/';props=['-Djavax.net.ssl.trustStore='+str(trust),'-Djavax.net.ssl.trustStorePassword=fixture-only69']
  checks=0
  def probe(mode,cache,expected,*,url=endpoint,custom_ca=True):
   nonlocal checks
   result=run([java,*(props if custom_ca else []),'-cp',cp,'com.fixmer.genesis.technology.server.remote.RemoteChecks69',mode,root/cache,url]);assert expected in result.stdout,(mode,result.stdout,result.stderr);checks+=1
  def public(name='public1',mode='normal'):state.update(root=root/name,mode=mode,requests=[])
  def only_complete(cache):
   for path in (root/cache/'downloads').iterdir():assert not (path/'client.part').exists() and (path/'receipt69.txt').is_file(),str(path)
  try:
   run([java,'-cp',cp,'com.fixmer.genesis.technology.server.remote.RemoteChecks69','fixture',root,endpoint]);public()
   probe('expect','tls-default','REMOTE_TLS',custom_ca=False)
   probe('expect','hostname','REMOTE_TLS',url=f'https://127.0.0.1:{server.server_port}/genesis/')
   public(mode='redirect');probe('expect','redirect','REMOTE_HTTP_STATUS');assert '/should-not-follow' not in state['requests'];checks+=1
   public(mode='metadata-limit');probe('expect','metadata','REMOTE_DOWNLOAD_LIMIT')
   public();probe('reject-untrusted','untrusted','REMOTE_TRUST_REQUIRED');assert not any('client-' in u for u in state['requests']);checks+=1
   probe('cancel-before','pre-cancel','REMOTE_CANCELLED')
   public();probe('network','client','VERIFIED 1');only_complete('client')
   public(mode='signature');probe('expect','client','REMOTE_SIGNATURE')
   public('wrong-key');probe('expect','client','REMOTE_TRUST_CHANGED')
   public('expired');probe('expect','client','REMOTE_OFFER_EXPIRED')
   public(mode='changed');probe('download-error','client','REMOTE_OFFER_CHANGED');only_complete('client')
   public('public2',mode='archive-hash');probe('download-error','client','REMOTE_ARCHIVE_HASH');only_complete('client')
   public('public2',mode='truncated');probe('download-error','client','REMOTE_DOWNLOAD_TRUNCATED');only_complete('client')
   public('public2');probe('cancel-body','cancel-body','REMOTE_CANCELLED');assert not list((root/'cancel-body/downloads').iterdir());checks+=1
   public('public2');probe('network','client','VERIFIED 2');only_complete('client')
   public();probe('expect','client','REMOTE_ROLLBACK')
   public('equivocation');probe('expect','client','REMOTE_EQUIVOCATION')
   # Exercise the actual shipped HTTPS helper too, not only the controlled fixture handler.
   server.shutdown();server.server_close();thread.join(timeout=5)
   runtime=root/'runtime';(runtime/'runtime').mkdir(parents=True)
   with zipfile.ZipFile(runtime/'runtime/core.jar','w',zipfile.ZIP_DEFLATED) as z:
    for file in classes.rglob('*.class'):z.write(file,file.relative_to(classes))
   process=subprocess.Popen([sys.executable,str(project/'tools/remote_update.py'),str(runtime),'--java',str(java),'serve',str(root/'public2'),'--cert',str(cert),'--key',str(key),'--port',str(server.server_port)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
   try:
    tls=ssl.create_default_context(cafile=str(cert));ready=False
    for _ in range(60):
     if process.poll() is not None:raise AssertionError(process.communicate())
     try:
      with urllib.request.urlopen(endpoint+'offer.bin',context=tls,timeout=1) as response:assert response.status==200
      ready=True;break
     except (urllib.error.URLError,TimeoutError):time.sleep(.1)
    assert ready,'Shipped HTTPS helper did not start'
    probe('network','shipped-server','VERIFIED 2')
    if extra.is_file():
     from remote_file_checks70 import check_files
     check_files(root,java,cp,props,root/'shipped-server')
    for name in ['private.der','public.der?token=fixture']:
     try:urllib.request.urlopen(endpoint+name,context=tls,timeout=2);raise AssertionError('Private/query route was served')
     except urllib.error.HTTPError as error:assert error.code==404;checks+=1
   finally:
    process.terminate()
    try:process.communicate(timeout=10)
    except subprocess.TimeoutExpired:process.kill();process.communicate()
   print(f'Actual HTTPS/signature/rollback checks passed: {checks}')
   print('Real TLS trust and hostname validation, no redirect, explicit trust, 2 releases with one publisher identity, signed package verification, cancellation and failed-download cleanup.')
   print('No Minecraft/process/restart/multiplayer test was performed. The fixture CA was never added to the OS or global JDK.')
  finally:server.shutdown();server.server_close();thread.join(timeout=5)
if __name__=='__main__':main()
