"""Actual signed pair -> sealed saved world -> independent recovered owner -> signed READY.
Java21 initialization/private ACL and Windows Job/save/READY are explicit models here.
"""
from pathlib import Path
from unittest.mock import Mock,patch
import copy,uuid

def check_recovery(root,project,runtime,java):
 from first58 import dedicated74 as b,dedicated77 as r
 from first58.bridge67 import read,write
 from first58.common import inventory,sha,json_write
 from first58.dedicated75 import History75
 source_root=root/'owner75';s,key=b.settings(source_root);source=source_root/'generations/server-2';(source/'world/session.lock').write_bytes(b'0')
 for version in (76,77):write(source_root/f'owner{version}',key,dict(schema=str(version),run=s['run']))
 owner=r.Owner77(runtime,source_root);owner.current=2;owner.current_inventory=inventory(source);owner.previous_info=b.core(runtime,java,'verify',source_root/'releases/r2',source_root/'publisher73/public.der');owner.active_label76='r2';cmd=owner.command(2,'SHUTDOWN');session=read(source_root/'server-2.session',key);d={k:session[k] for k in ('schema','run','generation','inventory')};d.update(sequence=cmd['sequence'],request=cmd['request'],round=cmd['round'],phase='STOPPED_SAVED',at='1');write(source_root/'server-2.status',key,d);job=Mock();job.status.return_value=dict(rootExited=True,exitCode=0,logFailed=False);job.empty.return_value=True;owner.jobs={2:job};owner.report('STOPPED_SAVED');legacy=r.catalog(source_root)['rows'][0];assert legacy['kind']=='LEGACY76' and legacy['eligible'];owner.confirmed_stop(2);job.close.assert_not_called();count=2
 view=r.catalog(source_root);row=view['rows'][0];assert row['eligible'] and row['generation']==2 and row['latest'];count+=1
 new_root=root/'recovered77'
 def init_model(project,src,target,java,publisher=None,recovering=False,source_locked=False):
  target.mkdir();(target/'import77.pending').write_text('77');(target/'generations').mkdir();(target/'releases').mkdir();(target/'stages').mkdir();b.copy_tree(src,target/'generations/server-0','world');ns=copy.deepcopy(s);ns['run']=str(uuid.uuid4());ns['source']=str(src);ns['initialInventory']=inventory(target/'generations/server-0');nk=b'n'*32;(target/'owner74.key').write_bytes(nk);json_write(target/'settings.json',ns);write(target/'settings74',nk,dict(schema='74',sha=sha(target/'settings.json')));b.session(target,ns,nk,0,ns['initialInventory']);(target/'publisher73').mkdir()
  for n in ('public.der','private.der'):(target/'publisher73'/n).write_bytes((publisher/n).read_bytes())
  for v in (75,76,77):write(target/f'owner{v}',nk,dict(schema=str(v),run=ns['run']))
  return target
 before=b.tree(source,'world')
 with patch.object(r,'initialize',init_model):result=r.recover(runtime,source_root,2,row['ticket'],new_root,java)
 assert result['root']==str(new_root) and not result['oldCohortCopied'];assert b.tree(source,'world')==before;assert b.tree(new_root/'generations/server-2','world')==before;assert not (new_root/'control73').exists();assert not (new_root/'request74').exists();count+=5
 from https_owner_checks78 import check_repair
 check_repair(runtime,source_root,new_root,java)
 restored=r.Owner77(runtime,new_root);assert restored.previous_info==owner.previous_info and restored.active_label76=='r2';assert restored.s['run']!=owner.s['run'];assert restored.s['initialGeneration']==2;count+=3
 def ready(argv,cwd,env,log):
  assert cwd==new_root/'generations/server-2' and env['GENESIS_DEDICATED74_RUN']==restored.s['run'];c=read(new_root/'server-2.session',restored.key);d={k:c[k] for k in ('schema','run','generation','inventory')};d.update(sequence='0',request=str(uuid.UUID(int=0)),phase='READY',round='none',at='1');write(new_root/'server-2.status',restored.key,d);job=Mock();job.status.return_value=dict(rootExited=False,exitCode=None,logFailed=False);job.empty.return_value=False;return job
 restored.job_factory=ready;restored.start(2);restored.report('ACTIVE_BASELINE');packet=read(new_root/'bootstrap76.local',restored.key);assert packet['phase']=='READY' and packet['release']==owner.previous_info['release'] and packet['run']==restored.s['run'];count+=1
 b.java_call(runtime,java,'com.fixmer.genesis.technology.server.remote.BootstrapPublisher76','once',new_root);assert (new_root/'bootstrap76.state').is_file();count+=1
 first=b.core(runtime,java,'verify',source_root/'releases/r1',source_root/'publisher73/public.der');assert not restored.successor(first);assert restored.successor(dict(owner.previous_info,parent=owner.previous_info['release'],sequence=int(owner.previous_info['sequence'])+1));count+=2
 assert not r.catalog(source_root)['rows'][0]['eligible'];count+=1
 # The signed pair and actual managed server files must agree, even with an authentic checkpoint.
 info=copy.deepcopy(restored.previous_info);info['files']=[];restored.previous_info=info;restored.jobs[2].status.return_value=dict(rootExited=True,exitCode=0,logFailed=False);restored.jobs[2].empty.return_value=True
 try:restored.confirmed_stop(2);raise AssertionError('Mismatched release inventory sealed')
 except ValueError as bad:assert 'RELEASE_INVENTORY' in str(bad);count+=1
 print('Recovery77 real signed-pair/copy/new-owner/READY checks passed:',count,'; Java21 init, Windows Job/save and game READY were explicitly modeled.')
