package com.fixmer.mared.technology.workspace.live122;
import com.fixmer.genesis.technology.workspace.live122.*;
import com.fixmer.genesis.technology.workspace.collaboration121.*;
import com.fixmer.genesis.technology.editor.MenuText154;
import java.util.*;import java.nio.file.*;import java.util.concurrent.atomic.*;
/** Production pointer transport with withheld ACKs, bounded latest-state lane and permission-filtered hub. */
public final class UxWireChecks154 {
 static int count;static void ok(boolean b,String why){if(!b)throw new AssertionError(why);count++;}
 static final UUID OWNER=UUID.randomUUID(),VIEWER=UUID.randomUUID(),ROOM=UUID.randomUUID();
 static final class Host implements WorkspaceClient122.Host135 {
  Object connection=new Object();long now;List<Map<String,String>> wire=new ArrayList<>();
  public Object connection(){return connection;}public boolean channel(Object c){return true;}
  public void execute(Runnable action){action.run();}public long now(){return now;}public void failed(Exception e){throw new AssertionError(e);}
  public void send(byte[] bytes){try{wire.add(WorkspaceCodec122.decode(bytes,WorkspaceCodec122.REQUEST));}catch(Exception e){throw new AssertionError(e);}}
 }
 static Map<String,String> point(String room,long sequence){return WorkspaceClient122.request("POINTER149",room,Map.of("path","a.java","mouse148",WorkspaceCodec122.id(CollaborativeDocument121.ROOT),"dx148",Long.toString(sequence%64),"dy148","3","sequence",Long.toString(sequence),"session",OWNER.toString(),"color","123","stream154","true"));}
 static Map<String,String> call(WorkspaceHub122 hub,UUID actor,String op,String project,Map<String,String> fields)throws Exception{return hub.execute(actor,true,actor.equals(OWNER)?"Owner":"Viewer",WorkspaceClient122.request(op,project,fields));}
 public static void main(String[] args)throws Exception{
  Host host=new Host();var cursor=new WorkspaceClient122(host,true);var reliable=new WorkspaceClient122(host);cursor.attach();reliable.attach();
  try{
   reliable.send(WorkspaceClient122.request("VIEW",ROOM.toString(),Map.of()),d->{});ok(reliable.busy(),"reliable operation remains pending");
   for(int i=1;i<=20;i++){host.now=(i-1)*50_000_000L;if(!cursor.stream154(point(ROOM.toString(),i)))throw new AssertionError("blocked on absent ACK "+i);}
   ok(host.wire.size()==21,"20 pointer samples sent while all ACKs withheld for one second");ok(!cursor.busy(),"no cursor pending queue");ok(reliable.busy(),"cursor emission never completes a durable request");
   ok(!cursor.stream154(point(ROOM.toString(),21)),"per-client stream frequency bounded");host.now+=50_000_000L;
   ok(!reliable.stream154(point(ROOM.toString(),21)),"reliable client cannot bypass its queue");ok(!cursor.stream154(WorkspaceClient122.request("VIEW",ROOM.toString(),Map.of("stream154","true"))),"durable operation cannot enter stream");
   cursor.tick();ok(host.wire.size()==21,"no retransmission of stale positions");host.connection=new Object();ok(!cursor.stream154(point(ROOM.toString(),22)),"replacement connection refused");
  }finally{cursor.detach();reliable.detach();}
  var tasks=new ArrayDeque<Runnable>();var delivered=new ArrayList<Integer>();var errors=new ArrayList<RuntimeException>();
  var lane=new LatestLane154<String,Integer>(tasks::add,2,delivered::add,errors::add);
  ok(lane.offer("A",1),"first state accepted");for(int i=2;i<=1000;i++)lane.offer("A",i);
  ok(lane.offer("B",2000),"second actor accepted");ok(!lane.offer("C",3000),"bounded actor capacity");ok(tasks.size()==1,"one drain scheduled for all obsolete states");
  tasks.remove().run();ok(delivered.equals(List.of(1000,2000)),"only latest state per actor delivered");ok(errors.isEmpty(),"consumer succeeded");
  lane.offer("A",5);lane.remove("A");tasks.remove().run();ok(delivered.size()==2,"logout removes queued point");lane.close();ok(!lane.offer("A",6),"shutdown refuses work");
  var rejected=new LatestLane154<String,Integer>(r->{throw new java.util.concurrent.RejectedExecutionException();},2,delivered::add,errors::add);
  ok(!rejected.offer("A",1)&&errors.size()==1,"executor shutdown reported without stranded work");
  var width=(java.util.function.ToIntFunction<String>)(s->s.codePointCount(0,s.length()));
  var columns=MenuText154.columns("Очень длинный пункт меню\tCtrl+Shift+Enter",24,width);
  ok(width.applyAsInt(columns.left())+12<=columns.rightOffset(),"label and shortcut never overlap");ok(width.applyAsInt(columns.right())+columns.rightOffset()<=24,"shortcut inside available width");
  String fitted=MenuText154.fit("😀😀😀😀😀😀",5,width);ok(fitted.equals("😀😀..."),"ellipsis preserves supplementary glyphs");ok(MenuText154.fit("abc",2,width).isEmpty(),"too narrow menu does not spill glyphs");
  var clock=new AtomicLong();var view=new PointerView149(clock::get);String row=OWNER+"|a.java|Owner|123|"+WorkspaceCodec122.id(CollaborativeDocument121.ROOT)+"|2|3|148";
  view.delta(row);view.position(OWNER.toString(),"pane",0,0);int previous=0,frames=0;
  for(int i=1;i<=12;i++){clock.set(i*8_333_333L);int x=view.position(OWNER.toString(),"pane",100,0)[0];if(x!=previous)frames++;if(x<previous||x>100)throw new AssertionError("smoothing overshot");previous=x;}
  ok(frames>=8,"pointer moves across render frames between network samples");ok(view.position(OWNER.toString(),"other",40,0)[0]==40,"layout switch resets interpolation");
  Path root=Files.createTempDirectory("ux154-");clock.set(0);
  try(var hub=new WorkspaceHub122(root,clock::get)){
   var room=call(hub,OWNER,"CREATE","",Map.of("text","Cursor"));String id=room.get("project");ok("true".equals(room.get("pointerStream154")),"stream negotiated by server");
   call(hub,OWNER,"NEW",id,Map.of("path","a.java","rev",room.get("revision")));var state=call(hub,OWNER,"VIEW",id,Map.of());
   call(hub,OWNER,"MEMBER",id,Map.of("target",VIEWER.toString(),"role","VIEWER","prefix","","deny","","hidden","","rev",state.get("revision")));
   call(hub,VIEWER,"OPEN",id,Map.of("path","a.java"));
   hub.execute(OWNER,false,"Owner",point(id,1));ok(hub.events130(OWNER,id,"POINTER149","a.java").size()==1,"stream still checks viewer permissions");
   hub.execute(OWNER,false,"Owner",point(id,2));ok(hub.events130(OWNER,id,"POINTER149","a.java").isEmpty(),"same-frame updates coalesced");
   clock.set(50_000_000L);var flush=hub.flushPointers154();ok(flush.size()==1&&flush.get(0).data().get("pointerDelta149").contains("|2|3|148"),"last movement flushed without waiting for another packet or heartbeat");ok(hub.flushPointers154().isEmpty(),"deferred push does not repeat consumed state");
   boolean denied=false;try{hub.execute(UUID.randomUUID(),false,"Stranger",point(id,3));}catch(SecurityException e){denied=true;}ok(denied,"stream does not bypass room membership");
   state=call(hub,OWNER,"VIEW",id,Map.of());call(hub,OWNER,"MEMBER",id,Map.of("target",VIEWER.toString(),"role","VIEWER","prefix","a.java","deny","READ","hidden","","rev",state.get("revision")));
   hub.execute(OWNER,false,"Owner",point(id,4));clock.set(100_000_000L);ok(hub.flushPointers154().stream().noneMatch(e->e.recipient().equals(VIEWER)&&e.data().getOrDefault("pointerDelta149","").contains("a.java")),"revoked file cannot leak through fast lane");
  }finally{try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  System.out.println("UxWireChecks154: "+count+" passed");
 }
}
