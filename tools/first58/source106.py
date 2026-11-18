"""Wait-only handle for the JVM that explicitly requested its own bootstrap handoff.
No PID adoption, AssignProcessToJobObject, termination, or guessed descendant count.
"""
import ctypes as C,os
class Source106:
 def __init__(self,pid,created_ms):
  if os.name!='nt' or C.sizeof(C.c_void_p)!=8:raise ValueError('SERVER106_WINDOWS_X64_REQUIRED')
  from ctypes import wintypes as W
  self.k=C.WinDLL('kernel32',use_last_error=True);self.handle=None
  for name,rest,args in [('OpenProcess',W.HANDLE,[W.DWORD,W.BOOL,W.DWORD]),('GetProcessTimes',W.BOOL,[W.HANDLE,C.c_void_p,C.c_void_p,C.c_void_p,C.c_void_p]),('WaitForSingleObject',W.DWORD,[W.HANDLE,W.DWORD]),('GetExitCodeProcess',W.BOOL,[W.HANDLE,C.POINTER(W.DWORD)]),('CloseHandle',W.BOOL,[W.HANDLE])]:
   f=getattr(self.k,name);f.restype=rest;f.argtypes=args
  try:
   self.handle=self.k.OpenProcess(0x1000|0x100000,False,pid)
   if not self.handle:raise C.WinError(C.get_last_error())
   made=C.c_ulonglong();exit_time=C.c_ulonglong();kernel=C.c_ulonglong();user=C.c_ulonglong()
   if not self.k.GetProcessTimes(self.handle,C.byref(made),C.byref(exit_time),C.byref(kernel),C.byref(user)):raise C.WinError(C.get_last_error())
   actual=made.value//10000-11644473600000
   if actual!=created_ms:raise ValueError('SERVER106_SOURCE_IDENTITY')
   if self.exited():raise ValueError('SERVER106_SOURCE_ALREADY_EXITED')
  except BaseException:self.close();raise
 def exited(self):
  v=self.k.WaitForSingleObject(self.handle,0)
  if v not in (0,258):raise ValueError('SERVER106_SOURCE_WAIT_FAILED')
  return v==0
 def exit_code(self):
  from ctypes import wintypes as W
  if not self.exited():raise ValueError('SERVER106_SOURCE_STILL_ACTIVE')
  value=W.DWORD()
  if not self.k.GetExitCodeProcess(self.handle,C.byref(value)):raise C.WinError(C.get_last_error())
  return value.value
 def close(self):
  if self.handle:self.k.CloseHandle(self.handle);self.handle=None
 def __enter__(self):return self
 def __exit__(self,*args):self.close()
