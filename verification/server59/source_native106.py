"""Optional actual Windows wait-only HANDLE/creation-time check. No Minecraft/server folder."""
from pathlib import Path
import argparse,ctypes as C,json,os,subprocess,sys,tempfile,time
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.source106 import Source106
from first58.common import atomic

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',type=Path,required=True);p.add_argument('--classpath-file',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 if os.name!='nt' or C.sizeof(C.c_void_p)!=8:raise RuntimeError('Actual Windows x64 required; no simulation fallback')
 with tempfile.TemporaryDirectory(prefix='Genesis106 source ') as tmp:
  root=Path(tmp);process=subprocess.Popen([str(a.java),'-cp',a.classpath_file.read_text(encoding='utf-8').strip(),'com.fixmer.mared.BootstrapSourceProbe106',str(root)],stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  try:
   until=time.monotonic()+10
   while not (root/'identity106').exists() and time.monotonic()<until:
    if process.poll() is not None:raise AssertionError('Source fixture exited before identity')
    time.sleep(.1)
   pid,created=map(int,(root/'identity106').read_text().splitlines())
   if pid!=process.pid:raise AssertionError('Native PID mismatch')
   try:Source106(pid,created-1)
   except ValueError:pass
   else:raise AssertionError('Accepted wrong creation time')
   with Source106(pid,created) as source:
    if source.exited():raise AssertionError('Source was already gone')
    atomic(root/'exit106',b'normal fixture exit');until=time.monotonic()+10
    while not source.exited() and time.monotonic()<until:time.sleep(.1)
    if source.exit_code()!=0:raise AssertionError('Source did not exit normally')
   if process.wait(timeout=5)!=0:raise AssertionError('Child fixture exit failed')
   result=dict(schema=106,status='passed',scope='REAL_WINDOWS_SOURCE_HANDLE_AND_CREATION_TIME',minecraft_tested=False)
   a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
  finally:
   atomic(root/'exit106',b'normal fixture exit')
   try:process.wait(timeout=10)
   except subprocess.TimeoutExpired:pass # Fixed child fixture self-expires at45s; no kill or PID adoption.
if __name__=='__main__':main()
