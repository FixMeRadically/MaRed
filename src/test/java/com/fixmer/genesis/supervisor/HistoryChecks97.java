package com.fixmer.genesis.supervisor;
import java.io.*;import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Actual bounded files/metrics/formatting and queued publication. No game UI/runtime assertions. */
public final class HistoryChecks97 {
 static int count;static void ok(boolean value){count++;if(!value)throw new AssertionError("check "+count);}static String id(){return UUID.randomUUID().toString();}
 interface IO{void run()throws Exception;}static void rejects(IO run)throws Exception{try{run.run();throw new AssertionError("accepted unsafe report");}catch(IOException expected){count++;}}
 static class Fixture implements AutoCloseable{
  final Path root=Files.createTempDirectory("Genesis history97 "),dir=root.resolve("config/mared/dev/history97");
  Fixture()throws Exception{}
  public void close()throws Exception{try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
  void prop(Path file,String text)throws Exception{Files.createDirectories(file.getParent());Files.writeString(file,text);}
 }
 static String legacy(String request){return "schema=95\nrequest="+request+"\nstate=DONE\nreason=normal_exit_and_save_confirmed\ncontrolToken=PRIVATE_TOKEN\ncommand=PRIVATE_COMMAND\n";}
 static class QueueExecutor extends AbstractExecutorService{
  final Queue<Runnable> queue=new ConcurrentLinkedQueue<>();boolean stopped;
  public void execute(Runnable run){if(stopped)throw new RejectedExecutionException();queue.add(run);}public void shutdown(){stopped=true;}public List<Runnable> shutdownNow(){stopped=true;return List.of();}public boolean isShutdown(){return stopped;}public boolean isTerminated(){return stopped;}public boolean awaitTermination(long t,TimeUnit u){return stopped;}
  void next(){var task=queue.remove();task.run();}
 }
 public static void main(String[]args)throws Exception{
  HistoryLinkChecks100.verify();
  try(var f=new Fixture()){
   ok(RestartHistory97.read(f.root).attempts().isEmpty());ok(!Files.exists(f.root.resolve("config")));
   String a=id();RestartHistory97.recordAttempt(f.root,"restart",a,"PREPARED","");var first=RestartHistory97.read(f.root).attempts().get(0);ok(first.phase().equals("PREPARED"));
   RestartHistory97.recordAttempt(f.root,"restart",a,"COMMITTED","");var next=RestartHistory97.read(f.root).attempts().get(0);ok(next.createdMs()==first.createdMs());ok(next.updatedMs()>=first.updatedMs());ok(next.phase().equals("COMMITTED"));
   RestartHistory97.recordAttempt(f.root,"restart",a,"HOLD","PRIVATE_EXCEPTION /secret/path");var held=RestartHistory97.read(f.root).attempts().get(0);ok(held.reason().equals("unknown"));
   f.prop(f.root.resolve("config/mared/dev/restart95-last.properties"),legacy(a));ok(RestartHistory97.read(f.root).attempts().size()==1);ok(RestartHistory97.read(f.root).attempts().get(0).phase().equals("HOLD"));
   String old=id();f.prop(f.root.resolve("config/mared/dev/restart95-last.properties"),legacy(old));var snap=RestartHistory97.read(f.root);ok(snap.attempts().size()==2);ok(snap.attempts().stream().anyMatch(RestartHistory97.Attempt::legacy));
   String cycle=id();RestartHistory97.recordCycle(f.root,cycle,"restart","direct_cache",System.currentTimeMillis(),Map.of("total",15000L,"mod_loaded",8000L,"resources_ready",12000L,"world_loading",12050L,"world_ready",14100L,"prepare",300L));
   snap=RestartHistory97.read(f.root);ok(snap.cycles().size()==1);ok(snap.cycles().get(0).readiness().equals("world"));var lines=HistoryPresentation97.lines(2,snap,ZoneOffset.UTC,true);ok(lines.stream().anyMatch(l->l.key().endsWith("timing.resources")&&l.args().equals(List.of("4.00"))));ok(lines.stream().anyMatch(l->l.key().endsWith("timing.world")&&l.args().equals(List.of("2.05"))));
   String report=HistoryPresentation97.clipboard(snap,(key,a2)->key+Arrays.toString(a2));ok(!report.contains("PRIVATE"));ok(!report.contains(f.root.toString()));ok(report.contains(old));ok(report.contains(cycle));
   String partial=id();RestartHistory97.recordCycle(f.root,partial,"initial","gradle",System.currentTimeMillis(),Map.of("total",10L,"spawn",8L));var partialCycle=RestartHistory97.read(f.root).cycles().stream().filter(c->c.id().equals(partial)).findFirst().orElseThrow();ok(partialCycle.readiness().equals("unconfirmed"));
   lines=HistoryPresentation97.lines(2,new RestartHistory97.Snapshot(List.of(),List.of(partialCycle),0,false),ZoneOffset.UTC,false);ok(lines.stream().noneMatch(l->l.key().endsWith("timing.total")));
   String invalid=id();RestartHistory97.recordCycle(f.root,invalid,"restart","gradle",System.currentTimeMillis(),Map.of("spawn",-1L));ok(!Files.exists(f.dir.resolve("cycle-"+invalid+".properties")));
   String bad=id();f.prop(f.dir.resolve("restart-"+bad+".properties"),"schema=97\nid="+bad+"\nkind=restart\nphase=HOLD\ncreated_ms=1\nupdated_ms=1\nschema=97\n");ok(RestartHistory97.read(f.root).unreadable()==1);
   Files.write(f.dir.resolve("restart-"+bad+".properties"),new byte[8193]);ok(RestartHistory97.read(f.root).unreadable()==1);
  }
  try(var f=new Fixture()){
   f.prop(f.dir.resolve("keep-me.txt"),"FOREIGN_DATA");f.prop(f.root.resolve("saves/world/playerdata.dat"),"WORLD_DATA");
   for(int i=0;i<60;i++)RestartHistory97.recordAttempt(f.root,"restart",id(),"DONE","normal_exit_and_save_confirmed");
   try(var stream=Files.list(f.dir)){ok(stream.filter(p->p.getFileName().toString().endsWith(".properties")).count()==48);}
   ok(RestartHistory97.read(f.root).attempts().size()==24);ok(Files.readString(f.dir.resolve("keep-me.txt")).equals("FOREIGN_DATA"));ok(Files.readString(f.root.resolve("saves/world/playerdata.dat")).equals("WORLD_DATA"));
  }
  try(var f=new Fixture();var other=new Fixture()){
   f.prop(f.dir.resolve("keep.txt"),"keep");other.prop(other.root.resolve("secret.txt"),"PRIVATE_TOKEN");String a=id();Path link=f.dir.resolve("restart-"+a+".properties");
   if(HistoryLinkChecks100.create(link,other.root.resolve("secret.txt"),"file")){
    ok(RestartHistory97.read(f.root).unreadable()==1);RestartHistory97.recordAttempt(f.root,"restart",a,"DONE","");ok(Files.readString(other.root.resolve("secret.txt")).equals("PRIVATE_TOKEN"));
   }
   Path profileLink=f.root.resolve("linked");
   if(HistoryLinkChecks100.create(profileLink,other.root,"directory"))rejects(()->RestartHistory97.read(profileLink));

  }
  try(var f=new Fixture()){
   Files.createDirectories(f.dir);for(int i=0;i<161;i++)f.prop(f.dir.resolve("foreign-"+i),"user data");ok(RestartHistory97.read(f.root).limited());String a=id();RestartHistory97.recordAttempt(f.root,"restart",a,"DONE","");ok(!Files.exists(f.dir.resolve("restart-"+a+".properties")));try(var stream=Files.list(f.dir)){ok(stream.count()==161);}
  }
  try(var f=new Fixture();var log=new SupervisorLog(f.root.resolve("supervisor.log"))){
   RestartMetrics metrics=new RestartMetrics(f.root);String launch=id();metrics.event(launch,"launch_direct_cache",0,log);metrics.event(launch,"mod_loaded",8000,log);metrics.event(launch,"resources_ready",12000,log);metrics.event(launch,"world_loading",12100,log);metrics.event(launch,"world_ready",14000,log);
   var snapshot=RestartHistory97.read(f.root);ok(snapshot.cycles().size()==1);ok(snapshot.cycles().get(0).mode().equals("direct_cache"));ok(snapshot.cycles().get(0).durations().get("world_ready")==14000L);ok(Files.isRegularFile(f.root.resolve("config/mared/dev/logs/timings.jsonl")));
   metrics.begin("restart");metrics.event(launch,"spawn",1,log);ok(RestartHistory97.read(f.root).cycles().size()==2);
  }
  try(var f=new Fixture()){
   QueueExecutor io=new QueueExecutor(),publish=new QueueExecutor();AtomicBoolean visible=new AtomicBoolean(true);var loader=new HistoryLoad97(f.root,publish,visible::get,io);
   ok(loader.refresh());ok(loader.state().equals("READING"));ok(!loader.refresh());io.next();ok(loader.state().equals("READING"));loader.close();publish.next();ok(loader.state().equals("CLOSED"));ok(!loader.refresh());
   loader=new HistoryLoad97(f.root,publish,visible::get,io);ok(loader.refresh());io.next();visible.set(false);publish.next();ok(loader.state().equals("IDLE"));visible.set(true);ok(loader.refresh());io.next();publish.next();ok(loader.state().equals("READY"));ok(loader.snapshot().attempts().isEmpty());loader.close();
   var brokenVisibility=new HistoryLoad97(f.root,publish,()->{throw new IllegalStateException("visibility fixture");},io);
   ok(brokenVisibility.refresh());io.next();publish.next();ok(brokenVisibility.state().equals("ERROR"));
   ok(brokenVisibility.refresh());io.next();brokenVisibility.close();publish.next();ok(brokenVisibility.state().equals("CLOSED"));
   QueueExecutor denied=new QueueExecutor();denied.shutdown();var busy=new HistoryLoad97(f.root,publish,visible::get,denied);ok(!busy.refresh());ok(busy.state().equals("ERROR"));
  }
  System.out.println("History97 actual files/metrics/privacy/retention/queued-publication checks passed: "+count+"; no Minecraft GUI/runtime.");
 }
}
