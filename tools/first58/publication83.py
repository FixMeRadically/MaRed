"""First-release UI backend and exact publication helpers. No signing keys travel over game packets."""
from pathlib import Path
import json,re,subprocess,tempfile,time
from . import dedicated74 as base
from . import publication79 as prior
from .runtime import core_classpath
from .common import safe

HELPER_ERROR = re.compile(
 r'(?:PUBLICATION79|PUBLICATION83|SCRIPT84|RENEWAL85|HTTPS78|HTTPS159|DEDICATED74|REMOTE|CONTROL73|FIRST58|PACKAGE)_[A-Z0-9_]{1,60}'
)

def command(project,root,main,*args,checkpoint=None):
 s,_=base.settings(root);cmd=[s['java'],'-XX:-UsePerfData','-Dstdout.encoding=UTF-8','-cp',str(core_classpath(project)),main,*map(str,args)]
 if checkpoint is None:
  r=subprocess.run(cmd,capture_output=True,encoding='utf-8',errors='replace',timeout=300,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0),stdin=subprocess.DEVNULL,env=base.environment(s['java'],root));code=r.returncode;output=r.stdout;error=r.stderr
 else:
  with tempfile.TemporaryFile() as out,tempfile.TemporaryFile() as err:
   child=subprocess.Popen(cmd,stdin=subprocess.DEVNULL,stdout=out,stderr=err,env=base.environment(s['java'],root),creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0),close_fds=True)
   try:
    deadline=time.monotonic()+300
    while child.poll() is None:
     checkpoint()
     if time.monotonic()>deadline:raise ValueError('PUBLICATION83_TIMEOUT')
     time.sleep(.2)
    out.seek(0);err.seek(0);data=out.read(65537);error=err.read(4097).decode('utf8','replace');code=child.returncode
    if len(data)>65536:raise ValueError('PUBLICATION83_RESULT_LIMIT')
    output=data.decode('utf8')
   finally:
    if child.poll() is None:
     child.kill();child.wait(timeout=5) # This exact read-only Java helper; never a game process.
 if code:
  error=error.strip()
  if HELPER_ERROR.fullmatch(error):raise ValueError(error)
  if main.endswith('.DedicatedTools74') and args and args[0] in {'verify','verify-history'}:
   raise ValueError('DEDICATED74_PAIR_CHECK_FAILED')
  raise ValueError('PUBLICATION83_REJECTED')
 return json.loads(output)

def call(project,root,*args,checkpoint=None):return command(project,root,'com.fixmer.genesis.technology.server.remote.Publication83',*args,checkpoint=checkpoint)
def verify_pair(project,root,pair,pub,checkpoint,*,historical=False):
 return command(project,root,'com.fixmer.genesis.technology.server.remote.DedicatedTools74',
                'verify-history' if historical else 'verify',pair,pub,checkpoint=checkpoint)

def first(project,root,client,server,label,ttl,game,endpoint,ticket=None):
 root=safe(root);s,key=base.settings(root);prior.available(root,key);base.label(label)
 if (root/'owner118').exists() and not (root/'publisher73').exists():call(project,root,'init',root/'publisher73')
 client=safe(client);server=safe(server)
 for folder in (client,server):
  if root/'generations'==folder or root/'generations' in folder.parents:raise ValueError('PUBLICATION79_LIVE_SOURCE')
 try:host,port=game.rsplit(':',1)
 except ValueError:raise ValueError('PUBLICATION83_ADDRESS') from None
 if port!=str(s['port']):raise ValueError('PUBLICATION83_ADDRESS')
 if s['auth']=='false':
  import urllib.parse
  if host not in {'localhost','127.0.0.1','[::1]'} or urllib.parse.urlsplit(endpoint).hostname not in {'localhost','127.0.0.1','::1'}:raise ValueError('PUBLICATION83_OFFLINE_LOOPBACK')
 args=['author' if ticket else 'preview',root/'publisher73',root/'releases',client,server,label,ttl,game,endpoint,s['minecraft'],s['neoforge']]
 if ticket:args.append(ticket)
 result=call(project,root,*args)
 if not ticket:
  for side in ('client','server'):result[side+'Count']=len(result[side]);result[side+'Bytes']=sum(x['bytes'] for x in result[side]);result[side]=result[side][:20]
 return result
