package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.file.*;import java.util.*;
/** Real records/JARs; no Minecraft/Windows UI claim. */
public final class FailureChecks101 {
 static int count;static void ok(boolean b){count++;if(!b)throw new AssertionError("failure101 "+count);}interface IO{void run()throws Exception;}static void reject(IO run)throws Exception{count++;try{run.run();}catch(IOException expected){return;}throw new AssertionError("accepted "+count);}
 static class Issues {private final List<String> modLoadErrors;Issues(boolean error){modLoadErrors=error?List.of("error"):List.of();}}
 public static void main(String[] args)throws Exception{
  ok(StartupErrorProbe101.classify(null).isEmpty());ok(StartupErrorProbe101.classify(new Issues(true)).isEmpty());ok(StartupErrorProbe101.classifyIssues(new Issues(true),"modLoadErrors").equals("LOADER_ERROR"));ok(StartupErrorProbe101.classifyIssues(new Issues(false),"modLoadErrors").equals("ATTENTION"));ok(StartupErrorProbe101.classifyIssues(new Object(),"absent").equals("ATTENTION"));
  for(boolean worldFirst:new boolean[]{true,false})try(var f=new ModSetChecks99.Fixture()){
   f.initial();String token=f.stage(List.of(ModSetChecks99.input("alpha_project",f.jar("alpha","2",""))),Set.of());f.install(token);
   String launch=UUID.randomUUID().toString(),nonce="b".repeat(64);ok(f.d.bindStartup101(token,launch,nonce));var binding=f.d.coldBinding94();
   ok(!f.d.acknowledgeStartup101(token,launch,"c".repeat(64)));ok(!f.d.abortStartup101(token,launch,nonce));ok(f.d.acknowledgeStartup101(token,launch,nonce));ok(f.d.coldBinding94().identity().equals(binding.identity()));
   if(worldFirst){f.d.protectTrial91(token);ok(!f.d.abortStartup101(token,launch,nonce));ok(f.d.coldBinding94().identity().equals(binding.identity()));}
   else {ok(f.d.abortStartup101(token,launch,nonce));reject(()->f.d.protectTrial91(token));ok(!f.d.acknowledgeStartup101(token,launch,nonce));}
   ok(!Files.readString(f.data.resolve("deployments/trial.properties")).contains(nonce));f.restore();ok(ModJarMetadata.read(f.mods.resolve("mared-dev-alpha.jar")).version().equals("1"));
  }
  try(var f=new ModSetChecks99.Fixture()){
   Path signal=f.game.resolve("signal101.properties");String launch=UUID.randomUUID().toString(),key="k".repeat(64);StartupAbort101.write(signal,launch,key,"LOADER_ERROR");ok(StartupAbort101.read(signal,launch,key).equals("LOADER_ERROR"));ok(StartupAbort101.read(signal,launch,"wrong").isEmpty());ok(StartupAbort101.read(signal,UUID.randomUUID().toString(),key).isEmpty());Files.writeString(signal,"# changed\n",StandardOpenOption.APPEND);ok(StartupAbort101.read(signal,launch,key).equals("LOADER_ERROR"));Files.writeString(signal,"mac=BAD\n",StandardOpenOption.APPEND);ok(StartupAbort101.read(signal,launch,key).isEmpty());
   String sample="Crash at Mod.java:42 --accessToken SECRET_A Authorization: Bearer SECRET_B accessToken=SECRET_C password:SECRET_D";String redacted=FailureReport101.redact(sample);ok(redacted.contains("Mod.java:42"));for(String secret:List.of("SECRET_A","SECRET_B","SECRET_C","SECRET_D"))ok(!redacted.contains(secret));
   Path logs=Files.createDirectories(f.game.resolve("logs"));Files.writeString(logs.resolve("latest.log"),"BROKEN_GAME_LOG accessToken=PRIVATE_GAME_TOKEN");Path crash=Files.createDirectories(f.game.resolve("crash-reports"));Files.writeString(crash.resolve("crash.txt"),"ORIGINAL_MOD_CRASH");
   var capture=new FailureReport101.Capture();capture.add("ORIGINAL_CONSOLE_ERROR --accessToken PRIVATE_CONSOLE_TOKEN");String id=UUID.randomUUID().toString();FailureReport101.capture(f.game,id,UUID.randomUUID().toString(),"LOADER_ERROR",capture,0);FailureReport101.status(f.game,id,"RESTORED");Files.writeString(logs.resolve("latest.log"),"OLD_VERSION_NOW_RUNNING");
   var report=FailureReport101.read(f.game);ok(report.status().equals("RESTORED"));ok(report.id().equals(id));ok(!report.acknowledged());ok(report.text().contains("ORIGINAL_CONSOLE_ERROR"));ok(report.text().contains("BROKEN_GAME_LOG"));ok(report.text().contains("ORIGINAL_MOD_CRASH"));ok(!report.text().contains("OLD_VERSION_NOW_RUNNING"));ok(!report.text().contains("PRIVATE_GAME_TOKEN"));ok(!report.text().contains("PRIVATE_CONSOLE_TOKEN"));FailureReport101.acknowledge(f.game,id);ok(FailureReport101.read(f.game).acknowledged());
   String stale=UUID.randomUUID().toString();FailureReport101.capture(f.game,stale,"","PROCESS_EXIT",capture,System.currentTimeMillis()+10000);ok(!Files.exists(f.data.resolve("failures101").resolve(stale).resolve("game-latest.log")));
   Path foreign=Files.createDirectories(f.data.resolve("failures101/operator-notes"));Files.writeString(foreign.resolve("keep.txt"),"FOREIGN_DATA");for(int i=0;i<12;i++)FailureReport101.capture(f.game,UUID.randomUUID().toString(),"","PROCESS_EXIT",capture,System.currentTimeMillis()+10000);
   try(var dirs=Files.list(f.data.resolve("failures101"))){ok(dirs.filter(p->Files.isDirectory(p)&&!p.equals(foreign)).count()<=8);}ok(Files.readString(foreign.resolve("keep.txt")).equals("FOREIGN_DATA"));
   Files.writeString(f.data.resolve("failures101/latest.properties"),"schema=101\nid=../escape\n");reject(()->FailureReport101.read(f.game));
  }
  System.out.println("Failure101 gate/race/report/redaction/probe/retention checks passed: "+count+"; no Minecraft or Windows runtime.");
 }
}
