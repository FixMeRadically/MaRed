#!/usr/bin/env python3
"""Real premain/process/OS file-lock checks; fixture JVMs are not Minecraft."""
from pathlib import Path
import argparse,base64,os,subprocess,tempfile,time

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--classes',required=True,type=Path);p.add_argument('--fixtures',required=True,type=Path);p.add_argument('--agent',required=True,type=Path);a=p.parse_args();cp=str(a.fixtures.resolve())+os.pathsep+str(a.classes.resolve());agent=a.agent.resolve();count=0
 def ok(v):
  nonlocal count
  count+=1;assert v,count
 def encoded(game):return base64.urlsafe_b64encode(str(game).encode()).decode().rstrip('=')
 def cmd(mode,game,guard=True):return [a.java,'-XX:-CreateCoredumpOnCrash',*(['-javaagent:'+str(agent)+'='+encoded(game)] if guard else []),'-cp',cp,'com.fixmer.genesis.supervisor.AdmissionProcessFixture93',mode,str(game)]
 def probe(game):return subprocess.run(cmd('probe',game,False),capture_output=True,timeout=10).returncode
 def wait(fn,timeout=9):
  end=time.monotonic()+timeout
  while time.monotonic()<end:
   if fn():return
   time.sleep(.025)
  raise AssertionError('wait timed out')
 def stop(proc):
  if proc.poll() is None:proc.stdin.write('x');proc.stdin.flush();proc.wait(8)
 def launch(mode,game,log):
  out=log.open('w');proc=subprocess.Popen(cmd(mode,game),stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True);return proc,out
 with tempfile.TemporaryDirectory(prefix='Genesis admission93 ') as raw:
  root=Path(raw);game=root/'игра profile';game.mkdir();log=root/'first.log'
  proc,out=launch('bridge',game,log)
  try:
   wait(lambda:'GAME_MAIN_READY' in log.read_text());ok(proc.poll() is None);ok(probe(game)==3)
   report=(game/'config/mared/dev/profile-admission-last93.properties').read_text();ok('phase=before_main' in report)
   # Second JVM never reaches its application main, even though no Minecraft/MaRed classes ran.
   (game/'main-entered93').unlink();second=subprocess.run(cmd('hold',game),capture_output=True,text=True,timeout=9)
   ok(second.returncode==73);ok(not (game/'main-entered93').exists());ok('PROFILE93_IN_USE_OR_RESTORE_IN_PROGRESS' in second.stderr)
   other=root/'other';other.mkdir();parallel,out2=launch('hold',other,root/'other.log')
   try:wait(lambda:'GAME_MAIN_READY' in (root/'other.log').read_text());ok(parallel.poll() is None);ok(probe(other)==3)
   finally:stop(parallel);out2.close()
   ok(probe(other)==0);ok(probe(game)==3)
  finally:stop(proc);out.close()
  ok(probe(game)==0)
  # Locks persist throughout user shutdown hooks, and disappear only after OS/JVM exit.
  proc,out=launch('hook',game,root/'hook.log')
  try:
   wait(lambda:'GAME_MAIN_READY' in (root/'hook.log').read_text());proc.stdin.write('x');proc.stdin.flush();wait(lambda:(game/'hook-entered93').exists());ok(proc.poll() is None);ok(probe(game)==3);proc.wait(8)
  finally:
   if proc.poll() is None:proc.kill();proc.wait()
   out.close()
  ok(probe(game)==0)
  # A writer cannot be bypassed by premain; once released the same waiting JVM may enter.
  writer=subprocess.Popen(cmd('writer',game,False),stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True)
  try:
   ok(writer.stdout.readline().strip()=='WRITER_READY');(game/'main-entered93').unlink();proc,out=launch('hold',game,root/'wait.log')
   try:
    time.sleep(.2);ok(not (game/'main-entered93').exists());ok(proc.poll() is None);stop(writer);wait(lambda:'GAME_MAIN_READY' in (root/'wait.log').read_text());ok(probe(game)==3)
   finally:stop(proc);out.close()
  finally:stop(writer)
  ok(probe(game)==0)
  # Before-MaRed failure releases both OS locks without a shutdown-hook unlock routine.
  crash=subprocess.run(cmd('crash',game),capture_output=True,text=True,timeout=9);ok(crash.returncode!=0);ok('INTENTIONAL_FIXTURE_BEFORE_MARED' in crash.stderr);ok(probe(game)==0)
  # Environment claims and Java properties cannot forge a premain lease.
  env=dict(os.environ,GENESIS_PROFILE_REQUIRED93=encoded(game));late=subprocess.run(cmd('require',game,False),env=env,capture_output=True,text=True,timeout=9);ok(late.returncode!=0);ok('PROFILE93_EARLY_ADMISSION_MISSING' in late.stderr);ok(probe(game)==0)
  bad=subprocess.run([a.java,'-XX:-CreateCoredumpOnCrash','-javaagent:'+str(agent)+'=invalid!','-cp',cp,'com.fixmer.genesis.supervisor.AdmissionProcessFixture93','hold',str(game)],capture_output=True,text=True,timeout=9);ok(bad.returncode==73);ok(probe(game)==0)
  prepared=subprocess.run(cmd('option',game,False)+[str(agent)],capture_output=True,text=True,timeout=9);ok(prepared.returncode==0);option=prepared.stdout.strip();ok(option.startswith('-javaagent:'));ok('admission93' in option);ok(str(agent) not in option)
  # The actual Gradle helper returns a pinned immutable profile copy, not build/.
  command=cmd('require',game,False);command.insert(2,option)
  launch=subprocess.run(command,env=env,capture_output=True,text=True,timeout=9);ok(launch.returncode==0);ok('admitted_before_main' in launch.stdout);ok(probe(game)==0)
  # Java @file grammar preserves spaces/non-ASCII paths and the agent option.
  argfile=subprocess.run(cmd('argfile',game,False)+[str(agent)],env=env,capture_output=True,text=True,timeout=9);ok(argfile.returncode==0);ok('admitted_before_main' in argfile.stdout);ok(probe(game)==0)
  # A real child JVM retains admission if its launcher dies; no PID takeover is used.
  parent=subprocess.Popen(cmd('parent',game,False)+[str(agent)],stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  try:
   orphanlog=game/'orphan-fixture93.log';wait(lambda:orphanlog.exists() and 'GAME_MAIN_READY' in orphanlog.read_text());ok(probe(game)==3);parent.kill();parent.wait(8);ok(probe(game)==3)
  finally:
   if parent.poll() is None:parent.kill();parent.wait(8)
   (game/'fixture-stop93').write_text('test fixture only');wait(lambda:probe(game)==0)
  ok(probe(game)==0)
 print('Admission93 actual premain/JVM/OS-lock/classloader/hook checks passed:',count,'; no Minecraft or Windows certification.')
if __name__=='__main__':main()
