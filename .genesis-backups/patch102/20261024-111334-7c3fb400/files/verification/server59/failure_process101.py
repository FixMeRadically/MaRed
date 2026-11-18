#!/usr/bin/env python3
"""Real supervisor, child JVM, files, profile locks and HTTP; signals are explicit fixtures."""
from pathlib import Path
import argparse,http.client,json,os,socket,subprocess,tempfile,time,uuid

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--classes',type=Path);p.add_argument('--fixtures',type=Path);p.add_argument('--classpath-file',type=Path);p.add_argument('--mode');a=p.parse_args()
 if not a.classpath_file and not(a.classes and a.fixtures):p.error('supply --classpath-file or both --classes and --fixtures')
 cp=os.pathsep.join(json.loads(a.classpath_file.read_text(encoding='utf-8'))) if a.classpath_file else str(a.fixtures.resolve())+os.pathsep+str(a.classes.resolve());count=0
 def command(arguments,folder,name):
  # JVM argfiles avoid Windows' command-line limit; no shell or token printing.
  argfile=folder/(name+'.args');argfile.write_text('\n'.join('"'+str(v).replace('\\','\\\\').replace('"','\\"').replace('\n','\\n').replace('\r','\\r')+'"' for v in arguments)+'\n',encoding='utf-8');return [a.java,'@'+str(argfile)]
 def ok(value):
  nonlocal count
  count+=1;assert value,count
 def wait(fn,seconds=18):
  end=time.monotonic()+seconds
  while time.monotonic()<end:
   try:
    v=fn()
    if v:return v
   except (OSError,ValueError,KeyError,http.client.HTTPException):pass
   time.sleep(.03)
  raise AssertionError('failure101 wait timed out')
 def props(path):return dict(l.split('=',1) for l in path.read_text().splitlines() if '=' in l and not l.startswith(('#','!')))
 for mode in ([a.mode] if a.mode else ('crash','exit-zero','late-ack','fatal','timeout','ignore','world','world-crash','unguarded','return-fails')):
  with tempfile.TemporaryDirectory(prefix='Genesis failure101 ') as raw:
   game=Path(raw)/'profile space';game.mkdir();data=game/'config/mared/dev';token=uuid.uuid4().hex*2;instance=str(uuid.uuid4())
   with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
   subprocess.run(command(['-cp',cp,'com.fixmer.genesis.supervisor.FailureProcessFixture101','prepare',str(game),mode,a.java,str(port),token,instance,cp],game,'prepare101'),check=True,capture_output=True,text=True)
   def rpc(path,method='GET'):
    c=http.client.HTTPConnection('127.0.0.1',port,timeout=2)
    try:c.request(method,path,headers={'X-Genesis-Token':token});r=c.getresponse();return r.status,r.read().decode()
    finally:c.close()
   def status():return json.loads(rpc('/status')[1])
   def report():
    index=props(data/'failures101/latest.properties');return props(data/'failures101'/index['id']/'report.properties')
   out=(game/'owner.log').open('w');owner=subprocess.Popen(command(['-Djava.awt.headless=true','--add-modules=jdk.httpserver','-cp',cp,'com.fixmer.genesis.supervisor.Supervisor','--config='+str(data/'supervisor.properties')],game,'owner101'),stdout=out,stderr=subprocess.STDOUT)
   try:
    if mode in ('world','unguarded'):
     wait(lambda:report().get('status')=='ACTION_REQUIRED');ok(owner.poll() is None);ok((game/'starts101').read_text().splitlines()==['2'])
     time.sleep(.5);ok(not (game/'returned101').exists());ok(status()['state']=='RUNNING');(game/'allow-close101').write_text('normal controlled close');
    if mode=='return-fails':
     wait(lambda:report().get('status')=='RECOVERY_FAILED');wait(lambda:status()['state']=='RECOVERY_REQUIRED');time.sleep(.4);ok((game/'starts101').read_text().splitlines()==['2','1']);ok(owner.poll() is None)
    else:
     wait(lambda:(game/'returned101').exists());wait(lambda:report().get('status')=='RESTORED');ok((game/'starts101').read_text().splitlines()==['2','1']);ok(status()['state']=='RUNNING');ok((game/'returned101').read_text()=='MENU_ONLY');ok('kind=hold91' in (data/'resume.properties').read_text())
    ok((game/'saves/world/level.dat').read_text()=='UNCHANGED_WORLD');r=report();folder=data/'failures101'/r['id'];saved='\n'.join(f.read_text(errors='replace') for f in folder.glob('*.log'));ok('CANDIDATE_ERROR' in saved);ok('PRIVATE_CONSOLE_SECRET' not in saved);ok('PRIVATE_GAME_SECRET' not in saved);ok('PREVIOUS_VERSION_LOG' not in saved)
   except Exception:
    out.flush();print(mode,(game/'owner.log').read_text(errors='replace')[-9000:]);raise
   finally:
    (game/'allow-close101').write_text('fixture teardown')
    try:rpc('/stop','POST')
    except OSError:pass
    try:owner.wait(10)
    except subprocess.TimeoutExpired:owner.kill();owner.wait(8)
    out.close()
   print('Failure101 process case:',mode,flush=True)
 print('Failure101 actual supervisor/child-JVM rollback checks passed:',count,'; no Minecraft/Windows UI runtime.')
if __name__=='__main__':main()
