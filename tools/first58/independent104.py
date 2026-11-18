"""Start the requested server controller outside a client Job, without weakening any Job.
Windows documented explicit-parent creation: same-user desktop shell, suspended child,
no inherited handles, verify no Job before resuming. No injection, scheduled task or elevation.
"""
from pathlib import Path
import ctypes as C,os,subprocess
from .common import safe

def minimal_environment():
 env={k:v for k,v in os.environ.items() if k.upper() in {'SYSTEMROOT','WINDIR','USERPROFILE','APPDATA','LOCALAPPDATA','PROGRAMDATA','TEMP','TMP'}}
 env.update(PYTHONUTF8='1',PYTHONIOENCODING='utf-8');return env

def spawn(argv,cwd):
 if os.name!='nt' or C.sizeof(C.c_void_p)!=8:raise ValueError('SERVER104_WINDOWS_X64_REQUIRED')
 from ctypes import wintypes as W
 k=C.WinDLL('kernel32',use_last_error=True);u=C.WinDLL('user32',use_last_error=True);a=C.WinDLL('advapi32',use_last_error=True)
 def bind(lib,name,result,args):f=getattr(lib,name);f.restype=result;f.argtypes=args;return f
 bind(k,'GetCurrentProcess',W.HANDLE,[]);bind(k,'IsProcessInJob',W.BOOL,[W.HANDLE,W.HANDLE,C.POINTER(W.BOOL)]);bind(k,'CloseHandle',W.BOOL,[W.HANDLE]);bind(k,'OpenProcess',W.HANDLE,[W.DWORD,W.BOOL,W.DWORD]);bind(k,'ProcessIdToSessionId',W.BOOL,[W.DWORD,C.POINTER(W.DWORD)]);bind(k,'GetCurrentProcessId',W.DWORD,[])
 bind(a,'OpenProcessToken',W.BOOL,[W.HANDLE,W.DWORD,C.POINTER(W.HANDLE)]);bind(a,'GetTokenInformation',W.BOOL,[W.HANDLE,C.c_int,C.c_void_p,W.DWORD,C.POINTER(W.DWORD)]);bind(a,'EqualSid',W.BOOL,[C.c_void_p,C.c_void_p])
 def ok(v):
  if not v:raise C.WinError(C.get_last_error())
  return v
 def in_job(process):member=W.BOOL();ok(k.IsProcessInJob(process,None,C.byref(member)));return member.value
 argv=[str(v) for v in argv];exe=safe(argv[0]);cwd=safe(cwd)
 if not exe.is_file() or exe.name.lower() not in {'python.exe','pythonw.exe'} or any('\x00' in v or '\n' in v or '\r' in v for v in argv):raise ValueError('SERVER104_LAUNCH_ARGUMENTS')
 if not in_job(k.GetCurrentProcess()):
  process=subprocess.Popen(argv,cwd=cwd,env=minimal_environment(),stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,close_fds=True,creationflags=0x08000000)
  return process.pid
 # A headless host can have no desktop shell. Use an already permitted breakaway,
 # without changing its parent Job limits; the owner still checks its own scope first.
 try:
  detached=subprocess.Popen(argv,cwd=cwd,env=minimal_environment(),stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,close_fds=True,creationflags=0x01000000|0x08000000)
  return detached.pid
 except OSError:pass
 bind(u,'GetShellWindow',W.HWND,[]);bind(u,'GetWindowThreadProcessId',W.DWORD,[W.HWND,C.POINTER(W.DWORD)]);pid=W.DWORD();shell=u.GetShellWindow()
 if not shell or not u.GetWindowThreadProcessId(shell,C.byref(pid)):raise ValueError('SERVER104_DESKTOP_LAUNCH_UNAVAILABLE')
 parent=ok(k.OpenProcess(0x80|0x1000,False,pid.value));attributes=None;attr_ready=False;process=thread=None
 try:
  current_session=W.DWORD();parent_session=W.DWORD();ok(k.ProcessIdToSessionId(k.GetCurrentProcessId(),C.byref(current_session)));ok(k.ProcessIdToSessionId(pid,C.byref(parent_session)))
  if current_session.value!=parent_session.value or in_job(parent):raise ValueError('SERVER104_INDEPENDENT_PARENT_REQUIRED')
  def sid(process):
   token=W.HANDLE();ok(a.OpenProcessToken(process,8,C.byref(token)))
   try:
    needed=W.DWORD();a.GetTokenInformation(token,1,None,0,C.byref(needed));buffer=C.create_string_buffer(needed.value);ok(a.GetTokenInformation(token,1,buffer,needed,C.byref(needed)));return buffer
   finally:k.CloseHandle(token)
  own_sid=sid(k.GetCurrentProcess());parent_sid=sid(parent)
  if not a.EqualSid(C.cast(own_sid,C.POINTER(C.c_void_p))[0],C.cast(parent_sid,C.POINTER(C.c_void_p))[0]):raise ValueError('SERVER104_SAME_USER_REQUIRED')
  bind(k,'QueryFullProcessImageNameW',W.BOOL,[W.HANDLE,W.DWORD,W.LPWSTR,C.POINTER(W.DWORD)]);image=C.create_unicode_buffer(32768);size=W.DWORD(len(image));ok(k.QueryFullProcessImageNameW(parent,0,image,C.byref(size)))
  expected=Path(os.environ.get('WINDIR',r'C:\Windows'))/'explorer.exe'
  if os.path.normcase(os.path.abspath(image.value))!=os.path.normcase(os.path.abspath(expected)):raise ValueError('SERVER104_DESKTOP_IDENTITY')
  class START(C.Structure):_fields_=[('cb',W.DWORD),('reserved',W.LPWSTR),('desktop',W.LPWSTR),('title',W.LPWSTR),('x',W.DWORD),('y',W.DWORD),('width',W.DWORD),('height',W.DWORD),('xc',W.DWORD),('yc',W.DWORD),('fill',W.DWORD),('flags',W.DWORD),('show',W.WORD),('n',W.WORD),('extra',C.c_void_p),('stdin',W.HANDLE),('stdout',W.HANDLE),('stderr',W.HANDLE)]
  class STARTEX(C.Structure):_fields_=[('start',START),('attributes',C.c_void_p)]
  class PROCESS(C.Structure):_fields_=[('process',W.HANDLE),('thread',W.HANDLE),('pid',W.DWORD),('tid',W.DWORD)]
  bind(k,'InitializeProcThreadAttributeList',W.BOOL,[C.c_void_p,W.DWORD,W.DWORD,C.POINTER(C.c_size_t)]);bind(k,'UpdateProcThreadAttribute',W.BOOL,[C.c_void_p,W.DWORD,C.c_size_t,C.c_void_p,C.c_size_t,C.c_void_p,C.c_void_p]);bind(k,'DeleteProcThreadAttributeList',None,[C.c_void_p]);bind(k,'CreateProcessW',W.BOOL,[W.LPCWSTR,W.LPWSTR,C.c_void_p,C.c_void_p,W.BOOL,W.DWORD,C.c_void_p,W.LPCWSTR,C.c_void_p,C.POINTER(PROCESS)]);bind(k,'ResumeThread',W.DWORD,[W.HANDLE]);bind(k,'TerminateProcess',W.BOOL,[W.HANDLE,W.UINT])
  count=C.c_size_t();k.InitializeProcThreadAttributeList(None,1,0,C.byref(count));attributes=C.create_string_buffer(count.value);ok(k.InitializeProcThreadAttributeList(attributes,1,0,C.byref(count)));attr_ready=True;handle=W.HANDLE(parent);ok(k.UpdateProcThreadAttribute(attributes,0,0x20000,C.byref(handle),C.sizeof(handle),None,None))
  info=STARTEX();info.start.cb=C.sizeof(info);info.attributes=C.cast(attributes,C.c_void_p);created=PROCESS();env=minimal_environment();block=C.create_unicode_buffer('\x00'.join(key+'='+value for key,value in sorted(env.items(),key=lambda i:i[0].upper()))+'\x00\x00');command=subprocess.list2cmdline(argv)
  if len(command)>32760:raise ValueError('SERVER104_LAUNCH_ARGUMENTS')
  ok(k.CreateProcessW(str(exe),C.create_unicode_buffer(command),None,None,False,0x4|0x400|0x80000|0x08000000,block,str(cwd),C.byref(info),C.byref(created)));process=created.process;thread=created.thread
  # Do not run even one instruction of a controller that remains in a client Job.
  if in_job(process):raise ValueError('SERVER104_CHILD_STILL_IN_CLIENT_JOB')
  if k.ResumeThread(thread)==0xffffffff:raise C.WinError(C.get_last_error())
  process=None # caller never adopts/terminates an arbitrary PID; controller proves itself via private files.
  k.CloseHandle(created.process);return created.pid
 except BaseException:
  if process:k.TerminateProcess(process,2)
  raise
 finally:
  if thread:k.CloseHandle(thread)
  if process:k.CloseHandle(process)
  if attr_ready:k.DeleteProcThreadAttributeList(attributes)
  k.CloseHandle(parent)
