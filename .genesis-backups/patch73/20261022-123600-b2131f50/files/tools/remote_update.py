#!/usr/bin/env python3
"""Remote69 publisher tools. Public export never contains a signing/TLS private key or profile."""
from pathlib import Path
import argparse,http.server,json,os,shutil,ssl,subprocess,urllib.parse,threading
from first58.common import safe
from first58.runtime import core_classpath

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('project',type=Path);parser.add_argument('--java',type=Path)
 sub=parser.add_subparsers(dest='action',required=True)
 p=sub.add_parser('init');p.add_argument('identity',type=Path);p.add_argument('game');p.add_argument('endpoint')
 p=sub.add_parser('publish');p.add_argument('identity',type=Path);p.add_argument('run',type=Path);p.add_argument('output',type=Path);p.add_argument('--ttl',type=int,default=86400)
 p=sub.add_parser('serve');p.add_argument('public',type=Path);p.add_argument('--cert',type=Path,required=True);p.add_argument('--key',type=Path,required=True);p.add_argument('--bind',default='127.0.0.1');p.add_argument('--port',type=int,default=8443)
 a=parser.parse_args();project=safe(a.project);java=a.java or (Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java') if os.environ.get('JAVA_HOME') else shutil.which('java'))
 if not java:raise ValueError('Set JAVA_HOME or pass --java pointing to JDK21 java.exe.')
 def core(*args):
  r=subprocess.run([str(java),'-cp',str(core_classpath(project)),'com.fixmer.genesis.technology.server.remote.RemoteUpdateCli69',*map(str,args)],capture_output=True,text=True,timeout=300)
  if r.returncode:raise ValueError(r.stderr.strip() or 'Remote operation failed; no game profile was installed.')
  return r.stdout
 if a.action=='init':print(core('init',safe(a.identity),a.game,a.endpoint),end='');return
 if a.action=='publish':
  run=safe(a.run);packages=run/'packages' if (run/'packages').is_dir() else run
  print(core('publish',safe(a.identity),packages,safe(a.output),a.ttl),end='');return
 folder=safe(a.public);info=json.loads(core('verify-public',folder));prefix=urllib.parse.urlsplit(info['endpoint']).path;allowed={'public.der','offer.bin','offer.sig',info['archive']}
 cert=safe(a.cert);key=safe(a.key)
 if folder==key.parent or folder in key.parents:raise ValueError('TLS private key must be outside the public directory.')
 class Handler(http.server.BaseHTTPRequestHandler):
  def log_message(self,*args):pass # Do not echo request lines, queries or headers.
  def do_GET(self):
   if not self.path.startswith(prefix) or self.path[len(prefix):] not in allowed:self.send_error(404);return
   try:
    file=safe(folder/self.path[len(prefix):]);length=file.stat().st_size
    with file.open('rb') as source:
     self.send_response(200);self.send_header('Content-Type','application/octet-stream');self.send_header('Content-Length',str(length));self.send_header('Cache-Control','no-store');self.end_headers()
     shutil.copyfileobj(source,self.wfile,65536)
   except (OSError,ValueError):self.close_connection=True
 context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.minimum_version=ssl.TLSVersion.TLSv1_2;context.load_cert_chain(cert,key)
 class Server(http.server.ThreadingHTTPServer):
  daemon_threads=True;request_queue_size=16
  def __init__(self,*args):self.permits=threading.BoundedSemaphore(16);super().__init__(*args)
  def get_request(self):
   connection,address=super().get_request();connection.settimeout(10)
   return context.wrap_socket(connection,server_side=True,do_handshake_on_connect=False),address
  def process_request(self,connection,address):
   if not self.permits.acquire(False):connection.close();return
   try:super().process_request(connection,address)
   except BaseException:self.permits.release();raise
  def process_request_thread(self,*args):
   try:super().process_request_thread(*args)
   finally:self.permits.release()
  def handle_error(self,*args):pass
 server=Server((a.bind,a.port),Handler)
 print('Serving only the four verified public files. Ctrl+C stops this publisher. Minecraft is not controlled.')
 try:server.serve_forever()
 except KeyboardInterrupt:pass
 finally:server.server_close()
if __name__=='__main__':
 try:main()
 except (OSError,ValueError,subprocess.SubprocessError) as error:raise SystemExit(str(error))
