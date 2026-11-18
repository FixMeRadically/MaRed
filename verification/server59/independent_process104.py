"""Optional REAL Windows broker/Job probe, with no Minecraft, server folder, tokens or world.
Only its own newly launched fixture tree is placed in a Job. Independent child expires itself.
"""
from pathlib import Path
import argparse,ctypes as C,json,os,subprocess,sys,tempfile,time
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools'))
from first58.common import atomic
from first58.independent104 import spawn,minimal_environment
from first58.windows_job import WindowsJob

def child(root):
 from first58.dedicated75 import independent_owner
 independent_owner();deadline=time.monotonic()+25
 while time.monotonic()<deadline and not (root/'stop').exists():atomic(root/'heartbeat',str(time.monotonic()).encode());time.sleep(.1)
 atomic(root/'done',b'done')
def broker(root):
 spawn([sys.executable,str(Path(__file__).resolve()),'--child',str(root)],root)
 deadline=time.monotonic()+10
 while time.monotonic()<deadline:
  if (root/'heartbeat').exists():return
  time.sleep(.1)
 raise RuntimeError('SERVER104_PROBE_CHILD_MISSING')
def main():
 p=argparse.ArgumentParser();p.add_argument('--child',type=Path);p.add_argument('--broker',type=Path);p.add_argument('--java',type=Path);p.add_argument('--classpath-file',type=Path);p.add_argument('--output',type=Path);a=p.parse_args()
 if a.child:child(a.child);return
 if a.broker:broker(a.broker);return
 if os.name!='nt' or C.sizeof(C.c_void_p)!=8:raise RuntimeError('Actual Windows x64 required; no simulation fallback')
 with tempfile.TemporaryDirectory(prefix='Genesis104 native ') as tmp:
  root=Path(tmp);job=None
  try:
   argv=[str(a.java),'-cp',a.classpath_file.read_text(encoding='utf-8').strip(),'com.fixmer.mared.IndependentProbe104',sys.executable,str(Path(__file__).resolve()),str(root)]
   job=WindowsJob(argv,root,minimal_environment(),root/'broker.log');deadline=time.monotonic()+15
   while time.monotonic()<deadline and not job.empty():time.sleep(.1)
   if not job.empty():raise RuntimeError('SERVER104_PROBE_PARENT_NOT_EMPTY')
   job.close();job=None
   if not (root/'heartbeat').exists():raise RuntimeError('SERVER104_PROBE_NO_HEARTBEAT')
   before=(root/'heartbeat').read_bytes();time.sleep(.5)
   if before==(root/'heartbeat').read_bytes():raise RuntimeError('SERVER104_PROBE_NOT_INDEPENDENT')
   atomic(root/'stop',b'voluntary exit');deadline=time.monotonic()+5
   while time.monotonic()<deadline and not (root/'done').exists():time.sleep(.1)
   if not (root/'done').exists():raise RuntimeError('SERVER104_PROBE_NO_VOLUNTARY_EXIT')
   result=dict(schema=104,status='passed',scope='REAL_WINDOWS_JOB_AND_INDEPENDENT_CHILD',minecraft_tested=False)
   if a.output:a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n')
   print(json.dumps(result))
  finally:
   atomic(root/'stop',b'voluntary exit')
   if job:job.close()
   # The only independent fixture child also has a fixed 25-second lifetime.
   if (root/'heartbeat').exists():
    deadline=time.monotonic()+5
    while time.monotonic()<deadline and not (root/'done').exists():time.sleep(.1)
if __name__=='__main__':main()
