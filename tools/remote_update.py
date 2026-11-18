#!/usr/bin/env python3
"""Remote69 publisher tools. Public export never contains a signing/TLS private key or profile."""
from pathlib import Path
import argparse,errno,http.server,json,os,shutil,ssl,subprocess,urllib.parse,threading,socket
from first58.common import safe
from first58.runtime import core_classpath

class LoopbackPublisher162:
 """One TLS publisher bound to both loopback families with a shared request budget."""
 def __init__(self,primary,companion):
  self.primary=primary;self.companion=companion

 def serve_forever(self):
  worker=threading.Thread(target=self.companion.serve_forever,name='genesis-https162-loopback',daemon=True)
  worker.start()
  try:
   self.primary.serve_forever()
  finally:
   if worker.is_alive():self.companion.shutdown()
   worker.join()

 def shutdown(self):
  self.primary.shutdown()

 def close_connections(self):
  self.primary.close_connections()

 def server_close(self):
  try:self.primary.server_close()
  finally:self.companion.server_close()


def publisher_server(project,java,public,certificate,private_key,bind,port,owner75=None,control_root=None):
 """Build the same bounded TLS server for the CLI or its independent server owner."""
 def core(*args):
  r=subprocess.run([str(java),'-XX:-UsePerfData','-cp',str(core_classpath(project)),'com.fixmer.genesis.technology.server.remote.RemoteUpdateCli69',*map(str,args)],capture_output=True,text=True,timeout=300,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
  if r.returncode:raise ValueError('HTTPS78_PUBLIC_REJECTED')
  return r.stdout
 folder=safe(public)
 # An expired baseline still anchors the next signed release. HTTP selections use fresh validation below.
 historical=owner75 and (safe(owner75)/'owner118').exists()
 info=json.loads(core('verify-public-history' if historical else 'verify-public',folder))
 prefix=urllib.parse.urlsplit(info['endpoint']).path;allowed={'public.der','offer.bin','offer.sig',info['archive']}
 cert=safe(certificate);key=safe(private_key)
 if folder==key.parent or folder in key.parents:raise ValueError('TLS private key must be outside the public directory.')
 dynamic=None
 if owner75:
  from first58.publication75 import Publication75
  dynamic=Publication75(safe(owner75),folder,lambda candidate:json.loads(core('verify-public',candidate)),initial_info=info)
  if control_root and safe(control_root)!=safe(owner75/'control73'):raise ValueError('Owner/control roots differ.')
  control_root=safe(owner75/'control73')
 bootstrap=None
 if owner75 and (safe(owner75)/'owner76').exists():
  from first58.https_bootstrap76 import Routes76
  bootstrap=Routes76(safe(owner75),prefix,dynamic,lambda candidate:json.loads(core('verify-public',candidate)))
 routes=None
 if control_root:
  from first58.https_control73 import Routes73
  control=safe(control_root)
  if control==folder or folder in control.parents or control in folder.parents:raise ValueError('Control root must be disjoint from public export.')
  routes=Routes73(control,prefix)
 class Handler(http.server.BaseHTTPRequestHandler):
  def log_message(self,*args):pass # Do not echo request lines, queries or headers.
  def do_POST(self):
   if bootstrap and bootstrap.route(self):return
   if routes and routes.route(self):return
   self.send_error(404)
  def do_GET(self):
   if bootstrap and bootstrap.route(self):return
   if routes and routes.route(self):return
   try:current,current_allowed=dynamic.snapshot() if dynamic else (folder,allowed)
   except (OSError,ValueError,KeyError,subprocess.SubprocessError):self.send_error(503);return
   if not self.path.startswith(prefix) or self.path[len(prefix):] not in current_allowed:self.send_error(404);return
   try:
    file=safe(current/self.path[len(prefix):]);from first58.http_range86 import serve
    serve(self,file)
   except (OSError,ValueError):self.close_connection=True
 context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.minimum_version=ssl.TLSVersion.TLSv1_2;context.load_cert_chain(cert,key,password=lambda: "")
 permits=threading.BoundedSemaphore(16);connections=set();connections_lock=threading.Lock()
 class Server(http.server.ThreadingHTTPServer):
  daemon_threads=True;request_queue_size=16
  def __init__(self,address):
   self.address_family=socket.AF_INET6 if ':' in address[0] else socket.AF_INET
   self.permits=permits;self.connections=connections;self.connections_lock=connections_lock
   super().__init__(address,Handler)
  def get_request(self):
   connection,address=super().get_request();connection.settimeout(10)
   try:wrapped=context.wrap_socket(connection,server_side=True,do_handshake_on_connect=False)
   except BaseException:connection.close();raise
   with self.connections_lock:self.connections.add(wrapped)
   return wrapped,address
  def process_request(self,connection,address):
   if not self.permits.acquire(False):
    with self.connections_lock:self.connections.discard(connection)
    connection.close();return
   try:super().process_request(connection,address)
   except BaseException:
    with self.connections_lock:self.connections.discard(connection)
    connection.close();self.permits.release();raise
  def process_request_thread(self,*args):
   try:super().process_request_thread(*args)
   finally:
    with self.connections_lock:self.connections.discard(args[0])
    self.permits.release()
  def close_connections(self):
   with self.connections_lock:connections=list(self.connections)
   for connection in connections:
    try:connection.shutdown(2)
    except OSError:pass
    connection.close()
  def handle_error(self,*args):pass
 primary=Server((bind,port))
 if urllib.parse.urlsplit(info['endpoint']).hostname!='localhost' or bind not in {'127.0.0.1','::1'}:
  return primary
 peer='::1' if bind=='127.0.0.1' else '127.0.0.1'
 try:
  companion=Server((peer,primary.server_port))
 except OSError as failure:
  unavailable={errno.EADDRNOTAVAIL,errno.EAFNOSUPPORT,errno.EPROTONOSUPPORT}
  if failure.errno in unavailable or getattr(failure,'winerror',None) in {10047,10049}:
   return primary
  primary.server_close()
  raise
 return LoopbackPublisher162(primary,companion)

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('project',type=Path);parser.add_argument('--java',type=Path)
 sub=parser.add_subparsers(dest='action',required=True)
 p=sub.add_parser('init');p.add_argument('identity',type=Path);p.add_argument('game');p.add_argument('endpoint')
 p=sub.add_parser('publish');p.add_argument('identity',type=Path);p.add_argument('run',type=Path);p.add_argument('output',type=Path);p.add_argument('--ttl',type=int,default=86400)
 p=sub.add_parser('serve');p.add_argument('public',type=Path);p.add_argument('--cert',type=Path,required=True);p.add_argument('--key',type=Path,required=True);p.add_argument('--bind',default='127.0.0.1');p.add_argument('--port',type=int,default=8443);p.add_argument('--control-root',type=Path);p.add_argument('--owner75',type=Path)
 a=parser.parse_args();project=safe(a.project);java=a.java or (Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java') if os.environ.get('JAVA_HOME') else shutil.which('java'))
 if not java:raise ValueError('Set JAVA_HOME or pass --java pointing to JDK21 java.exe.')
 def core(*args):
  r=subprocess.run([str(java),'-XX:-UsePerfData','-cp',str(core_classpath(project)),'com.fixmer.genesis.technology.server.remote.RemoteUpdateCli69',*map(str,args)],capture_output=True,text=True,timeout=300,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
  if r.returncode:raise ValueError(r.stderr.strip() or 'Remote operation failed; no game profile was installed.')
  return r.stdout
 if a.action=='init':print(core('init',safe(a.identity),a.game,a.endpoint),end='');return
 if a.action=='publish':
  run=safe(a.run);packages=run/'packages' if (run/'packages').is_dir() else run
  print(core('publish',safe(a.identity),packages,safe(a.output),a.ttl),end='');return
 server=publisher_server(project,java,a.public,a.cert,a.key,a.bind,a.port,a.owner75,a.control_root)
 print('Serving only the four verified public files. Ctrl+C stops this publisher. Minecraft is not controlled.')
 try:server.serve_forever()
 except KeyboardInterrupt:pass
 finally:server.close_connections();server.server_close()
if __name__=='__main__':
 try:main()
 except (OSError,ValueError,subprocess.SubprocessError) as error:raise SystemExit(str(error))
