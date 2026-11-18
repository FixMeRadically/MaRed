"""Actual Java snapshot/queue -> Python owner -> bundled Java verifier -> real change journal.
Game process save/READY/Job are explicit fixtures. No Minecraft or Windows certification.
"""
from pathlib import Path
import argparse,contextlib,json,os,shutil,subprocess,sys,tempfile,uuid
from unittest.mock import patch,Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.inplace104 import Owner104,root_for
from first58.bridge67 import read,write
from first58.common import inventory
from first58 import update115

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--classpath-file',type=Path,required=True);p.add_argument('--core',type=Path,required=True);a=p.parse_args();checks=0
 def ok(value):
  nonlocal checks
  checks+=1
  if not value:raise AssertionError('update cross115 '+str(checks))
 with tempfile.TemporaryDirectory(prefix='Genesis server update115 ') as temp:
  base=Path(temp).resolve();classpath=os.pathsep.join(json.loads(a.classpath_file.read_text(encoding='utf-8-sig')))
  def quote(s):
   if any(c in s for c in '\r\n\0'):raise ValueError('Invalid Java argv')
   return '"'+s.replace('\\','\\\\').replace('"','\\"')+'"'
  argfile=base/'fixture.args';argfile.write_text('-cp\n'+quote(classpath)+'\ncom.fixmer.mared.ServerUpdateChecks115\n'+quote(str(base))+'\n',encoding='utf-8');argfile.chmod(0o600)
  started=subprocess.run([a.java,'@'+str(argfile)],capture_output=True,text=True,timeout=60)
  ok(started.returncode==0)
  if started.returncode:raise AssertionError(started.stdout+started.stderr)
  server=base/'server';root=root_for(server);key=(root/'owner74.key').read_bytes();raw=(root/'request74').read_bytes();d=read(root/'request74',key);ok(d['schema']=='115');ok(d['action']=='APPLY115');update115.packet(d)
  app=base/'app';(app/'runtime').mkdir(parents=True);shutil.copyfile(a.core,app/'runtime/core.jar')
  c=dict(argv=['game is an explicit fixture'],java=str(Path(a.java).resolve()),neoforge='21.1.264',minecraft='1.21.1',level='world',port='25565',bind='',auth='false')
  with patch('first58.inplace104.config',return_value=c),patch('first58.inplace104.uuid.uuid4',return_value=uuid.UUID(d['run'])):
   owner=Owner104(server,app,Path(a.java))
  owner.generation=1;owner.expected=inventory(server);owner.report('RUNNING');(root/'request74').write_bytes(raw)
  ok(owner.receive()=='APPLY115');ok(owner.update_request115==d)
  owner.busy=True;owner.report('CHECKING');change=update115.prepare(owner,d) # actual separate JVM validates BuildSnapshot84 using bundled core
  ok(bool(change));ok(owner.changes.load()[0]['phase']=='PREPARED');ok((root/'logs/update115-verification.log').read_text().strip()=='SERVER115_VERIFIED')
  old=(server/'mods'/d['target']).read_bytes();owner.changes.apply();ok((server/'mods'/d['target']).read_bytes()!=old);owner.changes.restore();owner.changes.finish(False);ok((server/'mods'/d['target']).read_bytes()==old)
  (base/'project/source.java').write_text('changed after review',encoding='utf-8')
  try:update115.prepare(owner,d);raise AssertionError('stale Java snapshot accepted')
  except ValueError as failed:ok(str(failed)=='SERVER115_REVIEW_CHANGED')
  ok(owner.changes.load() is None);ok((server/'mods'/d['target']).read_bytes()==old)
  (base/'project/source.java').write_text('source 1',encoding='utf-8')
  # Full owner dispatch after a real JVM review, with an explicit early game-crash fixture.
  starts=[]
  def job(channel,generation,failed=False):
   value=Mock();value.dead=failed
   def status():
    command=channel/f'server-{generation}.command'
    if command.exists() and not value.dead:
     request=read(command,key);value.dead=True
     write(channel/f'server-{generation}.status',key,{k:request[k] for k in ('schema','run','generation','inventory','sequence','request','round')}|dict(phase='STOPPED_SAVED',at='1'))
     return dict(rootExited=False,active=1,exitCode=None)
    return dict(rootExited=value.dead,active=0 if value.dead else 1,exitCode=7 if failed else 0)
   value.status.side_effect=status;return value
  owner.launch=str(uuid.uuid4());owner.channel=root/'runs'/owner.launch;owner.channel.mkdir();owner.job=job(owner.channel,1)
  def factory(argv,cwd,env,log):
   starts.append((server/'mods'/d['target']).read_bytes());failed=starts[-1]!=old;log.write_text('early crash fixture' if failed else 'returned fixture',encoding='utf-8')
   if not failed:write(owner.channel/f'server-{owner.generation}.status',key,dict(schema='74',run=owner.run,generation=str(owner.generation),inventory=owner.expected,sequence='0',request=str(uuid.uuid4()),phase='READY',round='none',at='1'))
   return job(owner.channel,owner.generation,failed)
  owner.job_factory=factory;owner.request_id=d['request']
  with patch('first58.inplace104.config',return_value=c),patch('first58.inplace104.old.stopped_world',side_effect=lambda *a:contextlib.nullcontext()):ok(update115.execute(owner,d))
  ok(len(starts)==2);ok(starts[0]!=old and starts[1]==old);ok(owner.request_result=='ROLLED_BACK');ok(owner.phase=='RUNNING');ok(json.loads((root/'logs/update115.json').read_text())['status']=='RESTORED');ok((server/'mods'/d['target']).read_bytes()==old)

 print('Server115 actual Java-to-Python queue/bundled-JVM provenance/journal/late-source checks passed:',checks,'; no Minecraft or Windows game process.')
if __name__=='__main__':main()
