#!/usr/bin/env python3
"""Actual Java/Python HMAC, locking, revision and queue acceptance. No Minecraft/native owner."""
from pathlib import Path
import argparse,base64,concurrent.futures,os,subprocess,sys,tempfile,time,uuid
p=argparse.ArgumentParser();p.add_argument('project',type=Path);p.add_argument('java',type=Path);p.add_argument('--classes',type=Path,required=True);p.add_argument('--fixtures',type=Path,required=True);a=p.parse_args();sys.path.insert(0,str(a.project.resolve()/'tools'))
from first58 import access88 as acl
from first58.bridge67 import read,write
from first58.bootstrap_wire76 import decode
with tempfile.TemporaryDirectory(prefix='genesis-access88-') as temp:
 root=Path(temp);cmd=[str(a.java.resolve()),'-cp',str(a.classes.resolve())+os.pathsep+str(a.fixtures.resolve()),'AccessProbe88'];op=str(uuid.uuid4());user=str(uuid.uuid4());checks=0
 def java(action,*args,ok=True):
  r=subprocess.run([*cmd,action,str(root),*map(str,args)],capture_output=True,text=True,timeout=15);assert (r.returncode==0)==ok,(r.stdout,r.stderr);return r
 java('init');key=(root/'owner74.key').read_bytes();run=read(root/'owner88',key)['run'];checks+=1
 def heartbeat():
  d=read(root/'view75',key);d['updated']=str(int(time.time()*1000));write(root/'view75',key,d)
 def ack(d):write(root/'request74.ack',key,dict(schema='75',run=run,request=d['request']))
 def queue(action='STAGE',actor=user,operator='false'):
  heartbeat();java('queue',actor,operator,str(uuid.uuid4()),action,'r1');return read(root/'request74',key)
 def view(actor,operator='false'):return decode(base64.b64decode(java('view',actor,operator).stdout.strip(),validate=True))
 java('change',op,user,'PREPARER',0);s,roles=acl.state(root,key,run);assert roles[user]=='PREPARER';checks+=1
 d=queue();acl.consume(root,key,run,d);assert 'ACCEPTED' in acl.state(root,key,run)[0]['audit'];ack(d);checks+=2
 d=queue();java('change',op,user,'NONE',1)
 try:acl.consume(root,key,run,d);raise AssertionError('revoked queued command accepted')
 except ValueError as e:assert str(e)=='ACCESS88_REVOKED_OR_EXPIRED'
 ack(d);checks+=1
 s,_=acl.state(root,key,run);assert 'REJECTED' in view(op,'true')['audit'];checks+=1
 revision=2
 for role,actions in acl.ROLES.items():
  java('change',op,user,role,revision);revision+=1;d=view(user);assert set(d['actions'].split(','))==actions;assert not d['subjects'];assert d['manage']=='false';checks+=3
 java('queue',user,'false',str(uuid.uuid4()),'SHUTDOWN','r1',ok=False);checks+=1
 java('change',op,user,'PREPARER',revision);revision+=1
 d=queue();permit=__import__('first58.bridge67',fromlist=['verify']).verify(key,base64.b64decode(d['permit88']));permit['expires']=str(int(permit['issued'])+1);d['permit88']=base64.b64encode(__import__('first58.bridge67',fromlist=['sign']).sign(key,permit)).decode()
 try:acl.consume(root,key,run,d);raise AssertionError('expired accepted')
 except ValueError:pass
 ack(d);checks+=1
 # OS byte-range lock interoperability in both directions.
 with acl.lock(root):assert 'ACCESS88_BUSY' in java('view',op,'true',ok=False).stderr;checks+=1
 held=subprocess.Popen([*cmd,'hold',str(root),op],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
 try:
  assert held.stdout.readline().strip()=='LOCKED'
  try:
   with acl.lock(root):raise AssertionError('Python ignored Java lock')
  except (OSError,acl.AccessBusy88):checks+=1
 finally:held.communicate('x',timeout=10)
 # Two independent Java writers cannot both commit the same revision.
 one,two=str(uuid.uuid4()),str(uuid.uuid4())
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
  futures=[pool.submit(subprocess.run,[*cmd,'change',str(root),op,target,'OBSERVER',str(revision)],capture_output=True,text=True,timeout=15) for target in (one,two)];results=[f.result() for f in futures]
 assert sum(r.returncode==0 for r in results)==1;assert any('ACCESS88_BUSY' in r.stderr or 'ACCESS88_STALE' in r.stderr for r in results);s,roles=acl.state(root,key,run);assert int(s['revision'])==revision+1;assert (one in roles)!=(two in roles);checks+=4
 # Tampering is rejected independently by both codecs; no actor/argv/secret printed.
 saved=(root/'access88.state').read_bytes();(root/'access88.state').write_bytes(saved+b'TAMPER');java('view',op,'true',ok=False);checks+=1
 try:acl.state(root,key,run);raise AssertionError('unsigned state accepted')
 except ValueError:checks+=1
 (root/'access88.state').write_bytes(saved)
 print(f'Access88 actual Java/Python HMAC/revoke/expiry/policy/OS-lock/concurrent-CAS checks passed: {checks}; player/online/operator identity is a fixture, no Minecraft/native owner.')
