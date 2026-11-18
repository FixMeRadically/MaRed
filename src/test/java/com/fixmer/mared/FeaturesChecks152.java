package com.fixmer.mared;
import com.fixmer.genesis.technology.workspace.live122.*;import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.genesis.technology.server.firstlaunch.*;import com.fixmer.genesis.technology.permissions.*;
import com.fixmer.mared.gui2.genesis.node.*;import com.fixmer.mared.technology.editor.WorkbenchPreferences;
import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.*;

/** Color persistence, account propagation data, Genesis palettes and actual supervised remote upload boundary. */
public final class FeaturesChecks152 {
 static int count;static void ok(boolean value,String label){if(!value)throw new AssertionError(label);count++;}
 interface Work{void run()throws Exception;}static void refuse(Work work)throws Exception{try{work.run();throw new AssertionError("Refusal expected");}catch(java.io.IOException|IllegalArgumentException|SecurityException expected){count++;}}
 static Map<String,String> q(String op,String project,Map<String,String> fields){return com.fixmer.mared.technology.workspace.live122.WorkspaceClient122.request(op,project,fields);}
 static void delete(Path root)throws Exception{try(var files=Files.walk(root)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}}
 static final byte[] KEY=new byte[32];static void sign(Path path,Map<String,String> fields)throws Exception{FirstLaunchFiles.write(path,LocalApply67.sign(KEY,fields),true);}
 static void view(Path owner,String run,int generation)throws Exception{var state=new TreeMap<String,String>();state.putAll(Map.of("schema","75","run",run,"generation",Integer.toString(generation),"phase","RUNNING","updated",Long.toString(System.currentTimeMillis()),"ready","true","busy","false","control","","delta","","history",""));state.put("failureCode","");state.put("failureStage","");state.put("reason","");sign(owner.resolve("view75"),state);}
 public static void main(String[] args)throws Exception{
  var prefs=new WorkbenchPreferences();prefs.railWidth152=212;prefs.directionColors152.put("mods",0x123456);var draft=prefs.settingsDraft();draft.directionColors152.put("mods",0xabcdef);draft.surfaceColors152.put("panel",0x102030);
  ok(prefs.directionColors152.get("mods")==0x123456,"palette edits remain a draft");prefs.applySettings(draft);draft.directionColors152.clear();ok(prefs.directionColors152.get("mods")==0xabcdef&&prefs.railWidth152==212,"settings commit independent map and rail width");
  NodeRegistry.palette152(prefs.directionColors152);ok(NodeRegistry.buildAll().stream().filter(n->n.type()==GenesisNodeData.NodeType.MODS).findFirst().orElseThrow().defaultColor()==0xffabcdef,"existing Genesis nodes resolve custom color");NodeRegistry.palette152(Map.of());
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();String root=WorkspaceCodec122.id(com.fixmer.genesis.technology.workspace.collaboration121.CollaborativeDocument121.ROOT);
  String carets=a+"|a.java|Alice|1122867|"+root+"|"+root+"\n"+b+"|a.java|Bob|4478310|"+root+"|"+root;
  var people=WorkspaceActivity152.people("a.java",carets,"");ok(people.size()==2,"two participants share one file marker");ok(WorkspaceActivity152.color(people,0)!=WorkspaceActivity152.color(people,1_500_000_000L),"single marker cycles participant colors");ok(WorkspaceActivity152.color(people,0)==WorkspaceActivity152.color(people,3_000_000_000L),"cycle stable and deterministic");ok(WorkspaceActivity152.people("private.java",carets,"").isEmpty(),"activity does not cross files");
  var combined=WorkspaceActivity152.people("a.java",carets,b+"|Bob|a.java|7829367");ok(combined.size()==2&&combined.stream().filter(p->p.actor().equals(b.toString())).findFirst().orElseThrow().tool().equals("Blockbench"),"model activity merges without duplicate actors");
  Path temp=Files.createTempDirectory("features152-");
  try{
   var profiles=new WorkspaceProfiles152(temp.resolve("profiles"));ok(!profiles.saved(a),"unsaved server profile permits local migration");profiles.save(a,0x00ff99);ok(new WorkspaceProfiles152(temp.resolve("profiles")).color(a)==0x00ff99,"color survives server process reconstruction");refuse(()->profiles.save(a,-1));
   String project;
   try(var hub=new WorkspaceHub122(temp.resolve("workspaces"))){var created=hub.execute(a,true,"Alice",q("CREATE","",Map.of("text","Colors")));project=created.get("project");long revision=Long.parseLong(created.get("revision"));
    var saved=hub.execute(a,true,"Alice",q("APPEARANCE152","",Map.of("color","123456")));ok(saved.get("colorSaved152").equals("true"),"save acknowledges server persistence");
    var current=hub.execute(a,true,"Alice",q("VIEW",project,Map.of()));ok(current.get("color152").equals("123456")&&Long.parseLong(current.get("revision"))==revision,"color metadata persists without project journal edits");
    hub.execute(b,false,"Bob",q("APPEARANCE152","",Map.of("color","654321")));ok(hub.execute(a,true,"Alice",q("VIEW",project,Map.of())).get("color152").equals("123456"),"another account cannot overwrite color");
   }
   try(var hub=new WorkspaceHub122(temp.resolve("workspaces"))){ok(hub.execute(a,true,"Alice",q("VIEW",project,Map.of())).get("color152").equals("123456"),"wire color survives hub restart");}
   AtomicLong clock=new AtomicLong();AtomicBoolean allowed=new AtomicBoolean(true);AtomicInteger applications=new AtomicInteger();
   var backend=new RemoteTest152.Backend(){public void authorize(UUID actor){if(!allowed.get())throw new SecurityException();}public Map<String,String> review(UUID actor,Path folder,String hash,long size){return Map.of("hash152",hash);}public String apply(UUID actor,Path folder){applications.incrementAndGet();return UUID.randomUUID().toString();}};
   var upload=new RemoteTest152(temp.resolve("uploads"),backend,clock::get);byte[] bytes="actual upload bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash=FirstLaunchFiles.sha(bytes);
   var begin=upload.execute(a,"TEST_BEGIN152",Map.of("hash",hash,"size",Integer.toString(bytes.length)));String token=begin.get("token152");var part=Map.of("token",token,"offset","0","chunk",Base64.getEncoder().encodeToString(bytes));
   refuse(()->upload.execute(b,"TEST_PUT152",part));refuse(()->upload.execute(a,"TEST_APPLY152",Map.of("token",token)));
   ok(upload.execute(a,"TEST_PUT152",part).get("offset152").equals(Integer.toString(bytes.length)),"chunk accepted");ok(upload.execute(a,"TEST_PUT152",part).get("offset152").equals(Integer.toString(bytes.length)),"lost chunk ACK retry idempotent");
   refuse(()->upload.execute(a,"TEST_PUT152",Map.of("token",token,"offset","0","chunk",Base64.getEncoder().encodeToString("different".getBytes()))));
   upload.execute(a,"TEST_REVIEW152",Map.of("token",token));refuse(()->upload.execute(a,"TEST_PUT152",part));
   var applied=upload.execute(a,"TEST_APPLY152",Map.of("token",token));ok(upload.execute(a,"TEST_APPLY152",Map.of("token",token)).equals(applied)&&applications.get()==1,"replayed apply queues only one restart");
   allowed.set(false);refuse(()->upload.execute(a,"TEST_APPLY152",Map.of("token",token)));allowed.set(true);
   refuse(()->upload.execute(a,"TEST_BEGIN152",Map.of("hash",hash,"size","67108865")));
   // Actual signed owner + actual Maop file + actual JAR inspection and ServerUpdate115 queue.
   Path server=Files.createDirectories(temp.resolve("server"));Path mods=Files.createDirectory(server.resolve("mods"));Path owner=Files.createDirectories(ServerApp104.root(server));Files.write(owner.resolve("owner74.key"),KEY);String run=UUID.randomUUID().toString();
   sign(owner.resolve("owner104"),Map.of("schema","104","run",run,"profile",server.toString()));for(String version:List.of("75","115","152"))sign(owner.resolve("owner"+version),Map.of("schema",version,"run",run));
   ServerUpdateChecks115.state(server,run,"1","RUNNING",false);view(owner,run,1);
   var policy=new MaopPolicy113(server.resolve("world/genesis/maop113"));policy.change(a,MaopPolicy113.Role.MAINTAINER,0);sign(owner.resolve("maop113.binding"),Map.of("schema","113","run",run,"profile",server.toString(),"policy",policy.file.toString()));
   var real=new RemoteTest152(owner.resolve("remote-tests152"),RemoteTest152.server(server),clock::get);Path jar=temp.resolve("test.jar");ServerUpdateChecks115.jar(jar,"demo","compiled fixture");byte[] data=Files.readAllBytes(jar);String actualHash=FirstLaunchFiles.sha(data);
   String realToken=real.execute(a,"TEST_BEGIN152",Map.of("hash",actualHash,"size",Integer.toString(data.length))).get("token152");real.execute(a,"TEST_PUT152",Map.of("token",realToken,"offset","0","chunk",Base64.getEncoder().encodeToString(data)));
   var reviewed=real.execute(a,"TEST_REVIEW152",Map.of("token",realToken));ok(reviewed.get("hash152").equals(actualHash)&&reviewed.get("target152").startsWith("demo-script-"),"remote JAR reviewed against actual server inventory");
   var queued=real.execute(a,"TEST_APPLY152",Map.of("token",realToken));var packet=LocalApply67.verify(KEY,FirstLaunchFiles.read(owner.resolve("request74"),16384));ok(packet.get("action").equals("APPLY115")&&packet.get("request").equals(queued.get("deployment152")),"real supervised apply packet is signed and queued");
   var repeated=real.execute(a,"TEST_APPLY152",Map.of("token",realToken));ok(repeated.equals(queued),"real queued request reused after lost reply");
   policy.change(a,MaopPolicy113.Role.WORKSPACE,1);refuse(()->ServerUpdate115.main(new String[]{server.toString(),queued.get("deployment152")}));policy.change(a,MaopPolicy113.Role.MAINTAINER,2);
   ServerUpdate115.main(new String[]{server.toString(),queued.get("deployment152")});ok(true,"controller JVM verification accepts server seal with current rights");
   Files.write(owner.resolve("remote-tests152").resolve(realToken).resolve("artifact.jar"),new byte[]{1},StandardOpenOption.APPEND);refuse(()->ServerUpdate115.main(new String[]{server.toString(),queued.get("deployment152")}));
   view(owner,run,2);ServerUpdateChecks115.state(server,run,"2","RUNNING",false);
   String nextToken=real.execute(a,"TEST_BEGIN152",Map.of("hash",actualHash,"size",Integer.toString(data.length))).get("token152");
   ok(!Files.exists(owner.resolve("remote-tests152").resolve(realToken)),"completed generation staging is reclaimed before another upload");
   real.execute(a,"TEST_CANCEL152",Map.of("token",nextToken));ok(!Files.exists(owner.resolve("remote-tests152").resolve(nextToken)),"cancel removes unused staging files");
   refuse(()->WorkspaceHub122.request(q("TEST_BEGIN152","",Map.of("hash",actualHash,"size","10","server","C:/other-server"))));
   Files.delete(owner.resolve("owner152"));refuse(()->real.execute(a,"TEST_BEGIN152",Map.of("hash",actualHash,"size",Integer.toString(data.length))));
  }finally{delete(temp);}
  System.out.println("FeaturesChecks152: "+count+" passed");
 }
}
