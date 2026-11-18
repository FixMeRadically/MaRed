package com.fixmer.mared.dev.client;
import com.fixmer.mared.dev.DevMode;import net.minecraft.client.Minecraft;
import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;import java.util.function.BooleanSupplier;
/** Apply and manual restart share the same save/identity/cancellation pipeline. No auxiliary player-NBT rollback. */
public final class DevAutoRestart {
 private static final AtomicBoolean BUSY=new AtomicBoolean();private DevAutoRestart(){}
 public static CompletableFuture<Boolean> afterSuccessfulApply(String project){return afterSuccessfulApply(project,()->true);}
 public static CompletableFuture<Boolean> afterSuccessfulApply(String project,BooleanSupplier current){
  if(!BUSY.compareAndSet(false,true))return CompletableFuture.completedFuture(false);
  CompletableFuture<Boolean> result=new CompletableFuture<>();Minecraft.getInstance().execute(()->{try{
   if(!current.getAsBoolean())throw new CancellationException();DevMode.enableForSession();
   var request=DevRestartService.isUnderSupervisor()?DevRestartService.restartAsync(current):DevActivationService.activate(current);
   request.whenComplete((ok,error)->{BUSY.set(false);if(error!=null)result.complete(false);else result.complete(Boolean.TRUE.equals(ok));});
  }catch(Exception failure){BUSY.set(false);result.complete(false);}});return result;
 }
}
