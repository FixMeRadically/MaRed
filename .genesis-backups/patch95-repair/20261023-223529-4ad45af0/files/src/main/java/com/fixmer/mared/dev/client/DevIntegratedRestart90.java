package com.fixmer.mared.dev.client;
import com.fixmer.mared.MaredLang;import com.fixmer.mared.dev.storage.DevResumeStore;
import com.fixmer.mared.technology.server.IntegratedWorld90;
import net.minecraft.client.Minecraft;import net.minecraft.server.MinecraftServer;
import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Save on the server thread; no blocking render thread, no stale queued close after timeout. */
public final class DevIntegratedRestart90 {
 public record Receipt(MinecraftServer server,DevResumeStore.Target target){}
 private static volatile Receipt pending;
 private DevIntegratedRestart90(){}
 public static void validatePending(){if(pending!=null)validate(pending);}
 public static boolean hasPreparedWorld(){return pending!=null&&pending.server()!=null&&pending.server()==Minecraft.getInstance().getSingleplayerServer();}
 public static Receipt prepare(DevResumeStore.Target target)throws Exception{
  var result=new CompletableFuture<Receipt>();var allowed=new AtomicBoolean(true);
  Minecraft.getInstance().execute(()->{if(!allowed.get())return;try{
   Minecraft mc=Minecraft.getInstance();MinecraftServer server=mc.getSingleplayerServer();
   if(!java.util.Objects.equals(target,DevSessionResume.captureCurrent()))throw new IllegalStateException("changed");
   if(target==null||!target.localWorld()){result.complete(new Receipt(null,target));return;}
   if(server==null||server.isPublished())throw new IllegalStateException("lan");
   server.execute(()->{if(!allowed.get())return;try{
    if(!server.isRunning()||server.isPublished()||!server.saveEverything(true,true,true))throw new IllegalStateException("save");
    if(!allowed.get())return;IntegratedWorld90.arm(server);result.complete(new Receipt(server,target));
   }catch(RuntimeException e){result.completeExceptionally(new IllegalStateException(MaredLang.get("mared.integrated90.save_failed")));}});
  }catch(RuntimeException e){result.completeExceptionally(new IllegalStateException(MaredLang.get("mared.integrated90.unavailable")));}});
  try{Receipt receipt=result.get(30,TimeUnit.SECONDS);pending=receipt;if(receipt.server()!=null)System.out.println("[Genesis integrated90] CHECKPOINT_CONFIRMED");return receipt;}catch(TimeoutException e){throw new IllegalStateException(MaredLang.get("mared.integrated90.save_failed"));}catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}finally{allowed.set(false);}
 }
 /** Must run on the client thread immediately before committing exit. */
 public static void validate(Receipt receipt){Minecraft mc=Minecraft.getInstance();
  if(!java.util.Objects.equals(receipt.target(),DevSessionResume.captureCurrent())||mc.getSingleplayerServer()!=receipt.server()||(receipt.server()!=null&&receipt.server().isPublished()))throw new IllegalStateException(MaredLang.get("mared.integrated90.changed"));
 }
}
