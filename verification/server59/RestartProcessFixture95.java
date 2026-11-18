package com.fixmer.genesis.supervisor;
import java.nio.file.*;import java.util.*;
/** Actual child JVM with explicit lifecycle fixtures, not Minecraft/final-world-save proof. */
public final class RestartProcessFixture95 {
 public static void main(String[]args)throws Exception{
  Path game=Path.of(args[0]);ProfileLease92.start(game);GameSessionClient.start();String epoch=UUID.randomUUID().toString();GameSessionClient.integrated90(epoch,"ACTIVE");GameSessionClient.milestone("world_ready");
  String launch=System.getenv("GENESIS_LAUNCH_ID");Files.writeString(game.resolve("fixture-current95"),launch+"\n"+epoch);Files.writeString(game.resolve("fixture-starts95"),"START\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
  long deadline=System.nanoTime()+90_000_000_000L;
  while(System.nanoTime()<deadline){
   Path stop=Path.of(System.getenv("GENESIS_SHUTDOWN_FILE"));
   if(Files.isRegularFile(stop)){
    String signal=Files.readString(stop);
    if(signal.trim().equals(launch))break;
    if(signal.startsWith(launch+"\nrestart95=")&&Files.exists(game.resolve("fixture-allow95")))break;
   }
   Thread.sleep(15);
  }
  if(!Files.exists(game.resolve("fixture-fail-save95"))){GameSessionClient.integrated90(epoch,"SAVED");GameSessionClient.integrated90(epoch,"STOPPED");}else GameSessionClient.integrated90(epoch,"FAILED");
  GameSessionClient.normalClosing();
 }
}
