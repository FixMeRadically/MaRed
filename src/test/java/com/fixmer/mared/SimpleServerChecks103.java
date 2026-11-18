package com.fixmer.mared;
import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.genesis.technology.server.firstlaunch.*;import java.nio.file.*;import java.util.*;
/** Real signed queue/capability tests. Minecraft permission inputs remain fixtures. */
public final class SimpleServerChecks103 {
 static int n;static void ok(boolean b){n++;if(!b)throw new AssertionError("server103 "+n);}interface IO{void run()throws Exception;}static void reject(IO fn)throws Exception{n++;try{fn.run();}catch(Exception expected){return;}throw new AssertionError("accepted "+n);}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("genesis-simple103-");try{
   byte[] key=ServerAccessChecks88.fixture(root);var admin=DedicatedAdmin75.open(root);ok(!admin.supportsRestart103());reject(()->admin.submit(UUID.randomUUID(),"RESTART","r1"));
   FirstLaunchFiles.write(root.resolve("owner103"),LocalApply67.sign(key,Map.of("schema","103","run",UUID.randomUUID().toString())),false);ok(!admin.supportsRestart103());reject(()->admin.submit(UUID.randomUUID(),"RESTART","r1"));
   FirstLaunchFiles.write(root.resolve("owner103"),LocalApply67.sign(key,Map.of("schema","103","run",admin.run.toString())),true);ok(admin.supportsRestart103());
   var access=ServerAccess88.open(root);UUID op=UUID.randomUUID(),actor=UUID.randomUUID();access.change(op,true,true,actor,"OBSERVER",0,UUID.randomUUID());reject(()->access.queue(actor,false,true,System.currentTimeMillis(),UUID.randomUUID(),"RESTART","r1"));
   access.change(op,true,true,actor,"RESTARTER",1,UUID.randomUUID());reject(()->access.queue(actor,false,false,System.currentTimeMillis(),UUID.randomUUID(),"RESTART","r1"));reject(()->access.queue(actor,false,true,System.currentTimeMillis()-6000,UUID.randomUUID(),"RESTART","r1"));
   UUID request=UUID.randomUUID();access.queue(actor,false,true,System.currentTimeMillis(),request,"RESTART","r1");var queued=LocalApply67.verify(key,Files.readAllBytes(root.resolve("request74")));ok(queued.get("action").equals("RESTART"));var permit=LocalApply67.verify(key,Base64.getDecoder().decode(queued.get("permit88")));ok(permit.get("actor").equals(actor.toString()));ok(permit.get("action").equals("RESTART"));ok(permit.get("request").equals(request.toString()));
   reject(()->access.queue(actor,false,true,System.currentTimeMillis(),UUID.randomUUID(),"RESTART","r1"));Files.delete(root.resolve("request74"));access.change(op,true,true,actor,"OBSERVER",2,UUID.randomUUID());reject(()->access.queue(actor,false,true,System.currentTimeMillis(),UUID.randomUUID(),"RESTART","r1"));
  }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
  System.out.println("Simple103 actual signed owner-capability/restart-queue/role/revoke checks passed: "+n+"; player identity and permission are fixtures, not Minecraft runtime.");
 }
}
