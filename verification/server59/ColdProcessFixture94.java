package com.fixmer.genesis.supervisor;
import java.nio.file.*;import java.util.*;
/** Real JVM fixture. No Minecraft/world readiness is claimed. */
public final class ColdProcessFixture94 {
 public static void main(String[] args)throws Exception {
  Path game=Path.of(args[1]);String mode=args[0];
  if(mode.equals("prepare")){RecoveryProcessFixture92.main(new String[]{"prepare",game.toString()});return;}
  if(mode.equals("record-only")){
   var c=SupervisorConfig.load(new String[]{"--config="+game.resolve("config/mared/dev/supervisor.properties")});
   try(var owner=OwnerLease94.acquire(c.configPath())){ColdOwner94.record(c,UUID.randomUUID().toString(),new ModDeployment(game).activeTrial());}
   return;
  }
  if(mode.equals("game")){
   ProfileLease92.start(game);Files.writeString(game.resolve("fixture-entered94"),"RUNNING_FIXTURE");
   if(ModJarMetadata.read(game.resolve("mods/mared-dev-example.jar")).version().equals("2")){
    while(!Files.exists(game.resolve("fixture-crash94")))Thread.sleep(20);
    System.exit(17);return;
   }
   RecoveryProcessFixture92.main(new String[]{"game",game.toString()});return;
  }
  if(mode.equals("probe")){try(var w=ProfileLease92.writer(game)){return;}catch(java.io.IOException busy){System.exit(3);}return;}
  throw new IllegalArgumentException();
 }
}
