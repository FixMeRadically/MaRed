package com.fixmer.mared.dev.client;
import com.fixmer.mared.dev.storage.DevResumeStore;import com.fixmer.mared.technology.server.IntegratedWorld90;import com.fixmer.genesis.supervisor.GameSessionClient;
import net.minecraft.client.Minecraft;import net.minecraft.server.MinecraftServer;import java.util.concurrent.*;
/** Scheduling and game API are explicit fixtures; production save adapter and lifecycle hooks are real. */
public class IntegratedAdapterChecks90 {
 static int n;static void ok(boolean v){n++;if(!v)throw new AssertionError("check "+n);}static void rejects(Callable<?> r){n++;try{r.call();throw new AssertionError("accepted "+n);}catch(Exception expected){}}
 static MinecraftServer world(String id){var s=new MinecraftServer();Minecraft.I.server=s;DevSessionResume.target=DevResumeStore.Target.local(id);IntegratedWorld90.started(s);return s;}
 public static void main(String[]args)throws Exception{
  var a=world("one");var target=DevSessionResume.target;var receipt=DevIntegratedRestart90.prepare(target);ok(a.saves==1);DevIntegratedRestart90.validate(receipt);ok(true);IntegratedWorld90.stopping(a);ok(a.saves==2&&GameSessionClient.phase.equals("SAVED"));IntegratedWorld90.stopped(a);ok(GameSessionClient.phase.equals("STOPPED"));
  var b=world("two");rejects(()->{DevIntegratedRestart90.validate(receipt);return null;});b.published=true;rejects(()->DevIntegratedRestart90.prepare(DevSessionResume.target));ok(b.saves==0);b.published=false;b.save=false;rejects(()->DevIntegratedRestart90.prepare(DevSessionResume.target));ok(b.saves==1);IntegratedWorld90.stopping(b);IntegratedWorld90.stopped(b);ok(GameSessionClient.phase.equals("FAILED"));
  var c=world("three");c.fail=true;rejects(()->DevIntegratedRestart90.prepare(DevSessionResume.target));IntegratedWorld90.stopping(c);IntegratedWorld90.stopped(c);ok(GameSessionClient.phase.equals("FAILED"));
  var d=world("four");d.running=false;rejects(()->DevIntegratedRestart90.prepare(DevSessionResume.target));ok(d.saves==0);
  var e=world("five");e.queue=true;ExecutorService worker=Executors.newSingleThreadExecutor();var started=new CountDownLatch(1);var work=worker.submit(()->{started.countDown();try{DevIntegratedRestart90.prepare(DevSessionResume.target);}catch(Exception ignored){}});started.await();long end=System.nanoTime()+1_000_000_000L;while(e.queued.isEmpty()&&System.nanoTime()<end)Thread.yield();ok(!e.queued.isEmpty());work.cancel(true);worker.shutdown();ok(worker.awaitTermination(2,TimeUnit.SECONDS));for(Runnable r:e.queued)r.run();ok(e.saves==0);
  GameSessionClient.supervised=false;var f=world("six");IntegratedWorld90.stopping(f);ok(f.saves==0);var r=DevIntegratedRestart90.prepare(DevSessionResume.target);IntegratedWorld90.started(f);IntegratedWorld90.stopping(f);ok(f.saves==2);IntegratedWorld90.stopped(f);ok(GameSessionClient.phase.equals("STOPPED"));
  var g=new MinecraftServer();g.dedicated=true;IntegratedWorld90.started(g);rejects(()->{IntegratedWorld90.arm(g);return null;});IntegratedWorld90.stopping(g);ok(g.saves==0);
  var stale=world("stale");var newest=world("newest");IntegratedWorld90.stopping(stale);IntegratedWorld90.stopped(stale);ok(GameSessionClient.phase.equals("ACTIVE"));
  Minecraft.I.server=null;DevSessionResume.target=null;var empty=DevIntegratedRestart90.prepare(null);DevIntegratedRestart90.validate(empty);ok(empty.server()==null);
  System.out.println("Integrated90 actual native adapter checks passed: "+n+"; Minecraft/scheduling are explicit stubs, no game or OS process.");
 }
}
