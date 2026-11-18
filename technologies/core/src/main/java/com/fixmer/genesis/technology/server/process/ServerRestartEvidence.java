package com.fixmer.genesis.technology.server.process;

import com.fixmer.genesis.technology.server.packages.ProfileCoordinator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only observations of the ordinary supervisor. Never a native stopped-profile capability. */
public final class ServerRestartEvidence {
    private ServerRestartEvidence(){}
    static String hash(Path file)throws IOException{return HexFormat.of().formatHex(ServerNativeLaunchCapsule.digest(ServerNativeLaunchCapsule.bounded(file,512*1024)));}
    private static Path canonical(Path path)throws IOException{
        path=path.toAbsolutePath().normalize();ServerFiles.safe(path);if(!path.equals(path.toRealPath()))throw new IOException("EVIDENCE_CANONICAL_PATH_REQUIRED");return path;
    }
    private static void pin(String pin)throws IOException{if(pin==null||!pin.matches("[0-9a-f]{64}"))throw new IOException("EVIDENCE_EXPECTED_ADAPTER_SHA256_REQUIRED");}
    private static Properties endpoint(ServerProcessConfig config)throws IOException{
        Properties endpoint=ServerFiles.read(config.stateDir().resolve("control.properties"));
        if(!endpoint.getProperty("online","").equals("true")||!ServerFiles.canonicalUuid(endpoint.getProperty("instance",""))||!ServerFiles.sameProcess(ServerFiles.number(endpoint,"pid",0),endpoint.getProperty("start","")))throw new IOException("EVIDENCE_LIVE_SUPERVISOR_REQUIRED");return endpoint;
    }
    /** One sample is not a progressing-ticks claim. Use probe for a bounded two-sample observation. */
    public static Map<String,Object> sample(ServerProcessConfig config,String adapterSha256)throws IOException{
        pin(adapterSha256);canonical(config.file());canonical(config.workDir());canonical(config.stateDir());
        String configHash=hash(config.file());if(!ServerProcessConfig.load(config.file()).snapshot().equals(config.snapshot()))throw new IOException("EVIDENCE_CONFIG_OBJECT_STALE");
        Properties owner=endpoint(config),pointer=ServerFiles.read(config.stateDir().resolve("current.properties"));
        String id=pointer.getProperty("run","");if(!ServerFiles.canonicalUuid(id))throw new IOException("EVIDENCE_CURRENT_RUN_REQUIRED");
        Path run=canonical(config.stateDir().resolve("runs").resolve(id));
        Properties guardian=ServerFiles.read(run.resolve("status.properties"));
        if(!guardian.getProperty("phase","").equals("RUNNING")||!guardian.getProperty("forceSent","").equals("false")||!guardian.getProperty("ownershipUncertain","").equals("false")||!guardian.getProperty("logError","").isEmpty())throw new IOException("EVIDENCE_RUNNING_UNFORCED_ROOT_REQUIRED");
        for(String who:List.of("child","guardian"))if(!ServerFiles.sameProcess(ServerFiles.number(guardian,who+".pid",0),guardian.getProperty(who+".start","")))throw new IOException("EVIDENCE_PROCESS_IDENTITY_UNAVAILABLE");
        var handoff=ServerLaunchHandoff.load(run,ServerFiles.number(owner,"pid",0),owner.getProperty("start"));
        handoff.validateStatus(guardian,ServerFiles.number(guardian,"guardian.pid",0));
        Properties ready=ServerFiles.read(run.resolve("handoff.ready"));handoff.validateReady(ready,ServerFiles.number(guardian,"guardian.pid",0));
        if(!handoff.approved(run,ready))throw new IOException("EVIDENCE_HANDOFF_APPROVAL_REQUIRED");
        if(!ServerProcessConfig.load(run.resolve("launch.properties")).snapshot().equals(config.snapshot()))throw new IOException("EVIDENCE_CONFIG_CHANGED_SINCE_LAUNCH");
        var admission=ProfileCoordinator.inspect(config.workDir());Properties binding=ServerLifecycleChannel.binding(run);
        if(admission.scope()!=ProfileCoordinator.Scope.LIVE||admission.phase()!=ProfileCoordinator.Phase.PROCESS_RUNNING||!admission.profile().toString().equals(binding.getProperty("profile"))||admission.epoch()!=ServerFiles.number(binding,"epoch",0)||admission.run()==null||!admission.run().toString().equals(id))throw new IOException("EVIDENCE_PROFILE_OWNER_CHANGED");
        Properties facts=ServerLifecycleChannel.readGameRuntime(run);
        if(!facts.getProperty("adapterSha256").equals(adapterSha256)||!facts.getProperty("pid").equals(guardian.getProperty("child.pid"))||!facts.getProperty("start").equals(guardian.getProperty("child.start")))throw new IOException("EVIDENCE_GAME_ADAPTER_OR_ROOT_MISMATCH");
        var view=new ServerLifecycleView(run);view.update(run,guardian,System.nanoTime());
        if(!view.ready(true,System.nanoTime()))throw new IOException("EVIDENCE_LIFECYCLE_NOT_READY");
        var frame=view.describe(true,System.nanoTime());
        var out=base();out.put("run",id);out.put("profile",admission.profile().toString());out.put("owner",owner.getProperty("instance"));out.put("ownerEpoch",admission.epoch());
        out.put("sequence",view.observedFrameSequence());out.put("ticks",frame.get("lifecycleTicks"));out.put("adapterSha256",adapterSha256);
        for(String key:List.of("minecraft","neoforge","genesis","serverKind"))out.put(key,facts.getProperty(key));
        out.put("runtimeSha256",hash(run.resolve("game.runtime")));out.put("configSha256",configHash);out.put("automaticCrashRestartEnabled",config.restartOnCrash());
        if(!configHash.equals(hash(config.file()))||!ServerProcessConfig.load(config.file()).snapshot().equals(config.snapshot())||!owner.equals(endpoint(config))||!pointer.equals(ServerFiles.read(config.stateDir().resolve("current.properties")))||!sameGuardian(guardian,ServerFiles.read(run.resolve("status.properties")))||!facts.equals(ServerLifecycleChannel.readGameRuntime(run))||!admission.equals(ProfileCoordinator.inspect(config.workDir())))throw new IOException("EVIDENCE_CHANGED_DURING_SAMPLE");
        return out;
    }
    public static Map<String,Object> probe(ServerProcessConfig config,String adapterSha256)throws IOException{
        var first=sample(config,adapterSha256);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        while(System.nanoTime()<deadline){pause();var next=sample(config,adapterSha256);sameContext(first,next);
            if(number(next,"sequence")>number(first,"sequence")&&number(next,"ticks")>number(first,"ticks")){next.put("progressObserved",true);return next;}
        }throw new IOException("EVIDENCE_TICKS_DID_NOT_PROGRESS");
    }
    /** Reserve only a private NEW exercise directory. Does not start or stop the server. */
    public static Map<String,Object> begin(ServerProcessConfig config,String adapterSha256,Path NEWoutput)throws IOException{
        Path output=NEWoutput.toAbsolutePath().normalize();ServerFiles.safe(output);canonical(output.getParent());
        for(Path selected:List.of(config.workDir(),config.stateDir(),config.javaHome())){Path protectedPath=canonical(selected);if(output.startsWith(protectedPath)||protectedPath.startsWith(output))throw new IOException("EVIDENCE_OUTPUT_OVERLAPS_RUNTIME");}
        if(Files.exists(output,LinkOption.NOFOLLOW_LINKS))throw new IOException("EVIDENCE_NEW_OUTPUT_REQUIRED");
        var sample=probe(config,adapterSha256);if(config.restartOnCrash())throw new IOException("EVIDENCE_EXERCISE_REQUIRES_CRASH_RESTART_DISABLED");
        if(ServerPreparedRestart.interrupted(config.stateDir()))throw new IOException("EVIDENCE_PREVIOUS_RESTART_UNRESOLVED");
        Files.createDirectory(output);ServerFiles.privateMode(output,true);sample.put("exerciseDirectoryCreated",true);sample.put("readOnly",false);sample.put("runtimeReadOnly",true);return sample;
    }
    /** Historical request selector, not success or current readiness. */
    public static Map<String,Object> inspect(ServerProcessConfig config)throws IOException{
        var out=base();out.put("historical",true);out.putAll(ServerPreparedRestart.inspect(config.stateDir()));
        Properties pointer=ServerFiles.read(config.stateDir().resolve("restart.current"));
        String id=pointer.getProperty("run","");if(!ServerFiles.canonicalUuid(id))throw new IOException("EVIDENCE_RESTART_POINTER");
        Properties p=ServerFiles.read(config.stateDir().resolve("runs").resolve(id).resolve("restart.preparation"));
        String request=p.getProperty("request","");if(!ServerFiles.canonicalUuid(request))throw new IOException("EVIDENCE_RESTART_REQUEST");out.put("request",request);return out;
    }
    /** Capture an already completed selected restart. Never sends commands or launches/kills a process. */
    public static Map<String,Object> capture(ServerProcessConfig config,String adapterSha256,UUID request,Path NEWoutput)throws IOException{
        Objects.requireNonNull(request);pin(adapterSha256);Path output=NEWoutput.toAbsolutePath().normalize();ServerFiles.safe(output);canonical(output.getParent());
        for(Path selected:List.of(config.workDir(),config.stateDir(),config.javaHome())){Path protectedPath=canonical(selected);if(output.startsWith(protectedPath)||protectedPath.startsWith(output))throw new IOException("EVIDENCE_OUTPUT_OVERLAPS_RUNTIME");}
        if(Files.exists(output,LinkOption.NOFOLLOW_LINKS))throw new IOException("EVIDENCE_NEW_OUTPUT_REQUIRED");
        var first=completed(config,adapterSha256,request);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        Map<String,Object> second=null;
        while(System.nanoTime()<deadline){pause();var next=completed(config,adapterSha256,request);sameContext(first,next);
            if(!first.get("sourceEvidenceSha256").equals(next.get("sourceEvidenceSha256"))||!first.get("restartRecordSha256").equals(next.get("restartRecordSha256")))throw new IOException("EVIDENCE_RESTART_CHANGED_BETWEEN_SAMPLES");
            if(number(next,"sequence")>number(first,"sequence")&&number(next,"ticks")>number(first,"ticks")){second=next;break;}
        }
        if(second==null)throw new IOException("EVIDENCE_SUCCESSOR_TICKS_DID_NOT_PROGRESS");
        second.put("firstSequence",number(first,"sequence"));second.put("firstTicks",number(first,"ticks"));second.put("successorProgressObserved",true);second.put("localLifecycleEvidenceComplete",true);second.put("historical",true);second.put("readOnly",false);second.put("runtimeReadOnly",true);
        // All validation precedes output creation. Only a new private evidence directory is written.
        Files.createDirectory(output);ServerFiles.privateMode(output,true);
        var report=new Properties();for(var entry:second.entrySet())report.setProperty(entry.getKey(),entry.getValue().toString());
        ServerFiles.write(output.resolve("report.properties"),report);
        byte[] json=(ServerJson.object(second)+"\n").getBytes(StandardCharsets.UTF_8);
        Files.write(output.resolve("evidence.json"),json,StandardOpenOption.CREATE_NEW);ServerFiles.privateMode(output.resolve("evidence.json"),false);
        Properties seal=new Properties();seal.setProperty("schema","54");seal.setProperty("report.sha256",hash(output.resolve("report.properties")));seal.setProperty("json.sha256",hash(output.resolve("evidence.json")));ServerFiles.write(output.resolve("manifest.properties"),seal);
        return second;
    }
    public static Map<String,Object> bundleStatus(Path directory)throws IOException{
        directory=canonical(directory);Properties seal=ServerFiles.read(directory.resolve("manifest.properties"));
        if(!seal.stringPropertyNames().equals(Set.of("schema","report.sha256","json.sha256"))||!seal.getProperty("schema").equals("54")||!hash(directory.resolve("report.properties")).equals(seal.getProperty("report.sha256"))||!hash(directory.resolve("evidence.json")).equals(seal.getProperty("json.sha256")))throw new IOException("EVIDENCE_BUNDLE_CHANGED_OR_INCOMPLETE");
        Properties p=ServerFiles.read(directory.resolve("report.properties"));var out=base();out.put("historical",true);out.put("recordedCaptureComplete",p.getProperty("localLifecycleEvidenceComplete","false").equals("true"));
        for(String key:List.of("request","sourceRun","run","adapterSha256","minecraft","neoforge","serverKind"))out.put(key,p.getProperty(key,""));
        if(!seal.equals(ServerFiles.read(directory.resolve("manifest.properties"))))throw new IOException("EVIDENCE_BUNDLE_CHANGED_DURING_READ");return out;
    }
    private static Map<String,Object> completed(ServerProcessConfig config,String adapter,UUID request)throws IOException{
        Properties p=ServerPreparedRestart.completedEvidence(config.stateDir(),request);Path old=config.stateDir().resolve("runs").resolve(p.getProperty("run"));
        String frozen=hash(old.resolve("restart.preparation"));Properties oldRuntime=ServerLifecycleChannel.readGameRuntime(old);
        if(!oldRuntime.getProperty("adapterSha256").equals(adapter)||!oldRuntime.getProperty("pid").equals(p.getProperty("child.pid"))||!oldRuntime.getProperty("start").equals(p.getProperty("child.start")))throw new IOException("EVIDENCE_SOURCE_RUNTIME_MISMATCH");
        Properties oldGuardian=ServerFiles.read(old.resolve("status.properties"));
        if(ServerProcessSupervisor.knownAlive(oldGuardian))throw new IOException("EVIDENCE_OLD_PROCESSES_STILL_OBSERVED");
        var out=sample(config,adapter);
        if(!out.get("run").equals(p.getProperty("successor"))||!out.get("owner").equals(p.getProperty("owner"))||!out.get("profile").equals(p.getProperty("profile"))||!out.get("ownerEpoch").toString().equals(p.getProperty("epoch")))throw new IOException("EVIDENCE_SUCCESSOR_CONTEXT_MISMATCH");
        for(String key:List.of("minecraft","neoforge","genesis","serverKind"))if(!out.get(key).equals(oldRuntime.getProperty(key)))throw new IOException("EVIDENCE_RUNTIME_CHANGED_ACROSS_RESTART");
        out.put("request",request.toString());out.put("sourceRun",p.getProperty("run"));out.put("sourceRuntimeSha256",hash(old.resolve("game.runtime")));out.put("sourceEvidenceSha256",p.getProperty("evidence"));out.put("restartRecordSha256",frozen);out.put("saveStopRequestSequence",ServerFiles.number(p,"requestSequence",0));
        out.put("saveStopAckObserved",true);out.put("ordinaryExitRecorded",true);
        if(!p.equals(ServerPreparedRestart.completedEvidence(config.stateDir(),request))||!frozen.equals(hash(old.resolve("restart.preparation")))||!oldRuntime.equals(ServerLifecycleChannel.readGameRuntime(old)))throw new IOException("EVIDENCE_SOURCE_CHANGED_DURING_CAPTURE");return out;
    }
    private static boolean sameGuardian(Properties before,Properties after){
        for(String key:List.of("phase","child.pid","child.start","guardian.pid","guardian.start","forceSent","ownershipUncertain","logError","launchIntent","handoff.version","handoff.run","handoff.profile","handoff.epoch","handoff.ticket"))if(!Objects.equals(before.getProperty(key),after.getProperty(key)))return false;
        return true;
    }
    private static void sameContext(Map<String,Object> first,Map<String,Object> next)throws IOException{
        for(String key:List.of("run","owner","profile","ownerEpoch","adapterSha256","runtimeSha256","configSha256"))if(!first.get(key).equals(next.get(key)))throw new IOException("EVIDENCE_CONTEXT_CHANGED_BETWEEN_SAMPLES");
        if(number(next,"sequence")<number(first,"sequence")||number(next,"ticks")<number(first,"ticks"))throw new IOException("EVIDENCE_PROGRESS_REGRESSED");
    }
    private static long number(Map<String,Object> values,String key){return ((Number)values.get(key)).longValue();}
    private static void pause()throws IOException{try{Thread.sleep(150);}catch(InterruptedException stopped){Thread.currentThread().interrupt();throw new IOException("EVIDENCE_INTERRUPTED_NO_PROCESS_COMMAND_SENT",stopped);}}
    private static Map<String,Object> base(){var out=new LinkedHashMap<String,Object>();out.put("schema",54);out.put("scope","ORDINARY_LOCAL_LIFECYCLE_OBSERVATION");out.put("readOnly",true);out.put("localLifecycleEvidenceComplete",false);
        for(String key:List.of("nativeContainmentVerified","worldSaveVerified","actualMinecraftVerified","runtimeCompatibilityVerified","authorizationVerified","installAuthority","productionLaunchAllowed"))out.put(key,false);return out;}
}
