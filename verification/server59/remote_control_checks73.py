"""Actual shipped TLS endpoint + Java signature verification + HMAC receipt/journal test."""
import base64,json,subprocess,sys,urllib.request,urllib.error,uuid
from pathlib import Path

def check_control(root,java,cp,props,endpoint,tls,project):
 sys.path.insert(0,str(project/'tools'))
 from first58.control73 import decode
 from first58.bridge67 import sign
 cfg=root/'control73.cfg';key=root/'identity/public.der';c=decode(cfg.read_bytes());url=endpoint+'control73/';count=0
 def run(*args):return subprocess.run(list(map(str,[java,*props,'-cp',cp,'com.fixmer.genesis.technology.server.remote.ControlHttp73',*args])),capture_output=True,text=True,timeout=30)
 def command(op):
  r=subprocess.run(list(map(str,[java,'-cp',cp,'com.fixmer.mared.RemoteControlChecks73',op,root])),capture_output=True,text=True,timeout=30);assert r.returncode==0,(op,r.stderr);return r.stdout
 def poll():
  command('heartbeat')
  p=run('poll',cfg,key);assert p.returncode==0,p.stderr;return decode(base64.b64decode(p.stdout))
 def post(action,request=None,inv='c'*64):
  p=run('post',cfg,action,inv,request or uuid.uuid4());assert p.returncode==0,p.stderr
 def denied(path,code=404,data=None,headers=None):
  nonlocal count
  try:urllib.request.urlopen(urllib.request.Request(path,data=data,headers=headers or {}),context=tls,timeout=3);raise AssertionError('Unexpected HTTP access')
  except urllib.error.HTTPError as e:assert e.code==code,(path,e.code);count+=1
 s=poll();assert s['phase']=='PREPARING' and s['installed']=='0';count+=1
 for name in ['auth73','active73',c['round']+'.journal',c['round']+'.state?token=x','../identity/private.der']:denied(url+name)
 post('PREPARED');s=poll();assert 'PREPARED' not in s['clients'];count+=1 #202 was queue only
 command('drain');s=poll();assert 'PREPARED' in s['clients'] and s['installed']=='0';count+=1
 command('commit');s=poll();assert s['phase']=='COMMITTED' and s['admission']=='false';count+=1
 # Correct cap but wrong archive: host queues it, server refuses; no false install.
 request={'schema':'73','round':c['round'],'client':c['client'],'request':str(uuid.uuid4()),'action':'INSTALLED','archive':'f'*64,'inventory':'c'*64}
 raw=sign(bytes.fromhex(c['secret']),request);headers={'X-Genesis-Client':c['client']}
 with urllib.request.urlopen(urllib.request.Request(url+'receipt',data=raw,headers=headers),context=tls,timeout=3) as r:assert r.status==202
 command('drain');assert poll()['installed']=='0';count+=1
 corrupt=raw[:-2]+(b'1' if raw[-2:-1]==b'0' else b'0')+b'\n';denied(url+'receipt',403,corrupt,headers)
 denied(url+'receipt',403,b'x'*16385,headers)
 denied(url+'receipt?token=x',404,raw,headers)
 post('INSTALLED');command('drain');s=poll();assert s['installed']=='1' and s['admission']=='false';count+=1
 command('admit');s=poll();assert s['installed']=='1' and s['admission']=='true';count+=1
 post('JOINED');command('drain');assert 'JOINED' in poll()['clients'];count+=1
 # Wrong pinned publisher public key never permits an exit/transfer decision.
 p=run('poll',cfg,root/'wrong-key/public.der');assert p.returncode!=0;count+=1
 # Noncanonical/duplicate configuration fails before networking.
 malformed=root/'malformed73.cfg';malformed.write_bytes(cfg.read_bytes()+b'schema=NzM\n');assert run('poll',malformed,key).returncode!=0;count+=1
 print('Remote73 real TLS/Ed25519/HMAC/queue/admission checks passed:',count)
