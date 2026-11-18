"""Real signed SERVER package + copy/seal/channel/READY sequencing; native processes explicitly modeled."""
from pathlib import Path
import json,sys,uuid,zipfile
from unittest.mock import Mock

def check_dedicated(root,project,runtime,java):
 sys.path.insert(0,str(project/'tools'));sys.path.insert(0,str(project/'verification/server59'))
 from first58.dedicated74 import Owner74,session,core,tree,properties,digest
 from first58.common import inventory,json_write,sha
 from first58.bridge67 import write,read
 from test_remote_install70 import mod
 folder=root/'owner74';source=folder/'generations/server-0';(source/'mods').mkdir(parents=True);(folder/'releases').mkdir();(folder/'stages').mkdir();(folder/'publisher73').mkdir()
 for name in ('public.der','private.der'):(folder/'publisher73'/name).write_bytes((root/'identity'/name).read_bytes())
 mod(source/'mods/mared.jar','mared',adapter=True)
 with zipfile.ZipFile(source/'mods/mared.jar','a') as z:z.writestr('com/fixmer/mared/Mared.class',b'file fixture, not game bytecode')
 mod(source/'mods/old.jar','oldmod','old');(source/'world').mkdir();(source/'world/level.dat').write_bytes(b'old world');(source/'world/session.lock').write_bytes(b'');(source/'config').mkdir();(source/'config/user.txt').write_text('old config')
 (source/'server.properties').write_text('level-name=world\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=25565\n');(source/'eula.txt').write_text('eula=true\n');(source/'user_jvm_args.txt').write_text('-Xmx1G\n')
 key=b'q'*32;(folder/'owner74.key').write_bytes(key);s=dict(schema=74,run=str(uuid.uuid4()),source=str(root/'never-modified-source'),java=str(java),argv=[str(java)],minecraft='1.21.1',neoforge='21.1.264',auth='false',bind='127.0.0.1',port='25565',level='world',initialInventory=inventory(source));json_write(folder/'settings.json',s);write(folder/'settings74',key,{'schema':'74','sha':sha(folder/'settings.json',65536)});session(folder,s,key,0,s['initialInventory'])
 core(runtime,java,'author',folder/'publisher73',root/'public2',root/'client-mods',folder/'releases/r1');info=core(runtime,java,'verify',folder/'releases/r1',folder/'publisher73/public.der');assert len(info['files'])==1
 owner=Owner74(runtime,folder);old=Mock();old.status.return_value={'rootExited':False,'exitCode':None,'logFailed':False};old.empty.return_value=False;owner.jobs={0:old};before=tree(source,'world',raw=True);owner.prepare('r1');assert not (owner.pending['path']/'world').exists();assert not (owner.pending['path']/'config').exists();assert inventory(source)==s['initialInventory'];count=4
 try:owner.copy_saved();raise AssertionError('Live world copied')
 except ValueError as e:assert 'WORLD_OWNER_ACTIVE' in str(e);count+=1
 # Model the old game having written its final saved state; receipt is bound to one STOP command.
 (source/'world/level.dat').write_bytes(b'last saved world');(source/'config/user.txt').write_text('last saved config');round_id=str(uuid.uuid4());cmd=owner.command(0,'SAVE_STOP',round_id);c=read(folder/'server-0.session',key);status={k:c[k] for k in ('schema','run','generation','inventory')};status.update(sequence=cmd['sequence'],request=cmd['request'],phase='STOPPED_SAVED',round=round_id,at='1');write(folder/'server-0.status',key,status);old.status.return_value={'rootExited':True,'exitCode':0,'logFailed':False};old.empty.return_value=True;owner.copy_saved();new=folder/'generations/server-1';assert (new/'world/level.dat').read_bytes()==b'last saved world';assert (new/'config/user.txt').read_text()=='last saved config';assert not (new/'mods/old.jar').exists();assert sha(new/'mods/example.jar')==sha(root/'client-mods/example.jar');assert sha(new/'mods/mared.jar')==sha(source/'mods/mared.jar');assert (source/'mods/old.jar').is_file();assert not (new/'world/session.lock').exists();old.close.assert_not_called();count+=8
 try:owner.start(1);raise AssertionError('New process started while old Job handle retained')
 except ValueError as e:assert 'OLD_JOB_STILL_HELD' in str(e);count+=1
 old.close();del owner.jobs[0]
 def launch(argv,cwd,env,log):
  assert cwd==new and env['GENESIS_DEDICATED74_GENERATION']=='1';c=read(folder/'server-1.session',key);d={k:c[k] for k in ('schema','run','generation','inventory')};d.update(sequence='0',request=str(uuid.UUID(int=0)),phase='READY',round='none',at='1');write(folder/'server-1.status',key,d);job=Mock();job.status.return_value={'rootExited':False,'exitCode':None,'logFailed':False};job.empty.return_value=False;return job
 owner.job_factory=launch;owner.start(1);assert owner.ready_generation1;assert not owner.admit;count+=2
 owner.last_pump={'phase':'COMMITTED','installed':0,'needed':1};assert not owner.advance_admission();owner.last_pump['installed']=1;assert owner.advance_admission();count+=2
 owner.admit=False;owner.jobs[1].status.return_value['rootExited']=True;owner.jobs[1].status.return_value['exitCode']=0;assert not owner.advance_admission();count+=1
 print('Dedicated74 real signed package/world/config/channel checks passed:',count,'; source stop, Job and game READY were explicit models, not Minecraft/Windows.')


def check_external_publisher(root,runtime,java):
 from first58.dedicated74 import core
 from first58.control73 import decode
 from first58.bridge67 import sign
 import uuid
 owner=root/'control-owner74';cfg=decode((owner/'fixture.cfg').read_bytes());state=core(runtime,java,'pulse',owner)
 assert state['total']==1 and state['installed']==0 and state['admission'] is False
 try:core(runtime,java,'admit',owner);raise AssertionError('Admission before installation')
 except ValueError:pass
 packet=dict(schema='73',round=cfg['round'],client=cfg['client'],request=str(uuid.uuid4()),action='INSTALLED',archive=cfg['archive'],inventory='c'*64)
 (owner/'control73/inbox'/(cfg['client']+'.packet')).write_bytes(sign(bytes.fromhex(cfg['secret']),packet))
 state=core(runtime,java,'pulse',owner);assert state['installed']==1 and state['admission'] is False
 state=core(runtime,java,'admit',owner);assert state['admission'] is True
 print('Dedicated74 real external signed-journal publisher/threshold checks passed: 4; native server READY is separately modeled.')
