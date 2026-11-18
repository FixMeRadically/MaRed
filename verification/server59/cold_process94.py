#!/usr/bin/env python3
"""Real owner death and guarded JVM/HTTP recovery. Fixture processes are not Minecraft."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,hmac,http.client,os,socket,subprocess,tempfile,time,uuid

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--classes',required=True,type=Path);p.add_argument('--fixtures',required=True,type=Path);a=p.parse_args();java=a.java;classes=str(a.classes.resolve());cp=str(a.fixtures.resolve())+os.pathsep+classes;count=0
 def ok(v):
  nonlocal count
  count+=1;assert v,count
 def wait(fn,timeout=14):
  end=time.monotonic()+timeout
  while time.monotonic()<end:
   try:
    r=fn()
    if r:return r
   except (OSError,ValueError,KeyError,http.client.HTTPException):pass
   time.sleep(.025)
  raise AssertionError('fixture wait timed out')
 def cmd(mode,game):return [java,'-cp',cp,'com.fixmer.genesis.supervisor.ColdProcessFixture94',mode,str(game)]
 def run(mode,game):return subprocess.run(cmd(mode,game),capture_output=True,text=True,timeout=15)
 def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
 def props(text):return dict(line.split('=',1) for line in text.splitlines() if '=' in line and not line.startswith(('#','!')))
 def esc(s):return str(s).replace('\\','\\\\').replace('\n','\\n').replace('\r','\\r')
 owners=[];readers=[];outputs=[]
 with tempfile.TemporaryDirectory(prefix='Genesis cold94 ') as raw:
  root=Path(raw);game=root/'profile space';ok(run('prepare',game).returncode==0);data=game/'config/mared/dev';config=data/'supervisor.properties';target=game/'mods/mared-dev-example.jar';old_hash=sha(target)
  with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
  token=uuid.uuid4().hex+uuid.uuid4().hex;instance=str(uuid.uuid4());values={'gameDir':game,'workDir':game,'controlPort':port,'controlToken':token,'instanceId':instance,'logFile':data/'supervisor.log','restartUi.enabled':'false','fastLaunch.enabled':'false','cds.mode':'off','gracefulStopMs':'200','restartStopBudgetMs':'1000'}
  values.update({'command.'+str(i):v for i,v in enumerate(cmd('game',game))});config.write_text(''.join(k+'='+esc(v)+'\n' for k,v in values.items()),encoding='utf8')
  def owner(cold=False):
   log=root/('owner-'+str(len(owners))+'.log');out=log.open('w');outputs.append(out);command=[java,'-Djava.awt.headless=true','--add-modules=jdk.httpserver','-cp',cp,'com.fixmer.genesis.supervisor.Supervisor','--config='+str(config)]+(['--recover-cold94'] if cold else []);proc=subprocess.Popen(command,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True);owners.append(proc);return proc,log
  def check():return subprocess.run([java,'-cp',cp,'com.fixmer.genesis.supervisor.ColdOwner94','--config='+str(config),'--check'],capture_output=True,text=True,timeout=15)
  def rpc(path='/recovery92',method='GET',headers=None):
   h=dict(headers or {});nonce=uuid.uuid4().hex;epoch=str(int(time.time()*1000));h.update({'X-Genesis-Instance':instance,'X-Genesis-Recovery-Nonce':nonce,'X-Genesis-Recovery-Time':epoch});message='\n'.join(['GENESIS_RECOVERY92',instance,method,path,nonce,epoch,h.get('X-Genesis-Recovery-Ticket',''),h.get('X-Genesis-Recovery-Revision',''),h.get('X-Genesis-Recovery-Action','')]);h['X-Genesis-Recovery-Proof']=hmac.new(token.encode(),message.encode(),hashlib.sha256).hexdigest();c=http.client.HTTPConnection('127.0.0.1',port,timeout=3)
   try:
    c.request(method,path,headers=h);r=c.getresponse();body=r.read(16385).decode('utf8');signature=r.getheader('X-Genesis-Recovery-Response','')
    if r.status in (200,202,409):assert hmac.compare_digest(signature,hmac.new(token.encode(),('GENESIS_RECOVERY92_RESPONSE\n'+nonce+'\n'+str(r.status)+'\n'+body).encode(),hashlib.sha256).hexdigest())
    return r.status,body
   finally:c.close()
  def status():
   code,text=rpc();return props(text) if code==200 else {}
  def ready():
   s=status();return s if s.get('state')=='RECOVERY_REQUIRED' and s.get('busy')=='false' and s.get('used')=='false' else None
  def matching(test):
   s=ready();return s if s and test(s) else None
  def choose(s,action):return rpc('/recovery92/decide','POST',{'X-Genesis-Recovery-Ticket':s['ticket'],'X-Genesis-Recovery-Revision':s.get('revision',''),'X-Genesis-Recovery-Action':action})[0]
  def stop_legacy():
   c=http.client.HTTPConnection('127.0.0.1',port,timeout=3)
   try:c.request('POST','/stop',headers={'X-Genesis-Token':token});r=c.getresponse();r.read();return r.status
   finally:c.close()
  try:
   first,log=owner();wait(lambda:(game/'fixture-entered94').exists());candidate=target.read_bytes();candidate_hash=sha(target);ok(candidate_hash!=old_hash);ok(first.poll() is None)
   marker=data/'cold-owner94.properties';receipt=data/'profile-admitted94.properties';wait(lambda:receipt.exists());ok(marker.exists());ok(token not in marker.read_text()+receipt.read_text());busy=check();ok(busy.returncode!=0 and 'COLD94_OWNER_IN_USE' in busy.stderr);ok(sha(target)==candidate_hash)
   first.kill();first.wait(8);ok(run('probe',game).returncode==3);busy=check();ok(busy.returncode!=0 and 'PROFILE92_IN_USE' in busy.stderr);ok(sha(target)==candidate_hash)
   denied,deniedlog=owner(True);denied.wait(10);ok(denied.returncode==2);ok(run('probe',game).returncode==3);ok(sha(target)==candidate_hash)
   (game/'fixture-crash94').write_text('fixture stop');wait(lambda:run('probe',game).returncode==0);ok(check().returncode==0);ok(sha(target)==candidate_hash)
   # Guard request alone is insufficient: exact authenticated premain receipt is required.
   original_receipt=receipt.read_bytes();receipt.unlink();ok(check().returncode!=0);receipt.write_bytes(original_receipt)
   original_marker=marker.read_bytes();marker.write_text(marker.read_text()+'schema=94\n');ok(check().returncode!=0);marker.write_bytes(original_marker)
   ok(run('record-only',game).returncode==0);ok(check().returncode!=0);ok(sha(target)==candidate_hash);marker.write_bytes(original_marker);ok(check().returncode==0)
   pending=data/'deployments/pending.properties';pending.write_text('unexpected=pending\n');ok(check().returncode!=0);pending.unlink()
   target.write_bytes(b'FOREIGN_EDIT');ok(check().returncode!=0);ok(target.read_bytes()==b'FOREIGN_EDIT');target.write_bytes(candidate)
   # A foreign occupant of the old port is never contacted or stopped; bind fails before mutation.
   with socket.socket() as foreign:
    foreign.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);foreign.bind(('127.0.0.1',port));foreign.listen(2);blocked,blockedlog=owner(True);blocked.wait(10);ok(blocked.returncode!=0);ok(sha(target)==candidate_hash);ok(check().returncode==0)
    foreign.settimeout(.1)
    try:connection,_=foreign.accept();connection.close();raise AssertionError('foreign port was contacted')
    except socket.timeout:ok(True)
   cold,log=owner(True);s=wait(ready);ok(s['origin']=='cold94');ok(sha(target)==candidate_hash);ok(cold.poll() is None);ok(check().returncode!=0)
   old_ticket=s;ok(stop_legacy()==202);cold.wait(10);ok(sha(target)==candidate_hash);ok(check().returncode==0)
   cold,log=owner(True);s=wait(ready);ok(s['ticket']!=old_ticket['ticket']);ok(choose(old_ticket,'RESTORE')==409)
   # Review does not keep a permissive claim: reacquire the profile writer for every decision.
   reader=subprocess.Popen([java,'-cp',cp,'com.fixmer.genesis.supervisor.RecoveryProcessFixture92','reader',str(game)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True);readers.append(reader);ok(reader.stdout.readline().strip()=='READER_READY');ok(choose(s,'RESTORE')==202);s=wait(lambda:matching(lambda q:q.get('error')=='PROFILE92_IN_USE'));ok(sha(target)==candidate_hash);ok(reader.poll() is None);reader.stdin.write('x');reader.stdin.flush();reader.wait(8)
   cfg=config.read_bytes();config.write_bytes(cfg+b'\n# changed after review\n');ok(choose(s,'RESTORE')==202);s=wait(lambda:matching(lambda q:not q.get('phase')));ok(sha(target)==candidate_hash);config.write_bytes(cfg);ok(choose(s,'REFRESH')==202);s=wait(lambda:matching(lambda q:q.get('phase')=='CHOICE_REQUIRED'))
   # One explicit retry, new owner-controlled launch, new receipt; no hidden retry loop.
   with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda _:choose(s,'RETRY'),range(2)))
   ok(sorted(results)==[202,409]);hot=wait(lambda:matching(lambda q:q.get('origin')=='owned_exit92'));ok(sha(target)==candidate_hash);ok(props(marker.read_text())['record']!=props(original_marker.decode())['record']);ok(choose(s,'RETRY')==409)
   cold.kill();cold.wait(8);ok(check().returncode==0)
   final,log=owner(True);s=wait(ready);ok(s['origin']=='cold94');ok(choose(s,'RESTORE')==202);wait(lambda:(game/'fixture-menu92.txt').exists());ok(sha(target)==old_hash);ok((data/'deployments/world-review91.properties').exists());ok('kind=hold91' in (data/'resume.properties').read_text());ok(status()['state']=='RUNNING');ok(stop_legacy()==202);final.wait(12);ok(run('probe',game).returncode==0)
  except Exception:
   for out in outputs:out.flush()
   for log in root.glob('owner-*.log'):print(log.name+': '+log.read_text(errors='replace')[-2500:],flush=True)
   raise
  finally:
   (game/'fixture-crash94').write_text('fixture cleanup')
   for r in readers:
    if r.poll() is None:r.stdin.write('x');r.stdin.flush();r.wait(8)
   for proc in owners:
    if proc.poll() is None:proc.kill();proc.wait(8)
   for folder in (data/'runtime').glob('*'):
    if folder.is_dir():(folder/'shutdown.request').write_text(folder.name)
   wait(lambda:run('probe',game).returncode==0,40)
   for out in outputs:out.close()
 print('Cold94 actual owner-death/premain/locks/HMAC/CAS/retry/restore checks passed:',count,'; game fixtures are not Minecraft; Windows remains unverified.')
if __name__=='__main__':main()
