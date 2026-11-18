package com.fixmer.mared;
import com.fixmer.genesis.technology.workspace.live122.*;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.genesis.technology.editor.PointerGlyph149;
import java.io.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.concurrent.atomic.AtomicLong;

/** Protocol and bounded-work regressions; timing thresholds are not game FPS claims. */
public final class PerformanceChecks149 {
 static int count;static final UUID OWNER=UUID.randomUUID(),OTHER=UUID.randomUUID(),HIDDEN=UUID.randomUUID();
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
 interface Action{void run()throws Exception;}
 static void reject(Action action)throws Exception{try{action.run();throw new AssertionError("Expected rejection");}catch(IOException|IllegalArgumentException|SecurityException|IllegalStateException expected){count++;}}
 static Map<String,String> call(WorkspaceHub122 hub,UUID actor,String op,String project,Map<String,String> fields)throws Exception{var q=new TreeMap<>(fields);q.put("op",op);q.put("project",project);q.put("request",UUID.randomUUID().toString());return hub.execute(actor,true,"Player",q);}
 public static void main(String[] args)throws Exception{
  byte[] raw=("{\"meta\":{},\"elements\":\""+"model geometry ".repeat(10000)+"\"}").getBytes(StandardCharsets.UTF_8);var packed=ModelTransfer149.pack(raw);
  check(packed.encoding().equals("zlib")&&packed.bytes().length<raw.length/10,"repetitive model is substantially compressed");check(Arrays.equals(raw,ModelTransfer149.unpack(packed.bytes(),packed.encoding(),raw.length)),"lossless model");
  reject(()->ModelTransfer149.unpack(packed.bytes(),packed.encoding(),raw.length-1));reject(()->ModelTransfer149.unpack(Arrays.copyOf(packed.bytes(),packed.bytes().length-1),"zlib",raw.length));
  reject(()->ModelTransfer149.unpack(Arrays.copyOf(packed.bytes(),packed.bytes().length+1),"zlib",raw.length));reject(()->ModelTransfer149.unpack(packed.bytes(),"unknown",raw.length));reject(()->ModelTransfer149.pack(new byte[WorkspaceModels147.MAX+1]));
  byte[] random=new byte[100000];new Random(1).nextBytes(random);check(ModelTransfer149.pack(random).encoding().equals("raw"),"incompressible data stays raw");check(ModelTransfer149.pack(new byte[10]).encoding().equals("raw"),"small models avoid compression work");
  var baseDoc=new CollaborativeDocument121();baseDoc.apply(baseDoc.replace(OWNER,0,0,"o".repeat(512)));var replica=new WorkspaceReplica122(OWNER,baseDoc.snapshot());replica.grant("t".repeat(4096),1000,5095);replica.prepare("n".repeat(512));
  check(replica.pendingCount130()==2,"large replacements use two 256-character batches instead of four");var burst=replica.first();
  byte[] packet=WorkspaceCodec122.encode(Map.of("op","EDIT","project",UUID.randomUUID().toString(),"request",burst.request().toString(),"path","a.java","batch",burst.batch(),"lease",burst.lease()),WorkspaceCodec122.REQUEST);check(packet.length<=WorkspaceCodec122.REQUEST&&WorkspaceCodec122.batch(OWNER,burst.batch()).inserts().size()==256,"max batch and max lease fit request budget");
  var time=new AtomicLong();var view=new PointerView149(time::get);String anchor=WorkspaceCodec122.id(CollaborativeDocument121.ROOT);
  String a=OWNER+"|a.java|Owner|123|"+anchor+"|2|3|148",b=OTHER+"|a.java|Other|456|"+anchor+"|4|5|148";
  view.snapshot(a);view.delta(b);check(view.visible().size()==2,"delta preserves other participants");view.delta(OWNER.toString());check(view.visible().size()==1,"explicit hide removes one pointer");
  view.delta(a);var pos=view.position(OWNER.toString(),"pane",0,0);check(pos[0]==0,"initial pointer snaps");time.set(25_000_000L);pos=view.position(OWNER.toString(),"pane",20,20);check(pos[0]>0&&pos[0]<20,"movement smoothed without extrapolation");
  check(view.position(OWNER.toString(),"other pane",60,60)[0]==60,"layout switch snaps");check(view.position(OWNER.toString(),"other pane",300,300)[0]==300,"large jump snaps");
  time.set(50_000_000L);view.position(OWNER.toString(),0,"left",10,10);view.position(OWNER.toString(),1,"right",50,50);
  time.set(75_000_000L);int left=view.position(OWNER.toString(),0,"left",30,10)[0],right=view.position(OWNER.toString(),1,"right",70,50)[0];
  check(left>10&&left<30&&right>50&&right<70,"split views independently interpolate the same actor");
  view.delta("bad packet");check(view.visible().size()==2,"malformed delta harmless");time.set(2_100_000_000L);check(view.visible().isEmpty(),"per-participant expiry");
  check(PointerGlyph149.RUNS.stream().map(PointerGlyph149.Run::ink).collect(java.util.stream.Collectors.toSet()).equals(Set.of(1,2,3)),"outlined arrow has edge, personal colour and fill");check(PointerGlyph149.RUNS.stream().allMatch(run->run.x()>=0&&run.y()>=0&&run.x()+run.length()<=26&&run.y()<26),"arrow geometry bounded");
  time.set(0);Path root=Files.createTempDirectory("performance149-");
  try(var hub=new WorkspaceHub122(root,time::get)){
   var room=call(hub,OWNER,"CREATE_SCRIPT","",Map.of("source","code","data",WorkspaceProject126.pack(Map.of("a.java","hello"))));String id=room.get("project");
   check(room.get("pointer149").equals("true")&&room.get("compression149").equals("true"),"features negotiated");
   var rights=call(hub,OWNER,"MEMBER",id,Map.of("target",OTHER.toString(),"role","DEVELOPER","prefix","","deny","","hidden","","rev",room.get("revision")));
   call(hub,OWNER,"MEMBER",id,Map.of("target",HIDDEN.toString(),"role","VIEWER","prefix","","deny","","hidden","CODE","rev",rights.get("revision")));
   call(hub,OTHER,"OPEN",id,Map.of("path","a.java"));call(hub,HIDDEN,"VIEW",id,Map.of());
   var fields=new TreeMap<>(Map.of("path","a.java","mouse148",anchor,"dx148","2","dy148","3","color","123","sequence","1","session",UUID.randomUUID().toString()));
   var reply=call(hub,OWNER,"POINTER149",id,fields);check(reply.get("result").equals("OK")&&!reply.containsKey("document")&&!reply.containsKey("files"),"pointer ACK has no project snapshot");
   var events=hub.events130(OWNER,id,"POINTER149","a.java");check(events.size()==1&&events.get(0).recipient().equals(OTHER)&&events.get(0).data().get("pointerDelta149").startsWith(OWNER.toString()),"pointer push is permission-filtered");
   fields.put("sequence","2");fields.put("dx148","4");call(hub,OWNER,"POINTER149",id,fields);check(hub.events130(OWNER,id,"POINTER149","a.java").isEmpty(),"within-frame pointer pushes coalesced");
   time.set(50_000_000L);fields.put("sequence","3");call(hub,OWNER,"POINTER149",id,fields);check(hub.events130(OWNER,id,"POINTER149","a.java").get(0).data().get("pointerDelta149").contains("|4|3|148"),"latest coalesced position delivered");
   call(hub,OWNER,"POINTER149",id,fields);check(hub.events130(OWNER,id,"POINTER149","a.java").isEmpty(),"duplicate sequence idempotent");
   fields.put("sequence","4");fields.put("dx148","65");reject(()->call(hub,OWNER,"POINTER149",id,fields));fields.put("dx148","4");reject(()->call(hub,HIDDEN,"POINTER149",id,fields));reject(()->call(hub,UUID.randomUUID(),"POINTER149",id,fields));
   time.set(100_000_000L);fields.put("mouse148","");call(hub,OWNER,"POINTER149",id,fields);check(hub.events130(OWNER,id,"POINTER149","a.java").get(0).data().get("pointerDelta149").equals(OWNER.toString()),"hide delta has no stale location");
   // Both wire modes converge to identical stored bytes and source hashes.
   var begin=call(hub,OWNER,"MODEL_BEGIN147",id,Map.of("path","model.bbmodel","expected","","hash",WorkspaceRelease121.sha(raw),"size",Integer.toString(packed.bytes().length),"encoding149","zlib","rawSize149",Integer.toString(raw.length)));
   for(int at=0;at<packed.bytes().length;at+=WorkspaceModels147.CHUNK)call(hub,OWNER,"MODEL_PUT147",id,Map.of("path","model.bbmodel","token",begin.get("token147"),"offset",Integer.toString(at),"chunk",Base64.getEncoder().encodeToString(Arrays.copyOfRange(packed.bytes(),at,Math.min(packed.bytes().length,at+WorkspaceModels147.CHUNK)))));
   var commit=call(hub,OWNER,"MODEL_COMMIT147",id,Map.of("path","model.bbmodel","token",begin.get("token147")));check(commit.get("hash147").equals(WorkspaceRelease121.sha(raw)),"server verifies original hash after decompression");
   var compressed=call(hub,OWNER,"MODEL_OPEN147",id,Map.of("path","model.bbmodel","encoding149","zlib"));check(compressed.get("encoding149").equals("zlib")&&Integer.parseInt(compressed.get("wireSize149"))<raw.length/10,"compressed download negotiated");
   var legacy=call(hub,OWNER,"MODEL_OPEN147",id,Map.of("path","model.bbmodel"));check(!legacy.containsKey("encoding149")&&Integer.parseInt(legacy.get("size147"))==raw.length,"old client receives original protocol");
   reject(()->call(hub,OWNER,"MODEL_BEGIN147",id,Map.of("path","new.bbmodel","expected","","hash",WorkspaceRelease121.sha(raw),"size",Integer.toString(packed.bytes().length),"encoding149","zlib","rawSize149",Integer.toString(WorkspaceModels147.MAX+1))));
   var opened=call(hub,OWNER,"OPEN",id,Map.of("path","a.java"));var edit=new WorkspaceReplica122(OWNER,WorkspaceCodec122.document(opened.get("document")));edit.grant(opened.get("lease"),Long.parseLong(opened.get("first")),Long.parseLong(opened.get("last")));edit.prepare("changed");
   call(hub,OWNER,"EDIT",id,Map.of("path","a.java","batch",edit.first().batch(),"lease",edit.first().lease()));check(call(hub,OWNER,"MODEL_STAT147",id,Map.of("path","model.bbmodel")).get("hash147").equals(WorkspaceRelease121.sha(raw)),"editing code preserves shared immutable model bytes");

  }finally{try(var paths=Files.walk(root)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
  System.out.println("PerformanceChecks149: "+count+" passed; fixture "+raw.length+" -> "+packed.bytes().length+" bytes");
 }
}
