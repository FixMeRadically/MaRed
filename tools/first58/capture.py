"""Explicit read-only argv capture from a user-selected Java PID; never adopts or stops it."""
from pathlib import Path
import ctypes as C,json,os,subprocess
from .common import safe,json_write
from .setup import java

def resolve_profile(source, profile_hint=None):
 source=Path(source)
 hint=None
 if profile_hint is not None:
  if not Path(profile_hint).is_absolute():raise ValueError('Minecraft profile path must be absolute')
  hint=safe(profile_hint)
  if not hint.is_dir():raise ValueError('Minecraft profile directory is unavailable')
 if source.is_absolute():
  profile=safe(source)
  if hint is not None and profile!=hint:raise ValueError('Captured gameDir differs from the running Minecraft profile')
  return profile
 if hint is None:raise ValueError('Relative gameDir requires the Minecraft profile supplied by the in-game setup button')
 # A relative path is resolved by Minecraft itself, not against the wizard working directory.
 return hint

def without_source_admission93(argv, profile):
 # runClient adds this capsule for the source profile. Its argument must not follow the copy.
 import base64,re
 from .common import sha
 profile=safe(profile)
 expected=base64.urlsafe_b64encode(str(profile).encode('utf-8')).decode('ascii').rstrip('=')
 agents=[i for i,value in enumerate(argv) if value.startswith('-javaagent:')]
 if len(agents)!=1:return argv
 index=agents[0]
 path,separator,target=argv[index][11:].partition('=')
 if not separator or target!=expected:return argv
 jar=Path(path)
 match=re.fullmatch(r'profile-([a-f0-9]{64})\.jar',jar.name)
 if not match or jar.parent!=profile/'config/mared/dev/admission93':return argv
 if sha(jar,256*1024)!=match[1]:raise ValueError('PROFILE93_CAPSULE_CHANGED')
 return argv[:index]+argv[index+1:]

def read_client(pid,profile_hint=None,cwd=None):
 if os.name!='nt' or not 1<=pid<=0xffffffff:raise ValueError('Explicit Windows Java PID required')
 script=f'[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); $p=Get-CimInstance Win32_Process -Filter "ProcessId={pid}"; if (!$p) {{exit 2}}; $p | Select-Object ExecutablePath,CommandLine | ConvertTo-Json -Compress'
 result=subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-Command',script],capture_output=True,text=True,encoding="utf-8-sig",timeout=20)
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
 from .launch62 import expand_argfiles
 argv=expand_argfiles(argv,cwd)
 return normalize_client_argv(argv,profile_hint)

def normalize_client_argv(argv,profile_hint=None):
 argv=list(argv)
 positions=[i for i,value in enumerate(argv) if value=='--gameDir' or value.startswith('--gameDir=')]
 if len(positions)!=1:raise ValueError('Capture requires one expanded --gameDir argument')
 index=positions[0]
 if argv[index]=='--gameDir':
  if index+1>=len(argv) or argv[index+1].startswith('--'):raise ValueError('Incomplete gameDir argument')
  source=argv[index+1];argv[index:index+2]=['--gameDir','{profile}']
 else:
  source=argv[index].split('=',1)[1];argv[index:index+1]=['--gameDir','{profile}']
 profile=resolve_profile(source,profile_hint)
 argv=without_source_admission93(argv,profile)
 # Both --option value and --option=value occur in launcher/IDE commands.
 # A copied quick-play target could connect before the signed admission gate.
 from .return119 import menu_argv
 argv=menu_argv(argv)
 argv=[x for x in argv if not x.startswith(('-Dgenesis.launch.seed=','-Dgenesis.launch.capture=','-Dgenesis.restart.'))]
 return argv,profile

def capture(project,pid,output):
 argv,profile=read_client(pid)
 output=safe(output)
 if output.exists() or not output.parent.is_dir():raise ValueError('NEW private capture directory required')
 output.mkdir();java(project,argv[0],'private',output);json_write(output/'client-argv.json',argv,new=True)
 return {'captured':True,'file':str(output/'client-argv.json'),'processModified':False,'containsPrivateLaunchArguments':True}
