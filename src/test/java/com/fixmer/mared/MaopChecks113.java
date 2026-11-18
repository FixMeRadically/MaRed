package com.fixmer.mared;
import com.fixmer.genesis.technology.permissions.MaopPolicy113;
import com.fixmer.genesis.technology.permissions.MaopPolicy113.Role;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
/** Actual permissions persistence/CAS and canonical codec. Vanilla command sources are NOT simulated here. */
public final class MaopChecks113 {
 static int checks;static void ok(boolean value){checks++;if(!value)throw new AssertionError("Maop113 "+checks);}interface Work{void run()throws Exception;}
 static void rejects(String code,Work work)throws Exception{try{work.run();throw new AssertionError("accepted invalid input "+code);}catch(java.io.IOException expected){ok(expected.getMessage().equals(code));}}
 public static void main(String[] args)throws Exception{
  if(args.length==1){Path root=Path.of(args[0]);Files.createDirectories(root);var policy=new MaopPolicy113(root);UUID first=UUID.fromString("80000000-0000-0000-0000-000000000001"),second=UUID.fromString("10000000-0000-0000-0000-000000000001");policy.change(first,Role.ADMIN,policy.state().revision());policy.change(second,Role.WORKSPACE,policy.state().revision());Path profile=root.resolve("profile"),ownerRoot=root.resolve("owner");Files.createDirectories(profile);Files.createDirectories(ownerRoot);
   // The policy must be inside its HMAC-bound profile for the controller reader.
   Path bound=profile.resolve("world/genesis/maop113/permissions113.txt");Files.createDirectories(bound.getParent());Files.copy(policy.file,bound);
   byte[] key=new byte[32];Arrays.fill(key,(byte)'k');String run="30000000-0000-0000-0000-000000000001",nonce="40000000-0000-0000-0000-000000000001";
   com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles.write(ownerRoot.resolve("owner113"),com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.sign(key,Map.of("schema","113","run",run)),true);
   com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles.write(ownerRoot.resolve("maop113.binding"),com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.sign(key,Map.of("schema","113","run",run,"profile",profile.toString(),"policy",bound.toString())),true);
   var permit=new TreeMap<String,String>();permit.putAll(Map.of("schema","88","run",run,"actor",first.toString(),"root","true","revision","0","generation","1","request",nonce,"action","RESTART","label","r1"));permit.put("issued","1699999999000");permit.put("expires","1700000014000");
   var request=new TreeMap<String,String>(Map.of("schema","75","run",run,"generation","1","request",nonce,"action","RESTART","label","r1"));request.put("permit88",Base64.getEncoder().encodeToString(com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.sign(key,permit)));
   Files.write(root.resolve("request113"),com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.sign(key,request));
   var rights=new TreeMap<String,String>();for(Role role:Role.values())rights.put(role.name(),String.join(",",new TreeSet<>(MaopPolicy113.RIGHTS.get(role))));Files.write(root.resolve("rights113"),com.fixmer.genesis.technology.server.remote.ControlWire73.encode(rights));
   System.out.println(first);return;}
  byte[] markerKey=new byte[32];Arrays.fill(markerKey,(byte)7);UUID runId=UUID.randomUUID();byte[] marker=com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.sign(markerKey,Map.of("schema","113","run",runId.toString()));MaopPolicy113.requireController(markerKey,marker,runId);ok(true);rejects("MAOP113_CONTROLLER_REQUIRED",()->MaopPolicy113.requireController(markerKey,marker,UUID.randomUUID()));byte[] wrong=com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.sign(markerKey,Map.of("schema","112","run",runId.toString()));rejects("MAOP113_CONTROLLER_REQUIRED",()->MaopPolicy113.requireController(markerKey,wrong,runId));
  Path root=Files.createTempDirectory("Genesis Maop113 ");UUID actor=UUID.randomUUID(),other=UUID.randomUUID();var policy=new MaopPolicy113(root);
  for(String action:MaopPolicy113.ROOT)ok(!policy.state().allows(actor,action));
  for(Role role:Role.values()){
   long before=policy.state().revision();policy.change(actor,role,before);ok(policy.state().revision()==before+1);for(String action:MaopPolicy113.ROOT)ok(policy.state().allows(actor,action)==MaopPolicy113.RIGHTS.get(role).contains(action));
   var reopened=new MaopPolicy113(root);ok(reopened.state().equals(policy.state()));ok(Arrays.equals(MaopPolicy113.encode(reopened.state()),Files.readAllBytes(policy.file)));
  }
  policy.change(actor,Role.WORKSPACE,policy.state().revision());ok(policy.state().allows(actor,"WORKSPACE_CREATE"));ok(!policy.state().allows(actor,"RESTART"));
  policy.change(actor,Role.RESTARTER,policy.state().revision());ok(!policy.state().allows(actor,"WORKSPACE_CREATE"));ok(policy.state().allows(actor,"RESTART"));ok(!policy.state().allows(actor,"STAGE"));
  long base=policy.state().revision();var second=new MaopPolicy113(root);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);List<Future<String>> jobs=new ArrayList<>();
  try{for(var target:List.of(policy,second))jobs.add(pool.submit(()->{start.await();try{target.change(other,Role.PUBLISHER,base);return "ok";}catch(java.io.IOException error){return error.getMessage();}}));start.countDown();var result=new ArrayList<String>();for(var job:jobs)result.add(job.get(5,TimeUnit.SECONDS));ok(Collections.frequency(result,"ok")==1);ok(result.stream().filter(x->x.equals("MAOP113_BUSY")||x.equals("MAOP113_STALE")).count()==1);}finally{pool.shutdownNow();}
  var fresh=new MaopPolicy113(root);fresh.change(actor,null,fresh.state().revision());ok(!fresh.state().allows(actor,"RESTART"));ok(!new MaopPolicy113(root).state().subjects().containsKey(actor));rejects("MAOP113_STALE",()->fresh.change(actor,Role.ADMIN,0));
  byte[] good=Files.readAllBytes(fresh.file),bad=good.clone();bad[bad.length/2]^=1;Files.write(fresh.file,bad);rejects("MAOP113_STATE",()->new MaopPolicy113(root));ok(Arrays.equals(bad,Files.readAllBytes(fresh.file)));Files.write(fresh.file,good);
  rejects("MAOP113_STATE",()->MaopPolicy113.decode(new byte[20001]));rejects("MAOP113_IDENTITY",()->fresh.change(new UUID(0,0),Role.ADMIN,fresh.state().revision()));
  // A physical atomic move failure must not grant the requested role in memory.
  Path held=root.resolve("held");Files.move(fresh.file,held);Files.createDirectory(fresh.file);Files.writeString(fresh.file.resolve("block"),"block");try{fresh.change(actor,Role.ADMIN,fresh.state().revision());throw new AssertionError("directory accepted");}catch(java.io.IOException expected){checks++;}ok(!fresh.state().allows(actor,"RESTART"));Files.delete(fresh.file.resolve("block"));Files.delete(fresh.file);Files.move(held,fresh.file);
  try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
  System.out.println("Maop113 actual role separation/persistence/revocation/codec/CAS/failure checks passed: "+checks+"; no Minecraft OP/console dispatch executed.");
 }
}
