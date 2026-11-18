"""Actual CMD -> run.bat -> Java21 Job test in a disposable directory; no Minecraft/world."""
from pathlib import Path
import argparse
import os
import sys
import tempfile
import time
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
from first58.batch124 import batch, java21, WindowsServerJob124
from first58.native70 import environment

SOURCE = '''
import java.nio.file.*;
public class BatchProbe124 {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(System.getProperty("user.dir"));
  var gui=new java.util.concurrent.atomic.AtomicReference<javax.swing.JFrame>();
  if(Boolean.getBoolean("fixture.gui")){javax.swing.SwingUtilities.invokeAndWait(()->{var frame=new javax.swing.JFrame("Genesis BAT GUI128 test");frame.setSize(260,100);frame.setVisible(true);gui.set(frame);});if(!gui.get().isShowing())throw new AssertionError("GUI not visible");Files.writeString(root.resolve("gui-visible"),"true");}

  Files.writeString(root.resolve("ready.tmp"),System.getProperty("java.version")+"\\n"+root+"\\n"+String.join(",",args)+"\\n"+System.getenv("JAVA_HOME"));
  Files.move(root.resolve("ready.tmp"),root.resolve("ready"),StandardCopyOption.ATOMIC_MOVE);
  System.out.println("BATCH124_ACTUAL_JAVA_CHILD");
  long until=System.nanoTime()+10_000_000_000L;
  while(!Files.exists(root.resolve("release"))&&System.nanoTime()<until)Thread.sleep(20);
  int code=Integer.parseInt(System.getProperty("fixture.exit","0"));
  Files.writeString(root.resolve("finished"),Integer.toString(code));
  if(gui.get()!=null)javax.swing.SwingUtilities.invokeAndWait(()->gui.get().dispose());
  System.exit(code);
 }
}
'''

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--java',type=Path,required=True)
    java=java21(parser.parse_args().java)
    if os.name!='nt':raise RuntimeError('Real Windows required')
    for expected,gui in ((0,False),(7,False),(0,True)):
        with tempfile.TemporaryDirectory(prefix='Genesis124 real BAT & spaces ') as temporary:
            root=Path(temporary);job=None
            args=root/'libraries/net/neoforged/neoforge/21.1.264/win_args.txt'
            args.parent.mkdir(parents=True)
            source=root/'BatchProbe124.java';source.write_text(SOURCE)
            args.write_text('"'+str(source).replace('\\','/')+'"\n')
            (root/'user_jvm_args.txt').write_text('-Dfixture.exit='+str(expected)+'\n-Dfixture.gui='+str(gui).lower()+'\n')
            (root/'run.bat').write_text('@echo off\njava @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.264/win_args.txt %*\npause\n')
            (root/'java.cmd').write_text('@echo WRONG_JAVA>shadowed\nexit /b 99\n')
            launch=batch(root,'21.1.264');env=environment(java,root)
            env.update(GENESIS_INPLACE_VERSION='104',GENESIS_SERVER_BATCH124=launch['batchSha'],GENESIS_SERVER_NEO124='21.1.264',NoDefaultCurrentDirectoryInExePath='1')
            try:
                job=WindowsServerJob124(launch['argv'],root,env,root/'output.log')
                deadline=time.monotonic()+15
                while not (root/'ready').exists():
                    if job.status()['rootExited'] or time.monotonic()>deadline:raise AssertionError('Java child did not start: '+(root/'output.log').read_text(errors='replace'))
                    time.sleep(.05)
                ready=(root/'ready').read_text().splitlines()
                assert ready[0].startswith('21.') and ready[1]==str(root) and ready[2]=='',ready
                assert (root/'gui-visible').exists()==gui,'GUI visibility check'
                assert job.status()['active']>=2,'CMD and Java must remain in the same owned Job'
                (root/'release').write_text('finish normally')
                while not (job.status()['rootExited'] and job.status()['active']==0):
                    if time.monotonic()>deadline:raise AssertionError('BAT/pause left an active process')
                    time.sleep(.05)
                status=job.status()
                assert status['exitCode']==expected,status
                assert (root/'finished').read_text()==str(expected)
                assert not (root/'shadowed').exists(),'A local java.cmd must not shadow the selected Java21'
                assert not status['logFailed']
                print('Batch124 real Windows CMD/BAT/Java21 passed: exit='+str(expected)+', Job empty, cwd and spaces/ampersand correct; no Minecraft.')
            finally:
                (root/'release').write_text('cleanup')
                if job:job.close()

if __name__=='__main__':main()
