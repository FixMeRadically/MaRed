package com.fixmer.mared.technology.workspace.live122;
import com.fixmer.genesis.technology.workspace.live122.*;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.mared.technology.blockbench.WorkspaceModelClient147;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;

/** Wire-level isolation of transient pointer packets and reliable model/code requests. */
public final class RealtimeChecks149 {
 static int count;static final UUID ACTOR=UUID.randomUUID(),PROJECT=UUID.randomUUID();
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;}
 static class Host implements WorkspaceClient122.Host135{
  final Object connection;long now;final List<Map<String,String>> sent=new ArrayList<>();WorkspaceClient122 client;
  Host(Object connection){this.connection=connection;}public Object connection(){return connection;}public boolean channel(Object c){return true;}public long now(){return now;}public void execute(Runnable run){run.run();}public void failed(Exception error){throw new AssertionError(error);}
  public void send(byte[] bytes){try{sent.add(WorkspaceCodec122.decode(bytes,WorkspaceCodec122.REQUEST));}catch(IOException error){throw new AssertionError(error);}}
  void answer(int index,String result){try{var q=sent.get(index);client.receive135(WorkspaceCodec122.encode(Map.of("request",q.get("request"),"result",result,"actor",ACTOR.toString(),"store","store","project",PROJECT.toString()),WorkspaceCodec122.RESPONSE));}catch(IOException error){throw new AssertionError(error);}}
 }
 static Map<String,String> pointer(){return WorkspaceClient122.request("POINTER149",PROJECT.toString(),Map.of("path","a.java","mouse148","","dx148","0","dy148","0","sequence","1","session",UUID.randomUUID().toString(),"color","123"));}
 static class ModelHost extends Host{
  final WorkspaceHub122 hub;final Thread owner=Thread.currentThread();final Queue<Runnable> tasks=new ConcurrentLinkedQueue<>();boolean hold;
  ModelHost(WorkspaceHub122 hub){super(new Object());this.hub=hub;}
  @Override public void execute(Runnable task){tasks.add(task);}
  @Override public void send(byte[] bytes){check(Thread.currentThread()==owner,"model packet emitted on host thread");super.send(bytes);if(hold)return;var q=sent.get(sent.size()-1);try{
   Map<String,String> response;try{response=hub.execute(ACTOR,true,"Player",q);}catch(Exception failure){response=Map.of("result",failure.getMessage());}
   var out=new TreeMap<>(response);out.put("request",q.get("request"));client.receive135(WorkspaceCodec122.encode(out,WorkspaceCodec122.RESPONSE));
  }catch(IOException error){throw new AssertionError(error);}}
  <T>T await(CompletableFuture<T> future)throws Exception{long deadline=System.nanoTime()+5_000_000_000L;while(!future.isDone()&&System.nanoTime()<deadline){for(Runnable r=tasks.poll();r!=null;r=tasks.poll())r.run();WorkspaceModelClient147.tickAll147();Thread.sleep(1);}return future.get(1,TimeUnit.SECONDS);}
 }
 public static void main(String[] args)throws Exception{
  Object connection=new Object();Host reliable=new Host(connection),pointer=new Host(connection),other=new Host(connection);
  reliable.client=new WorkspaceClient122(reliable);pointer.client=new WorkspaceClient122(pointer,true);other.client=new WorkspaceClient122(other);
  reliable.client.attach();pointer.client.attach();other.client.attach();
  try{
   reliable.client.send(WorkspaceClient122.request("VIEW",PROJECT.toString(),Map.of()),d->{});check(reliable.sent.size()==1,"reliable request in flight");
   pointer.client.send(pointer(),d->{});check(pointer.sent.size()==1,"pointer bypasses reliable in-flight request");
   other.client.send(WorkspaceClient122.request("VIEW",PROJECT.toString(),Map.of()),d->{});check(other.sent.isEmpty(),"reliable transports still serialize");
   pointer.answer(0,"BUSY");check(!pointer.client.busy()&&reliable.client.busy(),"transient BUSY drops old point without affecting reliable request");
   pointer.client.send(pointer(),d->{});pointer.now=250_000_001L;pointer.client.tick();check(!pointer.client.busy()&&pointer.sent.size()==2,"stale point is dropped without retries");
   pointer.answer(1,"OK");check(!pointer.client.busy()&&reliable.client.busy(),"late point ACK cannot finish a code request");
   int before=pointer.sent.size();pointer.client.send(WorkspaceClient122.request("VIEW",PROJECT.toString(),Map.of()),d->check(d.get("result").equals("INVALID"),"ephemeral channel rejects durable commands"));check(pointer.sent.size()==before,"no durable command bypass");
   reliable.answer(0,"OK");other.client.tick();check(other.sent.size()==1,"durable fairness resumes after ACK");
  }finally{reliable.client.detach();pointer.client.detach();other.client.detach();}
  Host measured=new Host(new Object());measured.client=new WorkspaceClient122(measured);measured.client.attach();
  try{var document=new CollaborativeDocument121();String batch=WorkspaceCodec122.batch(document.replace(ACTOR,0,0,"a"));measured.client.send(WorkspaceClient122.request("EDIT",PROJECT.toString(),Map.of("path","a.java","batch",batch,"lease","token")),d->{});measured.now=125_000_000L;measured.answer(0,"OK");check(measured.client.editConfirmationMillis149()==125,"edit acknowledgment measurement uses monotonic elapsed time");}finally{measured.client.detach();}
  Path root=Files.createTempDirectory("realtime149-");
  try(var hub=new WorkspaceHub122(root)){
   var room=hub.execute(ACTOR,true,"Player",WorkspaceClient122.request("CREATE","",Map.of("text","Models")));ModelHost host=new ModelHost(hub);host.client=new WorkspaceClient122(host);
   var models=new WorkspaceModelClient147(host.client,room,d->check(Thread.currentThread()==host.owner,"metadata delivered on host thread"));
   try{
    String text="{\"meta\":{},\"elements\":\""+"geometry ".repeat(15000)+"\"}";
    var saved=host.await(models.save("model.bbmodel","",text));check(saved.hash().equals(WorkspaceRelease121.sha(text.getBytes(StandardCharsets.UTF_8))),"compressed upload preserves source hash");
    int upload=host.sent.size();check(upload<=4,"compressible model uploads in at most four packets");
    var loaded=host.await(models.open("model.bbmodel"));check(loaded.text().equals(text),"compressed round trip lossless");check(host.sent.size()-upload<=3,"compressed download uses few packets");
    host.hold=true;var waiting=models.watch("model.bbmodel");check(!waiting.isDone(),"request really waits for server");models.close();check(waiting.isCompletedExceptionally(),"close completes outstanding requests");
    check(models.save("x.bbmodel","","{\"meta\":{}}").isCompletedExceptionally(),"closed model channel cannot queue another transfer");
    System.out.println("Compressed model fixture: "+text.getBytes(StandardCharsets.UTF_8).length+" bytes, upload "+upload+" packets");
   }finally{models.close();}
  }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  System.out.println("RealtimeChecks149: "+count+" passed");
 }
}
