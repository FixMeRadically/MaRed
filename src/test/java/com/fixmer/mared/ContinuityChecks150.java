package com.fixmer.mared;
import com.fixmer.genesis.technology.creation.*;
import com.fixmer.genesis.technology.workspace.live122.*;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import static com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121.*;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicLong;

/** Regression cases for false final ACKs, old connection messages and lost model confirmations. */
public final class ContinuityChecks150 {
 static int count;static final UUID OWNER=UUID.randomUUID(),OTHER=UUID.randomUUID(),ROOM=UUID.randomUUID();
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
 interface Action{void run()throws Exception;}
 static void reject(Action action)throws Exception{try{action.run();throw new AssertionError("Expected refusal");}catch(IOException|IllegalArgumentException|SecurityException expected){count++;}}
 static void waitFor(java.util.function.BooleanSupplier ready)throws Exception{long until=System.nanoTime()+3_000_000_000L;while(!ready.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(5);check(ready.getAsBoolean(),"asynchronous step completed");}
 static Socket connect(DesktopBridge148 bridge,long previous)throws Exception{String[] parts=bridge.pairing().split("[:/]");Socket socket=new Socket("127.0.0.1",Integer.parseInt(parts[1]));socket.setSoTimeout(2000);socket.getOutputStream().write(("MARED148 "+parts[2]+"\n").getBytes(StandardCharsets.UTF_8));waitFor(()->bridge.connection150()>previous);return socket;}
 static Map<String,String> request(String op,Map<String,String> fields){var q=new TreeMap<>(fields);q.put("op",op);q.put("project",ROOM.toString());q.put("request",UUID.randomUUID().toString());return q;}
 public static void main(String[] args)throws Exception{
  var empty=new CollaborativeDocument121();var replica=new WorkspaceReplica122(OWNER,empty.snapshot());replica.grant("lease",1,2048);replica.prepare("keep this");var pending=replica.first();
  reject(()->replica.acknowledge(pending.request(),empty.snapshot()));check(replica.pendingCount130()==1&&replica.text().equals("keep this"),"last batch survives false empty ACK");check(WorkspaceReplica122.restore(OWNER,replica.checkpoint(replica.text())).text().equals("keep this"),"failed final ACK remains recoverable");
  var batch=WorkspaceCodec122.batch(OWNER,pending.batch());var inserts=new ArrayList<>(batch.inserts());var original=inserts.get(0);inserts.set(0,new CollaborativeDocument121.Insert(original.id(),original.after(),'X'));var corrupt=new CollaborativeDocument121();corrupt.apply(new CollaborativeDocument121.Batch(OWNER,inserts,List.of()));reject(()->replica.acknowledge(pending.request(),corrupt.snapshot()));
  var correct=new CollaborativeDocument121();correct.apply(batch);correct.apply(correct.replace(OTHER,correct.visibleIds().size(),correct.visibleIds().size(),"!"));replica.merge(correct.snapshot());replica.acknowledge(pending.request(),empty.snapshot());check(replica.text().equals("keep this!")&&!replica.pending(),"newer confirmed snapshot can validate a late ACK");
  var equalReplica=new WorkspaceReplica122(OWNER,empty.snapshot());equalReplica.grant("lease",1,2048);equalReplica.prepare("a");var equalPending=equalReplica.first();var equalServer=new CollaborativeDocument121();equalServer.apply(WorkspaceCodec122.batch(OWNER,equalPending.batch()));equalServer.apply(equalServer.replace(OTHER,1,1,"b"));equalReplica.merge(equalServer.snapshot());var onlyOwn=WorkspaceCodec122.batch(OWNER,equalPending.batch());equalReplica.acknowledge(equalPending.request(),new CollaborativeDocument121.Snapshot(onlyOwn.inserts(),Set.of(),equalServer.revision()));check(equalReplica.text().equals("ab"),"equal-revision ACK cannot discard an already confirmed foreign edit");
  var baseline=new CollaborativeDocument121();baseline.apply(baseline.replace(OWNER,0,0,"remove"));var deletion=new WorkspaceReplica122(OWNER,baseline.snapshot());deletion.grant("lease",100,200);deletion.prepare("");var removed=deletion.first();reject(()->deletion.acknowledge(removed.request(),baseline.snapshot()));check(deletion.pending()&&deletion.text().isEmpty(),"unacknowledged deletion is retained");baseline.apply(WorkspaceCodec122.batch(OWNER,removed.batch()));deletion.acknowledge(removed.request(),baseline.snapshot());check(!deletion.pending()&&deletion.text().isEmpty(),"real deletion acknowledged");
  var context=new DesktopConnection150();check(context.observe(1),"first generation starts");var ticket=context.ticket();check(context.accepts(ticket),"current ticket accepted");check(context.observe(2)&&!context.accepts(ticket),"quick reconnect invalidates old work even without an observed disconnect");var second=context.ticket();check(!ticket.session().equals(second.session()),"each connection gets a fresh nonce");context.observe(0);check(!context.accepts(second),"disconnect invalidates callbacks");context.observe(2);check(!context.accepts(second),"generation reuse still changes nonce");
  reject(()->DesktopBridge148.line(new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28,10}),100));
  try(var bridge=new DesktopBridge148()){
   Socket first=connect(bridge,0);long old=bridge.connection150();first.getOutputStream().write("old\n".getBytes(StandardCharsets.UTF_8));final DesktopBridge148.Incoming150[] packet={null};waitFor(()->{if(packet[0]==null)packet[0]=bridge.poll150();return packet[0]!=null;});check(packet[0].generation()==old&&packet[0].json().equals("old"),"inbound message captures its generation");first.close();
   try(var next=connect(bridge,old)){long current=bridge.connection150();check(current>old,"bridge generation increases");check(!bridge.send150(old,"stale"),"stale deferred send cannot reach new app");check(bridge.poll150()==null,"old receive queue cannot leak into reconnect");check(bridge.send150(current,"fresh"),"current generation can send");check(DesktopBridge148.line(next.getInputStream(),100).equals("fresh"),"only fresh message delivered");}
  }
  try(var bridge=new DesktopBridge148()){
   int port=Integer.parseInt(bridge.pairing().split("[:/]")[1]);try(var socket=new Socket("127.0.0.1",port)){socket.setSoTimeout(2000);bridge.close();boolean stopped=false;try{stopped=socket.getInputStream().read()==-1;}catch(SocketException closed){stopped=true;}check(stopped,"closing during authentication closes the pending socket");}
  }
  Path root=Files.createTempDirectory("continuity150-");AtomicLong time=new AtomicLong();var service=new WorkspaceModels147(time::get);String path="model.bbmodel",text="{\"meta\":{},\"name\":\"Model\"}";byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
  try(var engine=new WorkspaceEngine121(root.resolve("journal"),ROOM,OWNER)){
   engine.grant(OWNER,engine.revision(),OTHER,new Member(Role.DEVELOPER,Set.of(),Set.of(),List.of()));
   var begin=service.execute("MODEL_BEGIN147",ROOM,engine,OTHER,Map.of("path",path,"expected","","hash",WorkspaceRelease121.sha(bytes),"size",Integer.toString(bytes.length)));String token=begin.get("token147");
   var put=Map.of("path",path,"token",token,"offset","0","chunk",Base64.getEncoder().encodeToString(bytes));service.execute("MODEL_PUT147",ROOM,engine,OTHER,put);service.execute("MODEL_PUT147",ROOM,engine,OTHER,put);
   var fields=Map.of("path",path,"token",token);var saved=service.execute("MODEL_COMMIT147",ROOM,engine,OTHER,fields);long revision=engine.revision();var again=service.execute("MODEL_COMMIT147",ROOM,engine,OTHER,fields);check(saved.get("hash147").equals(again.get("hash147"))&&engine.revision()==revision,"lost commit reply can be retried without another write");
   reject(()->service.execute("MODEL_COMMIT147",UUID.randomUUID(),engine,OTHER,fields));reject(()->service.execute("MODEL_COMMIT147",ROOM,engine,OWNER,fields));
   engine.grant(OWNER,engine.revision(),OTHER,new Member(Role.VIEWER,Set.of(),Set.of(),List.of()));reject(()->service.execute("MODEL_COMMIT147",ROOM,engine,OTHER,fields));check(engine.file149(OWNER,path).size149()==bytes.length,"permission refusal leaves committed bytes intact");
   engine.grant(OWNER,engine.revision(),OTHER,new Member(Role.DEVELOPER,Set.of(),Set.of(),List.of()));time.set(60_000_000_001L);reject(()->service.execute("MODEL_COMMIT147",ROOM,engine,OTHER,fields));
  }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  for(String op:List.of("MODEL_GET147","MODEL_PUT147","MODEL_COMMIT147")){
   var fields=new TreeMap<>(Map.of("path",path,"token",UUID.randomUUID().toString()));if(!op.equals("MODEL_COMMIT147"))fields.put("offset","0");if(op.equals("MODEL_PUT147"))fields.put("chunk","eA==");var retry=new WorkspaceRequest123(request(op,fields),0);check(retry.tick(3_000_000_000L)==WorkspaceRequest123.Action.SEND,"idempotent chunk/commit retries after timeout");
  }
  var open=new WorkspaceRequest123(request("MODEL_OPEN147",Map.of("path",path)),0);check(open.tick(3_000_000_000L)==WorkspaceRequest123.Action.UNKNOWN,"token-creating OPEN is not blindly retried");
  System.out.println("ContinuityChecks150: "+count+" passed");
 }
}
