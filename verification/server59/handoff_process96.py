#!/usr/bin/env python3
"""Actual first-owner/source JVM/HTTP/OS-lease integration. Explicit lifecycle fixtures, not Minecraft."""
from pathlib import Path
import argparse,http.client,json,os,socket,subprocess,tempfile,time,uuid

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--classes',type=Path,required=True);p.add_argument('--fixtures',type=Path,required=True);a=p.parse_args();cp=str(a.fixtures.resolve())+os.pathsep+str(a.classes.resolve());count=0
 def ok(v):
  nonlocal count
  count+=1;assert v,count
 def wait(fn,seconds=12):
  until=time.monotonic()+seconds
  while time.monotonic()<until:
   try:
    v=fn()
    if v:return v
   except (OSError,ValueError,KeyError,http.client.HTTPException):pass
   time.sleep(.025)
  raise AssertionError('fixture wait timed out')
 def props(text):return dict(line.split('=',1) for line in text.splitlines() if '=' in line and not line.startswith(('#','!')))
 def esc(v):return str(v).replace('\\','\\\\')
 with tempfile.TemporaryDirectory(prefix='Genesis handoff96 ') as raw:
  root=Path(raw)
  for mode in ['good','menu','hook_hold','cancel','crash','save_failed','missing_stop','missing_normal','wrong_world','resume_changed','tamper_before_commit']:
   game=root/mode;data=game/'config/mared/dev';data.mkdir(parents=True);config=data/'supervisor.properties';secret=uuid.uuid4().hex+uuid.uuid4().hex;instance=str(uuid.uuid4())
   with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
   values={'gameDir':game,'workDir':game,'controlToken':secret,'instanceId':instance,'controlPort':port,'logFile':data/'supervisor.log','restartUi.enabled':'false','fastLaunch.enabled':'false','cds.mode':'off','gracefulStopMs':'500'}
   values.update({'command.'+str(i):v for i,v in enumerate([a.java,'-cp',cp,'com.fixmer.genesis.supervisor.HandoffProcessFixture96','replacement',str(game)])});config.write_text(''.join(k+'='+esc(v)+'\n' for k,v in values.items()))
   source_log=(game/'source.log').open('w');source=subprocess.Popen([a.java,'-cp',cp,'com.fixmer.genesis.supervisor.HandoffProcessFixture96',mode,str(game),str(config)],stdout=source_log,stderr=subprocess.STDOUT)
   owner=None;owner_log=(game/'owner.log').open('w')
   def rpc(path,method='GET',extra=None):
    c=http.client.HTTPConnection('127.0.0.1',port,timeout=2)
    try:c.request(method,path,headers={'X-Genesis-Token':secret,**(extra or {})});r=c.getresponse();return r.status,r.read(16385).decode()
    finally:c.close()
   def waiting():
    code,body=rpc('/identity');p=props(body);return p if code==200 and p.get('state')=='WAITING_FOR_PARENT' else None
   try:
    ready=wait(lambda:props((game/'source96-ready.properties').read_text()));ok(source.poll() is None);ok(int(ready['pid'])==source.pid)
    owner=subprocess.Popen([a.java,'-Djava.awt.headless=true','--add-modules=jdk.httpserver','-cp',cp,'com.fixmer.genesis.supervisor.Supervisor','--config='+str(config),'--handoff-pid='+str(source.pid),'--handoff96='+ready['id']],stdin=subprocess.PIPE,stdout=owner_log,stderr=subprocess.STDOUT,text=True)
    identity=wait(waiting);ok(identity['handoffProtocol96']=='96' and identity['handoff96']==ready['id']);ok(not (game/'replacement96-entered').exists())
    headers={'X-Genesis-Instance':instance,'X-Genesis-Parent':str(source.pid)}
    ok(rpc('/handoff/commit','POST',{**headers,'X-Genesis-Parent':str(source.pid+1)})[0]==409)
    if mode=='tamper_before_commit':
     file=data/'handoff96'/ready['id']/'progress.properties';file.write_text(file.read_text().replace('exit=false','exit=true'));ok(rpc('/handoff/commit','POST',headers)[0]==409);ok(source.poll() is None);ok(not (game/'replacement96-entered').exists());continue
    ok(rpc('/handoff/commit','POST',headers)[0]==202);ok(rpc('/handoff/commit','POST',headers)[0]==409);ok(source.poll() is None);ok(not (game/'replacement96-entered').exists())
    (game/'source96-action').write_text('explicit fixture action')
    if mode=='cancel':
     wait(lambda:(game/'source96-cancelled').exists());owner.wait(10);ok(source.poll() is None);ok(not (game/'replacement96-entered').exists());ok(props((data/'handoff96-last.properties').read_text())['phase']=='HOLD');continue
    if mode=='hook_hold':
     wait(lambda:(game/'source96-hook-held').exists());progress=data/'handoff96'/ready['id']/'progress.properties';wait(lambda:props(progress.read_text()).get('hook')=='true');ok(source.poll() is None);ok(not (game/'replacement96-entered').exists());time.sleep(.2);ok(not (game/'replacement96-entered').exists());(game/'source96-release-hook').write_text('explicit release')
    source.wait(10)
    if mode in ['good','menu','hook_hold']:
     wait(lambda:(game/'replacement96-entered').exists());ok(owner.poll() is None);report=props((data/'handoff96-last.properties').read_text());ok(report['phase']=='CONFIRMED');ok(secret not in str(report));ok(rpc('/handoff/commit','POST',headers)[0]==409);ok(rpc('/stop','POST')[0]==202);owner.wait(10)
    else:
     owner.wait(10);ok(not (game/'replacement96-entered').exists());report=props((data/'handoff96-last.properties').read_text());ok(report['phase']=='HOLD');ok(secret not in str(report))
   except Exception:
    source_log.flush();owner_log.flush();print('MODE',mode);print((game/'source.log').read_text(errors='replace')[-2000:]);print((game/'owner.log').read_text(errors='replace')[-5000:]);raise
   finally:
    (game/'source96-end').write_text('fixture cleanup');(game/'source96-release-hook').write_text('fixture cleanup')
    if owner and owner.poll() is None:
     try:rpc('/handoff/cancel','POST',{'X-Genesis-Instance':instance})
     except OSError:pass
     try:owner.wait(5)
     except subprocess.TimeoutExpired:owner.kill();owner.wait(5)
    if source.poll() is None:source.kill();source.wait(5)
    for folder in (data/'runtime').glob('*'):
     if folder.is_dir():(folder/'shutdown.request').write_text(folder.name)
    source_log.close();owner_log.close()
  # A bare PID is no longer permission to migrate an already running game.
  for flags in [['--handoff-pid=123'],['--handoff96='+str(uuid.uuid4())],['--handoff-pid=0','--handoff96='+str(uuid.uuid4())]]:
   result=subprocess.run([a.java,'-Djava.awt.headless=true','--add-modules=jdk.httpserver','-cp',cp,'com.fixmer.genesis.supervisor.Supervisor','--config='+str(config),*flags],capture_output=True,text=True,timeout=8);ok(result.returncode!=0);ok(not (game/'replacement96-entered').exists())
 print('Handoff96 actual source/owner JVM/HTTP/save-signal/lease checks passed:',count,'; save signals are explicit fixtures, no Minecraft or Windows certification.')
if __name__=='__main__':main()
