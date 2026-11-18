package com.fixmer.genesis.supervisor;
import java.nio.file.*;import java.util.*;import com.fixmer.mared.dev.storage.DevResumeStore;
/** Real JVM/OS lease fixture. Lifecycle signals are explicit and do not load Minecraft. */
public final class HandoffProcessFixture96 {
 public static void main(String[]args)throws Exception{
  String mode=args[0];Path game=Path.of(args[1]);
  if(mode.equals("replacement")){
   ProfileLease92.start(game);GameSessionClient.start();GameSessionClient.milestone("title_ready");Files.writeString(game.resolve("replacement96-entered"),"fixture, not Minecraft");
   long end=System.nanoTime()+90_000_000_000L;while(System.nanoTime()<end&&!Files.exists(Path.of(System.getenv("GENESIS_SHUTDOWN_FILE"))))Thread.sleep(20);GameSessionClient.normalClosing();return;
  }
  Path configPath=Path.of(args[2]);SupervisorConfig config=SupervisorConfig.load(new String[]{"--config="+configPath});String epoch=mode.equals("menu")?"":UUID.randomUUID().toString(),request=UUID.randomUUID().toString();String resume=DevResumeStore.prepare(game.resolve("config/mared/dev/resume.properties"),epoch.isEmpty()?null:DevResumeStore.Target.local("world"),ProcessHandle.current().pid(),System.currentTimeMillis());
  String id=Handoff96.prepare(configPath,game,config.instanceId(),config.token(),request,epoch,resume);
  Properties ready=new Properties();ready.setProperty("pid",Long.toString(ProcessHandle.current().pid()));ready.setProperty("id",id);ready.setProperty("request",request);try(var out=Files.newBufferedWriter(game.resolve("source96-ready.properties"))){ready.store(out,"fixture identity only");}
  Path action=game.resolve("source96-action");long deadline=System.nanoTime()+120_000_000_000L;while(!Files.exists(action)&&System.nanoTime()<deadline)Thread.sleep(15);if(!Files.exists(action))return;
  if(mode.equals("cancel")){Handoff96.cancel(request);Files.writeString(game.resolve("source96-cancelled"),"cancelled");while(!Files.exists(game.resolve("source96-end")))Thread.sleep(20);return;}
  Handoff96.exitClaimed(request);
  if(mode.equals("crash"))Runtime.getRuntime().halt(17);
  if(mode.equals("wrong_world"))Handoff96.world(UUID.randomUUID().toString(),"ACTIVE");
  if(!epoch.isEmpty()){
   Handoff96.world(epoch,mode.equals("save_failed")?"FAILED":"SAVED");
   if(!mode.equals("missing_stop"))Handoff96.world(epoch,"STOPPED");
  }
  if(mode.equals("resume_changed"))DevResumeStore.prepare(game.resolve("config/mared/dev/resume.properties"),DevResumeStore.Target.local("other-world"),ProcessHandle.current().pid(),System.currentTimeMillis());
  if(!mode.equals("missing_normal"))GameSessionClient.normalClosing();
  if(mode.equals("hook_hold"))Runtime.getRuntime().addShutdownHook(new Thread(()->{try{Files.writeString(game.resolve("source96-hook-held"),"hook fixture");while(!Files.exists(game.resolve("source96-release-hook")))Thread.sleep(20);}catch(Exception e){throw new RuntimeException(e);}}));
 }
}
