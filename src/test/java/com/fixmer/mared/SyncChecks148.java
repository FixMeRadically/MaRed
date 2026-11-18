package com.fixmer.mared;
import com.fixmer.genesis.technology.creation.*;
import com.fixmer.genesis.technology.workspace.live122.*;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.genesis.technology.editor.EditorLayout;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Core protocol, actual loopback transport, Unicode editing and cache invalidation regressions. */
public final class SyncChecks148 {
 static int count;static final UUID OWNER=UUID.randomUUID(),OTHER=UUID.randomUUID();
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
 interface Action{void run()throws Exception;}
 static void reject(Action action)throws Exception{try{action.run();throw new AssertionError("Expected rejection");}catch(IOException|IllegalArgumentException|SecurityException expected){count++;}}
 static Map<String,String> call(WorkspaceHub122 hub,UUID actor,String op,String room,Map<String,String> fields)throws Exception{var q=new TreeMap<>(fields);q.put("request",UUID.randomUUID().toString());q.put("op",op);q.put("project",room);return hub.execute(actor,true,"Player",q);}
 public static void main(String[] args)throws Exception{
  var document=new CollaborativeDocument121();var first=document.replace(OWNER,0,0,"a😀б\n");document.apply(first);
  String text=document.text();var visible=document.visibleIds();var snapshot=document.snapshot();
  check(text.equals("a😀б\n"),"Unicode cache");check(document.text()==text,"text reused");check(document.visibleIds()==visible,"immutable ID index reused");check(document.snapshot()==snapshot,"snapshot reused");
  for(int i=0;i<visible.size();i++)check(document.position(visible.get(i))==i+1,"codepoint anchor index");
  document.apply(document.replace(OWNER,1,2,"Z"));check(document.text().equals("aZб\n"),"replacement invalidates cache");check(document.position(visible.get(1))==2,"deleted anchor position includes replacement");
  check(snapshot.nodes().size()==4&&snapshot.deleted().isEmpty(),"old snapshot immutable");check(!document.apply(first),"idempotent retry");String cached=document.text();
  reject(()->document.apply(new CollaborativeDocument121.Batch(OWNER,List.of(),List.of(new CollaborativeDocument121.Id(OWNER,999)))));check(document.text()==cached,"failed operation preserves cache");
  var replica=new WorkspaceReplica122(OWNER,new CollaborativeDocument121().snapshot());replica.grant("lease",1,4096);check(replica.prepare("a"),"first burst");var pending=replica.first();
  var server=new CollaborativeDocument121();server.apply(WorkspaceCodec122.batch(OWNER,pending.batch()));check(replica.prepare("a😀б"),"type while ACK pending");
  var recovery=WorkspaceReplica122.restore(OWNER,replica.checkpoint(replica.text()));check(recovery.text().equals("a😀б")&&recovery.pendingCount130()==2,"pending work survives restart");
  replica.acknowledge(pending.request(),server.snapshot());check(replica.text().equals("a😀б")&&replica.pendingCount130()==1,"ACK retains later local typing");
  var next=replica.first();server.apply(WorkspaceCodec122.batch(OWNER,next.batch()));replica.acknowledge(next.request(),server.snapshot());check(replica.text().equals(server.text())&&!replica.pending(),"both bursts converge");
  var time=new AtomicLong();var pointers=new WorkspacePointers148(time::get);var policy=new WorkspacePolicy121(OWNER);
  pointers.update(OWNER,OWNER,"a.java","Owner",0xffffff,CollaborativeDocument121.ROOT,5,4);check(!pointers.visible(OWNER,policy,OWNER).isEmpty(),"pointer visible");check(pointers.visible(OTHER,policy,OWNER).isEmpty(),"project isolation");
  reject(()->pointers.update(OWNER,OWNER,"a.java","x|y",0xffffff,CollaborativeDocument121.ROOT,0,0));reject(()->pointers.update(OWNER,OWNER,"a.java","Owner",0xffffff,CollaborativeDocument121.ROOT,65,0));
  policy.grant(OWNER,policy.revision(),OTHER,new WorkspacePolicy121.Member(WorkspacePolicy121.Role.VIEWER,Set.of(),Set.of(WorkspacePolicy121.Panel.CODE),List.of()));check(pointers.visible(OWNER,policy,OTHER).isEmpty(),"hidden code conceals pointers");
  policy.grant(OWNER,policy.revision(),OTHER,new WorkspacePolicy121.Member(WorkspacePolicy121.Role.VIEWER,Set.of(),Set.of(),List.of()));check(!pointers.visible(OWNER,policy,OTHER).isEmpty(),"reader can see pointer");
  policy.revoke(OWNER,policy.revision(),OTHER);check(pointers.visible(OWNER,policy,OTHER).isEmpty(),"revoked reader sees no pointer");
  time.set(2_000_000_001L);check(pointers.visible(OWNER,policy,OWNER).isEmpty(),"stale pointer expires");
  Path root=Files.createTempDirectory("sync148-");
  try(var hub=new WorkspaceHub122(root)){
   var room=call(hub,OWNER,"CREATE_SCRIPT","",Map.of("source","test","data",WorkspaceProject126.pack(Map.of("a.java","hello 😀"))));String id=room.get("project");
   var opened=call(hub,OWNER,"OPEN",id,Map.of("path","a.java"));var snap=WorkspaceCodec122.document(opened.get("document"));
   var fields=new TreeMap<>(Map.of("path","a.java","known148",Long.toString(snap.revision()),"epoch148",opened.get("epoch")));
   var compact=call(hub,OWNER,"SYNC",id,fields);check("true".equals(compact.get("unchanged148"))&&!compact.containsKey("document")&&!compact.containsKey("files"),"unchanged sync is compact");
   check(call(hub,OWNER,"SYNC",id,Map.of("path","a.java")).containsKey("document"),"old clients retain full protocol");
   fields.put("epoch148",UUID.randomUUID().toString());check(call(hub,OWNER,"SYNC",id,fields).containsKey("document"),"new epoch forces snapshot");fields.put("epoch148",opened.get("epoch"));
   fields.putAll(Map.of("anchor",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"selection",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"color","123","sequence","1","session",UUID.randomUUID().toString(),"mouse148",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"dx148","2","dy148","3"));
   compact=call(hub,OWNER,"POLL",id,fields);check(compact.containsKey("pointers148")&&!compact.get("pointers148").isEmpty()&&!compact.containsKey("document"),"pointer and caret poll is compact");
   reject(()->call(hub,OTHER,"POLL",id,fields));
   fields.put("sequence","2");fields.put("mouse148","");check(call(hub,OWNER,"POLL",id,fields).get("pointers148").isEmpty(),"leaving pane hides pointer");
   var local=new WorkspaceReplica122(OWNER,snap);local.grant(opened.get("lease"),Long.parseLong(opened.get("first")),Long.parseLong(opened.get("last")));local.prepare("hello 😀 changed");var burst=local.first();
   var ack=call(hub,OWNER,"EDIT",id,Map.of("path","a.java","lease",burst.lease(),"batch",burst.batch(),"compact148","true"));
   check(ack.containsKey("document")&&!ack.containsKey("files")&&ack.get("result").equals("OK"),"edit ACK avoids unrelated catalogue");
   fields.put("sequence","3");check(call(hub,OWNER,"POLL",id,fields).containsKey("document"),"changed version returns full recovery snapshot");
   var incomplete=new TreeMap<>(fields);incomplete.remove("epoch148");reject(()->call(hub,OWNER,"SYNC",id,incomplete));
  }
  for(int w:new int[]{0,30,260,600,1000}){var frame=EditorLayout.calculate(w,400,.18,.29,.24,true,true,true,76,138);check(frame.rail().width()<=w&&frame.editor().width()>=0&&frame.toolbar().right()==w,"bounded wide rail");}
  reject(()->DesktopBridge148.line(new ByteArrayInputStream("12345\n".getBytes(StandardCharsets.UTF_8)),4));check(DesktopBridge148.line(new ByteArrayInputStream("abc\nrest".getBytes(StandardCharsets.UTF_8)),3).equals("abc"),"bounded framing");
  try(var bridge=new DesktopBridge148()){
   String[] pair=bridge.pairing().split("[:/]");int port=Integer.parseInt(pair[1]);check(pair[0].equals("127.0.0.1")&&pair[2].length()==64,"ephemeral loopback with random secret");
   try(var socket=new Socket("127.0.0.1",port)){socket.setSoTimeout(2000);socket.getOutputStream().write("MARED148 wrong\n".getBytes(StandardCharsets.UTF_8));check(socket.getInputStream().read()==-1,"bad token rejected");}
   try(var socket=new Socket("127.0.0.1",port)){socket.setSoTimeout(2000);var output=socket.getOutputStream();output.write(("MARED148 "+pair[2]+"\n{\"type\":\"save\"}\n").getBytes(StandardCharsets.UTF_8));output.flush();
    String message=null;long deadline=System.nanoTime()+2_000_000_000L;while(message==null&&System.nanoTime()<deadline){message=bridge.poll();if(message==null)Thread.sleep(5);}
    check("{\"type\":\"save\"}".equals(message),"authenticated desktop inbound");check(bridge.send("{\"type\":\"load\"}"),"desktop outbound queued");check(DesktopBridge148.line(socket.getInputStream(),100).equals("{\"type\":\"load\"}"),"desktop outbound framed");check(!bridge.send("one\ntwo"),"multiline injection rejected");
   }
  }
  System.out.println("SyncChecks148: "+count+" passed");
 }
}
