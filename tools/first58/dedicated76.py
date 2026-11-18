"""Pre-login state for a persistent75 owner. Never changes its world/commit/Job barriers."""
from pathlib import Path
import json,os,subprocess,time,zipfile
from . import dedicated75 as old
from . import dedicated74 as base
from .bridge67 import read,write
from .common import safe,json_write
from .runtime import core_classpath

def initialize(project,source,root,java,publisher=None,recovering=False,source_locked=False):
 found=0
 for jar in safe(source/'mods').glob('*.jar'):
  if jar.stat().st_size>64*1024*1024:raise ValueError('DEDICATED76_MOD_LIMIT')
  with zipfile.ZipFile(jar) as z:
   if 'com/fixmer/mared/Mared.class' in z.namelist():
    found+=1
    if 'com/fixmer/mared/mixin/BootstrapConnectMixin76.class' not in z.namelist():raise ValueError('DEDICATED76_INSTALL_NEW_BOOTSTRAP')
 if found!=1:raise ValueError('DEDICATED76_BOOTSTRAP')
 root=old.initialize(project,source,root,java,publisher=publisher,recovering=recovering,source_locked=source_locked);s,key=base.settings(root);write(root/'owner76',key,{'schema':'76','run':s['run']});return root

class Owner76(old.Owner75):
 def __init__(self,*args,**kwargs):
  super().__init__(*args,**kwargs)
  if read(self.root/'owner76',self.key)!={'schema':'76','run':self.s['run']}:raise ValueError('DEDICATED76_NEW_OWNER_REQUIRED')
  self.active_label76=None;self.serial76=0;self.signer76=None
 def successor(self,info):
  # A cancelled publication may still be a signed ancestor of the next release.
  # Follow only locally supplied, signature-verified pairs; never invent or skip an unknown parent.
  previous=self.previous_info
  if int(info['sequence'])<=int(previous['sequence']) or info['distribution']!=previous['distribution']:return False
  parent=info['parent'];ceiling=int(info['sequence']);seen=set();folders=list((self.root/'releases').iterdir())
  if len(folders)>128:return False
  verified={}
  while parent!=previous['release']:
   if parent=='none' or parent in seen or len(seen)>=32:return False
   seen.add(parent);found=None
   for folder in folders:
    try:
     base.label(folder.name)
     if folder not in verified:verified[folder]=self.tool('verify-history',folder,self.root/'publisher73/public.der')
     candidate=verified[folder]
     if candidate['release']==parent:found=candidate;break
    except Exception:continue
   if not found or found['distribution']!=previous['distribution'] or not int(previous['sequence'])<int(found['sequence'])<ceiling:return False
   ceiling=int(found['sequence']);parent=found['parent']
  return True
 def event(self,phase):
  super().event(phase)
  if phase=='SERVER_READY':self.active_label76=self.pending['label']
 def report(self,phase=None,reason=''):
  super().report(phase,reason)
  if not hasattr(self,'serial76'):return
  try:
   p=self.pending;committed=bool(p and self.last_pump and self.last_pump.get('phase')=='COMMITTED' and self.last_pump.get('release')==p['info']['release']);label=p['label'] if committed else self.active_label76
   if not label:return
   generation=p['targetGen'] if committed else self.current
   if generation<1:return
   live=self.current in self.jobs and not self.jobs[self.current].status()['rootExited'];native=self.status(self.current) if live else None
   ready=live and native and native['phase'] in {'READY','COMMAND_ACCEPTED'} and self.ready_generation1 and self.current==generation
   if committed:ready=bool(ready and self.last_pump.get('admission'))
   state='READY' if ready else 'TRANSITION' if committed else 'UNAVAILABLE'
   if self.stop_requested or self.phase in {'FAILED','STOP_NOT_CONFIRMED','STOPPED_SAVED','SAVING_OLD_SERVER'}:state='TRANSITION' if self.phase=='SAVING_OLD_SERVER' and not self.stop_requested else 'UNAVAILABLE'
   release=p['info']['release'] if committed else self.previous_info['release'];self.serial76+=1
   write(self.root/'bootstrap76.local',self.key,dict(schema='76',run=self.s['run'],at=str(int(time.time()*1000)),serial=str(self.serial76),phase=state,label=label,generation=str(generation),release=release))
  except Exception:json_write(self.root/'bootstrap76-error.json',{'schema':76,'code':'BOOTSTRAP76_UNAVAILABLE','serverContinues':True})
 def run(self):
  old.independent_owner()
  try:
   self.signer76=subprocess.Popen([self.s['java'],'-cp',str(core_classpath(self.project)),'com.fixmer.genesis.technology.server.remote.BootstrapPublisher76','serve',str(self.root)],stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0),close_fds=True)
   super().run()
  finally:
   if self.signer76:
    if self.signer76.stdin:self.signer76.stdin.close()
    try:self.signer76.wait(timeout=5)
    except subprocess.TimeoutExpired:self.signer76.kill();self.signer76.wait(timeout=5)
   (self.root/'bootstrap76.state').unlink(missing_ok=True)
