#!/usr/bin/env python3
"""Actual supervisor/child JVM and authenticated HTTP. Fixtures do not load Minecraft."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,hmac,http.client,json,os,socket,subprocess,tempfile,time,uuid

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--classes',required=True,type=Path);p.add_argument('--fixtures',required=True,type=Path);a=p.parse_args();cp=str(a.fixtures.resolve())+os.pathsep+str(a.classes.resolve());count=0
 def ok(value):
  nonlocal count
  count+=1;assert value,count
 def wait(fn,seconds=14):
  end=time.monotonic()+seconds
  while time.monotonic()<end:
   try:
    value=fn()
    if value:return value
   except (OSError,ValueError,KeyError,http.client.HTTPException):pass
   time.sleep(.025)
  raise AssertionError('fixture wait timed out')
 def props(text):return dict(line.split('=',1) for line in text.splitlines() if '=' in line and not line.startswith(('#','!')))
 def esc(v):return str(v).replace('\\','\\\\')
 with tempfile.TemporaryDirectory(prefix='Genesis restart95 ') as raw:
  root=Path(raw);game=root/'profile space';game.mkdir();data=game/'config/mared/dev';data.mkdir(parents=True);config=data/'supervisor.properties'
  with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
  token=uuid.uuid4().hex+uuid.uuid4().hex;instance=str(uuid.uuid4());values={'gameDir':game,'workDir':game,'controlPort':port,'controlToken':token,'instanceId':instance,'logFile':data/'supervisor.log','restartUi.enabled':'false','fastLaunch.enabled':'false','cds.mode':'off','gracefulStopMs':'1000','restartStopBudgetMs':'1500'}
  values.update({'command.'+str(i):v for i,v in enumerate([a.java,'-cp',cp,'com.fixmer.genesis.supervisor.RestartProcessFixture95',str(game)])});config.write_text(''.join(k+'='+esc(v)+'\n' for k,v in values.items()))
  def wire_http(path,method='GET',headers=None):
   c=http.client.HTTPConnection('127.0.0.1',port,timeout=3)
   try:c.request(method,path,headers=headers or {});r=c.getresponse();return r.status,r.read(16385).decode(),{k.lower():v for k,v in r.getheaders()}
   finally:c.close()
  def ready():
   code,body,_=wire_http('/status',headers={'X-Genesis-Token':token});s=json.loads(body);return code==200 and s['state']=='RUNNING' and not s['busy']
  def current():return (game/'fixture-current95').read_text().splitlines()
  def starts():return len((game/'fixture-starts95').read_text().splitlines())
  def headers(path,method,request,snapshot,grant='',**overrides):
   nonce=uuid.uuid4().hex;epoch=str(int(time.time()*1000));h={'X-Genesis-Instance':instance,'X-Genesis-Recovery-Nonce':nonce,'X-Genesis-Recovery-Time':epoch,'X-Genesis-Recovery-Ticket':request,'X-Genesis-Recovery-Revision':snapshot,'X-Genesis-Recovery-Action':grant};h.update(overrides)
   wire='\n'.join(['GENESIS_RECOVERY92',h['X-Genesis-Instance'],method,path,h['X-Genesis-Recovery-Nonce'],h['X-Genesis-Recovery-Time'],h['X-Genesis-Recovery-Ticket'],h['X-Genesis-Recovery-Revision'],h['X-Genesis-Recovery-Action']]);h['X-Genesis-Recovery-Proof']=hmac.new(token.encode(),wire.encode(),hashlib.sha256).hexdigest();return h
  def rpc(action,request,snapshot,grant='',extra=None):
   path='/restart95/'+action;method='GET' if action=='status' else 'POST';h=headers(path,method,request,snapshot,grant);h.update(extra or {});code,body,rh=wire_http(path,method,h)
   if code in (200,202,409):ok(hmac.compare_digest(rh.get('x-genesis-recovery-response',''),hmac.new(token.encode(),('GENESIS_RECOVERY92_RESPONSE\n'+h['X-Genesis-Recovery-Nonce']+'\n'+str(code)+'\n'+body).encode(),hashlib.sha256).hexdigest()))
   return code,props(body)
  def bound():
   launch,epoch=current();return epoch+'.'+str(uuid.uuid4())+'/'+launch
  out=(root/'owner.log').open('w');owner=subprocess.Popen([a.java,'-Djava.awt.headless=true','--add-modules=jdk.httpserver','-cp',cp,'com.fixmer.genesis.supervisor.Supervisor','--config='+str(config)],stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
  try:
   wait(lambda:(game/'fixture-current95').exists() and ready());time.sleep(.4);ok(starts()==1)
   request=str(uuid.uuid4());snapshot=bound();code,p=wait(lambda:(q if (q:=rpc('prepare',request,snapshot))[0]==202 else None));ok(code==202 and p.get('state')=='PREPARED');ok(starts()==1);stop=data/'runtime'/current()[0]/'shutdown.request';ok(not stop.exists())
   ok(rpc('prepare',str(uuid.uuid4()),snapshot)[0]==409);ok(rpc('commit',request,snapshot,str(uuid.uuid4()))[0]==409);ok(not stop.exists())
   ok(rpc('cancel',request,snapshot)[1]['state']=='CANCELLED');ok(rpc('commit',request,snapshot,p['grant'])[0]==409);ok(ready());ok(starts()==1)
   request=str(uuid.uuid4());snapshot=bound();code,p=rpc('prepare',request,snapshot);ok(code==202);ok(rpc('commit',request,snapshot,p['grant'])[0]==202);wait(stop.exists);ok(stop.read_text()==current()[0]+'\nrestart95='+request)
   ok(rpc('cancel',request,snapshot)[1]['state']=='CANCELLED');wait(lambda:ready() and not stop.exists());ok(starts()==1)
   old=request;request=str(uuid.uuid4());snapshot=bound();_,p=rpc('prepare',request,snapshot);ok(rpc('cancel',old,snapshot)[0]==409)
   path='/restart95/status';h=headers(path,'GET',request,snapshot);code,_,_=wire_http(path,'GET',h);ok(code==200);ok(wire_http(path,'GET',h)[0]==403);ok(rpc('status',request,snapshot,extra={'Origin':'http://example.invalid'})[0]==403);ok(rpc('status',request,snapshot,extra={'X-Genesis-Recovery-Proof':'0'*64})[0]==403)
   ok(rpc('status',request,snapshot.rsplit('/',1)[0]+'/'+str(uuid.uuid4()))[0]==409)
   ok(wire_http('/restart95/commit','GET')[0]==405)
   (game/'fixture-allow95').write_text('explicit fixture exit')
   with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda _:rpc('commit',request,snapshot,p['grant'])[0],range(2)))
   ok(all(code in (202,409) for code in results) and 202 in results);wait(lambda:starts()==2 and ready());ok(starts()==2);code,state=rpc('status',request,snapshot);ok(code==200 and state['state']=='DONE');ok(rpc('commit',request,snapshot,p['grant'])[0]==409)
   request=str(uuid.uuid4());snapshot=bound();ok(rpc('prepare',request,'-. '+str(uuid.uuid4())+'/'+current()[0])[0]==409)
   _,p=rpc('prepare',request,snapshot);(game/'fixture-fail-save95').write_text('explicit failed save fixture');ok(rpc('commit',request,snapshot,p['grant'])[0]==202)
   state=wait(lambda:(q if (q:=rpc('status',request,snapshot)[1]).get('state')=='HOLD' else None));ok(state['reason']=='save_exit_unconfirmed');ok(starts()==2);ok(owner.poll() is None)
   report=wait(lambda:props((data/'restart95-last.properties').read_text()));ok('grant' not in report and token not in str(report));ok(not list(game.glob('mods/*.jar')))
  except Exception:
   out.flush();print((root/'owner.log').read_text(errors='replace')[-6000:]);raise
  finally:
   try:wire_http('/stop','POST',{'X-Genesis-Token':token})
   except OSError:pass
   try:owner.wait(10)
   except subprocess.TimeoutExpired:owner.kill();owner.wait(8)
   out.close()
 print('Restart95 actual JVM/HTTP lifecycle checks passed:',count,'; lifecycle/save fixtures are not Minecraft; Windows remains unverified.')
if __name__=='__main__':main()
