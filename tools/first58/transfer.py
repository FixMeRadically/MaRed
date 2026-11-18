"""Authenticated loopback-only chunk transport for the SAME-PC pilot. Not remote HTTPS."""
from http.server import BaseHTTPRequestHandler,HTTPServer
from socketserver import ThreadingMixIn
from pathlib import Path
import hashlib,hmac,http.client,re,secrets,socket,threading
from .common import MAX_ARCHIVE,safe,sha
CHUNK=512*1024
class PackageServer(ThreadingMixIn,HTTPServer):
 daemon_threads=True;request_queue_size=8
 def __init__(self,archives):
  self.archives={sha(p):safe(p) for p in archives};self.token=secrets.token_hex(32);self.bound=threading.BoundedSemaphore(4)
  outer=self
  class Handler(BaseHTTPRequestHandler):
   protocol_version='HTTP/1.1'
   def setup(self):super().setup();self.connection.settimeout(5)
   def log_message(self,*args):pass
   def do_GET(self):
    try:
     if len(self.headers)>16 or self.headers.get_all('Authorization')!=['Bearer '+outer.token] or self.headers.get_all('Host')!=['127.0.0.1:'+str(outer.server_port)] or any(x in self.headers for x in ('Origin','Referer','Transfer-Encoding')):raise ValueError()
     if self.headers.get('Content-Length','0')!='0':raise ValueError()
     match=re.fullmatch(r'/v58/([a-f0-9]{64})/(0|[1-9][0-9]{0,9})',self.path)
     if not match or match[1] not in outer.archives:raise ValueError()
     path=outer.archives[match[1]];offset=int(match[2]);size=path.stat().st_size
     if size>MAX_ARCHIVE or not 0<=offset<size or offset%CHUNK:raise ValueError()
     with safe(path).open('rb') as stream:stream.seek(offset);body=stream.read(min(CHUNK,size-offset))
     self.send_response(206);self.send_header('Content-Length',str(len(body)));self.send_header('Content-Type','application/octet-stream');self.send_header('Content-Range',f'bytes {offset}-{offset+len(body)-1}/{size}');self.send_header('ETag','"'+match[1]+'"');self.send_header('Cache-Control','no-store');self.send_header('Connection','close');self.end_headers();self.wfile.write(body)
    except (OSError,ValueError):
     try:self.send_error(403)
     except OSError:pass
    finally:self.close_connection=True
  super().__init__(('127.0.0.1',0),Handler)
  self.thread=threading.Thread(target=self.serve_forever,daemon=True,name='first58-loopback');self.thread.start()
 def process_request(self,request,address):
  if not self.bound.acquire(False):request.close();return
  try:super().process_request(request,address)
  except Exception:self.bound.release();raise
 def process_request_thread(self,request,address):
  try:super().process_request_thread(request,address)
  finally:self.bound.release()
 def close(self):self.shutdown();self.server_close();self.thread.join(timeout=2)
 def __enter__(self):return self
 def __exit__(self,*args):self.close()

def download(server,expected,size,cache,progress=lambda current,total:None,cancel=lambda:False):
 if not re.fullmatch(r'[a-f0-9]{64}',expected) or not 1<=size<=MAX_ARCHIVE:raise ValueError('Download expectation')
 cache=safe(cache);cache.mkdir(exist_ok=True);final=cache/(expected+'.zip');partial=cache/(expected+'.part')
 if final.exists():
  if final.stat().st_size!=size or sha(final)!=expected:raise ValueError('Unknown cached archive; refusing overwrite')
  return final
 safe(partial);offset=partial.stat().st_size if partial.exists() else 0
 if offset>size or offset<size and offset%CHUNK:raise ValueError('Partial chunk needs explicit new download directory')
 with partial.open('ab') as output:
  while offset<size:
   if cancel():raise InterruptedError('Download cancelled')
   connection=http.client.HTTPConnection('127.0.0.1',server.server_port,timeout=8)
   try:
    connection.request('GET',f'/v58/{expected}/{offset}',headers={'Authorization':'Bearer '+server.token})
    response=connection.getresponse();wanted=min(CHUNK,size-offset)
    if response.status!=206 or response.getheader('Content-Length')!=str(wanted) or response.getheader('ETag')!='"'+expected+'"' or response.getheader('Content-Range')!=f'bytes {offset}-{offset+wanted-1}/{size}':raise ValueError('Chunk binding mismatch')
    data=response.read(wanted+1)
    if len(data)!=wanted:raise ValueError('Chunk size')
    output.write(data);output.flush();__import__('os').fsync(output.fileno());offset+=wanted;progress(offset,size)
   finally:connection.close()
 if sha(partial)!=expected:raise ValueError('Downloaded archive hash mismatch; partial retained')
 if final.exists():raise ValueError('Concurrent final archive')
 partial.rename(final);return final
