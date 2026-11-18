package com.fixmer.mared.technology.workspace.live122;
import com.fixmer.genesis.technology.workspace.live122.*;import com.fixmer.genesis.technology.server.remote.RemoteTest152;import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.*;
/** Production client + codec + upload service, deliberately dropping replies after server-side writes. */
public final class RemoteWireChecks152 {
 static int count;static void ok(boolean value,String reason){if(!value)throw new AssertionError(reason);count++;}
 static final UUID ACTOR=UUID.randomUUID();
 static final class Host implements WorkspaceClient122.Host135 {
  final Object connection;final RemoteTest152 service;WorkspaceClient122 client;long now;boolean hold;String drop="";final Map<String,Integer> attempts=new HashMap<>();Map<String,String> previous;
  Host(Object connection,RemoteTest152 service){this.connection=connection;this.service=service;}
  public Object connection(){return connection;}public boolean channel(Object value){return true;}public void execute(Runnable task){task.run();}public long now(){return now;}public void failed(Exception error){throw new AssertionError(error);}
  public void send(byte[] bytes){try{var q=WorkspaceCodec122.decode(bytes,WorkspaceCodec122.REQUEST);WorkspaceHub122.request(q);String op=q.get("op");attempts.merge(op,1,Integer::sum);if(hold)return;
   Map<String,String> result=service.execute(ACTOR,op,q);if(op.equals(drop)){drop="";previous=q;now+=3_000_000_000L;return;}
   if(previous!=null){ok(previous.equals(q),"retry preserves request, token and exact bytes");previous=null;}
   var answer=new TreeMap<>(result);answer.put("result","OK");answer.put("request",q.get("request"));client.receive135(WorkspaceCodec122.encode(answer,WorkspaceCodec122.RESPONSE));
  }catch(Exception error){throw new AssertionError(error);}}
  Map<String,String> call(String op,Map<String,String> fields){AtomicReference<Map<String,String>> result=new AtomicReference<>();client.send(WorkspaceClient122.request(op,"",fields),result::set);for(int i=0;i<8&&result.get()==null;i++)client.tick();if(result.get()==null)throw new AssertionError("missing terminal response");return result.get();}
 }
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("remote-wire152-");AtomicInteger queued=new AtomicInteger();
  var backend=new RemoteTest152.Backend(){public void authorize(UUID actor){}public Map<String,String> review(UUID actor,Path folder,String hash,long size){return Map.of("hash152",hash);}public String apply(UUID actor,Path folder){queued.incrementAndGet();return UUID.randomUUID().toString();}};
  var service=new RemoteTest152(root,backend,System::nanoTime);Object connection=new Object();Host host=new Host(connection,service);host.client=new WorkspaceClient122(host);var busy=new RealtimeChecks149.Host(connection);busy.client=new WorkspaceClient122(busy);
  host.client.attach();busy.client.attach();
  try{
   busy.client.send(WorkspaceClient122.request("VIEW",RealtimeChecks149.PROJECT.toString(),Map.of()),answer->{});ok(!host.client.readyForSend152(),"reconnect is not armed while another reliable channel blocks apply");busy.answer(0,"OK");ok(host.client.readyForSend152(),"apply can arm only when it can transmit immediately");
   byte[] data="model of compiled JAR bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);String token=host.call("TEST_BEGIN152",Map.of("size",Integer.toString(data.length),"hash",FirstLaunchFiles.sha(data))).get("token152");
   host.drop="TEST_PUT152";var uploaded=host.call("TEST_PUT152",Map.of("token",token,"offset","0","chunk",Base64.getEncoder().encodeToString(data)));ok(uploaded.get("offset152").equals(Integer.toString(data.length))&&host.attempts.get("TEST_PUT152")==2,"lost chunk reply recovered exactly once");
   host.call("TEST_REVIEW152",Map.of("token",token));host.drop="TEST_APPLY152";var applied=host.call("TEST_APPLY152",Map.of("token",token));ok(applied.get("queued152").equals("true")&&queued.get()==1&&host.attempts.get("TEST_APPLY152")==2,"lost apply reply retries without a second deployment");
   host.drop="TEST_BEGIN152";var unknown=host.call("TEST_BEGIN152",Map.of("size",Integer.toString(data.length),"hash",FirstLaunchFiles.sha(data)));ok(unknown.get("result").equals("UNKNOWN")&&host.attempts.get("TEST_BEGIN152")==2,"unknown BEGIN is not blindly repeated");
  }finally{host.client.detach();busy.client.detach();try(var files=Files.walk(root)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}}
  System.out.println("RemoteWireChecks152: "+count+" passed");
 }
}
