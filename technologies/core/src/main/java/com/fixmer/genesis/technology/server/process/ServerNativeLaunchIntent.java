package com.fixmer.genesis.technology.server.process;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import com.fixmer.genesis.technology.server.packages.ProfileCoordinator;
/** One-use diagnostic binding. Never grants production launch or installation rights. */
final class ServerNativeLaunchIntent implements AutoCloseable {
 private final ProfileCoordinator coordinator;private final ProfileCoordinator.LaunchPermit permit;
 private final Path run,helper,file;private final ServerNativeLaunchCapsule.Frozen capsule;private final ServerNativeEvidence.Checked evidence;
 private final ServerFiles.Lease lease;private Properties state;private Process nativeOwner;private boolean poisoned,closed;
 private static final List<String> CONTEXT_FILES=List.of("handoff.ticket","handoff.ready","handoff.approval","launch.properties");
 private static final Set<String> KEYS;
 static {var keys=new HashSet<>(List.of("schema","mode","profile","run","epoch","ticket","challenge","parent.pid","parent.start","guardian.pid","guardian.start","nonce","capsule.sha256","helper.sha256","evidence.sha256","runtime.source.sha256","runtime.java.sha256","state","revision","owner.pid","owner.start","owner.exit","reason","native.runtime.verified","install.authority","production.launch.allowed","sha256"));for(String f:CONTEXT_FILES)keys.add("context."+f+".sha256");KEYS=Set.copyOf(keys);}
 private ServerNativeLaunchIntent(ProfileCoordinator coordinator,ProfileCoordinator.LaunchPermit permit,Path run,Path helper,ServerNativeLaunchCapsule.Frozen capsule,ServerNativeEvidence.Checked evidence,ServerFiles.Lease lease,Properties state){this.coordinator=coordinator;this.permit=permit;this.run=run;this.helper=helper;this.file=run.resolve("native.intent");this.capsule=capsule;this.evidence=evidence;this.lease=lease;this.state=state;}
 static ServerNativeLaunchIntent prepare(ProfileCoordinator owner,ProfileCoordinator.LaunchPermit permit,Path run,Path helper,ServerNativeLaunchCapsule.Frozen frozen,ServerNativeEvidence.Checked evidence)throws IOException {
  run=run.toAbsolutePath().normalize();helper=helper.toAbsolutePath().normalize();owner.validateLaunchFor(permit,ServerProcessConfig.load(run.resolve("launch.properties")).workDir());var snapshot=owner.snapshot();
  if(snapshot.phase()!=ProfileCoordinator.Phase.PROCESS_RESERVED||frozen.plan().kind()!=ServerNativeLaunchCapsule.DIAGNOSTIC)throw new IOException("NATIVE_INTENT_DIAGNOSTIC_RESERVATION_REQUIRED");
  Properties context=context(run,frozen,evidence,helper);
  if(!context.getProperty("profile").equals(snapshot.profile().toString())||!context.getProperty("run").equals(permit.run().toString())||ServerFiles.number(context,"epoch",0)!=permit.epoch())throw new IOException("NATIVE_INTENT_PROFILE_MISMATCH");
  ServerFiles.Lease lease=new ServerFiles.Lease(run.resolve("native-intent.lock"));
  try{
   Path target=run.resolve("native.intent");if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("NATIVE_INTENT_ALREADY_EXISTS");
   Properties second=context(run,frozen,evidence,helper);if(!second.equals(context))throw new IOException("NATIVE_INTENT_CONTEXT_CHANGED");owner.validateLaunch(permit);
   Properties state=new Properties();state.setProperty("schema","47");state.setProperty("mode","DIAGNOSTIC_BINDING_ONLY");
   for(String key:List.of("profile","run","epoch","ticket","challenge","parent.pid","parent.start","guardian.pid","guardian.start"))state.setProperty(key,context.getProperty(key));
   state.setProperty("nonce",frozen.plan().binding().nonce());state.setProperty("capsule.sha256",frozen.sha());state.setProperty("helper.sha256",evidence.helperSha());state.setProperty("evidence.sha256",evidence.sha());state.setProperty("runtime.source.sha256",evidence.runtimeSourceSha());state.setProperty("runtime.java.sha256",evidence.runtimeJavaSha());
   for(String name:CONTEXT_FILES)state.setProperty("context."+name+".sha256",fileHash(run.resolve(name)));
   state.setProperty("state","PREPARED");state.setProperty("revision","1");state.setProperty("owner.pid","0");state.setProperty("owner.start","");state.setProperty("owner.exit","unobserved");state.setProperty("reason","BINDING_PREPARED");state.setProperty("native.runtime.verified","false");state.setProperty("install.authority","false");state.setProperty("production.launch.allowed","false");state.setProperty("sha256",digest(state));validate(state);
   ServerFiles.write(target,state);return new ServerNativeLaunchIntent(owner,permit,run,helper,frozen,evidence,lease,state);
  }catch(IOException|RuntimeException failure){try{owner.requireProcessReview(permit.run());}catch(IOException extra){failure.addSuppressed(extra);}lease.close();throw failure;}
 }
 private static Properties context(Path run,ServerNativeLaunchCapsule.Frozen frozen,ServerNativeEvidence.Checked evidence,Path helper)throws IOException {
  if(frozen.plan().kind()!=ServerNativeLaunchCapsule.DIAGNOSTIC)throw new IOException("NATIVE_SERVER_LAUNCH_DISABLED");
  var verified=ServerNativeLaunchCapsule.verify(frozen.file(),frozen.sha());if(!Arrays.equals(ServerNativeLaunchCapsule.encode(verified.plan()),ServerNativeLaunchCapsule.encode(frozen.plan())))throw new IOException("NATIVE_INTENT_CAPSULE_MISMATCH");
  var plan=verified.plan();Path privateRoot=verified.file().getParent();
  if(!Path.of(plan.cwd()).equals(privateRoot))throw new IOException("NATIVE_INTENT_FIXED_PROBE_REQUIRED");ServerNativeLaunchCapsule.verifyDiagnosticShape(plan,run);
  if(!ServerNativeEvidence.read(evidence.directory(),evidence.sha(),helper).equals(evidence))throw new IOException("NATIVE_INTENT_EVIDENCE_MISMATCH");
  var config=ServerProcessConfig.load(run.resolve("launch.properties"));if(!run.getParent().equals(config.stateDir().resolve("runs"))||!config.javaExecutable().equals(Path.of(verified.plan().module()))||!config.workDir().equals(Path.of(verified.plan().cwd()))||!config.arguments().equals(verified.plan().arguments()))throw new IOException("NATIVE_INTENT_CONFIG_MISMATCH");
  Properties ticket=ServerFiles.read(run.resolve("handoff.ticket")),ready=ServerFiles.read(run.resolve("handoff.ready"));long parent=ServerFiles.number(ticket,"parent.pid",0),guardian=ServerFiles.number(ready,"guardian.pid",0);
  ServerLaunchHandoff handoff=ServerLaunchHandoff.load(run,parent,ticket.getProperty("parent.start",""));Properties context=handoff.approvedNativeContext(run,guardian,ready.getProperty("guardian.start",""));
  if(!context.getProperty("run").equals(verified.plan().binding().run().toString())||ServerFiles.number(context,"epoch",0)!=verified.plan().binding().epoch())throw new IOException("NATIVE_INTENT_RUN_MISMATCH");return context;
 }
 synchronized void beginOwnerStart()throws IOException {ready("PREPARED");recheck();save("OWNER_STARTING","OWNER_START_MAY_HAVE_HAPPENED",null);}
 synchronized void requireStartStillValid()throws IOException {ready("OWNER_STARTING");recheck();}
 /** Persist uncertainty BEFORE START can cross the native pipe. */
 synchronized void beginChildStart()throws IOException {
  ready("OWNER_BOUND");
  try{recheck();if(nativeOwner==null||!nativeOwner.isAlive()||nativeOwner.pid()!=ServerFiles.number(state,"owner.pid",0)||!ServerFiles.sameProcess(nativeOwner.pid(),state.getProperty("owner.start")))throw new IOException("NATIVE_INTENT_OWNER_CHANGED");save("CHILD_STARTING","CHILD_START_MAY_HAVE_HAPPENED",null);}
  catch(IOException|RuntimeException bad){unknown("CHILD_START_PRECONDITION_CHANGED");throw bad;}
 }
 synchronized void observeChildStart(long pid)throws IOException {
  ready("CHILD_STARTING");if(pid<1||pid>0xffffffffL)throw new IOException("NATIVE_INTENT_CHILD_PID");
  try{recheck();coordinator.markRunning(permit);save("CHILD_RUNNING","DIAGNOSTIC_CHILD_OBSERVED",null);}catch(IOException|RuntimeException bad){unknown("CHILD_START_OUTCOME_UNKNOWN");throw bad;}
 }
 synchronized void observeOwner(Process process)throws IOException {
  ready("OWNER_STARTING");recheck();
  try{
   if(process==null||!process.isAlive())throw new IOException("NATIVE_OWNER_NOT_ALIVE");long pid=process.pid();String start=ServerFiles.start(process.toHandle());String command=process.info().command().orElseThrow(()->new IOException("NATIVE_OWNER_IMAGE_UNAVAILABLE"));
   if(!Path.of(command).toRealPath().equals(helper.toRealPath())||!ServerFiles.sameProcess(pid,start))throw new IOException("NATIVE_OWNER_IMAGE_MISMATCH");
   Properties fields=new Properties();fields.setProperty("owner.pid",Long.toString(pid));fields.setProperty("owner.start",start);save("OWNER_BOUND","OWNER_IDENTITY_RECORDED",fields);nativeOwner=process;
  }catch(IOException|RuntimeException invalid){unknown("OWNER_IDENTITY_UNKNOWN");throw invalid;}
 }
 synchronized void observeOwnerExit()throws IOException {
  String phase=state.getProperty("state");if(!Set.of("OWNER_BOUND","CHILD_STARTING","CHILD_RUNNING").contains(phase))throw new IOException("NATIVE_INTENT_NOT_REUSABLE");ready(phase);if(nativeOwner==null||nativeOwner.isAlive())throw new IOException("NATIVE_OWNER_EXIT_UNCONFIRMED");
  Properties fields=new Properties();fields.setProperty("owner.exit",Integer.toString(nativeOwner.exitValue()));save("OWNER_EXIT_OBSERVED","OWNER_EXIT_IS_NOT_CONTAINMENT_PROOF",fields);
 }
 synchronized void completeDiagnostic()throws IOException {ready("OWNER_EXIT_OBSERVED");if(!state.getProperty("owner.exit").equals("0"))throw new IOException("NATIVE_INTENT_OWNER_EXIT_FAILED");save("DIAGNOSTIC_COMPLETE","DIAGNOSTIC_RECEIPT_CHECKED_NOT_INSTALL_PROOF",null);review();}
 synchronized void fail(String reason){unknown(reason);}
 synchronized void validateRunningForStop()throws IOException {
  ready("CHILD_RUNNING");
  try{
   coordinator.validateLaunchFor(permit,Path.of(capsule.plan().cwd()));if(coordinator.snapshot().phase()!=ProfileCoordinator.Phase.PROCESS_RUNNING)throw new IOException("NATIVE_STOP_PROFILE_NOT_RUNNING");
   ServerNativeLaunchCapsule.verify(capsule.file(),capsule.sha());if(!ServerNativeEvidence.read(evidence.directory(),evidence.sha(),helper).equals(evidence))throw new IOException("NATIVE_STOP_EVIDENCE_CHANGED");
   for(String f:CONTEXT_FILES)if(!fileHash(run.resolve(f)).equals(state.getProperty("context."+f+".sha256")))throw new IOException("NATIVE_STOP_CONTEXT_CHANGED");
   if(!ServerFiles.sameProcess(ServerFiles.number(state,"parent.pid",0),state.getProperty("parent.start"))||!ServerFiles.sameProcess(ServerFiles.number(state,"guardian.pid",0),state.getProperty("guardian.start"))||nativeOwner==null||!nativeOwner.isAlive()||!ServerFiles.sameProcess(nativeOwner.pid(),state.getProperty("owner.start")))throw new IOException("NATIVE_STOP_OWNER_CHANGED");
   for(String name:List.of("native.cancel","stop.request","force.request"))if(Files.exists(run.resolve(name),LinkOption.NOFOLLOW_LINKS))throw new IOException("NATIVE_STOP_CANCELLED_OR_FORCED");
  }catch(IOException|RuntimeException bad){unknown("STOP_CONTEXT_CHANGED");throw bad;}
 }
 private void recheck()throws IOException {
  try{
   coordinator.validateLaunchFor(permit,ServerProcessConfig.load(run.resolve("launch.properties")).workDir());if(coordinator.snapshot().phase()!=ProfileCoordinator.Phase.PROCESS_RESERVED)throw new IOException("NATIVE_INTENT_RESERVATION_CHANGED");Properties current=context(run,capsule,evidence,helper);
   for(String key:List.of("profile","run","epoch","ticket","challenge","parent.pid","parent.start","guardian.pid","guardian.start"))if(!current.getProperty(key).equals(state.getProperty(key)))throw new IOException("NATIVE_INTENT_CONTEXT_CHANGED");
   for(String f:CONTEXT_FILES)if(!fileHash(run.resolve(f)).equals(state.getProperty("context."+f+".sha256")))throw new IOException("NATIVE_INTENT_CONTEXT_BYTES_CHANGED");
  }catch(IOException|RuntimeException invalid){unknown("PRECONDITION_CHANGED");throw invalid;}
 }
 private void ready(String phase)throws IOException{if(closed||poisoned||!state.getProperty("state").equals(phase))throw new IOException("NATIVE_INTENT_NOT_REUSABLE");try{if(!state.equals(read(file)))throw new IOException("NATIVE_INTENT_FILE_CHANGED");}catch(IOException bad){poisoned=true;review();throw bad;}}
 private void save(String phase,String reason,Properties fields)throws IOException {
  if(poisoned||closed)throw new IOException("NATIVE_INTENT_UNAVAILABLE");
  try{if(!state.equals(read(file)))throw new IOException("NATIVE_INTENT_FILE_CHANGED");Properties next=copy(state);next.setProperty("state",phase);next.setProperty("reason",reason);next.setProperty("revision",Long.toString(Math.addExact(ServerFiles.number(state,"revision",0),1)));if(fields!=null)next.putAll(fields);next.setProperty("sha256",digest(next));validate(next);ServerFiles.write(file,next);state=next;}
  catch(IOException|RuntimeException failure){poisoned=true;review();throw failure;}
 }
 private void unknown(String reason){if(!closed&&!poisoned)try{save("UNKNOWN",reason,null);}catch(IOException|RuntimeException ignored){}review();}
 private void review(){try{if(!coordinator.requiresReview())coordinator.requireProcessReview(permit.run());}catch(IOException ignored){}}
 synchronized Map<String,Object> describe(){var result=describe(state);if(poisoned)result.put("state","UNKNOWN_IO");result.put("historical",false);return result;}
 static Map<String,Object> inspect(Path file)throws IOException {var result=describe(read(file));result.put("historical",true);return result;}
 private static Map<String,Object> describe(Properties p){var out=new LinkedHashMap<String,Object>();out.put("schema",47);for(String key:List.of("state","profile","run","epoch","revision","reason","owner.pid","owner.exit"))out.put(key,p.getProperty(key));out.put("consumed",!p.getProperty("state").equals("PREPARED"));out.put("nativeRuntimeVerified",false);out.put("productionLaunchAllowed",false);out.put("installAuthority",false);return out;}
 private static Properties read(Path path)throws IOException {Properties p=ServerFiles.read(path);validate(p);return p;}
 private static void validate(Properties p)throws IOException {
  if(!p.stringPropertyNames().equals(KEYS)||!p.getProperty("schema").equals("47")||!p.getProperty("mode").equals("DIAGNOSTIC_BINDING_ONLY")||!p.getProperty("sha256").equals(digest(p))||!Set.of("PREPARED","OWNER_STARTING","OWNER_BOUND","CHILD_STARTING","CHILD_RUNNING","OWNER_EXIT_OBSERVED","DIAGNOSTIC_COMPLETE","UNKNOWN").contains(p.getProperty("state"))||ServerFiles.number(p,"epoch",0)<1||ServerFiles.number(p,"revision",0)<1||ServerFiles.number(p,"owner.pid",-1)<0||!p.getProperty("native.runtime.verified").equals("false")||!p.getProperty("install.authority").equals("false")||!p.getProperty("production.launch.allowed").equals("false"))throw new IOException("NATIVE_INTENT_CORRUPT");
  for(String key:List.of("run","profile","ticket","challenge"))if(!ServerFiles.canonicalUuid(p.getProperty(key)))throw new IOException("NATIVE_INTENT_ID");
  for(String key:KEYS)if(key.endsWith(".sha256")&&!ServerNativeEvidence.hash(p.getProperty(key)))throw new IOException("NATIVE_INTENT_HASH");
  if(!p.getProperty("nonce").matches("[0-9a-f]{64}")||!p.getProperty("reason").matches("[A-Z_]{1,96}")||ServerFiles.number(p,"parent.pid",0)<1||ServerFiles.number(p,"guardian.pid",0)<1||p.getProperty("parent.start").isBlank()||p.getProperty("guardian.start").isBlank())throw new IOException("NATIVE_INTENT_BINDING");
  if(Set.of("OWNER_BOUND","CHILD_STARTING","CHILD_RUNNING","OWNER_EXIT_OBSERVED","DIAGNOSTIC_COMPLETE").contains(p.getProperty("state"))&&(ServerFiles.number(p,"owner.pid",0)<1||p.getProperty("owner.start").isBlank()))throw new IOException("NATIVE_INTENT_OWNER");
  String phase=p.getProperty("state");if(Set.of("PREPARED","OWNER_STARTING").contains(phase)&&(ServerFiles.number(p,"owner.pid",0)!=0||!p.getProperty("owner.start").isEmpty()||!p.getProperty("owner.exit").equals("unobserved")))throw new IOException("NATIVE_INTENT_FALSE_OWNER");
  if(Set.of("OWNER_EXIT_OBSERVED","DIAGNOSTIC_COMPLETE").contains(phase)){try{if(!Integer.toString(Integer.parseInt(p.getProperty("owner.exit"))).equals(p.getProperty("owner.exit")))throw new IOException("NATIVE_INTENT_EXIT");}catch(NumberFormatException bad){throw new IOException("NATIVE_INTENT_EXIT",bad);}}
 }
 private static String fileHash(Path p)throws IOException{return HexFormat.of().formatHex(ServerNativeLaunchCapsule.hash(p,ServerFiles.MAX_PROPERTIES));}
 private static Properties copy(Properties p){var c=new Properties();c.putAll(p);return c;}
 private static String digest(Properties p){var text=new StringBuilder();for(String key:new TreeSet<>(p.stringPropertyNames()))if(!key.equals("sha256")){String value=p.getProperty(key);text.append(key.length()).append(':').append(key).append(value.length()).append(':').append(value).append('\n');}return HexFormat.of().formatHex(ServerNativeLaunchCapsule.digest(text.toString().getBytes(StandardCharsets.UTF_8)));}
 @Override public synchronized void close()throws IOException{if(closed)return;if(!state.getProperty("state").equals("PREPARED"))review();closed=true;lease.close();}
}
