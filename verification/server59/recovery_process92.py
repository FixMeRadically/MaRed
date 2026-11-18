#!/usr/bin/env python3
"""Actual supervisor/JVM/HTTP/profile-lock integration. Child is an explicit fixture, never Minecraft."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,hmac,http.client,http.server,os,socket,subprocess,tempfile,time,uuid,threading

def main():
 p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);p.add_argument('--classes',type=Path,required=True);p.add_argument('--fixtures',type=Path,required=True);a=p.parse_args();java=str(a.java.resolve());classes=str(a.classes.resolve());cp=str(a.fixtures.resolve())+os.pathsep+classes;checks=0
 def ok(value):
  nonlocal checks
  checks+=1;assert value,checks
 def command(mode,game):return [java,'-cp',cp,'com.fixmer.genesis.supervisor.RecoveryProcessFixture92',mode,str(game)]
 def run(mode,game):return subprocess.run(command(mode,game),capture_output=True,text=True,timeout=15)
 def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
 def wait(fn,timeout=12):
  end=time.monotonic()+timeout
  while time.monotonic()<end:
   try:
    result=fn()
    if result:return result
   except (OSError,KeyError,ValueError,http.client.HTTPException):pass
   time.sleep(.03)
  raise AssertionError('fixture wait timed out')
 def prop(text):return dict(line.split('=',1) for line in text.splitlines() if '=' in line and not line.startswith(('#','!')))
 def escape(v):return str(v).replace('\\','\\\\').replace('\n','\\n').replace('\r','\\r')
 with tempfile.TemporaryDirectory(prefix='genesis recovery92 ') as raw:
  root=Path(raw)
  # Real cross-JVM locks: probing in the reader JVM must not accidentally release its lease.
  locks=root/'locks';locks.mkdir();reader=subprocess.Popen(command('reader',locks),stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
  try:
   ok(reader.stdout.readline().strip()=='READER_READY');ok(run('probe',locks).returncode==3);reader.stdin.write('x');reader.stdin.flush();reader.wait(5);ok(run('probe',locks).returncode==0)
  finally:
   if reader.poll() is None:reader.kill();reader.wait()
  writer=subprocess.Popen(command('writer',locks),stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
  try:
   ok(writer.stdout.readline().strip()=='WRITER_READY')
   log=root/'reader-waits.txt'
   with log.open('w') as out:
    r=subprocess.Popen(command('reader',locks),stdin=subprocess.PIPE,stdout=out,text=True)
    try:
     time.sleep(.15);ok(r.poll() is None and 'READER_READY' not in log.read_text());writer.stdin.write('x');writer.stdin.flush();writer.wait(5);wait(lambda:'READER_READY' in log.read_text());ok(True);r.stdin.write('x');r.stdin.flush();r.wait(5)
    finally:
     if r.poll() is None:r.kill();r.wait()
  finally:
   if writer.poll() is None:writer.kill();writer.wait()
  game=root/'profile';ok(run('prepare',game).returncode==0);data=game/'config/mared/dev';config=data/'supervisor.properties'
  with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
  token=uuid.uuid4().hex+uuid.uuid4().hex;instance=str(uuid.uuid4());values=dict(gameDir=game,workDir=game,controlPort=port,controlToken=token,instanceId=instance,logFile=data/'logs/supervisor.log',**{'restartUi.enabled':'false','fastLaunch.enabled':'false','cds.mode':'off','gracefulStopMs':'200','restartStopBudgetMs':'1000','session.startupTimeoutMs':'500'})
  values.update({'command.'+str(i):v for i,v in enumerate(command('game',game))});config.write_text(''.join(k+'='+escape(v)+'\n' for k,v in values.items()),encoding='utf8')
  def rpc(path='/recovery92',method='GET',headers=None,auth=token):
   c=http.client.HTTPConnection('127.0.0.1',port,timeout=3)
   try:
    hdr={'X-Genesis-Token':auth,**(headers or {})};nonce=''
    if path.startswith('/recovery92'):
     hdr.pop('X-Genesis-Token');nonce=uuid.uuid4().hex;epoch=str(int(time.time()*1000));hdr.setdefault('X-Genesis-Instance',instance);hdr.update({'X-Genesis-Recovery-Nonce':nonce,'X-Genesis-Recovery-Time':epoch});message='\n'.join(['GENESIS_RECOVERY92',hdr['X-Genesis-Instance'],method,path,nonce,epoch,hdr.get('X-Genesis-Recovery-Ticket',''),hdr.get('X-Genesis-Recovery-Revision',''),hdr.get('X-Genesis-Recovery-Action','')]);hdr['X-Genesis-Recovery-Proof']=hmac.new(auth.encode(),message.encode(),hashlib.sha256).hexdigest()
    c.request(method,path,headers=hdr);r=c.getresponse();body=r.read(16385).decode('utf8')
    if nonce and r.status in (200,202,409):assert hmac.compare_digest(r.getheader('X-Genesis-Recovery-Response',''),hmac.new(auth.encode(),('GENESIS_RECOVERY92_RESPONSE\n'+nonce+'\n'+str(r.status)+'\n'+body).encode(),hashlib.sha256).hexdigest())
    return r.status,body
   finally:c.close()
  def status():
   code,text=rpc();return prop(text) if code==200 else {}
  def ready():
   s=status();return s if s.get('state')=='RECOVERY_REQUIRED' and s.get('busy')=='false' and s.get('used')=='false' else None
  def choose(s,action,inst=instance):return rpc('/recovery92/decide','POST',{'X-Genesis-Instance':inst,'X-Genesis-Recovery-Ticket':s['ticket'],'X-Genesis-Recovery-Revision':s.get('revision',''),'X-Genesis-Recovery-Action':action})[0]
  target=game/'mods/mared-dev-example.jar';ownerlog=root/'owner.log'
  with ownerlog.open('w') as output:
   owner=subprocess.Popen([java,'-Djava.awt.headless=true','--add-modules=jdk.httpserver','-cp',cp,'com.fixmer.genesis.supervisor.Supervisor','--config='+str(config)],stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True)
   try:
    s=wait(ready);candidate=digest(target);ok(s['phase']=='CHOICE_REQUIRED');ok(owner.poll() is None);ok(run('probe',game).returncode==0);ok(rpc(auth='wrong')[0]==403);ok(rpc(headers={'Origin':'http://foreign.invalid'})[0]==403);ok(rpc(method='POST')[0]==405);ok(choose(s,'RESTORE',str(uuid.uuid4()))==403);ok(choose(s,'SHELL')==409)
    query=subprocess.run([java,'-cp',classes,'com.fixmer.genesis.supervisor.RecoveryBootstrap92','--config='+str(config),'--status'],capture_output=True,text=True,timeout=10);ok(query.returncode==0 and 'state=RECOVERY_REQUIRED' in query.stdout);ok(token not in query.stdout+query.stderr);ok(str(game) not in query.stdout)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:ok(all(code==200 for code in pool.map(lambda _:rpc()[0],range(64))))
    copied=config.with_name('copied-config92.properties');copied.write_bytes(config.read_bytes());wrong=subprocess.run([java,'-cp',classes,'com.fixmer.genesis.supervisor.RecoveryBootstrap92','--config='+str(copied),'--status'],capture_output=True,text=True,timeout=10);ok(wrong.returncode==2);ok(digest(target)==candidate)
    # Another cooperative profile process blocks all recovery mutation; it is never stopped/adopted.
    other=subprocess.Popen(command('reader',game),stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
    try:
     ok(other.stdout.readline().strip()=='READER_READY');ok(choose(s,'RESTORE')==202);blocked=wait(lambda:(t if (t:=ready()) and t['ticket']!=s['ticket'] else None));ok(blocked['error']!='');ok(digest(target)==candidate);ok(other.poll() is None)
    finally:
     other.stdin.write('x');other.stdin.flush();other.wait(5)
    s=blocked
    # Single reservation across concurrent HTTP workers; no duplicate attempt.
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:codes=list(pool.map(lambda _:choose(s,'RETRY'),range(2)))
    ok(sorted(codes)==[202,409]);again=wait(lambda:(t if (t:=ready()) and t['ticket']!=s['ticket'] else None));ok(digest(target)==candidate);ok(again['revision']!=s['revision']);ok(choose(s,'RESTORE')==409);ok((game/'fixture-starts92.txt').read_text().count('START')==2)
    ok(choose(again,'RESTORE')==202);wait(lambda:(game/'fixture-menu92.txt').is_file());running=wait(lambda:(t if (t:=status()).get('state')=='RUNNING' else None));ok(digest(target)!=candidate);ok(run('probe',game).returncode==3);ok(choose(again,'RETRY')==409);ok((data/'deployments/world-review91.properties').is_file());ok('kind=hold91' in (data/'resume.properties').read_text());ok('trial.properties' not in [p.name for p in (data/'deployments').iterdir()])
    ok(rpc('/stop','POST')[0]==202);owner.wait(8);ok(owner.returncode==0);ok(run('probe',game).returncode==0)
   finally:
    if not (game/'fixture-menu92.txt').exists():print(ownerlog.read_text(errors='replace')[-8000:].replace(token,'[fixture-token]'),flush=True)
    if owner.poll() is None:owner.kill();owner.wait()
  # Dead owner: the client refuses, and does not create an offline writer fallback.
  after=digest(target);dead=subprocess.run([java,'-cp',classes,'com.fixmer.genesis.supervisor.RecoveryBootstrap92','--config='+str(config),'--status'],capture_output=True,text=True,timeout=10);ok(dead.returncode==2);ok(digest(target)==after);ok(token not in dead.stdout+dead.stderr)
  # A registered, normally closed direct JVM never leaves an idle recovery owner.
  (game/'fixture-normal-close92.flag').write_text('normal exit')
  with (root/'normal-owner.log').open('w') as output:
   normal=subprocess.Popen([java,'-Djava.awt.headless=true','--add-modules=jdk.httpserver','-cp',cp,'com.fixmer.genesis.supervisor.Supervisor','--config='+str(config)],stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True)
   try:normal.wait(8);ok(normal.returncode==0);ok(digest(target)==after)
   finally:
    if normal.poll() is None:normal.kill();normal.wait()
  (game/'fixture-normal-close92.flag').write_text('normal nonzero exit')
  with (root/'normal-nonzero-owner.log').open('w') as output:
   normal=subprocess.Popen([java,'-Djava.awt.headless=true','--add-modules=jdk.httpserver','-cp',cp,'com.fixmer.genesis.supervisor.Supervisor','--config='+str(config)],stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True)
   try:normal.wait(8);ok(normal.returncode==0);ok(digest(target)==after)
   finally:
    if normal.poll() is None:normal.kill();normal.wait()
  # A port occupant receives a one-request proof, never the control secret, and cannot forge a response.
  captured=[]
  class Fake(http.server.BaseHTTPRequestHandler):
   def log_message(self,*args):pass
   def do_GET(self):
    captured.append(dict(self.headers));body=b'schema=92\nstate=RECOVERY_REQUIRED\n';self.send_response(200);self.send_header('Content-Length',str(len(body)));self.send_header('X-Genesis-Recovery-Response','f'*64);self.end_headers();self.wfile.write(body)
  fake=http.server.ThreadingHTTPServer(('127.0.0.1',port),Fake);thread=threading.Thread(target=fake.serve_forever,daemon=True);thread.start()
  try:
   foreign=subprocess.run([java,'-cp',classes,'com.fixmer.genesis.supervisor.RecoveryBootstrap92','--config='+str(config),'--status'],capture_output=True,text=True,timeout=10);ok(foreign.returncode==2);ok(len(captured)==1);ok('X-Genesis-Token' not in captured[0]);ok(token not in str(captured));ok(digest(target)==after)
  finally:fake.shutdown();fake.server_close();thread.join(2)
  print('Recovery92 actual JVM/HTTP/owner/CAS/profile-lock/retry/restore checks passed:',checks,'; child fixtures are not Minecraft; Windows remains unverified.')
if __name__=='__main__':main()
