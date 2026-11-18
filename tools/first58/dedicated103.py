"""Routine same-code restart: save receipt + empty owned Job, then next generation.
No player update grant is invented. World data are never downgraded or copied live.
"""
from pathlib import Path
import re,shutil,uuid,hashlib
from .dedicated88 import Owner88
from . import dedicated74 as base
from .bridge67 import read,write
from .common import safe,inventory,json_write,atomic

def launcher(project,root):
 project=safe(project);root=safe(root);settings,key=base.settings(root)
 # Batch has a fixed grammar. Refuse shell expansion instead of trying to escape it.
 args=['py','-3',str(project/'tools/dedicated_server75.py'),str(project),'run',str(root)]
 if any(re.search(r'[\x00-\x1f"%!&|<>^]',v) for v in args):raise ValueError('SERVER103_LAUNCHER_PATH')
 file=root/'Genesis-server.cmd';data=('@echo off\r\nsetlocal DisableDelayedExpansion\r\nchcp 65001 >nul\r\n'+' '.join('"'+v+'"' for v in args)+'\r\nif errorlevel 1 pause\r\n').encode('utf-8')
 proof=root/'launcher103'
 if file.exists():
  old=file.read_bytes()
  if old!=data:
   if not proof.exists() or read(proof,key)!=dict(schema='103',run=settings['run'],sha=hashlib.sha256(old).hexdigest()):raise ValueError('SERVER103_LAUNCHER_CHANGED')
   atomic(file,data)
 else:atomic(file,data,new=True)
 write(proof,key,dict(schema='103',run=settings['run'],sha=hashlib.sha256(data).hexdigest()))
 return file

class Owner103(Owner88):
 def __init__(self,*args,**kwargs):
  super().__init__(*args,**kwargs);write(self.root/'owner103',self.key,dict(schema='103',run=self.s['run']))
 def restart103(self):
  self.restartStopping103=False
  if self.pending or self.external or self.busy75:raise ValueError('SERVER103_UPDATE_ACTIVE')
  if self.current>=32:raise ValueError('SERVER103_GENERATION_LIMIT')
  g=self.current;source=self.root/f'generations/server-{g}';targetg=g+1;final=self.root/f'generations/server-{targetg}'
  if final.exists() or (self.root/f'server-{targetg}.session').exists():raise ValueError('SERVER103_GENERATION_EXISTS')
  self.checkpoint();expected=read(self.root/f'server-{g}.session',self.key)['inventory']
  if inventory(source)!=expected:raise ValueError('SERVER103_CODE_CHANGED')
  stage=self.root/'stages'/(uuid.uuid4().hex+'.building');self.busy75=True;stopping=False
  try:
   self.report('RESTART_CHECKING');seal=base.copy_tree(source,stage,self.s['level'],True,self.checkpoint)
   if inventory(stage)!=expected:raise ValueError('SERVER103_CODE_CHANGED')
   if base.tree(source,self.s['level'],True)!=seal:raise ValueError('SERVER103_CODE_CHANGED')
   round_id=str(uuid.uuid4());self.report('RESTART_SAVING');stopping=True;self.restartStopping103=True;cmd=self.stop(g,'SHUTDOWN',round_id)
   job=self.jobs[g];native=job.status();receipt=self.status(g)
   if not job.empty() or not native['rootExited'] or native['exitCode']!=0 or not receipt or receipt['phase']!='STOPPED_SAVED' or any(receipt[k]!=cmd[k] for k in ('sequence','request','round','inventory')):raise ValueError('SERVER103_SAVE_UNCONFIRMED')
   if base.tree(source,self.s['level'],True)!=seal or base.tree(stage,self.s['level'],True)!=seal or inventory(source)!=expected:raise ValueError('SERVER103_CODE_CHANGED')
   level=self.s['level'];self.report('RESTART_COPYING')
   with base.stopped_world(source,level):
    base.copy_tree(source/level,stage/level,'unused',checkpoint=self.health,raw=True)
    if (source/'config').is_dir():base.copy_tree(source/'config',stage/'config','unused',checkpoint=self.health,raw=True)
    for name in base.MUTABLE:
     path=safe(source/name)
     if path.exists():
      if not path.is_file():raise ValueError('SERVER103_MUTABLE_FILE')
      shutil.copyfile(path,stage/name)
   if inventory(stage)!=expected or base.tree(source,self.s['level'],True)!=seal:raise ValueError('SERVER103_CODE_CHANGED')
   base.session(self.root,self.s,self.key,targetg,expected);stage.rename(final)
   json_write(self.root/f'restart103-{targetg}.json',dict(schema=103,source=g,target=targetg,round=round_id,worldCopiedAfterSave=True))
   self.event('RESTART_SAVED');job.close();del self.jobs[g];self.ready_generation1=False;self.current_inventory=expected
   self.report('RESTART_STARTING');self.start(targetg);self.failure_code=self.failure_stage='';self.event('RESTART_READY');self.report('ACTIVE_UPDATED' if self.previous_info else 'ACTIVE_BASELINE')
  except Exception:
   # Before STOP the old server stays alive. After STOP do not guess a safe replay.
   if not stopping:self.report('PREPARATION_FAILED','Не удалось подготовить перезапуск. Текущий сервер продолжает работать.')
   raise
  finally:self.busy75=False
 def receive(self):
  request=super().receive()
  if request and request['action']=='RESTART':
   try:self.restart103()
   except Exception as error:
    if str(error)=='SERVER103_UPDATE_ACTIVE':self.report(reason='Дождись завершения текущего обновления.')
    elif not getattr(self,'restartStopping103',False):
     self.failure_code=str(error) if re.fullmatch('[A-Z0-9_]{1,100}',str(error)) else 'SERVER103_PREPARATION_FAILED'
     self.report('PREPARATION_FAILED','Не удалось подготовить перезапуск. Текущий сервер продолжает работать.')
    else:raise
   return None
  return request
