package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;import java.nio.file.*;import java.util.*;
/** Chooses an existing UI, never grants a queue permit or publisher trust. */
public final class ServerNavigation116 {
 private ServerNavigation116(){}
 public enum Destination {LOCAL_FOLDER,REMOTE_FOLDER,REMOTE_GENERATIONS,WAITING,DENIED,UNAVAILABLE}
 public enum Owner {FOLDER104,GENERATIONS75}
 public record LocalOwner(Path root,UUID run,Owner owner){}
 public static Destination remote(boolean channel,Map<String,String> state,String result,long now){
  if(!channel)return Destination.LOCAL_FOLDER;
  if(Set.of("DENIED","ACCESS88_DENIED","ACCESS88_REVOKED_OR_EXPIRED").contains(result))return Destination.DENIED;
  if(Set.of("NOT_MANAGED","REFUSED","INVALID").contains(result))return Destination.UNAVAILABLE;
  if(state.isEmpty())return Destination.WAITING;
  try{UUID run=UUID.fromString(state.get("run"));if(!run.toString().equals(state.get("run"))||run.equals(new UUID(0,0))||!state.get("generation").matches("0|[1-9][0-9]{0,8}"))return Destination.UNAVAILABLE;
   long age=Math.subtractExact(now,Long.parseLong(state.get("updated")));if(age>10000||age< -30000)return Destination.UNAVAILABLE;
   return switch(state.getOrDefault("inplace104","")){case "true"->Destination.REMOTE_FOLDER;case "false"->Destination.REMOTE_GENERATIONS;default->Destination.UNAVAILABLE;};
  }catch(RuntimeException bad){return Destination.UNAVAILABLE;}
 }
 public static LocalOwner owner(Path root)throws Exception{
  root=FirstLaunchFiles.safe(root);var admin=DedicatedAdmin75.open(root);if(admin.run.equals(new UUID(0,0)))throw new IOException("SERVER116_OWNER_CONTEXT");
  boolean inplace=Files.exists(root.resolve("owner104"),LinkOption.NOFOLLOW_LINKS);if(inplace&&!admin.isInPlace104())throw new IOException("SERVER116_OWNER_CONTEXT");
  return new LocalOwner(root,admin.run,inplace?Owner.FOLDER104:Owner.GENERATIONS75);
 }
 public static void requirePublisherOwner(Path root)throws Exception{if(owner(root).owner()!=Owner.GENERATIONS75)throw new IOException("SERVER116_ROLLOUT_ADAPTER_REQUIRED");}
 public static boolean publication(Owner owner){return owner==Owner.GENERATIONS75;}
}
