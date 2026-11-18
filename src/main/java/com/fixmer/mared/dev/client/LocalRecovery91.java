package com.fixmer.mared.dev.client;
import com.fixmer.genesis.supervisor.*;import com.fixmer.mared.Mared;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Local disk ownership only; never changes a live JAR, launches a shell or adopts a process. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class LocalRecovery91 {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"genesis-recovery91");t.setDaemon(true);return t;});
 private static volatile Object openingResource;
 private static final AtomicBoolean opening=new AtomicBoolean();private static final ThreadLocal<Boolean> pass=ThreadLocal.withInitial(()->false);
 private static final AtomicBoolean busy=new AtomicBoolean();private static volatile RecoveryView91 view=new RecoveryView91("CHECKING","","","","","",false);private static volatile boolean failed;private static long next;private static String shown="";
 private LocalRecovery91(){}
 private static ModDeployment store(){return new ModDeployment(Minecraft.getInstance().gameDirectory.toPath());}
 public static void revealReview(){var mc=Minecraft.getInstance();if(mc.getSingleplayerServer()==null){com.fixmer.mared.dev.DevTelemetry.mark("return_failed");System.out.println("[Genesis recovery91] REVIEW_REQUIRED");}}
 public static RecoveryView91 view(){return view;}public static boolean busy(){return busy.get()||opening.get();}
 public static boolean ownedTrial(){String t=System.getenv("GENESIS_MOD_TRIAL");return DevRestartService.isUnderSupervisor()&&t!=null&&t.equals(view.transaction())&&view.needsChoice();}
 public static boolean blocksAutoOpen(){return failed||view.blocked()&&!ownedTrial();}
 public static void refresh(){if(!busy.compareAndSet(false,true))return;IO.execute(()->{try{view=store().recovery91();failed=false;}catch(Exception e){failed=true;view=new RecoveryView91("ERROR","","","","","",false);}finally{busy.set(false);}});}
 public static void decide(String revision,String action){if(opening.get()||!busy.compareAndSet(false,true))return;IO.execute(()->{try{store().decide91(revision,action);DevQuickStart.suppressForSession91();view=store().recovery91();failed=false;}catch(Exception e){failed=true;view=new RecoveryView91("ERROR","","","","","",false);}finally{busy.set(false);}});}
 /** Worker-side check before preparing a controller; a pending restore may proceed only from the menu. */
 public static void activationCheck(com.fixmer.mared.dev.storage.DevResumeStore.Target target)throws java.io.IOException{
  if(DevRestartService.isUnderSupervisor()&&!"91".equals(System.getenv("GENESIS_RECOVERY_PROTOCOL91")))throw new java.io.IOException(com.fixmer.mared.MaredLang.get("mared.recovery91.owner_old"));
  var v=store().recovery91();if(v.needsChoice()||v.phase().equals("RESTORE_REQUESTED")&&target!=null)throw new java.io.IOException(com.fixmer.mared.MaredLang.get("mared.recovery91.required"));
 }
 /** Captured invocation is resumed once, on the same screen and only after durable review. */
 public static boolean interceptWorldOpen(Runnable continuation){return interceptWorldOpen(continuation,()->{},new Object());}
 public static boolean interceptWorldOpen(Runnable continuation,Runnable cancelled,Object resource){
  if(pass.get())return false;
  if(!opening.compareAndSet(false,true)){if(resource!=openingResource)cancelled.run();return true;}openingResource=resource;
  var mc=Minecraft.getInstance();var origin=mc.screen;var cleaned=new AtomicBoolean();Runnable cleanup=()->{if(cleaned.compareAndSet(false,true))cancelled.run();};
  IO.execute(()->{try{var d=store();var v=d.recovery91();String trial=System.getenv("GENESIS_MOD_TRIAL");boolean owned=DevRestartService.isUnderSupervisor()&&trial!=null&&trial.equals(v.transaction())&&v.needsChoice();
   if(v.blocked()&&!owned)throw new java.io.IOException("RECOVERY91_REVIEW_REQUIRED");if(trial!=null)d.protectTrial91(trial);view=d.recovery91();failed=false;
   mc.execute(()->{try{if(mc.screen!=origin||mc.getSingleplayerServer()!=null){cleanup.run();return;}pass.set(true);continuation.run();}catch(RuntimeException e){failed=true;view=new RecoveryView91("ERROR","","","","","",false);if(mc.getSingleplayerServer()==null){cleanup.run();revealReview();mc.setScreen(new LocalRecoveryScreen91(new TitleScreen()));}}finally{pass.remove();openingResource=null;opening.set(false);}});
  }catch(Exception e){mc.execute(()->{try{cleanup.run();failed=true;view=new RecoveryView91("ERROR","","","","","",false);if(mc.screen==origin&&mc.getSingleplayerServer()==null){revealReview();mc.setScreen(new LocalRecoveryScreen91(new TitleScreen()));}}finally{openingResource=null;opening.set(false);}});}});return true;
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();if(!(mc.screen instanceof TitleScreen)&&!(mc.screen instanceof LocalRecoveryScreen91))return;
  if(System.nanoTime()>=next){next=System.nanoTime()+5_000_000_000L;refresh();}
  if(mc.screen instanceof TitleScreen&&mc.getOverlay()==null&&!busy()&&view.blocked()&&!ownedTrial()&&!view.phase().equals("CHECKING")&&!shown.equals(view.phase()+view.revision())){shown=view.phase()+view.revision();revealReview();mc.setScreen(new LocalRecoveryScreen91(mc.screen));}
 }
}
