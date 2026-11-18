package com.fixmer.mared.dev.client;
import com.fixmer.mared.MaredLang;import com.fixmer.mared.dev.storage.DevResumeStore;
import com.fixmer.mared.technology.server.IntegratedWorld90;import com.fixmer.genesis.supervisor.RestartAttempt95;
import net.minecraft.client.Minecraft;import net.minecraft.server.MinecraftServer;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicLong;import java.util.function.BooleanSupplier;
/** One server-thread pre-save and a single cancellable exit permission. Final stop/save remains vanilla +90. */
public final class DevIntegratedRestart90 {
 public record Receipt(MinecraftServer server,DevResumeStore.Target target,UUID player,String epoch,RestartAttempt95 gate,BooleanSupplier current,CompletableFuture<Receipt> ready){}
 private static volatile Receipt pending;private static final AtomicLong cancellation95=new AtomicLong();
 private DevIntegratedRestart90(){}
 public static long generation95(){return cancellation95.get();}
 public static Receipt pending95(){return pending;}
 public static void validatePending(){if(pending!=null)validate(pending);}
 public static boolean hasPreparedWorld(){Receipt r=pending;return r!=null&&r.server()!=null&&r.server()==Minecraft.getInstance().getSingleplayerServer()&&r.gate().active(System.nanoTime())&&Set.of("SAVED","AUTHORIZED","EXITING").contains(r.gate().phase());}
 public static Receipt prepare(DevResumeStore.Target target)throws Exception{return prepare(target,()->true);}
 public static Receipt prepare(DevResumeStore.Target target,BooleanSupplier current)throws Exception{
  CompletableFuture<Receipt> result=new CompletableFuture<>();long generation=generation95();RestartAttempt95 gate=new RestartAttempt95(System.nanoTime(),60_000_000_000L);
  Minecraft.getInstance().execute(()->{try{
   Minecraft mc=Minecraft.getInstance();if(generation!=generation95()||!current.getAsBoolean()||!Objects.equals(target,DevSessionResume.captureCurrent()))throw new CancellationException();
   MinecraftServer server=mc.getSingleplayerServer();UUID player=mc.player==null?null:mc.player.getUUID();String epoch=server==null?"":IntegratedWorld90.epoch95(server);
   if(server!=null&&(target==null||!target.localWorld()||server.isPublished()||player==null))throw new IllegalStateException("lan_or_world_changed");
   Receipt receipt=new Receipt(server,target,player,epoch,gate,current,result);
   synchronized(DevIntegratedRestart90.class){if(generation!=generation95()||!gate.active(System.nanoTime()))throw new CancellationException();if(pending!=null&&pending.gate().active(System.nanoTime()))throw new IllegalStateException("restart_busy");pending=receipt;}
   Runnable save=()->{try{
    if(!gate.beginSave(System.nanoTime()))throw new CancellationException();
    if(server!=null&&(!server.isRunning()||server.isPublished()||!server.saveEverything(true,true,true)))throw new IllegalStateException("save_failed");
    if(!gate.saved(System.nanoTime(),()->{if(server!=null)IntegratedWorld90.arm95(server,gate.id());}))throw new CancellationException();
    result.complete(receipt);
   }catch(Exception failed){result.completeExceptionally(failed);}};
   if(server==null)save.run();else server.execute(save);
  }catch(Exception failed){result.completeExceptionally(failed);}});
  try{Receipt r=result.get(30,TimeUnit.SECONDS);if(r.server()!=null)System.out.println("[Genesis integrated95] CHECKPOINT_CONFIRMED");return r;}
  catch(Exception failed){gate.cancel();Receipt r=pending;if(r!=null&&r.gate()==gate)cancel(r);if(failed instanceof InterruptedException)Thread.currentThread().interrupt();throw failed;}
 }
 /** Client thread only. World identity, owner player and caller's apply revision are checked together. */
 public static void validate(Receipt r){Minecraft mc=Minecraft.getInstance();
  if(r==null||!r.gate().active(System.nanoTime())||!r.current().getAsBoolean()||!Objects.equals(r.target(),DevSessionResume.captureCurrent())||mc.getSingleplayerServer()!=r.server()||!Objects.equals(r.player(),mc.player==null?null:mc.player.getUUID())||(r.server()!=null&&(r.server().isPublished()||!r.epoch().equals(IntegratedWorld90.epoch95(r.server())))))throw new IllegalStateException(MaredLang.get("mared.integrated90.changed"));
 }
 public static void authorize95(Receipt r){validate(r);if(!r.gate().authorize(System.nanoTime()))throw new CancellationException();}
 /** One caller wins; neither a late render task nor a stale shutdown file can start a second close. */
 public static boolean exit95(Receipt r){validate(r);if(!r.gate().claimExit(System.nanoTime(),()->com.fixmer.genesis.supervisor.Handoff96.exitClaimed(r.gate().id())))return r.gate().exiting();DevWorldProfile.restore();Minecraft.getInstance().stop();return true;}
 public static boolean cancel(Receipt r){if(r==null)return true;if(!r.gate().cancel())return false;com.fixmer.genesis.supervisor.Handoff96.cancel(r.gate().id());synchronized(DevIntegratedRestart90.class){if(pending==r)pending=null;}if(r.server()!=null)IntegratedWorld90.disarm95(r.server(),r.gate().id());r.ready().completeExceptionally(new CancellationException());return true;}
 public static synchronized boolean cancelPending95(){Receipt r=pending;if(r!=null&&r.gate().exiting())return false;cancellation95.incrementAndGet();return cancel(r);}
}
