"""Single bounded byte range for an already-authorized immutable archive path. No route or trust decisions."""
import os,re

def serve(handler,file):
 with file.open('rb') as stream:
  size=os.fstat(stream.fileno()).st_size;match=re.fullmatch(r'client-([a-f0-9]{64})\.zip',file.name);etag='"'+match[1]+'"' if match else None;start=0;end=size-1;partial=False;raw=handler.headers.get('Range') if etag else None
  if raw and handler.headers.get('If-Range') not in (None,etag):raw=None
  if raw:
   m=re.fullmatch(r'bytes=([0-9]{1,20})-([0-9]{1,20})?',raw)
   if not m or int(m[1])>=size or m[2] is not None and (int(m[2])<int(m[1]) or int(m[2])>=size):
    handler.send_response(416);handler.send_header('Content-Range',f'bytes */{size}');handler.send_header('Content-Length','0');handler.end_headers();return
   start=int(m[1]);end=int(m[2]) if m[2] else size-1;partial=True
  stream.seek(start);remaining=end-start+1;handler.send_response(206 if partial else 200);handler.send_header('Content-Length',str(remaining));handler.send_header('Content-Type','application/octet-stream');handler.send_header('Cache-Control','no-store')
  if etag:
   handler.send_header('ETag',etag);handler.send_header('Accept-Ranges','bytes')
  if partial:handler.send_header('Content-Range',f'bytes {start}-{end}/{size}')
  handler.end_headers()
  while remaining:
   data=stream.read(min(65536,remaining))
   if not data:raise OSError('immutable file truncated')
   handler.wfile.write(data);remaining-=len(data)
