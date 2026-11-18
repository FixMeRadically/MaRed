package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.io.*;import java.nio.channels.*;import java.nio.file.*;import java.util.*;
/** Private local queue, not a remote filesystem API. Network caller permissions belong to ServerPlayer handler. */
public final class DedicatedAdmin75 {
 public final Path root;public final UUID run;private final byte[] key;
 private DedicatedAdmin75(Path root,UUID run,byte[] key){this.root=root;this.run=run;this.key=key;}
 public static DedicatedAdmin75 open(Path root)throws Exception{
  root=FirstLaunchFiles.safe(root);byte[] key=FirstLaunchFiles.read(root.resolve("owner74.key"),32);if(key.length!=32)throw new IOException("DEDICATED75_KEY");var marker=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("owner75"),16384));if(!marker.keySet().equals(Set.of("schema","run"))||!marker.get("schema").equals("75"))throw new IOException("DEDICATED75_MARKER");return new DedicatedAdmin75(root,UUID.fromString(marker.get("run")),key);
 }
 public static boolean mayAdminister(boolean permission4,boolean online){return permission4&&online;}
 public Map<String,String> view()throws Exception{
  var d=LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("view75"),32768));if(!d.keySet().equals(Set.of("schema","run","generation","phase","updated","ready","control","delta","history","failureCode","failureStage","reason","busy"))||!d.get("schema").equals("75")||!d.get("run").equals(run.toString())||!d.get("generation").matches("0|[1-9][0-9]?")||Integer.parseInt(d.get("generation"))>32||!d.get("updated").matches("[1-9][0-9]{11,15}")||!Set.of("true","false").contains(d.get("ready"))||!Set.of("true","false").contains(d.get("busy")))throw new IOException("DEDICATED75_VIEW");return d;
 }
 public void submit(UUID request,String action,String label)throws Exception{
  if(request.equals(new UUID(0,0))||!Set.of("STAGE","OFFER","STATUS","COMMIT","CANCEL","CLOSE","LOCAL_TEST_ON","LOCAL_TEST_OFF","SHUTDOWN").contains(action)||!label.matches("[A-Za-z0-9_-]{1,40}"))throw new IOException("DEDICATED75_ACTION");
  var state=view();long age=System.currentTimeMillis()-Long.parseLong(state.get("updated"));if(age< -30000||(age>10000&&!action.equals("SHUTDOWN"))||(state.get("busy").equals("true")&&!action.equals("SHUTDOWN"))||Set.of("FAILED","STOPPED_SAVED","STOP_NOT_CONFIRMED").contains(state.get("phase")))throw new IOException("DEDICATED75_OWNER_UNAVAILABLE");
  var d=Map.of("schema","75","run",run.toString(),"generation",state.get("generation"),"request",request.toString(),"action",action,"label",label);Path lock=root.resolve("submit75.lock");
  try(var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){FirstLaunchFiles.privateMode(lock);try(var held=channel.tryLock(0,1,false)){if(held==null)throw new IOException("DEDICATED75_BUSY");Path p=root.resolve("request74");if(Files.exists(p)){
   var previous=LocalApply67.verify(key,FirstLaunchFiles.read(p,16384));if(previous.get("request").equals(request.toString())){if(!previous.equals(d))throw new IOException("DEDICATED75_NONCE_CONFLICT");return;}
   var ack=Files.exists(root.resolve("request74.ack"))?LocalApply67.verify(key,FirstLaunchFiles.read(root.resolve("request74.ack"),16384)):Map.<String,String>of();if(!requestOf(previous).equals(ack.get("request"))||!run.toString().equals(ack.get("run")))throw new IOException("DEDICATED75_REQUEST_PENDING");
  }FirstLaunchFiles.write(p,LocalApply67.sign(key,d),true);}}
 }
 private String requestOf(Map<String,String> previous)throws IOException{if(!run.toString().equals(previous.get("run")))throw new IOException("DEDICATED75_BINDING");return previous.get("request");}
}
