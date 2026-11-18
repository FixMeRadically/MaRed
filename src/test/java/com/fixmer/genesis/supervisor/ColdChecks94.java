package com.fixmer.genesis.supervisor;
import java.nio.file.*;import java.util.*;import java.io.*;
/** Real owner/profile locks and real deployment JARs. Premain receipt is a labelled signature fixture here. */
public final class ColdChecks94 {
 private static int n;private static void ok(boolean v){n++;if(!v)throw new AssertionError("check "+n);}private interface IO{void run()throws Exception;}private static void rejects(IO op)throws Exception{try{op.run();throw new AssertionError("accepted invalid input "+n);}catch(IOException expected){n++;}}
 static SupervisorConfig config(Path game)throws Exception {
  Path path=game.resolve("config/mared/dev/supervisor.properties");Files.createDirectories(path.getParent());Properties p=new Properties();p.setProperty("gameDir",game.toString());p.setProperty("workDir",game.toString());p.setProperty("controlToken","fixture-key94-".repeat(4));p.setProperty("instanceId",UUID.randomUUID().toString());p.setProperty("command.0",Path.of(System.getProperty("java.home"),"bin/java").toString());p.setProperty("command.1","-version");try(var out=Files.newBufferedWriter(path)){p.store(out,"");}return SupervisorConfig.load(new String[]{"--config="+path});
 }
 static void receipt(SupervisorConfig c,String record,String launch)throws Exception {
  // Explicit fixture, not evidence that Minecraft or premain ran. Native harness uses the real agent.
  Properties p=new Properties();p.setProperty("schema","94");p.setProperty("record",record);p.setProperty("launch",launch);p.setProperty("game",ColdOwner94.sha(c.gameDir().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));p.setProperty("pid","1");p.setProperty("created","1");StringBuilder b=new StringBuilder("GENESIS_ADMITTED_BEFORE_MAIN94\n");for(String k:new TreeSet<>(p.stringPropertyNames()))b.append(k).append('\0').append(p.getProperty(k)).append('\0');p.setProperty("mac",RecoveryAuth92.mac(c.token(),b.toString()));try(var out=Files.newBufferedWriter(c.gameDir().resolve("config/mared/dev/profile-admitted94.properties"))){p.store(out,"");}
 }
 public static void main(String[] args)throws Exception {
  try(var f=new RecoveryChecks91.F()){
   f.apply(true);SupervisorConfig c=config(f.game);String launch=UUID.randomUUID().toString();
   rejects(()->ColdOwner94.record(c,launch,f.token));rejects(()->ColdOwner94.verify(c,null));
   try(var owner=OwnerLease94.acquire(c.configPath())){
    ok(OwnerLease94.owned(c.configPath()));ok(!OwnerLease94.available(c.configPath()));ok(!SupervisorRecovery.lockFree(c.configPath()));rejects(()->OwnerLease94.acquire(c.configPath()));
    String record=ColdOwner94.record(c,launch,f.token);rejects(()->ColdOwner94.verify(c,null));
    try(var w=ProfileLease92.writer(f.game)){
     rejects(()->ColdOwner94.verify(c,null));receipt(c,record,launch);var proof=ColdOwner94.verify(c,null);ok(proof.launch().equals(launch));ok(proof.record().equals(record));
     rejects(()->ColdOwner94.verify(c,UUID.randomUUID().toString()));
     Path receipt=f.game.resolve("config/mared/dev/profile-admitted94.properties");byte[] original=Files.readAllBytes(receipt);Files.writeString(receipt,Files.readString(receipt).replace("pid=1","pid=2"));rejects(()->ColdOwner94.verify(c,null));Files.write(receipt,original);
     Path marker=f.game.resolve("config/mared/dev/cold-owner94.properties");byte[] signed=Files.readAllBytes(marker);Files.writeString(marker,Files.readString(marker)+"schema=94\n");rejects(()->ColdOwner94.verify(c,null));Files.write(marker,signed);
     byte[] cfg=Files.readAllBytes(c.configPath());Files.writeString(c.configPath(),Files.readString(c.configPath())+"\n# changed\n");rejects(()->ColdOwner94.verify(c,null));Files.write(c.configPath(),cfg);
     Files.write(c.configPath(),(Files.readString(c.configPath())+"controlToken=duplicate\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));rejects(()->SupervisorConfig.load(new String[]{"--config="+c.configPath()}));Files.write(c.configPath(),cfg);
     Files.write(c.configPath(),new byte[]{(byte)0xff});rejects(()->SupervisorConfig.load(new String[]{"--config="+c.configPath()}));Files.write(c.configPath(),cfg);
     Files.write(c.configPath(),new byte[65537]);rejects(()->SupervisorConfig.load(new String[]{"--config="+c.configPath()}));Files.write(c.configPath(),cfg);
     Path trial=f.data.resolve("trial.properties");byte[] before=Files.readAllBytes(trial);Files.writeString(trial,Files.readString(trial)+"unexpected=changed\n");rejects(()->ColdOwner94.verify(c,null));Files.write(trial,before);
     var view=f.d.recovery91();f.d.retryAfterExit92(view.revision());ok(ColdOwner94.verify(c,null).record().equals(record)); // Explicit retry changes only allowed volatile fields.
     f.d.restoreAfterExit92(f.d.recovery91().revision());ok(f.d.recovery91().phase().equals("RESTORED_REVIEW"));ok(ColdOwner94.verify(c,null).transaction().equals(f.token));
     f.d.decide91(f.d.recovery91().revision(),"ACK");rejects(()->ColdOwner94.verify(c,null));
    }
   }
   ok(!OwnerLease94.owned(c.configPath()));ok(OwnerLease94.available(c.configPath()));
  }
  try(var f=new RecoveryChecks91.F()){
   f.apply(true);var c=config(f.game);try(var owner=ColdOwner94.acquireCold(c);var w=ProfileLease92.writer(f.game)){
    String launch=UUID.randomUUID().toString(),record=ColdOwner94.record(c,launch,f.token);receipt(c,record,launch);Path file=f.game.resolve("config/mared/dev/cold-owner94.properties");String original=Files.readString(file);Files.writeString(file,original.replace("guard=93","guard=92"));rejects(()->ColdOwner94.verify(c,null));Files.writeString(file,original);Path adm=f.game.resolve("config/mared/dev/profile-admitted94.properties");Files.delete(adm);rejects(()->ColdOwner94.verify(c,null));receipt(c,UUID.randomUUID().toString(),launch);rejects(()->ColdOwner94.verify(c,null));
   }
  }
  System.out.println("Cold94 actual owner/profile/HMAC/JAR/config/journal checks passed: "+n+"; receipt signature here is an explicit fixture, not Minecraft/premain proof.");
 }
}
