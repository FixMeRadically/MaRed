#!/usr/bin/env python3
"""One-button server-folder launcher; the host needs Java21/Python3, not a Minecraft client."""
from pathlib import Path
import argparse,contextlib,json,os,secrets,sys,time,traceback
from first58.common import safe,atomic
from first58.bridge67 import read,write
from first58.inplace104 import root_for,identity,config,current,request,lease,Owner104,error_code
from first58.independent104 import spawn

def prepare(server,java):
 c=config(server,java);root=root_for(server);root.mkdir(parents=True,exist_ok=True);(root/'logs').mkdir(exist_ok=True);(server/'mods').mkdir(exist_ok=True)
 if not (root/'owner74.key').exists():atomic(root/'owner74.key',secrets.token_bytes(32),new=True)
 identity(server);return c,root

def start(server,app,java):
 c,root=prepare(server,java)
 with lease(root/'start104.lock'):return start_locked(server,app,java)

def start_locked(server,app,java):
 c,root=prepare(server,java)
 # Never interpret a historical PID as ownership of a process.
 try:
  with lease(root/'owner104.lock'):pass
 except (OSError,ValueError):return current(server)
 # Check the actual Minecraft port, not an unrelated supervisor port.
 import socket
 family=socket.AF_INET6 if ':' in c['bind'] else socket.AF_INET
 try:
  with socket.socket(family,socket.SOCK_STREAM) as sock:
   if os.name=='nt':sock.setsockopt(socket.SOL_SOCKET,socket.SO_EXCLUSIVEADDRUSE,1)
   sock.bind((c['bind'] or '0.0.0.0',int(c['port'])))
 except OSError:raise ValueError('SERVER104_PORT_BUSY') from None
 key=identity(server)[1];ticket=__import__('uuid').uuid4().hex;write(root/'start104',key,dict(schema='104',ticket=ticket,profile=str(server),app=str(app),java=str(java)))
 spawn([sys.executable,str(app/'tools/server104.py'),str(app),'run',str(server),'--java',str(java),'--ticket',ticket],server)
 deadline=time.monotonic()+30
 while time.monotonic()<deadline:
  try:
   answer=read(root/'start104.answer',key)
   if answer.get('ticket')==ticket:
    if answer.get('result')!='STARTED':raise ValueError(answer.get('error','SERVER104_START_FAILED'))
    return current(server)
  except FileNotFoundError:pass
  time.sleep(.1)
 raise ValueError('SERVER104_OWNER_ACK_TIMEOUT')

def run(server,app,java,ticket):
 from first58.dedicated75 import independent_owner
 independent_owner();c,root=prepare(server,java);_,key=identity(server)
 with lease(root/'owner104.lock'):
  start_record=read(root/'start104',key)
  if start_record!=dict(schema='104',ticket=ticket,profile=str(server),app=str(app),java=str(java)):raise ValueError('SERVER104_START_BINDING')
  # Another old game may still own the real folder even after its controller went away.
  from first58.inplace104 import lease as l,old
  with l(root/'game104.lock'),old.stopped_world(server,c['level']):pass
  owner=Owner104(server,app,java);owner.report('STARTING');write(root/'start104.answer',key,dict(schema='104',ticket=ticket,result='STARTED',error=''))
  try:owner.run_loop()
  except BaseException as error:
   owner.hold_live(error_code(error))
   if owner.phase not in {'RECOVERY_REQUIRED','BOOTSTRAP_FAILED_RESTORED'}:owner.report('FAILED',error_code(error))
   traceback.print_exc();raise
  finally:owner.close()

def main():
 p=argparse.ArgumentParser();p.add_argument('app',type=Path);p.add_argument('action',choices=['start','run','status','restart','stop','acknowledge']);p.add_argument('server',type=Path);p.add_argument('--java',type=Path);p.add_argument('--ticket');a=p.parse_args();app=safe(a.app);server=safe(a.server);root=root_for(server)
 if a.action=='status':print(json.dumps(current(server),ensure_ascii=False));return
 if a.action in {'restart','stop'}:print(json.dumps(request(server,'RESTART' if a.action=='restart' else 'SHUTDOWN')));return
 if a.action=='acknowledge':
  c=config(server,safe(a.java)) if a.java else None
  if c is None:raise ValueError('SERVER104_JAVA_REQUIRED')
  from first58.inplace104 import old
  with lease(root/'owner104.lock'),lease(root/'game104.lock'),old.stopped_world(server,c['level']):
   # Explicit operator acknowledgement only; no world files are changed.
   (root/'review104').unlink(missing_ok=True)
  print('{"phase":"ACKNOWLEDGED"}');return
 if a.java is None:raise ValueError('SERVER104_JAVA_REQUIRED')
 java=safe(a.java)
 if a.action=='start':print(json.dumps(start(server,app,java),ensure_ascii=False));return
 (root/'logs').mkdir(parents=True,exist_ok=True)
 original_out,original_err=sys.stdout,sys.stderr
 with (root/'logs/controller.log').open('a',encoding='utf-8',buffering=1) as log:
  sys.stdout=log;sys.stderr=log
  try:run(server,app,java,a.ticket)
  except BaseException as error:
   print(error_code(error));traceback.print_exc()
   try:
    _,key=identity(server);write(root/'start104.answer',key,dict(schema='104',ticket=a.ticket or '',result='FAILED',error=error_code(error)))
   except Exception:pass
   raise
  finally:sys.stdout,sys.stderr=original_out,original_err
if __name__=='__main__':
 try:main()
 except Exception as error:
  try:print(error_code(error),file=sys.stderr)
  except Exception:pass
  raise SystemExit(2)
