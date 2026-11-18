package com.fixmer.genesis.technology.server.process;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import com.fixmer.genesis.technology.server.packages.ProfileCoordinator;

/** Actual SAVE/STOP42 + native channel join. Diagnostic scope only, never install authority. */
final class ServerNativeStopBarrier implements AutoCloseable {
 private static final List<String> PINS=List.of("handoff.ticket","handoff.ready","handoff.approval","launch.properties","lifecycle.binding");
 private static final List<String> FINAL=List.of("lifecycle.status","status.properties","checkpoint.request","stop.current","shutdown.observed","stop.receipt");
 private static final String ZERO="0".repeat(64);
 private static final Set<String> KEYS;
 static{var keys=new HashSet<>(List.of("schema","scope","phase","revision","reason","run","profile","epoch","ticket","request","request.sequence","root.pid","root.start","owner.pid","owner.start","capsule.sha256","intent.sha256","context.sha256","request.sha256","guardian.sha256","native.receipt.sha256","world.save.verified","native.runtime.verified","install.authority","sha256"));for(String name:PINS)keys.add("pin."+name);for(String name:FINAL)keys.add("final."+name);KEYS=Set.copyOf(keys);}
 private final Path run,file;private final Process owner;private final ProfileCoordinator coordinator;private final ProfileCoordinator.LaunchPermit launch;
 private final ProfileCoordinator.StopReview review;private final ServerNativeLaunchCapsule.Frozen capsule;private final ServerFiles.Lease lease;
 private final ServerStopPreparation stop;private final ServerLifecycleView view;private Properties state,guardian;private boolean poisoned,closed;
 private final long requestStartedNanos=nanoNow();
 private ServerNativeStopBarrier(Path run,Process owner,ProfileCoordinator coordinator,ProfileCoordinator.LaunchPermit launch,ProfileCoordinator.StopReview review,ServerNativeLaunchCapsule.Frozen capsule,ServerFiles.Lease lease,ServerStopPreparation stop,ServerLifecycleView view,Properties state,Properties guardian){this.run=run;this.file=run.resolve("native.stop.barrier");this.owner=owner;this.coordinator=coordinator;this.launch=launch;this.review=review;this.capsule=capsule;this.lease=lease;this.stop=stop;this.view=view;this.state=state;this.guardian=guardian;}
 static ServerNativeStopBarrier arm(Path run,Process owner,ProfileCoordinator coordinator,ProfileCoordinator.LaunchPermit launch,ServerNativeLaunchCapsule.Frozen capsule,ServerNativeJavaProtocol.Event event,UUID request,long sequence)throws IOException {
  if(capsule.plan().kind()!=ServerNativeLaunchCapsule.DIAGNOSTIC)throw new IOException("NATIVE49_PRODUCTION_STOP_DISABLED");
  coordinator.validateLaunchFor(launch,Path.of(capsule.plan().cwd()));
  if(event.phase()!=ServerNativeJavaProtocol.Phase.RUNNING||event.pid()<1||event.forced()||event.stopSent()||event.ownerLost()||event.logFailed()||event.error()!=0||!owner.isAlive()||request==null||!ServerFiles.canonicalUuid(request.toString())||sequence<1)throw new IOException("NATIVE49_RUNNING_IDENTITY_REQUIRED");
  var root=ProcessHandle.of(event.pid()).filter(ProcessHandle::isAlive).orElseThrow(()->new IOException("NATIVE49_ROOT_IDENTITY_UNAVAILABLE"));String start=ServerFiles.start(root);
  Properties binding=ServerLifecycleChannel.binding(run),guardian=ServerFiles.read(run.resolve("status.properties"));
  Properties ticket=ServerFiles.read(run.resolve("handoff.ticket")),ready=ServerFiles.read(run.resolve("handoff.ready"));var handoff=ServerLaunchHandoff.load(run,ServerFiles.number(ticket,"parent.pid",0),ticket.getProperty("parent.start",""));long guardianPid=ServerFiles.number(ready,"guardian.pid",0);handoff.validateReady(ready,guardianPid);handoff.validateStatus(guardian,guardianPid);if(!guardian.getProperty("guardian.start","").equals(ready.getProperty("guardian.start","")))throw new IOException("NATIVE49_GUARDIAN_IDENTITY_CHANGED");
  if(!binding.getProperty("profile").equals(coordinator.snapshot().profile().toString())||!binding.getProperty("run").equals(launch.run().toString())||ServerFiles.number(binding,"epoch",0)!=launch.epoch())throw new IOException("NATIVE49_PROFILE_BINDING_MISMATCH");
  guardian.setProperty("child.pid",Long.toString(event.pid()));guardian.setProperty("child.start",start);guardian.setProperty("phase","RUNNING");guardian.setProperty("forceSent","false");guardian.setProperty("ownershipUncertain","false");guardian.setProperty("exitCode","0");
  var view=new ServerLifecycleView(run);view.update(run,guardian,System.nanoTime());if(!view.ready(true,System.nanoTime())||sequence<=view.observedRequestSequence())throw new IOException("NATIVE49_FRESH_READY_REQUIRED");
  var lease=new ServerFiles.Lease(run.resolve("native-stop.lock"));
  try{
   if(Files.exists(run.resolve("native.stop.barrier"),LinkOption.NOFOLLOW_LINKS))throw new IOException("NATIVE49_STOP_ALREADY_USED");
   Properties p=new Properties();p.setProperty("schema","49");p.setProperty("scope","DIAGNOSTIC_NO_GAME");p.setProperty("phase","ARMING");p.setProperty("revision","1");p.setProperty("reason","SAVE_STOP_MAY_BE_REQUESTED");
   for(String name:List.of("run","profile","epoch","ticket"))p.setProperty(name,binding.getProperty(name));p.setProperty("request",request.toString());p.setProperty("request.sequence",Long.toString(sequence));p.setProperty("root.pid",Long.toString(event.pid()));p.setProperty("root.start",start);p.setProperty("owner.pid",Long.toString(owner.pid()));p.setProperty("owner.start",ServerFiles.start(owner.toHandle()));p.setProperty("capsule.sha256",capsule.sha());p.setProperty("intent.sha256",hash(run.resolve("native.intent")));p.setProperty("request.sha256",ZERO);p.setProperty("guardian.sha256",ZERO);p.setProperty("native.receipt.sha256",ZERO);
   for(String name:List.of("world.save.verified","native.runtime.verified","install.authority"))p.setProperty(name,"false");for(String name:PINS)p.setProperty("pin."+name,hash(run.resolve(name)));for(String name:FINAL)p.setProperty("final."+name,ZERO);
   p.setProperty("context.sha256",digest(p));p.setProperty("sha256",digest(p));validate(p);
   var review=coordinator.reserveDiagnosticStopReview(launch,request,p.getProperty("context.sha256"));
   // Durable fence precedes mailbox publication; a crash can never imply a completed stop.
   ServerFiles.write(run.resolve("native.stop.barrier"),p);ServerFiles.write(run.resolve("status.properties"),guardian);
   var stop=ServerStopPreparation.create(run,request,sequence,guardian);
   var result=new ServerNativeStopBarrier(run,owner,coordinator,launch,review,capsule,lease,stop,view,p,guardian);
   try{
    result.checkPins();coordinator.validateLaunchFor(launch,Path.of(capsule.plan().cwd()));if(!ServerFiles.sameProcess(event.pid(),start))throw new IOException("NATIVE49_ROOT_CHANGED_BEFORE_REQUEST");
    ServerLifecycleChannel.request(run,sequence,request,System.currentTimeMillis(),ServerLifecycleState.Operation.SAVE_AND_STOP);
    var fields=new Properties();fields.setProperty("request.sha256",hash(run.resolve("checkpoint.request")));fields.setProperty("guardian.sha256",hash(run.resolve("status.properties")));result.save("REQUESTED","SAVE_STOP_REQUEST_PUBLISHED",fields);return result;
   }catch(IOException|RuntimeException bad){result.fail("REQUEST_PUBLICATION_UNKNOWN");throw bad;}
  }catch(IOException|RuntimeException bad){try{coordinator.requireProcessReview(launch.run());}catch(IOException extra){bad.addSuppressed(extra);}lease.close();throw bad;}
 }
 synchronized boolean observe(ServerNativeJavaProtocol.Event event)throws IOException {
  usable();if(state.getProperty("phase").equals("DRAINED")){
   try{checkPins();checkRequest();coordinator.validateLaunchFor(launch,Path.of(capsule.plan().cwd()));if(event.phase()!=ServerNativeJavaProtocol.Phase.EMPTY||event.pid()!=pid()||event.active()!=0||event.exitCode()!=0||event.forced()||event.ownerLost()||event.logFailed()||event.error()!=0)throw new IOException("NATIVE49_DRAIN_REGRESSED");for(String name:FINAL)if(!hash(finalPath(name)).equals(state.getProperty("final."+name)))throw new IOException("NATIVE49_FINAL_INPUT_CHANGED");return true;}
   catch(IOException|RuntimeException bad){fail("DRAIN_RECHECK_UNKNOWN");throw bad;}
  }
  if(!Set.of("REQUESTED","ACKNOWLEDGED").contains(state.getProperty("phase")))throw new IOException("NATIVE49_OBSERVATION_NOT_REUSABLE");
  try{
   checkPins();coordinator.validateLaunchFor(launch,Path.of(capsule.plan().cwd()));checkRequest();
   if(!hash(run.resolve("status.properties")).equals(state.getProperty("guardian.sha256")))throw new IOException("NATIVE49_GUARDIAN_CHANGED");
   if(event.pid()!=pid()||event.forced()||event.ownerLost()||event.logFailed()||event.error()!=0||event.phase()==ServerNativeJavaProtocol.Phase.UNKNOWN)throw new IOException("NATIVE49_NATIVE_OUTCOME_UNKNOWN");
   view.update(run,guardian,System.nanoTime());var report=view.describe(ServerFiles.sameProcess(pid(),state.getProperty("root.start")),System.nanoTime());
   boolean matching=request().toString().equals(report.get("checkpointReportedRequest"));
   if(!report.get("lifecycleError").equals("")||matching&&!report.get("checkpointError").equals("")||Set.of("FAILED","CANCELLED","EXPIRED").contains(view.outcome(request())))throw new IOException("NATIVE49_SAVE_STOP_FAILED");
   boolean ack=view.stopEvidence(request(),sequence());
   if(!ack&&(currentMillis()>ServerFiles.number(ServerFiles.read(run.resolve("checkpoint.request")),"expiresAt",0)||nanoNow()-requestStartedNanos>=30_000_000_000L))throw new IOException("NATIVE49_SAVE_STOP_DEADLINE");
   if(ack){stop.acknowledged();stop.signalShutdownObserved();if(state.getProperty("phase").equals("REQUESTED"))save("ACKNOWLEDGED","SAVE_STOP_ACK_OBSERVED",null);}
   if(event.phase()!=ServerNativeJavaProtocol.Phase.EMPTY)return false;
   if(!ack||event.active()!=0||event.exitCode()!=0||ServerFiles.sameProcess(pid(),state.getProperty("root.start")))throw new IOException("NATIVE49_EMPTY_WITHOUT_BOUND_SAVE_STOP");
   guardian.setProperty("phase","EXITED");ServerFiles.write(run.resolve("status.properties"),guardian);stop.exited(guardian);ServerStopPreparation.verifiedComplete(run,request(),sequence(),pid(),state.getProperty("root.start"));
   var fields=new Properties();fields.setProperty("guardian.sha256",hash(run.resolve("status.properties")));for(String name:FINAL)fields.setProperty("final."+name,hash(finalPath(name)));save("DRAINED","SAVE_STOP_AND_NATIVE_EMPTY_OBSERVED",fields);return true;
  }catch(IOException|RuntimeException bad){fail("STOP_OBSERVATION_UNKNOWN");throw bad;}
 }
 synchronized void complete(ServerNativeJavaCli.ExitReceipt receipt)throws IOException {
  usable();if(!state.getProperty("phase").equals("DRAINED"))throw new IOException("NATIVE49_DRAIN_REQUIRED");
  try{
   checkPins();checkRequest();coordinator.validateLaunchFor(launch,Path.of(capsule.plan().cwd()));
   if(owner.isAlive()||owner.pid()!=ServerFiles.number(state,"owner.pid",0)||owner.exitValue()!=0||receipt.pid()!=pid())throw new IOException("NATIVE49_OWNER_EXIT_REQUIRED");
   for(String name:FINAL)if(!hash(finalPath(name)).equals(state.getProperty("final."+name)))throw new IOException("NATIVE49_FINAL_INPUT_CHANGED");
   ServerStopPreparation.verifiedComplete(run,request(),sequence(),pid(),state.getProperty("root.start"));var checked=ServerNativeJavaCli.checkedReceipt(capsule,false,false,receipt.stopSent(),pid());if(!checked.equals(receipt))throw new IOException("NATIVE49_RECEIPT_CHANGED");
   var fields=new Properties();fields.setProperty("native.receipt.sha256",receipt.sha256());save("DRAINED","OWNER_EXIT_AND_RECEIPT_OBSERVED",fields);
   coordinator.completeDiagnosticStopReview(review,request(),state.getProperty("context.sha256"),state.getProperty("sha256"));save("REVIEW_COMPLETE","DIAGNOSTIC_STOP_REVIEW_ONLY",null);
  }catch(IOException|RuntimeException bad){fail("STOP_COMPLETION_UNKNOWN");throw bad;}
 }
 synchronized void cancel()throws IOException {usable();if(!state.getProperty("phase").equals("REQUESTED"))throw new IOException("NATIVE49_CANCEL_TOO_LATE");try{ServerLifecycleChannel.cancel(run,request());}finally{fail("CANCELLATION_REQUESTED_OUTCOME_UNCONFIRMED");}}
 private void checkPins()throws IOException {for(String name:PINS)if(!hash(run.resolve(name)).equals(state.getProperty("pin."+name)))throw new IOException("NATIVE49_CONTEXT_CHANGED");for(String name:List.of("native.cancel","stop.request","force.request"))if(Files.exists(run.resolve(name),LinkOption.NOFOLLOW_LINKS))throw new IOException("NATIVE49_CANCELLED_OR_FORCED");}
 private void checkRequest()throws IOException {
  if(!hash(run.resolve("checkpoint.request")).equals(state.getProperty("request.sha256")))throw new IOException("NATIVE49_REQUEST_CHANGED");
  Path cancel=run.resolve("checkpoint.cancel");if(Files.exists(cancel,LinkOption.NOFOLLOW_LINKS)){Properties p=ServerFiles.read(cancel);var keys=new HashSet<>(ServerLifecycleChannel.BINDING_KEYS);keys.add("request");if(!p.stringPropertyNames().equals(keys)||!ServerFiles.canonicalUuid(p.getProperty("request","")))throw new IOException("NATIVE49_INVALID_CANCEL");ServerLifecycleChannel.verifyBinding(p,ServerLifecycleChannel.binding(run));if(p.getProperty("request").equals(request().toString()))throw new IOException("NATIVE49_REQUEST_CANCELLED");}
 }
 private UUID request(){return UUID.fromString(state.getProperty("request"));}private long sequence()throws IOException{return ServerFiles.number(state,"request.sequence",0);}private long pid()throws IOException{return ServerFiles.number(state,"root.pid",0);}
 private Path finalPath(String name){return name.equals("stop.receipt")?run.resolve("stop-preparations").resolve(request()+".properties"):run.resolve(name);}
 private void usable()throws IOException {if(closed||poisoned||!state.equals(read(file)))throw new IOException("NATIVE49_BARRIER_UNAVAILABLE_OR_CHANGED");}
 private void save(String phase,String reason,Properties fields)throws IOException {
  try{usable();var next=ServerLifecycleChannel.copy(state);next.setProperty("phase",phase);next.setProperty("reason",reason);next.setProperty("revision",Long.toString(Math.addExact(ServerFiles.number(state,"revision",0),1)));if(fields!=null)next.putAll(fields);next.setProperty("sha256",digest(next));validate(next);ServerFiles.write(file,next);state=next;}catch(IOException|RuntimeException bad){poisoned=true;review();throw bad;}
 }
 synchronized void fail(String reason){if(!closed&&!poisoned&&!state.getProperty("phase").equals("REVIEW_COMPLETE"))try{save("UNKNOWN",reason,null);}catch(IOException|RuntimeException ignored){}review();}
 private void review(){try{if(!coordinator.requiresReview())coordinator.requireProcessReview(launch.run());}catch(IOException ignored){}}
 synchronized Map<String,Object> describe(){var out=describe(state);if(poisoned)out.put("state","UNKNOWN_IO");out.put("historical",false);return out;}
 static Map<String,Object> inspect(Path run)throws IOException {Properties p=read(run.resolve("native.stop.barrier"));if(!run.getFileName().toString().equals(p.getProperty("run")))throw new IOException("NATIVE49_HISTORY_RUN_MISMATCH");var out=describe(p);out.put("historical",true);return out;}
 private static Map<String,Object> describe(Properties p){var out=new LinkedHashMap<String,Object>();out.put("schema",49);for(String key:List.of("run","profile","epoch","request","request.sequence","root.pid","owner.pid","reason","revision"))out.put(key,p.getProperty(key));out.put("state",p.getProperty("phase"));out.put("scope","DIAGNOSTIC_NO_GAME");out.put("reviewComplete",p.getProperty("phase").equals("REVIEW_COMPLETE"));out.put("worldSaveVerified",false);out.put("nativeRuntimeVerified",false);out.put("productionLaunchAllowed",false);out.put("installAuthority",false);return out;}
 private static Properties read(Path path)throws IOException {var p=ServerFiles.read(path);validate(p);return p;}
 private static void validate(Properties p)throws IOException {
  if(!p.stringPropertyNames().equals(KEYS)||!p.getProperty("schema").equals("49")||!p.getProperty("scope").equals("DIAGNOSTIC_NO_GAME")||!p.getProperty("sha256").equals(digest(p))||!Set.of("ARMING","REQUESTED","ACKNOWLEDGED","DRAINED","REVIEW_COMPLETE","UNKNOWN").contains(p.getProperty("phase"))||!p.getProperty("reason").matches("[A-Z_]{1,96}"))throw new IOException("NATIVE49_BARRIER_CORRUPT");
  for(String key:List.of("run","profile","ticket","request"))if(!ServerFiles.canonicalUuid(p.getProperty(key)))throw new IOException("NATIVE49_BARRIER_ID");for(String key:List.of("epoch","request.sequence","root.pid","owner.pid","revision"))if(ServerFiles.number(p,key,0)<1)throw new IOException("NATIVE49_BARRIER_NUMBER");
  for(String key:KEYS)if(key.endsWith(".sha256")||key.startsWith("pin.")||key.startsWith("final."))if(!ServerNativeEvidence.hash(p.getProperty(key)))throw new IOException("NATIVE49_BARRIER_HASH");
  for(String key:List.of("world.save.verified","native.runtime.verified","install.authority"))if(!p.getProperty(key).equals("false"))throw new IOException("NATIVE49_FALSE_AUTHORITY");
  if(!Set.of("ARMING","UNKNOWN").contains(p.getProperty("phase"))&&(p.getProperty("request.sha256").equals(ZERO)||p.getProperty("guardian.sha256").equals(ZERO)))throw new IOException("NATIVE49_FALSE_REQUEST");
  if(Set.of("DRAINED","REVIEW_COMPLETE").contains(p.getProperty("phase")))for(String name:FINAL)if(p.getProperty("final."+name).equals(ZERO))throw new IOException("NATIVE49_FALSE_DRAIN");
  if(p.getProperty("phase").equals("REVIEW_COMPLETE")&&p.getProperty("native.receipt.sha256").equals(ZERO))throw new IOException("NATIVE49_FALSE_COMPLETION");
  try{for(String key:List.of("root.start","owner.start"))if(!Instant.parse(p.getProperty(key)).toString().equals(p.getProperty(key)))throw new IOException("NATIVE49_BARRIER_START");}catch(java.time.DateTimeException bad){throw new IOException("NATIVE49_BARRIER_START",bad);}
 }
 private static String hash(Path path)throws IOException{return HexFormat.of().formatHex(ServerNativeLaunchCapsule.hash(path,ServerFiles.MAX_PROPERTIES));}
 private static long currentMillis(){return System.currentTimeMillis();}
 private static long nanoNow(){return System.nanoTime();}
 private static String digest(Properties p){var text=new StringBuilder();for(String key:new TreeSet<>(p.stringPropertyNames()))if(!key.equals("sha256")){String value=p.getProperty(key);text.append(key.length()).append(':').append(key).append(value.length()).append(':').append(value).append('\n');}return HexFormat.of().formatHex(ServerNativeLaunchCapsule.digest(text.toString().getBytes(StandardCharsets.UTF_8)));}
 @Override public synchronized void close()throws IOException {if(closed)return;if(owner.isAlive())throw new IOException("NATIVE49_OWNER_ALIVE_RETAIN_STOP_LEASE");if(!state.getProperty("phase").equals("REVIEW_COMPLETE"))fail("STOP_BARRIER_CLOSED_INCOMPLETE");lease.close();closed=true;}
}
