#!/usr/bin/env python3
"""Actual TLS/range/resume/cancel/corruption and fresh same-code archive refresh. No Minecraft/Windows process."""
from pathlib import Path
import argparse,hashlib,http.server,json,os,re,shutil,ssl,subprocess,tempfile,threading,sys,uuid

def main():
 p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);p.add_argument('--classes',type=Path,required=True);p.add_argument('--fixtures',type=Path,required=True);a=p.parse_args();java=a.java.resolve();cp=str(a.classes.resolve())+os.pathsep+str(a.fixtures.resolve());sys.path.insert(0,str(a.project.resolve()/'tools'))
 from first58.http_range86 import serve
 with tempfile.TemporaryDirectory(prefix='genesis-range86-') as temp:
  root=Path(temp);cert=root/'cert.pem';key=root/'key.pem';trust=root/'trust.p12'
  def run(cmd):return subprocess.run(list(map(str,cmd)),capture_output=True,text=True,timeout=180,check=True)
  run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost','-keyout',key,'-out',cert]);run([java.with_name('keytool'),' -importcert'.strip(),'-noprompt','-alias','fixture86','-file',cert,'-keystore',trust,'-storepass','fixture86'])
  props=['-Djavax.net.ssl.trustStore='+str(trust),'-Djavax.net.ssl.trustStorePassword=fixture86'];cmd=[java,*props,'-cp',cp,'com.fixmer.genesis.verification.UpdaterProbe86'];state=dict(label='r1',mode='normal',ranges=[],requests=[])
  class Handler(http.server.BaseHTTPRequestHandler):
   def log_message(self,*args):pass
   def do_GET(self):
    state['requests'].append(self.path)
    folder=root/'releases'/state['label']/'public';name=self.path.removeprefix('/genesis/')
    if name=='bootstrap76/state':
     state['serial']+=1;run([*cmd,'state',root,state['label'],state['owner'],state.get('generation','1'),str(state['serial'])])
     data=(root/'state86').read_bytes();self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data);return
    if name.startswith('bootstrap76/'):
     match=re.fullmatch(r'bootstrap76/([a-f0-9-]{36})/(public\.der|offer\.bin|offer\.sig|client-[a-f0-9]{64}\.zip)',name)
     if not match:self.send_error(404);return
     # Fixture exposes only the currently selected public directory, same as shipped Routes76.
     name=match[2]
    if not re.fullmatch(r'public\.der|offer\.bin|offer\.sig|client-[a-f0-9]{64}\.zip',name) or not (folder/name).is_file():self.send_error(404);return
    file=folder/name
    if file.suffix=='.zip':
     state['ranges'].append(self.headers.get('Range'))
     if state['mode']=='cut':
      data=file.read_bytes();self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data[:131072]);self.close_connection=True;return
     if state['mode']=='wrong-range' and self.headers.get('Range'):
      self.send_response(206);self.send_header('ETag','"'+'0'*64+'"');self.send_header('Content-Range','bytes 0-1/2');self.send_header('Content-Length','2');self.end_headers();self.wfile.write(b'xx');return
     if state['mode']=='ignore-range':
      data=file.read_bytes();self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data);return
     if state['mode']=='corrupt':
      data=bytearray(file.read_bytes());data[-1]^=1;self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data);return
    try:serve(self,file)
    except (OSError,ssl.SSLError):self.close_connection=True
  server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);server.daemon_threads=True;context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(cert,key);server.socket=context.wrap_socket(server.socket,server_side=True);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start();endpoint=f'https://localhost:{server.server_port}/genesis/';checks=0
  def probe(cache,mode='normal',action='download'):
   state.update(mode=mode,ranges=[]);r=run([*cmd,action,root/cache,endpoint]);return r.stdout.strip()
  def partial(cache):return list((root/cache/'partials86').glob('*/client.part'))
  try:
   run([*cmd,'fixture',root,endpoint]);assert 'REMOTE_DOWNLOAD_TRUNCATED' in probe('resume','cut');parts=partial('resume');assert len(parts)==1 and parts[0].stat().st_size==131072;checks+=2
   result=probe('resume');assert result.startswith('OK '),result;assert state['ranges']==['bytes=131072-'];assert not partial('resume');checks+=3
   assert 'REMOTE_CANCELLED' in probe('cancel',action='cancel');assert partial('cancel');checks+=2
   assert probe('cancel').startswith('OK ');assert state['ranges'][0].startswith('bytes=');checks+=2
   assert 'REMOTE_DOWNLOAD_TRUNCATED' in probe('fallback','cut');assert probe('fallback','ignore-range').startswith('OK ');checks+=2
   assert 'REMOTE_DOWNLOAD_TRUNCATED' in probe('bad-range','cut');assert 'REMOTE_RESUME_RANGE' in probe('bad-range','wrong-range');assert not partial('bad-range');assert probe('bad-range').startswith('OK ');checks+=4
   assert 'REMOTE_ARCHIVE_HASH' in probe('hash','corrupt');assert not partial('hash');checks+=2
   # A changed signed release never receives old partial bytes.
   assert 'REMOTE_DOWNLOAD_TRUNCATED' in probe('changed','cut');state['label']='r2';assert probe('changed').startswith('OK ');assert state['ranges']==[None];checks+=3
   state['label']='r1';state['mode']='normal';owner=str(uuid.uuid4());state.update(owner=owner,serial=10);run([*cmd,'state',root,'r1',owner,'1','10']);cache=root/'bootstrap-cache';candidate=Path(run([*cmd,'bootstrap',cache,endpoint]).stdout.strip());checks+=1
   state['label']='r2';run([*cmd,'state',root,'r2',owner,'1','11']);freshcmd=[java,*props,'-cp',cp,'com.fixmer.genesis.technology.server.remote.BootstrapRenewal86'];join=run([*freshcmd,'join',cache,candidate,'1.21.1','21.1.264']);assert join.stdout.strip();checks+=1
   refresh=json.loads(run([*freshcmd,'refresh',cache,candidate,'1.21.1','21.1.264']).stdout);new=Path(refresh['candidate']);assert new!=candidate;assert new.is_dir();assert (new/'bootstrap76.cfg').is_file();assert refresh['installAuthority'] is False;checks+=4
   # Old candidate may be expired. Fresh target is still required, and bytes are compared to the new full archive.
   run([*cmd,'expire',candidate,root/'id']);run([*freshcmd,'join',cache,candidate,'1.21.1','21.1.264']);json.loads(run([*freshcmd,'refresh',cache,candidate,'1.21.1','21.1.264']).stdout);checks+=2
   state['label']='r3';run([*cmd,'state',root,'r3',owner,'1','12']);bad=subprocess.run(list(map(str,[*freshcmd,'refresh',cache,candidate,'1.21.1','21.1.264'])),capture_output=True,text=True);assert bad.returncode!=0 and 'BOOTSTRAP86_' in bad.stderr;checks+=1
   state['label']='r2';state['owner']=str(uuid.uuid4());run([*cmd,'state',root,'r2',state['owner'],'1','13']);bad=subprocess.run(list(map(str,[*freshcmd,'join',cache,new,'1.21.1','21.1.264'])),capture_output=True,text=True);assert bad.returncode!=0;checks+=1
   print(f'Updater86 actual TLS truncation/range/cancel/fallback/hash/release-isolation/fresh-rebind checks passed: {checks}; signed bootstrap READY is a fixture, no Minecraft/native process.')
  finally:server.shutdown();server.server_close();thread.join(timeout=5)
if __name__=='__main__':main()
