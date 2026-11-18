package com.fixmer.mared.technology.workspace.live122;
import com.fixmer.genesis.technology.workspace.live122.*;
import com.fixmer.genesis.technology.workspace.collaboration121.WorkspaceRelease121;
import com.fixmer.mared.technology.blockbench.WorkspaceModelClient147;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;

/** Actual codec and hub, with lost replies AFTER the server performed the operation. */
public final class RecoveryWireChecks150 {
 static int count;static final UUID ACTOR=UUID.randomUUID();
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
 static class Host implements WorkspaceClient122.Host135{
  final WorkspaceHub122 hub;final Object connection=new Object();final Queue<Runnable> tasks=new ConcurrentLinkedQueue<>();final Set<String> drop=new HashSet<>();final Map<String,Map<String,String>> first=new HashMap<>();final Map<String,Integer> attempts=new HashMap<>();WorkspaceClient122 client;long now;
  Host(WorkspaceHub122 hub){this.hub=hub;}public Object connection(){return connection;}public boolean channel(Object value){return true;}public long now(){return now;}public void execute(Runnable action){tasks.add(action);}public void failed(Exception error){throw new AssertionError(error);}
  public void send(byte[] bytes){try{
   var q=WorkspaceCodec122.decode(bytes,WorkspaceCodec122.REQUEST);String op=q.get("op");attempts.merge(op,1,Integer::sum);Map<String,String> answer;
   try{answer=hub.execute(ACTOR,true,"Player",q);}catch(Exception error){answer=Map.of("result",String.valueOf(error.getMessage()));}
   if(drop.remove(op)){first.put(op,q);now+=3_000_000_000L;return;}
   if(first.containsKey(op)){check(first.remove(op).equals(q),"retry preserves operation, request ID, token and bytes");}
   var response=new TreeMap<>(answer);response.put("request",q.get("request"));client.receive135(WorkspaceCodec122.encode(response,WorkspaceCodec122.RESPONSE));
  }catch(IOException error){throw new AssertionError(error);}}
  <T>T await(CompletableFuture<T> future)throws Exception{long until=System.nanoTime()+8_000_000_000L;while(!future.isDone()&&System.nanoTime()<until){for(Runnable task=tasks.poll();task!=null;task=tasks.poll())task.run();client.tick();Thread.sleep(1);}return future.get(1,TimeUnit.SECONDS);}
 }
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("recovery-wire150-");
  try(var hub=new WorkspaceHub122(root)){
   var room=hub.execute(ACTOR,true,"Player",WorkspaceClient122.request("CREATE","",Map.of("text","Recovery")));String project=room.get("project");Host host=new Host(hub);host.client=new WorkspaceClient122(host);
   try(var channel=new WorkspaceModelClient147(host.client,room,data->{})){
    String text="{\"meta\":{},\"elements\":\""+"geometry ".repeat(10000)+"\"}";
    host.drop.addAll(Set.of("MODEL_PUT147","MODEL_COMMIT147"));var saved=host.await(channel.save("model.bbmodel","",text));
    check(saved.hash().equals(WorkspaceRelease121.sha(text.getBytes(StandardCharsets.UTF_8))),"lost PUT and COMMIT replies do not lose model");check(host.attempts.get("MODEL_PUT147")==2&&host.attempts.get("MODEL_COMMIT147")==2,"both lost replies cause exactly one retry");
    var view=hub.execute(ACTOR,true,"Player",WorkspaceClient122.request("VIEW",project,Map.of()));check(view.get("revision").equals("2"),"commit retry does not create a duplicate history revision");
    host.drop.add("MODEL_GET147");var loaded=host.await(channel.open("model.bbmodel"));check(loaded.text().equals(text),"download resumes after a lost chunk reply");check(host.attempts.get("MODEL_GET147")==2,"lost GET is retried with the same pinned snapshot");
    check(host.drop.isEmpty()&&host.first.isEmpty(),"all intentionally lost replies recovered");
   }
  }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  System.out.println("RecoveryWireChecks150: "+count+" passed");
 }
}
