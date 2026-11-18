package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.file.*;import java.util.*;import com.fixmer.mared.dev.storage.DevResumeStore;
/** Real crypto/files/lease checks in the test JVM. They are not Minecraft lifecycle evidence. */
public final class HandoffChecks96 {
 static int count;static void ok(boolean value){count++;if(!value)throw new AssertionError("check "+count);}
 interface IO{void run()throws Exception;}static void reject(String code,IO task)throws Exception{try{task.run();throw new AssertionError("accepted "+code);}catch(IOException expected){ok(expected.getMessage().contains(code));}}
 static class Fixture implements AutoCloseable {
  final Path root=Files.createTempDirectory("Genesis handoff96 "),configPath=root.resolve("supervisor.properties");final String token=UUID.randomUUID()+""+UUID.randomUUID(),instance=UUID.randomUUID().toString(),request=UUID.randomUUID().toString(),epoch=UUID.randomUUID().toString();final SupervisorConfig config;String id,resume;
  Fixture()throws Exception{Properties p=new Properties();p.setProperty("gameDir",root.toString());p.setProperty("workDir",root.toString());p.setProperty("controlToken",token);p.setProperty("instanceId",instance);p.setProperty("command.0",Path.of(System.getProperty("java.home"),"bin","java").toString());p.setProperty("command.1","-version");try(var out=Files.newBufferedWriter(configPath)){p.store(out,"");}config=SupervisorConfig.load(new String[]{"--config="+configPath});resume=DevResumeStore.prepare(root.resolve("config/mared/dev/resume.properties"),DevResumeStore.Target.local("world"),ProcessHandle.current().pid(),System.currentTimeMillis());}
  Handoff96.Proof start()throws Exception{id=Handoff96.prepare(configPath,root,instance,token,request,epoch,resume);return Handoff96.admit(config,id,ProcessHandle.current().pid());}
  Properties progress()throws Exception{Properties p=new Properties();try(var in=Files.newBufferedReader(Handoff96.folder(config,id).resolve("progress.properties"))){p.load(in);}p.remove("mac");return p;}
  void write(Properties p)throws Exception{Handoff96.write(Handoff96.folder(config,id).resolve("progress.properties"),p,token);}
  public void close()throws Exception{
   // Test-only reset, never an authority provided to a production controller.
   var field=Handoff96.class.getDeclaredField("active");field.setAccessible(true);Object writer=field.get(null);if(writer!=null){var lf=writer.getClass().getDeclaredField("lease");lf.setAccessible(true);((GameLease.Lease)lf.get(writer)).close();field.set(null,null);}
   try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
  }
 }
 public static void main(String[]args)throws Exception{
  try(var f=new Fixture()){
   var proof=f.start();ok(!Handoff96.exited(proof));ok(!Handoff96.cancelled(f.config,proof));reject("EXIT_EVIDENCE",()->Handoff96.confirmed(f.config,proof));
   reject("PROCESS",()->Handoff96.admit(f.config,f.id,ProcessHandle.current().pid()+1));
   Handoff96.cancel(f.request);ok(Handoff96.cancelled(f.config,proof));reject("SOURCE_LEASE",()->Handoff96.admit(f.config,f.id,ProcessHandle.current().pid()));
  }
  try(var f=new Fixture()){
   var proof=f.start();Handoff96.exitClaimed(f.request);Handoff96.world(f.epoch,"SAVED");Handoff96.world(f.epoch,"STOPPED");Handoff96.normalClosing();Properties p=f.progress();p.setProperty("hook","true");f.write(p);
   reject("SOURCE_",()->Handoff96.confirmed(f.config,proof));Handoff96.cancel(f.request);ok(!Handoff96.cancelled(f.config,proof));ok(!Handoff96.exited(proof));
   p=f.progress();p.setProperty("world","SAVED");f.write(p);reject("FINAL_SAVE",()->Handoff96.confirmed(f.config,proof));
   p.setProperty("world","STOPPED");p.setProperty("normal","false");f.write(p);reject("EXIT_EVIDENCE",()->Handoff96.confirmed(f.config,proof));
   p.setProperty("normal","true");p.setProperty("hook","false");f.write(p);reject("EXIT_EVIDENCE",()->Handoff96.confirmed(f.config,proof));
   p.setProperty("hook","true");p.setProperty("resume",UUID.randomUUID().toString());f.write(p);reject("PROGRESS_BINDING",()->Handoff96.confirmed(f.config,proof));
  }
  try(var f=new Fixture()){
   var proof=f.start();Handoff96.exitClaimed(f.request);Handoff96.world(UUID.randomUUID().toString(),"ACTIVE");Handoff96.world(f.epoch,"SAVED");Handoff96.world(f.epoch,"STOPPED");ok(f.progress().getProperty("world").equals("FAILED"));
   var p=f.progress();p.setProperty("hook","true");p.setProperty("normal","true");f.write(p);reject("FINAL_SAVE",()->Handoff96.confirmed(f.config,proof));
  }
  try(var f=new Fixture()){
   var proof=f.start();Path path=Handoff96.folder(f.config,f.id).resolve("progress.properties");byte[] original=Files.readAllBytes(path);Files.writeString(path,Files.readString(path).replace("exit=false","exit=true"));reject("SIGNATURE",()->Handoff96.cancelled(f.config,proof));Files.write(path,original);Files.write(path,new byte[4097]);reject("FILE",()->Handoff96.cancelled(f.config,proof));Files.write(path,original);Files.writeString(path,"\nschema=96\n",StandardOpenOption.APPEND);reject("RECOVERY92_WIRE",()->Handoff96.cancelled(f.config,proof));
  }
  try(var f=new Fixture()){
   var proof=f.start();DevResumeStore.prepare(f.root.resolve("config/mared/dev/resume.properties"),DevResumeStore.Target.local("other"),ProcessHandle.current().pid(),System.currentTimeMillis());reject("RESUME_BINDING",()->Handoff96.admit(f.config,f.id,ProcessHandle.current().pid()));reject("RESUME_BINDING",()->Handoff96.confirmed(f.config,proof));
  }
  try(var f=new Fixture()){
   var proof=f.start();Files.writeString(f.configPath,"\n# changed\n",StandardOpenOption.APPEND);reject("CONFIG",()->Handoff96.admit(f.config,f.id,ProcessHandle.current().pid()));reject("CONFIG",()->Handoff96.confirmed(f.config,proof));
  }
  try(var f=new Fixture()){
   f.start();Path report=Handoff96.folder(f.config,f.id).resolve("progress.properties");Files.delete(report);Files.createDirectory(report);
   try{Handoff96.exitClaimed(f.request);throw new AssertionError("accepted failed publication");}catch(UncheckedIOException expected){count++;}
   Handoff96.cancel(f.request);Files.delete(report);String next=Handoff96.prepare(f.configPath,f.root,f.instance,f.token,f.request,f.epoch,f.resume);ok(!next.equals(f.id));Handoff96.cancel(f.request);
  }
  try(var f=new Fixture()){
   RuntimeFiles runtime=new RuntimeFiles(f.root);var lease=GameLease.hold(runtime.directory.resolve("game.lease"),runtime.id,777,888);ok(lease!=null);
   Properties p=new Properties();p.setProperty("schema","1");p.setProperty("launch",runtime.id);p.setProperty("pid","777");p.setProperty("processStart","888");p.setProperty("sequence","1");p.setProperty("state","EXITED");p.setProperty("normalClose","true");p.setProperty("leaseToken",lease.token);Path file=runtime.directory.resolve("session.properties");try(var out=Files.newBufferedWriter(file)){p.store(out,"");}
   GameSession session=new GameSession(file,runtime.id,10000,10000,(pid,start)->GameSession.Life.DEAD);
   ok(session.poll()==GameSession.End.NONE);ok(!session.confirmedExit());ok(!session.normalExit91());lease.close();ok(session.poll()==GameSession.End.NORMAL);ok(session.confirmedExit());
  }
  var gate=new RestartAttempt95(0,100);ok(gate.beginSave(1));ok(gate.saved(2,()->{}));ok(gate.authorize(3));try{gate.claimExit(4,()->{throw new UncheckedIOException(new IOException("disk failure"));});throw new AssertionError();}catch(UncheckedIOException expected){ok(gate.phase().equals("AUTHORIZED"));}ok(gate.cancel());ok(!gate.claimExit(5,()->{throw new AssertionError("late IO");}));
  System.out.println("Handoff96 real signature/config/resume/lease/exit-gate checks passed: "+count+"; final lifecycle fields in this suite are explicit fixtures.");
 }
}
