"""Explicit read-only argv capture from a user-selected Java PID; never adopts or stops it."""
from pathlib import Path
import ctypes as C,json,os,subprocess
from .common import safe,json_write
from .setup import java

def capture(project,pid,output):
 if os.name!='nt' or not 1<=pid<=0xffffffff:raise ValueError('Explicit Windows Java PID required')
 script=f'$p=Get-CimInstance Win32_Process -Filter "ProcessId={pid}"; if (!$p) {{exit 2}}; $p | Select-Object ExecutablePath,CommandLine | ConvertTo-Json -Compress'
 result=subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-Command',script],capture_output=True,text=True,timeout=20)
 if result.returncode:raise ValueError('Selected process is unavailable')
 data=json.loads(result.stdout);exe=safe(data['ExecutablePath'])
 if exe.name.lower() not in ('java.exe','javaw.exe'):raise ValueError('Selected PID is not Java')
 shell=C.WinDLL('shell32',use_last_error=True);kernel=C.WinDLL('kernel32',use_last_error=True)
 shell.CommandLineToArgvW.argtypes=[C.c_wchar_p,C.POINTER(C.c_int)];shell.CommandLineToArgvW.restype=C.POINTER(C.c_wchar_p);kernel.LocalFree.argtypes=[C.c_void_p];kernel.LocalFree.restype=C.c_void_p
 count=C.c_int();pointer=shell.CommandLineToArgvW(data['CommandLine'],C.byref(count))
 if not pointer:raise C.WinError(C.get_last_error())
 try:argv=[pointer[i] for i in range(count.value)]
 finally:kernel.LocalFree(pointer)
 if not argv or safe(argv[0])!=exe:raise ValueError('Process executable/command mismatch')
 argv[0]=str(exe.with_name('java.exe'))
 if argv.count('--gameDir')!=1 or argv.index('--gameDir')+1>=len(argv):raise ValueError('Capture requires expanded --gameDir argv; launch with normal full arguments')
 argv[argv.index('--gameDir')+1]='{profile}'
 # Old dev seed/cover flags refer to a different owner and must not survive the isolated launch.
 argv=[x for x in argv if not x.startswith(('-Dgenesis.launch.seed=','-Dgenesis.launch.capture=','-Dgenesis.restart.'))]
 output=safe(output)
 if output.exists() or not output.parent.is_dir():raise ValueError('NEW private capture directory required')
 output.mkdir();java(project,argv[0],'private',output);json_write(output/'client-argv.json',argv,new=True)
 return {'captured':True,'file':str(output/'client-argv.json'),'processModified':False,'containsPrivateLaunchArguments':True}
