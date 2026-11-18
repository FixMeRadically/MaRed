package com.fixmer.genesis.technology.server.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** One ordinary-backend restart, never a native containment or installation capability.
 * Durable intent is written before SAVE/STOP. Only the retaining live supervisor may
 * consume it. Reopening the journal is observation/recovery only, never replay. */
final class ServerPreparedRestart {
    private static final Set<String> KEYS=Set.of("schema","run","profile","epoch","ticket","nonce","owner","request","requestSequence","child.pid","child.start","phase","reason","successor","evidence","sha256");
    private static final Set<String> TERMINAL=Set.of("COMPLETE","CANCELLED","FAILED");
    private final Path run,path;private Properties state;private boolean poisoned;
    private ServerPreparedRestart(Path run,Properties state){this.run=run;this.path=run.resolve("restart.preparation");this.state=state;}
    static ServerPreparedRestart arm(Path run,String owner,UUID request,long sequence,Properties guardian)throws IOException {
        if(!ServerFiles.canonicalUuid(owner)||request==null||sequence<1||ServerFiles.number(guardian,"child.pid",0)<1||guardian.getProperty("child.start","").isBlank())throw new IOException("PREPARED_RESTART_IDENTITY");
        Properties p=ServerLifecycleChannel.binding(run);p.setProperty("schema","51");p.setProperty("owner",owner);p.setProperty("request",request.toString());p.setProperty("requestSequence",Long.toString(sequence));p.setProperty("child.pid",guardian.getProperty("child.pid"));p.setProperty("child.start",guardian.getProperty("child.start"));p.setProperty("phase","ARMING");p.setProperty("reason","SAVE_STOP_NOT_YET_PUBLISHED");p.setProperty("successor","");p.setProperty("evidence","");p.setProperty("sha256",digest(p));validate(p);
        Path path=run.resolve("restart.preparation");if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("PREPARED_RESTART_ALREADY_USED_FOR_RUN");
        // The profile still holds PROCESS_RUNNING here. Persist intent before its pointer,
        // then publish SAVE/STOP only after both writes. Reopen never replays intent.
        Properties pointer=new Properties();pointer.setProperty("schema","51");pointer.setProperty("run",run.getFileName().toString());ServerFiles.write(path,p);ServerFiles.write(run.getParent().getParent().resolve("restart.current"),pointer);
        return new ServerPreparedRestart(run,p);
    }
    boolean pending(){return poisoned||!TERMINAL.contains(state.getProperty("phase"));}
    boolean from(Path source){return run.equals(source);}
    boolean targets(Path successor){return successor!=null&&run.getParent().equals(successor.toAbsolutePath().normalize().getParent())&&successor.getFileName().toString().equals(state.getProperty("successor"));}
    boolean waiting(){return !poisoned&&state.getProperty("phase").equals("WAITING_RESTART");}
    String phase(){return poisoned?"UNKNOWN_IO":state.getProperty("phase");}
    UUID request(){return UUID.fromString(state.getProperty("request"));}
    void requested()throws IOException{transition("ARMING","REQUESTED","SAVE_STOP_PUBLISHED");}
    void cancel(String reason)throws IOException{if(!pending())return;Properties p=copy();p.setProperty("phase","CANCELLED");p.setProperty("reason",reason);save(p);}
    void failed(String reason)throws IOException{if(!pending())return;Properties p=copy();p.setProperty("phase","FAILED");p.setProperty("reason",reason);save(p);}
    void ready(Properties guardian,ServerLifecycleView lifecycle)throws IOException {
        if(!state.getProperty("phase").equals("REQUESTED"))throw new IOException("PREPARED_RESTART_NOT_REQUESTED");
        UUID request=request();long seq=ServerFiles.number(state,"requestSequence",0),pid=ServerFiles.number(state,"child.pid",0);
        if(lifecycle==null||!lifecycle.stopEvidence(request,seq)||!guardian.getProperty("phase","").equals("EXITED")||ServerFiles.number(guardian,"exitCode",-1)!=0||!guardian.getProperty("forceSent","").equals("false")||!guardian.getProperty("ownershipUncertain","").equals("false")||!guardian.getProperty("child.pid","").equals(Long.toString(pid))||!guardian.getProperty("child.start","").equals(state.getProperty("child.start")))throw new IOException("PREPARED_RESTART_EXIT_NOT_CONFIRMED");
        if(!ServerFiles.read(run.resolve("status.properties")).equals(guardian)||!guardian.getProperty("logError","").isEmpty())throw new IOException("PREPARED_RESTART_GUARDIAN_STATUS_CHANGED");
        String frozen=evidence();var finalView=new ServerLifecycleView(run);finalView.update(run,guardian,System.nanoTime());
        if(!ServerFiles.read(run.resolve("status.properties")).equals(guardian)||!finalView.stopEvidence(request,seq))throw new IOException("PREPARED_RESTART_FINAL_ACK_CHANGED");
        ServerStopPreparation.verifiedComplete(run,request,seq,pid,state.getProperty("child.start"));
        if(!frozen.equals(evidence()))throw new IOException("PREPARED_RESTART_FINAL_FILES_CHANGED");
        Properties p=copy();p.setProperty("evidence",frozen);p.setProperty("phase","WAITING_RESTART");p.setProperty("reason","SAVE_STOP_AND_ORDINARY_EXIT_OBSERVED");save(p);
    }
    UUID beginLaunch(String owner)throws IOException {
        if(!waiting()||!state.getProperty("owner").equals(owner))throw new IOException("PREPARED_RESTART_LIVE_OWNER_REQUIRED");
        if(!state.getProperty("evidence").equals(evidence()))throw new IOException("PREPARED_RESTART_EVIDENCE_CHANGED");
        UUID id=UUID.randomUUID();Properties p=copy();p.setProperty("successor",id.toString());p.setProperty("phase","LAUNCHING");p.setProperty("reason","SUCCESSOR_LAUNCH_RESERVED");save(p);return id;
    }
    void validateLaunch(UUID successor,String owner)throws IOException {
        if(poisoned||!state.equals(read(run))||!run.equals(current(run.getParent().getParent()))||!state.getProperty("phase").equals("LAUNCHING")||!state.getProperty("owner").equals(owner)||!successor.toString().equals(state.getProperty("successor"))||!state.getProperty("evidence").equals(evidence()))throw new IOException("PREPARED_RESTART_LAUNCH_CONTEXT_CHANGED");
    }
    void successorReady(Path successor)throws IOException {
        if(!state.getProperty("phase").equals("LAUNCHING")||!targets(successor))throw new IOException("PREPARED_RESTART_SUCCESSOR_MISMATCH");
        transition("LAUNCHING","COMPLETE","SUCCESSOR_REPORT_AND_TICKS_READY");
    }
    private void transition(String from,String to,String reason)throws IOException{if(!state.getProperty("phase").equals(from))throw new IOException("PREPARED_RESTART_PHASE");Properties p=copy();p.setProperty("phase",to);p.setProperty("reason",reason);save(p);}
    private String evidence()throws IOException {
        Properties p=new Properties();for(String name:List.of("handoff.ticket","handoff.approval","lifecycle.binding","lifecycle.status","status.properties","shutdown.observed","stop.current","launch.properties","stop-preparations/"+request()+".properties"))p.setProperty(name,HexFormat.of().formatHex(ServerNativeLaunchCapsule.digest(ServerNativeLaunchCapsule.bounded(run.resolve(name),ServerFiles.MAX_PROPERTIES))));if(Files.exists(run.resolve("game.runtime"),LinkOption.NOFOLLOW_LINKS))p.setProperty("game.runtime",fileHash(run.resolve("game.runtime")));return digest(p);
    }
    /** Read-only exact completed ordinary restart, not retained process exit or native containment proof. */
    static Properties completedEvidence(Path stateDir,UUID request)throws IOException{
        Path source=current(stateDir);if(source==null)throw new IOException("EVIDENCE_NO_PREPARED_RESTART");
        Properties p=read(source);
        if(!p.getProperty("phase").equals("COMPLETE")||!p.getProperty("request").equals(request.toString()))throw new IOException("EVIDENCE_SELECTED_RESTART_NOT_COMPLETE");
        var record=new ServerPreparedRestart(source,p);
        if(!p.getProperty("evidence").equals(record.evidence()))throw new IOException("EVIDENCE_FROZEN_SOURCE_CHANGED");
        Properties guardian=ServerFiles.read(source.resolve("status.properties"));
        if(!guardian.getProperty("phase","").equals("EXITED")||ServerFiles.number(guardian,"exitCode",-1)!=0||!guardian.getProperty("forceSent","").equals("false")||!guardian.getProperty("ownershipUncertain","").equals("false")||!guardian.getProperty("logError","").isEmpty())throw new IOException("EVIDENCE_CLEAN_ORDINARY_EXIT_REQUIRED");
        ServerStopPreparation.verifiedComplete(source,request,ServerFiles.number(p,"requestSequence",0),ServerFiles.number(p,"child.pid",0),p.getProperty("child.start"));
        var view=new ServerLifecycleView(source);view.update(source,guardian,System.nanoTime());
        if(!view.stopEvidence(request,ServerFiles.number(p,"requestSequence",0)))throw new IOException("EVIDENCE_SAVE_STOP_ACK_MISMATCH");
        if(!source.equals(current(stateDir))||!p.equals(read(source))||!p.getProperty("evidence").equals(record.evidence()))throw new IOException("EVIDENCE_COMPLETION_CHANGED_DURING_READ");return p;
    }
    private Properties copy(){return ServerLifecycleChannel.copy(state);}
    private void save(Properties p)throws IOException {
        if(poisoned)throw new IOException("PREPARED_RESTART_WRITE_UNKNOWN");
        try{if(!run.equals(current(run.getParent().getParent()))||!state.equals(read(run)))throw new IOException("PREPARED_RESTART_CHANGED");p.setProperty("sha256",digest(p));validate(p);ServerFiles.write(path,p);state=p;}
        catch(IOException failed){poisoned=true;throw failed;}
    }
    Map<String,Object> describe(){var out=describe(state);out.put("preparedRestartState",phase());out.put("preparedRestartPending",pending());out.put("preparedRestartHistorical",false);return out;}
    static Map<String,Object> inspect(Path stateDir)throws IOException {
        Path run=current(stateDir);if(run==null)return Map.of("preparedRestartState","NONE","preparedRestartPending",false,"preparedRestartHistorical",true,"preparedRestartIsInstallPermit",false);
        var out=describe(read(run));out.put("preparedRestartHistorical",true);out.put("preparedRestartPending",false);return out;
    }
    static boolean interrupted(Path stateDir)throws IOException {
        if(reviewed(stateDir))return false;
        try{Path run=current(stateDir);return run!=null&&!TERMINAL.contains(read(run).getProperty("phase"));}catch(IOException invalid){return true;}
    }
    /** Operator recovery records abandonment only; no launch or installation authority. */
    static void abandon(Path stateDir)throws IOException {
        try{Path run=current(stateDir);if(run==null)return;Properties p=read(run);if(TERMINAL.contains(p.getProperty("phase")))return;
            var record=new ServerPreparedRestart(run,p);record.cancel("OPERATOR_REVIEWED_NO_REPLAY");
        }catch(IOException invalid){
            // Explicit operator review may retire corrupt history without rewriting it.
            // This is only a no-replay acknowledgement, never process/world proof.
            Properties marker=historyFingerprint(stateDir);marker.setProperty("schema","51");marker.setProperty("scope","MANUAL_PROCESS_REVIEW_NO_REPLAY");ServerFiles.write(stateDir.resolve("restart.reviewed"),marker);
        }
    }
    private static boolean reviewed(Path stateDir){
        Path path=stateDir.resolve("restart.reviewed");if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return false;
        try{Properties p=ServerFiles.read(path);if(!p.stringPropertyNames().equals(Set.of("schema","scope","pointer","journal","binding","ticket"))||!p.getProperty("schema").equals("51")||!p.getProperty("scope").equals("MANUAL_PROCESS_REVIEW_NO_REPLAY"))return false;
            Properties expected=historyFingerprint(stateDir);for(String key:expected.stringPropertyNames())if(!expected.getProperty(key).equals(p.getProperty(key)))return false;return true;
        }catch(IOException invalid){return false;}
    }
    private static Properties historyFingerprint(Path stateDir)throws IOException {
        Properties p=new Properties();p.setProperty("pointer",fileHash(stateDir.resolve("restart.current")));Path run=null;
        try{run=current(stateDir);}catch(IOException invalid){/* Do not follow an invalid pointer. */}
        for(String name:List.of("journal","binding","ticket"))p.setProperty(name,run==null?"UNRESOLVED":fileHash(run.resolve(switch(name){case "journal"->"restart.preparation";case "binding"->"lifecycle.binding";default->"handoff.ticket";})));
        return p;
    }
    private static String fileHash(Path path)throws IOException {
        ServerFiles.safe(path);if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return "MISSING";
        return HexFormat.of().formatHex(ServerNativeLaunchCapsule.digest(ServerNativeLaunchCapsule.bounded(path,ServerFiles.MAX_PROPERTIES)));
    }
    private static Path current(Path stateDir)throws IOException {
        Path pointer=stateDir.resolve("restart.current");if(!Files.exists(pointer,LinkOption.NOFOLLOW_LINKS))return null;Properties p=ServerFiles.read(pointer);
        if(!p.stringPropertyNames().equals(Set.of("schema","run"))||!p.getProperty("schema","").equals("51")||!ServerFiles.canonicalUuid(p.getProperty("run","")))throw new IOException("PREPARED_RESTART_POINTER");return stateDir.resolve("runs").resolve(p.getProperty("run"));
    }
    private static Properties read(Path run)throws IOException {
        Properties p=ServerFiles.read(run.resolve("restart.preparation"));validate(p);if(!p.getProperty("run").equals(run.getFileName().toString()))throw new IOException("PREPARED_RESTART_RUN_MISMATCH");
        Properties binding=ServerLifecycleChannel.binding(run);for(String key:ServerLifecycleChannel.BINDING_KEYS)if(!key.equals("schema")&&!p.getProperty(key).equals(binding.getProperty(key)))throw new IOException("PREPARED_RESTART_BINDING_CHANGED");return p;
    }
    private static Map<String,Object> describe(Properties p){var out=new LinkedHashMap<String,Object>();out.put("preparedRestartState",p.getProperty("phase"));out.put("preparedRestartReason",p.getProperty("reason"));out.put("preparedRestartSource",p.getProperty("run"));out.put("preparedRestartSuccessor",p.getProperty("successor"));out.put("preparedRestartPending",!TERMINAL.contains(p.getProperty("phase")));out.put("preparedRestartIsInstallPermit",false);return out;}
    private static void validate(Properties p)throws IOException {
        if(!p.stringPropertyNames().equals(KEYS)||!p.getProperty("schema","").equals("51")||!p.getProperty("sha256","").equals(digest(p)))throw new IOException("PREPARED_RESTART_CORRUPT");
        for(String k:List.of("run","profile","ticket","nonce","owner","request"))if(!ServerFiles.canonicalUuid(p.getProperty(k)))throw new IOException("PREPARED_RESTART_IDENTITY");
        if(ServerFiles.number(p,"epoch",0)<1||ServerFiles.number(p,"requestSequence",0)<1||ServerFiles.number(p,"child.pid",0)<1||p.getProperty("child.start").isBlank()||!p.getProperty("reason").matches("[A-Z_]{1,96}"))throw new IOException("PREPARED_RESTART_IDENTITY");
        String phase=p.getProperty("phase"),successor=p.getProperty("successor"),evidence=p.getProperty("evidence");
        if(!Set.of("ARMING","REQUESTED","WAITING_RESTART","LAUNCHING","COMPLETE","CANCELLED","FAILED").contains(phase)||!successor.isEmpty()&&!ServerFiles.canonicalUuid(successor)||!evidence.isEmpty()&&!evidence.matches("[0-9a-f]{64}"))throw new IOException("PREPARED_RESTART_PHASE");
        if(Set.of("WAITING_RESTART","LAUNCHING","COMPLETE").contains(phase)&&evidence.isEmpty()||Set.of("LAUNCHING","COMPLETE").contains(phase)&&successor.isEmpty()||Set.of("ARMING","REQUESTED","WAITING_RESTART").contains(phase)&&!successor.isEmpty())throw new IOException("PREPARED_RESTART_FALSE_COMPLETION");
    }
    private static String digest(Properties p){try{var sha=MessageDigest.getInstance("SHA-256");for(String key:new TreeSet<>(p.stringPropertyNames()))if(!key.equals("sha256")){String value=p.getProperty(key);sha.update((key.length()+":"+key+value.length()+":"+value+"\n").getBytes(StandardCharsets.UTF_8));}return HexFormat.of().formatHex(sha.digest());}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
