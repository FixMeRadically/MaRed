"""Windows x64 owner: Job-at-create, suspended launch, no breakaway, bounded output.
No PID adoption, shell, in-place install permit or fallback to an unmanaged subprocess.
"""
import os,subprocess,threading,time,ctypes as C
from pathlib import Path
from .common import safe

class WindowsJob:
 def launch_command(self,argv,cwd,env):
  module=safe(argv[0])
  if module.name.lower()!='java.exe' or not module.is_file():raise ValueError('Only explicit Java executable')
  return module,subprocess.list2cmdline(argv)
 def __init__(self,argv,cwd,env,log):
  if os.name!='nt' or C.sizeof(C.c_void_p)!=8:raise RuntimeError('Windows x64 is required; no native simulation fallback')
  from ctypes import wintypes as W
  self.W=W;self.k=C.WinDLL('kernel32',use_last_error=True);self.job=self.process=self.thread=self.read=None;self.output_error=False;self.closed=False
  class SECURITY(C.Structure):_fields_=[('length',W.DWORD),('descriptor',C.c_void_p),('inherit',W.BOOL)]
  class IO(C.Structure):_fields_=[(n,C.c_ulonglong) for n in ('readOps','writeOps','otherOps','readBytes','writeBytes','otherBytes')]
  class LIMIT(C.Structure):_fields_=[('per',C.c_longlong),('job',C.c_longlong),('flags',W.DWORD),('min',C.c_size_t),('max',C.c_size_t),('active',W.DWORD),('affinity',C.c_size_t),('priority',W.DWORD),('scheduling',W.DWORD)]
  class EXTENDED(C.Structure):_fields_=[('basic',LIMIT),('io',IO),('processMemory',C.c_size_t),('jobMemory',C.c_size_t),('peakProcess',C.c_size_t),('peakJob',C.c_size_t)]
  class START(C.Structure):_fields_=[('cb',W.DWORD),('reserved',W.LPWSTR),('desktop',W.LPWSTR),('title',W.LPWSTR),('x',W.DWORD),('y',W.DWORD),('width',W.DWORD),('height',W.DWORD),('xchars',W.DWORD),('ychars',W.DWORD),('fill',W.DWORD),('flags',W.DWORD),('show',W.WORD),('reserved2size',W.WORD),('reserved2',C.c_void_p),('stdin',W.HANDLE),('stdout',W.HANDLE),('stderr',W.HANDLE)]
  class STARTEX(C.Structure):_fields_=[('start',START),('attributes',C.c_void_p)]
  class PROCESS(C.Structure):_fields_=[('process',W.HANDLE),('thread',W.HANDLE),('pid',W.DWORD),('tid',W.DWORD)]
  class ACCOUNT(C.Structure):_fields_=[('user',C.c_longlong),('kernel',C.c_longlong),('periodUser',C.c_longlong),('periodKernel',C.c_longlong),('faults',W.DWORD),('total',W.DWORD),('active',W.DWORD),('terminated',W.DWORD)]
  self.ACCOUNT=ACCOUNT
  signatures={
   'CreateJobObjectW':(W.HANDLE,[C.c_void_p,W.LPCWSTR]),'SetInformationJobObject':(W.BOOL,[W.HANDLE,C.c_int,C.c_void_p,W.DWORD]),
   'QueryInformationJobObject':(W.BOOL,[W.HANDLE,C.c_int,C.c_void_p,W.DWORD,C.c_void_p]),'CloseHandle':(W.BOOL,[W.HANDLE]),
   'CreatePipe':(W.BOOL,[C.POINTER(W.HANDLE),C.POINTER(W.HANDLE),C.POINTER(SECURITY),W.DWORD]),'SetHandleInformation':(W.BOOL,[W.HANDLE,W.DWORD,W.DWORD]),
   'CreateFileW':(W.HANDLE,[W.LPCWSTR,W.DWORD,W.DWORD,C.c_void_p,W.DWORD,W.DWORD,W.HANDLE]),
   'InitializeProcThreadAttributeList':(W.BOOL,[C.c_void_p,W.DWORD,W.DWORD,C.POINTER(C.c_size_t)]),
   'UpdateProcThreadAttribute':(W.BOOL,[C.c_void_p,W.DWORD,C.c_size_t,C.c_void_p,C.c_size_t,C.c_void_p,C.c_void_p]),
   'DeleteProcThreadAttributeList':(None,[C.c_void_p]),
   'CreateProcessW':(W.BOOL,[W.LPCWSTR,W.LPWSTR,C.c_void_p,C.c_void_p,W.BOOL,W.DWORD,C.c_void_p,W.LPCWSTR,C.c_void_p,C.POINTER(PROCESS)]),
   'ResumeThread':(W.DWORD,[W.HANDLE]),'WaitForSingleObject':(W.DWORD,[W.HANDLE,W.DWORD]),'GetExitCodeProcess':(W.BOOL,[W.HANDLE,C.POINTER(W.DWORD)]),
   'ReadFile':(W.BOOL,[W.HANDLE,C.c_void_p,W.DWORD,C.POINTER(W.DWORD),C.c_void_p]),'IsProcessInJob':(W.BOOL,[W.HANDLE,W.HANDLE,C.POINTER(W.BOOL)]),
   'TerminateJobObject':(W.BOOL,[W.HANDLE,W.UINT])}
  for name,(result,args) in signatures.items():fn=getattr(self.k,name);fn.restype=result;fn.argtypes=args
  def ok(value):
   if not value:raise C.WinError(C.get_last_error())
   return value
  self.ok=ok;out_write=nul=None;attributes=None;attributes_ready=False;started=False
  try:
   if not argv or len(argv)>128 or any(not isinstance(x,str) or '\x00' in x or '\n' in x or '\r' in x for x in argv):raise ValueError('Native argv')
   cwd=safe(cwd);module,command=self.launch_command(argv,cwd,env)
   self.job=ok(self.k.CreateJobObjectW(None,None));limits=EXTENDED();limits.basic.flags=0x2000;ok(self.k.SetInformationJobObject(self.job,9,C.byref(limits),C.sizeof(limits)))
   sec=SECURITY(C.sizeof(SECURITY),None,True);read=W.HANDLE();write=W.HANDLE();ok(self.k.CreatePipe(C.byref(read),C.byref(write),C.byref(sec),0));self.read=read.value;out_write=write.value;ok(self.k.SetHandleInformation(self.read,1,0))
   nul=self.k.CreateFileW('NUL',0x80000000,3,C.byref(sec),3,0,None)
   if nul==C.c_void_p(-1).value:raise C.WinError(C.get_last_error())
   size=C.c_size_t();self.k.InitializeProcThreadAttributeList(None,2,0,C.byref(size))
   if not size.value:raise C.WinError(C.get_last_error())
   storage=C.create_string_buffer(size.value);attributes=C.cast(storage,C.c_void_p)
   ok(self.k.InitializeProcThreadAttributeList(attributes,2,0,C.byref(size)));attributes_ready=True
   handles=(W.HANDLE*2)(nul,out_write);jobs=(W.HANDLE*1)(self.job)
   ok(self.k.UpdateProcThreadAttribute(attributes,0,0x20002,C.byref(handles),C.sizeof(handles),None,None))
   ok(self.k.UpdateProcThreadAttribute(attributes,0,0x2000D,C.byref(jobs),C.sizeof(jobs),None,None))
   startup=STARTEX();startup.start.cb=C.sizeof(STARTEX);startup.start.flags=0x100;startup.start.stdin=nul;startup.start.stdout=startup.start.stderr=out_write;startup.attributes=attributes
   if len(command.encode('utf-16-le'))//2>32760:raise ValueError('Windows command length')
   if any('\x00' in k+v or '=' in k for k,v in env.items()):raise ValueError('Environment')
   environment=C.create_unicode_buffer('\x00'.join(k+'='+v for k,v in sorted(env.items(),key=lambda item:item[0].upper()))+'\x00\x00')
   information=PROCESS();ok(self.k.CreateProcessW(str(module),C.create_unicode_buffer(command),None,None,True,0x4|0x400|0x80000|0x8000000,environment,str(cwd),C.byref(startup),C.byref(information)))
   self.process=information.process;self.thread=information.thread;self.pid=information.pid
   member=W.BOOL();ok(self.k.IsProcessInJob(self.process,self.job,C.byref(member)))
   if not member.value:raise RuntimeError('Child was not in owner Job at creation')
   self.reader=threading.Thread(target=self._drain,args=(Path(log),),daemon=True,name='first58-output');self.reader.start()
   previous=self.k.ResumeThread(self.thread)
   if previous==0xFFFFFFFF:raise C.WinError(C.get_last_error())
   if previous!=1:raise RuntimeError('Unexpected Java process suspend count')
   self.k.CloseHandle(self.thread);self.thread=None;started=True
  finally:
   if attributes_ready:self.k.DeleteProcThreadAttributeList(attributes)
   for h in (out_write,nul):
    if h and h!=C.c_void_p(-1).value:self.k.CloseHandle(h)
   if not started:self.close()
 def _drain(self,log):
  kept=0;buffer=C.create_string_buffer(65536);count=self.W.DWORD()
  try:
   with log.open('xb') as out:
    while self.read:
     if not self.k.ReadFile(self.read,buffer,65536,C.byref(count),None):
      if not self.closed and C.get_last_error() not in {109,232}:self.output_error=True
      break
     if not count.value:break
     size=min(count.value,max(0,8*1024*1024-kept))
     if size:out.write(buffer.raw[:size]);kept+=size
    out.flush()
  except OSError:self.output_error=True
 def status(self):
  if self.closed:raise RuntimeError('Owner closed')
  accounting=self.ACCOUNT();self.ok(self.k.QueryInformationJobObject(self.job,1,C.byref(accounting),C.sizeof(accounting),None));wait=self.k.WaitForSingleObject(self.process,0)
  if wait not in (0,258):raise RuntimeError('Process wait unknown')
  code=self.W.DWORD();self.ok(self.k.GetExitCodeProcess(self.process,C.byref(code)))
  return {'active':accounting.active,'rootExited':wait==0,'exitCode':code.value if wait==0 else None,'logFailed':self.output_error}
 def empty(self):
  s=self.status();return s['rootExited'] and s['active']==0 and s['exitCode']==0 and not s['logFailed']
 def terminate_failed119(self):
  """Only the already-owned isolated failed client Job; retain handles until active=0.
  Callers must have withheld WORLD_FRAME_READY. Never used for a server/world owner.
  """
  if self.closed:raise RuntimeError('Owner closed')
  self.ok(self.k.TerminateJobObject(self.job,119))
  deadline=time.monotonic()+15
  while True:
   state=self.status()
   if state['rootExited'] and state['active']==0:return
   if time.monotonic()>=deadline:raise ValueError('RETURN119_CHILD_BUSY')
   time.sleep(.05)
 def close(self):
  if self.closed:return
  self.closed=True
  if self.job:self.k.CloseHandle(self.job);self.job=None
  for field in ('thread','process'):
   handle=getattr(self,field,None)
   if handle:self.k.CloseHandle(handle);setattr(self,field,None)
  # Closing Job kills remaining descendants; no PID lookup or fallback process is used.
  if getattr(self,'reader',None):self.reader.join(timeout=1)
  if self.read:self.k.CloseHandle(self.read);self.read=None
