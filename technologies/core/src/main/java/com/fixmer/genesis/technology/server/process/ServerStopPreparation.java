package com.fixmer.genesis.technology.server.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Durable observation of save-and-stop, never an OS containment or world-integrity proof. */
final class ServerStopPreparation {
    private static final Set<String> KEYS=Set.of("schema","run","profile","epoch","ticket","nonce","request","requestSequence","child.pid","child.start","phase","saveStopAck","exitObserved","exitCode","forced","reason","sha256");
    private final Path run,path;private Properties state;private boolean poisoned;
    private ServerStopPreparation(Path run,Path path,Properties state){this.run=run;this.path=path;this.state=state;}
    static ServerStopPreparation create(Path run,UUID request,long sequence,Properties guardian)throws IOException{
        if(request==null||!ServerFiles.canonicalUuid(request.toString())||sequence<1||ServerFiles.number(guardian,"child.pid",0)<1||guardian.getProperty("child.start","").isBlank())throw new IOException("STOP_PREPARATION_IDENTITY_REQUIRED");
        Path directory=run.resolve("stop-preparations");ServerFiles.directory(directory);
        try(var entries=Files.list(directory)){if(entries.limit(33).count()>=32)throw new IOException("STOP_PREPARATION_HISTORY_LIMIT");}
        Properties p=ServerLifecycleChannel.binding(run);p.setProperty("request",request.toString());p.setProperty("requestSequence",Long.toString(sequence));p.setProperty("child.pid",guardian.getProperty("child.pid"));p.setProperty("child.start",guardian.getProperty("child.start"));p.setProperty("phase","REQUESTED");p.setProperty("saveStopAck","false");p.setProperty("exitObserved","false");p.setProperty("exitCode","0");p.setProperty("forced","false");p.setProperty("reason","REQUEST_RECORDED");p.setProperty("sha256",digest(p));
        Path path=directory.resolve(request+".properties");if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("STOP_PREPARATION_EXISTS");ServerFiles.write(path,p);
        Properties pointer=new Properties();pointer.setProperty("request",request.toString());ServerFiles.write(run.resolve("stop.current"),pointer);return new ServerStopPreparation(run,path,p);
    }
    long sequence(){try{return ServerFiles.number(state,"requestSequence",0);}catch(IOException impossible){throw new IllegalStateException(impossible);}}
    UUID request(){return UUID.fromString(state.getProperty("request"));}
    boolean terminal(){return Set.of("COMPLETE","FAILED").contains(state.getProperty("phase"));}
    void acknowledged()throws IOException{if(terminal()||state.getProperty("saveStopAck").equals("true"))return;Properties next=ServerLifecycleChannel.copy(state);next.setProperty("saveStopAck","true");next.setProperty("phase","SAVE_STOP_ACK");next.setProperty("reason","GAME_SAVE_AND_STOP_ACK");save(next);}
    void failed(String reason)throws IOException{if(terminal())return;Properties next=ServerLifecycleChannel.copy(state);next.setProperty("phase","FAILED");next.setProperty("reason",reason);save(next);}
    void unknown(String reason)throws IOException{if(terminal()||state.getProperty("phase").equals("UNKNOWN")&&reason.equals(state.getProperty("reason")))return;Properties next=ServerLifecycleChannel.copy(state);next.setProperty("phase","UNKNOWN");next.setProperty("reason",reason);save(next);}
    void exited(Properties guardian)throws IOException{
        if(terminal())return;Properties next=ServerLifecycleChannel.copy(state);
        boolean same=state.getProperty("child.pid").equals(guardian.getProperty("child.pid"))&&state.getProperty("child.start").equals(guardian.getProperty("child.start"));
        if(!same||!guardian.getProperty("phase","").equals("EXITED"))throw new IOException("STOP_PREPARATION_EXIT_MISMATCH");
        long exit=ServerFiles.number(guardian,"exitCode",-1);boolean forced=!guardian.getProperty("forceSent","").equals("false"),uncertain=!guardian.getProperty("ownershipUncertain","").equals("false");
        next.setProperty("exitObserved","true");next.setProperty("exitCode",Long.toString(exit));next.setProperty("forced",Boolean.toString(forced));
        boolean complete=next.getProperty("saveStopAck").equals("true")&&exit==0&&!forced&&!uncertain;
        next.setProperty("phase",complete?"COMPLETE":"UNKNOWN");next.setProperty("reason",complete?"SAVE_ACK_AND_ORDINARY_EXIT":"EXIT_WITHOUT_COMPLETE_EVIDENCE");save(next);
    }
    void signalShutdownObserved()throws IOException{
        if(poisoned||!state.getProperty("saveStopAck").equals("true"))throw new IOException("SAVE_STOP_ACK_REQUIRED");
        Properties marker=ServerLifecycleChannel.binding(run);marker.setProperty("request",request().toString());marker.setProperty("requestSequence",Long.toString(sequence()));marker.setProperty("child.pid",state.getProperty("child.pid"));marker.setProperty("child.start",state.getProperty("child.start"));
        Path target=run.resolve("shutdown.observed");if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){if(!ServerFiles.read(target).equals(marker))throw new IOException("SHUTDOWN_OBSERVATION_CHANGED");return;}ServerFiles.write(target,marker);
    }
    static boolean shutdownObserved(Path run,long pid,String start)throws IOException{
        Path path=run.resolve("shutdown.observed");if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return false;Properties p=ServerFiles.read(path);Set<String> keys=new HashSet<>(ServerLifecycleChannel.BINDING_KEYS);keys.addAll(List.of("request","requestSequence","child.pid","child.start"));
        if(!p.stringPropertyNames().equals(keys)||!ServerFiles.canonicalUuid(p.getProperty("request",""))||ServerFiles.number(p,"requestSequence",0)<1||ServerFiles.number(p,"child.pid",0)!=pid||!start.equals(p.getProperty("child.start")))throw new IOException("INVALID_SHUTDOWN_OBSERVATION");ServerLifecycleChannel.verifyBinding(p,ServerLifecycleChannel.binding(run));return true;
    }
    private void save(Properties next)throws IOException{
        if(poisoned)throw new IOException("STOP_PREPARATION_WRITE_UNKNOWN");
        try{if(!state.equals(read(run,path)))throw new IOException("STOP_PREPARATION_CHANGED");next.setProperty("sha256",digest(next));validate(next);ServerFiles.write(path,next);state=next;}
        catch(IOException bad){poisoned=true;throw bad;}
    }
    Map<String,Object> describe(){Map<String,Object> result=describe(state);if(poisoned){result.put("stopPreparationState","UNKNOWN_IO");result.put("stopPreparationComplete",false);}result.put("stopPreparationHistorical",false);return result;}
    static Map<String,Object> inspect(Path run)throws IOException{
        Path pointer=run.resolve("stop.current");if(!Files.exists(pointer,LinkOption.NOFOLLOW_LINKS))return Map.of("stopPreparationState","NONE","stopPreparationComplete",false,"stopPreparationHistorical",true);
        Properties p=ServerFiles.read(pointer);String id=p.getProperty("request","");if(p.size()!=1||!ServerFiles.canonicalUuid(id))throw new IOException("STOP_PREPARATION_POINTER");
        Map<String,Object> result=describe(read(run,run.resolve("stop-preparations").resolve(id+".properties")));result.put("stopPreparationHistorical",true);return result;
    }
    /** Exact current SAVE/STOP observation, still deliberately not an installation capability. */
    static Properties verifiedComplete(Path run,UUID request,long sequence,long pid,String start)throws IOException {
        Properties pointer=ServerFiles.read(run.resolve("stop.current"));if(pointer.size()!=1||!request.toString().equals(pointer.getProperty("request")))throw new IOException("STOP_PREPARATION_CURRENT_CHANGED");
        Properties p=read(run,run.resolve("stop-preparations").resolve(request+".properties"));
        if(!p.getProperty("phase").equals("COMPLETE")||ServerFiles.number(p,"requestSequence",0)!=sequence||ServerFiles.number(p,"child.pid",0)!=pid||!p.getProperty("child.start").equals(start)||!shutdownObserved(run,pid,start))throw new IOException("STOP_PREPARATION_NOT_BOUND_COMPLETE");
        Properties marker=ServerFiles.read(run.resolve("shutdown.observed"));if(!marker.getProperty("request").equals(request.toString())||ServerFiles.number(marker,"requestSequence",0)!=sequence)throw new IOException("STOP_PREPARATION_SHUTDOWN_REQUEST_CHANGED");return ServerLifecycleChannel.copy(p);
    }
    private static Map<String,Object> describe(Properties p){Map<String,Object> result=new LinkedHashMap<>();result.put("stopPreparationState",p.getProperty("phase"));result.put("stopPreparationComplete",p.getProperty("phase").equals("COMPLETE"));result.put("stopPreparationRequest",p.getProperty("request"));result.put("stopPreparationReason",p.getProperty("reason"));result.put("stopPreparationSaveAck",p.getProperty("saveStopAck").equals("true"));result.put("stopPreparationExitObserved",p.getProperty("exitObserved").equals("true"));result.put("stopPreparationIsInstallPermit",false);return result;}
    private static Properties read(Path run,Path path)throws IOException{Properties p=ServerFiles.read(path);validate(p);ServerLifecycleChannel.verifyBinding(p,ServerLifecycleChannel.binding(run));if(!path.getFileName().toString().equals(p.getProperty("request")+".properties"))throw new IOException("STOP_PREPARATION_PATH_MISMATCH");return p;}
    private static void validate(Properties p)throws IOException{
        if(!p.stringPropertyNames().equals(KEYS)||!p.getProperty("sha256","").equals(digest(p))||!ServerFiles.canonicalUuid(p.getProperty("request",""))||ServerFiles.number(p,"requestSequence",0)<1||ServerFiles.number(p,"child.pid",0)<1||p.getProperty("child.start","").isBlank()||!p.getProperty("reason","").matches("[A-Z_]{1,96}"))throw new IOException("STOP_PREPARATION_CORRUPT");
        for(String k:List.of("saveStopAck","exitObserved","forced"))if(!Set.of("true","false").contains(p.getProperty(k)))throw new IOException("STOP_PREPARATION_CORRUPT");
        long code=ServerFiles.number(p,"exitCode",Long.MIN_VALUE);String phase=p.getProperty("phase");if(!Set.of("REQUESTED","SAVE_STOP_ACK","COMPLETE","FAILED","UNKNOWN").contains(phase))throw new IOException("STOP_PREPARATION_CORRUPT");
        if(phase.equals("COMPLETE")&&(!p.getProperty("saveStopAck").equals("true")||!p.getProperty("exitObserved").equals("true")||p.getProperty("forced").equals("true")||code!=0))throw new IOException("STOP_PREPARATION_FALSE_COMPLETION");
    }
    private static String digest(Properties p){try{var hash=MessageDigest.getInstance("SHA-256");for(String key:new TreeSet<>(p.stringPropertyNames()))if(!key.equals("sha256")){String value=p.getProperty(key);hash.update((key.length()+":"+key+value.length()+":"+value+"\n").getBytes(StandardCharsets.UTF_8));}return HexFormat.of().formatHex(hash.digest());}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
