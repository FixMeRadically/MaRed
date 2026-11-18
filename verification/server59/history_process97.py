#!/usr/bin/env python3
"""Real concurrent history writer/readers. Not Minecraft or native UI testing."""
from pathlib import Path
import argparse,concurrent.futures,os,subprocess,tempfile

def main():
 p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--classes',type=Path,required=True);p.add_argument('--fixtures',type=Path,required=True);a=p.parse_args();cp=str(a.fixtures.resolve())+os.pathsep+str(a.classes.resolve());count=0
 def ok(v):
  nonlocal count
  count+=1;assert v,count
 def command(mode,game,*extra):return [a.java,'-cp',cp,'com.fixmer.genesis.supervisor.HistoryProcessFixture97',mode,str(game),*extra]
 def reader(game,phase):
  r=subprocess.run(command('reader',game,phase),capture_output=True,text=True,timeout=12);return r.returncode==0 and 'READ97_OK' in r.stdout
 with tempfile.TemporaryDirectory(prefix='Genesis history97 ') as raw:
  game=Path(raw)/'profile with space';game.mkdir();private=game/'supervisor.properties';private.write_text('controlToken=PRIVATE_TOKEN\ncommand=PRIVATE_COMMAND\n');before=private.read_bytes();world=game/'saves/world.bin';world.parent.mkdir();world.write_bytes(b'WORLD_DATA')
  writer=subprocess.Popen(command('writer',game),stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
  def marker(expected):
   for i in range(40):
    text=writer.stdout.readline()
    if text.strip()==expected:return
    if not text:raise AssertionError('writer ended before '+expected)
   raise AssertionError('marker not found')
  def update(action):writer.stdin.write(action+'\n');writer.stdin.flush();marker('UPDATED97')
  try:
   marker('READY97');ok(reader(game,'PREPARED'));ok(writer.poll() is None);update('commit');ok(reader(game,'COMMITTED'));update('done');ok(reader(game,'DONE'))
   writer.stdin.write('burst\n');writer.stdin.flush()
   with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    for good in pool.map(lambda _:reader(game,'ANY'),range(9)):ok(good)
   marker('UPDATED97');ok(writer.poll() is None);writer.stdin.write('exit\n');writer.stdin.flush();writer.wait(8);ok(writer.returncode==0);ok(reader(game,'DONE'));ok(private.read_bytes()==before);ok(world.read_bytes()==b'WORLD_DATA')
  finally:
   if writer.poll() is None:writer.kill();writer.wait(5)
 print('History97 actual concurrent JVM/read-only/persistence/privacy checks passed:',count,'; no Minecraft or GUI runtime.')
if __name__=='__main__':main()
