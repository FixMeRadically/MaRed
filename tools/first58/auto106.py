"""Authenticated first-start handoff. The original JVM is only observed, never owned/killed.
No replacement JAR/world is installed during handoff. New launches then use the existing Job owner.
"""
from pathlib import Path
import json,sys,time,traceback,uuid
from .common import safe,inventory,atomic
from .bridge67 import read,write
from .inplace104 import identity,root_for,lease,config,Owner104,error_code
from .source106 import Source106
from .dedicated75 import independent_owner

FIELDS={'schema','ticket','profile','app','java','pid','created','inventory','issued','argv'}
def registration(server,app,java,ticket,now=None):
 root,key=identity(server);d=read(root/'auto106.request',key);now=int(time.time()*1000) if now is None else now
 if set(d)!=FIELDS or d['schema']!='106' or d['ticket']!=ticket or str(uuid.UUID(ticket))!=ticket or uuid.UUID(ticket).int==0 or d['profile']!=str(safe(server)) or d['app']!=str(safe(app)) or d['java']!=str(safe(java)) or not d['pid'].isdigit() or not 1<=int(d['pid'])<=0xffffffff or not d['created'].isdigit() or not 0<int(d['created'])<=int(d['issued']) or not 0<=now-int(d['issued'])<=60000:raise ValueError('SERVER106_SOURCE_RECORD')
 if len(d['inventory'])!=64 or any(c not in '0123456789abcdef' for c in d['inventory']):raise ValueError('SERVER106_SOURCE_RECORD')
 args=json.loads(d['argv'])
 if not isinstance(args,list) or len(args)>128 or any(not isinstance(a,str) or '\x00' in a or '\n' in a or '\r' in a for a in args):raise ValueError('SERVER106_SOURCE_ARGUMENTS')
 c=config(server,java);expected=[str(a).replace('\\','/') for a in c.get('java_argv',c['argv'])[1:] if a!='nogui'];actual=[a.replace('\\','/') for a in args if a not in {'nogui','--nogui'}]
 if actual!=expected:raise ValueError('SERVER106_SOURCE_ARGUMENTS')
 if inventory(server)!=d['inventory']:raise ValueError('SERVER106_SOURCE_MODS_CHANGED')
 if safe(app).parent!=safe(root/'app') or len(app.name)!=64 or any(c not in '0123456789abcdef' for c in app.name):raise ValueError('SERVER106_APP_BINDING')
 return d,key,c

def handoff(server,app,java,ticket,source_factory=Source106,owner_factory=Owner104,independent=independent_owner,clock=time.monotonic,sleep=time.sleep):
 independent();server=safe(server);app=safe(app);java=safe(java);root=root_for(server)
 with lease(root/'auto106.lock'),lease(root/'start104.lock'),lease(root/'owner104.lock'):
  d,key,c=registration(server,app,java,ticket)
  with source_factory(int(d['pid']),int(d['created'])) as source:
   write(root/'auto106.answer',key,dict(schema='106',ticket=ticket,result='WAITING_FOR_SOURCE_EXIT'))
   deadline=clock()+90
   while not source.exited():
    if (root/'auto106.cancel').exists():
     cancel=read(root/'auto106.cancel',key)
     if cancel==dict(schema='106',ticket=ticket):return
    if clock()>deadline:raise ValueError('SERVER106_SOURCE_EXIT_TIMEOUT')
    sleep(.1)
   if source.exit_code()!=0:raise ValueError('SERVER106_SOURCE_EXIT_FAILED')
  release=read(root/'auto106.release',key)
  if release!=dict(schema='106',ticket=ticket,profile=str(server),phase='RELEASED_FOR_HANDOFF',inventory=d['inventory']):raise ValueError('SERVER106_SOURCE_NOT_RELEASED')
  # Re-check the inputs after the held original process HANDLE has signalled exit.
  if inventory(server)!=d['inventory']:raise ValueError('SERVER106_SOURCE_MODS_CHANGED')
  owner=owner_factory(server,app,java)
  def unchanged():
   if inventory(server)!=d['inventory']:raise ValueError('SERVER106_SOURCE_MODS_CHANGED')
  owner.bootstrap=unchanged # Never replace a JAR while handing over an initially unmanaged JVM.
  owner.report('STARTING')
  try:owner.run_loop()
  except BaseException as error:
   owner.hold_live(error_code(error))
   if owner.phase not in {'RECOVERY_REQUIRED','BOOTSTRAP_FAILED_RESTORED'}:owner.report('FAILED',error_code(error))
   raise
  finally:owner.close()

def entry(server,app,java,ticket):
 root=root_for(server);logs=root/'logs';logs.mkdir(exist_ok=True);original=sys.stdout,sys.stderr
 with (logs/'bootstrap106.log').open('a',encoding='utf-8',buffering=1) as log:
  sys.stdout=sys.stderr=log
  try:handoff(server,app,java,ticket)
  except BaseException as error:
   print(error_code(error));traceback.print_exc()
   try:write(root/'auto106.answer',identity(server)[1],dict(schema='106',ticket=ticket,result='FAILED',error=error_code(error)))
   except Exception:pass
   raise
  finally:sys.stdout,sys.stderr=original
