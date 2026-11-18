package com.fixmer.mared.dev.client;
import com.fixmer.genesis.supervisor.FailureReport101;
import com.fixmer.mared.Mared;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.client.event.ClientTickEvent;import net.neoforged.fml.loading.FMLPaths;
import java.util.concurrent.*;
/** Read-only async notification. A diagnostic record never authorizes restoration or opening a world. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class ClientFailure101 {
 private static final ExecutorService IO=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"genesis-failure101-report");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private static long next;private static boolean reading;private static String shown="";
 private ClientFailure101(){}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){
  var mc=Minecraft.getInstance();if(mc.level!=null||mc.getSingleplayerServer()!=null||mc.getOverlay()!=null||!(mc.screen instanceof TitleScreen||mc.screen instanceof LocalRecoveryScreen91)||reading||System.nanoTime()<next)return;
  next=System.nanoTime()+1_000_000_000L;reading=true;var origin=mc.screen;
  try{CompletableFuture.supplyAsync(()->{try{return FailureReport101.read(FMLPaths.GAMEDIR.get());}catch(Exception unavailable){return null;}},IO).whenComplete((report,error)->mc.execute(()->{
   reading=false;if(report==null||report.acknowledged()||shown.equals(report.id())||mc.screen!=origin||mc.level!=null||mc.getSingleplayerServer()!=null||mc.getOverlay()!=null)return;
   shown=report.id();mc.setScreen(new FailureScreen101(origin,report));
  }));}catch(RejectedExecutionException busy){reading=false;}
 }
 static void acknowledge(FailureReport101.Snapshot report){try{IO.execute(()->{try{FailureReport101.acknowledge(FMLPaths.GAMEDIR.get(),report.id());}catch(Exception ignored){}});}catch(RejectedExecutionException ignored){}}
}
