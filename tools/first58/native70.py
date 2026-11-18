"""Read-only source HANDLE and actual Windows Job descendant probe. No PID adoption/kill fallback."""
from pathlib import Path
import os,ctypes as C,time,uuid
from .windows_job import WindowsJob
from .runtime import core_classpath

class SourceLease70:
 def __init__(self,pid,birth):
  if os.name!='nt' or C.sizeof(C.c_void_p)!=8:raise ValueError('REMOTE70_WINDOWS')
  from ctypes import wintypes as W
  self.W=W;self.k=C.WinDLL('kernel32',use_last_error=True);self.handle=None
  self.k.OpenProcess.argtypes=[W.DWORD,W.BOOL,W.DWORD];self.k.OpenProcess.restype=W.HANDLE
  self.k.CloseHandle.argtypes=[W.HANDLE];self.k.CloseHandle.restype=W.BOOL
  self.k.WaitForSingleObject.argtypes=[W.HANDLE,W.DWORD];self.k.WaitForSingleObject.restype=W.DWORD
  self.k.GetExitCodeProcess.argtypes=[W.HANDLE,C.POINTER(W.DWORD)];self.k.GetExitCodeProcess.restype=W.BOOL
  class FileTime(C.Structure):_fields_=[('low',W.DWORD),('high',W.DWORD)]
  self.k.GetProcessTimes.argtypes=[W.HANDLE,*[C.POINTER(FileTime)]*4];self.k.GetProcessTimes.restype=W.BOOL
  self.handle=self.k.OpenProcess(0x100000|0x1000,False,pid)
  try:
   if not self.handle:raise ValueError('REMOTE70_SOURCE_UNAVAILABLE')
   values=[FileTime() for _ in range(4)]
   if not self.k.GetProcessTimes(self.handle,*map(C.byref,values)):raise ValueError('REMOTE70_SOURCE_IDENTITY')
   actual=((values[0].high<<32|values[0].low)-116444736000000000)//10000
   if abs(actual-birth)>1:raise ValueError('REMOTE70_SOURCE_IDENTITY')
  except BaseException:self.close();raise
 def closed(self):
  state=self.k.WaitForSingleObject(self.handle,0)
  if state not in (0,258):raise ValueError('REMOTE70_SOURCE_WAIT')
  return state==0
 def normal(self):
  if not self.closed():return False
  code=self.W.DWORD()
  if not self.k.GetExitCodeProcess(self.handle,C.byref(code)):raise ValueError('REMOTE70_SOURCE_WAIT')
  return code.value==0
 def close(self):
  if self.handle:self.k.CloseHandle(self.handle);self.handle=None

def environment(java,profile):
 env={k:v for k,v in os.environ.items() if k.upper() in {'SYSTEMROOT','WINDIR','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA','COMSPEC'}}
 if os.name!='nt':
  # Java decodes POSIX command-line paths using the process locale.
  env.update({k:os.environ[k] for k in ('LANG','LC_ALL','LC_CTYPE') if k in os.environ})
 temp=Path(profile)/'remote70-temp';temp.mkdir(exist_ok=True)
 env.update({'JAVA_HOME':str(Path(java).parent.parent),'PATH':str(Path(java).parent)+';'+str(Path(os.environ.get('SystemRoot','C:\\Windows'))/'System32'),'TEMP':str(temp),'TMP':str(temp)})
 return env

def native_probe(project,java,root):
 if os.name!='nt' or C.sizeof(C.c_void_p)!=8:raise ValueError('REMOTE70_WINDOWS')
 probe=Path(root)/('native-probe-'+uuid.uuid4().hex);probe.mkdir();leaf=probe/'leaf.pid';job=WindowsJob([str(java),'-cp',str(core_classpath(project)),'com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchProbe','tree',str(leaf)],probe,environment(java,probe),probe/'output.log');child=None
 try:
  end=time.monotonic()+15
  while not leaf.exists() or not job.status()['rootExited']:
   if time.monotonic()>end:raise ValueError('REMOTE70_NATIVE_PROBE')
   time.sleep(.05)
  if job.status()['active']<1 or job.empty():raise ValueError('REMOTE70_NATIVE_PROBE')
  from ctypes import wintypes as W
  job.k.OpenProcess.argtypes=[W.DWORD,W.BOOL,W.DWORD];job.k.OpenProcess.restype=W.HANDLE
  child=job.k.OpenProcess(0x100000,False,int(leaf.read_text()))
  if not child:raise ValueError('REMOTE70_NATIVE_PROBE')
  job.close()
  if job.k.WaitForSingleObject(child,10000)!=0:raise ValueError('REMOTE70_NATIVE_PROBE')
 finally:
  job.close()
  if child:job.k.CloseHandle(child)
