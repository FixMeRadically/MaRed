package com.fixmer.mared;
import com.fixmer.genesis.technology.permissions.*;import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.genesis.technology.server.firstlaunch.*;import com.fixmer.genesis.technology.workspace.live122.*;
import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicLong;
/** Actual Ed25519, connection-pinned replay checks, persisted trust and controller consumption. */
public final class DeviceChecks153 {
 static int count;static void ok(boolean condition,String text){if(!condition)throw new AssertionError(text);count++;}interface Work{void run()throws Exception;}static void no(Work work)throws Exception{try{work.run();throw new AssertionError("refusal expected");}catch(Exception expected){count++;}}
 static Map<String,String> signed(DeviceKey153 key,String ticket,long counter,String operation,Map<String,String> fields)throws Exception{var q=new TreeMap<>(com.fixmer.mared.technology.workspace.live122.WorkspaceClient122.request(operation,"",fields));q.put("ticket153",ticket);q.put("counter153",Long.toString(counter));q.put("proof153",key.sign(DeviceSessions153.requestBody(q)));WorkspaceHub122.request(q);return Map.copyOf(q);}
 static final byte[] KEY=new byte[32];static void seal(Path file,Map<String,String> fields)throws Exception{FirstLaunchFiles.write(file,LocalApply67.sign(KEY,fields),true);}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("devices153-");AtomicLong now=new AtomicLong();UUID actor=UUID.randomUUID();Object connection=new Object();
  try{
   DeviceKey153 key=DeviceKey153.load(root.resolve("client-A")),same=DeviceKey153.load(root.resolve("client-A")),other=DeviceKey153.load(root.resolve("client-B"));
   ok(key.fingerprint().equals(same.fingerprint()),"device key survives restart");ok(!key.fingerprint().equals(other.fingerprint()),"separate server directories do not share keys");
   var trust=new DeviceTrust153(root.resolve("trust"));var sessions=new DeviceSessions153(trust,now::get);var begin=sessions.begin(actor,connection,key.publicEncoded());
   no(()->sessions.prove(actor,new Object(),begin.get("ticket153"),key.sign(begin.get("challenge153"))));
   sessions.prove(actor,connection,begin.get("ticket153"),key.sign(begin.get("challenge153")));ok(!sessions.verified(actor,connection),"possession does not grant console trust");
   trust.change(actor,key.fingerprint(),true);ok(sessions.verified(actor,connection),"console trust enables proven connection");ok(new DeviceTrust153(root.resolve("trust")).allows(actor,key.fingerprint()),"trust is persistent");
   no(()->sessions.prove(actor,connection,begin.get("ticket153"),key.sign(begin.get("challenge153"))));
   String hash=FirstLaunchFiles.sha(new byte[]{1});var q=signed(key,begin.get("ticket153"),1,"TEST_BEGIN152",Map.of("hash",hash,"size","1"));
   ok(sessions.request(actor,connection,q),"signed request accepted once");ok(!sessions.request(actor,connection,q),"duplicate requires the cached original response");
   sessions.rejected(actor,connection,q.get("request"));ok(sessions.request(actor,connection,q),"known BUSY permits one exact retry");ok(!sessions.request(actor,connection,q),"retry is one use");
   var altered=new TreeMap<>(q);altered.put("size","2");no(()->sessions.request(actor,connection,altered));
   var reused=new TreeMap<>(signed(key,begin.get("ticket153"),2,"TEST_BEGIN152",Map.of("hash",hash,"size","1")));reused.put("request",q.get("request"));reused.put("proof153",key.sign(DeviceSessions153.requestBody(reused)));no(()->sessions.request(actor,connection,reused));
   no(()->sessions.request(actor,connection,signed(other,begin.get("ticket153"),3,"TEST_BEGIN152",Map.of("hash",hash,"size","1"))));
   trust.change(actor,key.fingerprint(),false);ok(!sessions.verified(actor,connection),"revocation affects current session immediately");no(()->sessions.request(actor,connection,q));trust.change(actor,key.fingerprint(),true);
   var expired=sessions.begin(actor,connection,key.publicEncoded());now.set(30_000_000_000L);no(()->sessions.prove(actor,connection,expired.get("ticket153"),key.sign(expired.get("challenge153"))));
   Object newConnection=new Object();var fresh=sessions.begin(actor,newConnection,key.publicEncoded());sessions.prove(actor,newConnection,fresh.get("ticket153"),key.sign(fresh.get("challenge153")));sessions.disconnect(actor,connection);ok(sessions.verified(actor,newConnection),"old logout cannot erase new connection replay state");no(()->sessions.request(actor,newConnection,q));
   sessions.disconnect(actor,newConnection);ok(!sessions.verified(actor,newConnection),"logout invalidates session");
   // Updated controller verifies the same device binding when applying an offline upload.
   Path server=Files.createDirectories(root.resolve("server"));Files.createDirectory(server.resolve("mods"));Path owner=Files.createDirectories(ServerApp104.root(server));Files.write(owner.resolve("owner74.key"),KEY);String run=UUID.randomUUID().toString();
   seal(owner.resolve("owner104"),Map.of("schema","104","run",run,"profile",server.toString()));for(String version:List.of("75","115","152","153"))seal(owner.resolve("owner"+version),Map.of("schema",version,"run",run));ServerUpdateChecks115.state(server,run,"1","RUNNING",false);
   var view=new TreeMap<String,String>();view.putAll(Map.of("schema","75","run",run,"generation","1","phase","RUNNING","updated",Long.toString(System.currentTimeMillis()),"ready","true","busy","false","control","","delta","","history",""));view.put("failureCode","");view.put("failureStage","");view.put("reason","");seal(owner.resolve("view75"),view);
   var policy=new MaopPolicy113(server.resolve("world/genesis/maop113"));policy.change(actor,MaopPolicy113.Role.MAINTAINER,0);seal(owner.resolve("maop113.binding"),Map.of("schema","113","run",run,"profile",server.toString(),"policy",policy.file.toString()));
   var devices=new DeviceTrust153(server.resolve("world/genesis/maop113"));devices.change(actor,key.fingerprint(),true);seal(owner.resolve("maop153.binding"),Map.of("schema","153","run",run,"profile",server.toString(),"devices",devices.file.toString()));
   var upload=new RemoteTest152(owner.resolve("remote-tests152"),RemoteTest152.server(server),now::get);Path jar=root.resolve("candidate.jar");ServerUpdateChecks115.jar(jar,"demo","offline candidate");byte[] bytes=Files.readAllBytes(jar);String digest=FirstLaunchFiles.sha(bytes);
   String token=upload.execute(actor,"TEST_BEGIN152",Map.of("hash",digest,"size",Integer.toString(bytes.length)),key.fingerprint()).get("token152");
   no(()->upload.execute(actor,"TEST_PUT152",Map.of("token",token,"offset","0","chunk",Base64.getEncoder().encodeToString(bytes)),other.fingerprint()));
   upload.execute(actor,"TEST_PUT152",Map.of("token",token,"offset","0","chunk",Base64.getEncoder().encodeToString(bytes)),key.fingerprint());var reviewed=upload.execute(actor,"TEST_REVIEW152",Map.of("token",token),key.fingerprint());
   no(()->upload.execute(actor,"TEST_APPLY152",Map.of("token",token),key.fingerprint()));
   var apply=Map.of("token",token,"hash153",digest,"target153",reviewed.get("target152"),"generation153",reviewed.get("generation152"));var changed=new TreeMap<>(apply);changed.put("hash153","0".repeat(64));no(()->upload.execute(actor,"TEST_APPLY152",changed,key.fingerprint()));
   var queued=upload.execute(actor,"TEST_APPLY152",apply,key.fingerprint());ok(queued.get("queued152").equals("true"),"offline device-bound apply reaches supervised queue");
   ServerUpdate115.main(new String[]{server.toString(),queued.get("deployment152")});ok(true,"controller validates sealed device and JAR");devices.change(actor,key.fingerprint(),false);no(()->ServerUpdate115.main(new String[]{server.toString(),queued.get("deployment152")}));
   no(()->upload.execute(actor,"TEST_APPLY152",apply,key.fingerprint()));
  }finally{try(var files=Files.walk(root)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}}
  System.out.println("DeviceChecks153: "+count+" passed");
 }
}
