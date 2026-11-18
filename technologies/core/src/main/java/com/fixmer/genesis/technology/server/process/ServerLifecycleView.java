package com.fixmer.genesis.technology.server.process;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Supervisor-side observations. Heartbeat freshness never grants permission to install mods. */
final class ServerLifecycleView {
    static final long STALE_MS=5_000;
    private final Properties binding;
    private Properties frame=new Properties();private long sequence,revision,ticks,lastSeen=-1;
    private String error="";
    ServerLifecycleView(Path run)throws IOException{binding=ServerLifecycleChannel.binding(run);}
    void update(Path run,Properties guardian,long now){
        if(guardian.getProperty("child.start","").isBlank())return;
        Path path=run.resolve("lifecycle.status");if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return;
        try{
            Properties next=ServerFiles.read(path);if(!next.stringPropertyNames().equals(ServerLifecycleChannel.FRAME_KEYS))throw new IOException("LIFECYCLE_FRAME_FIELDS");ServerLifecycleChannel.verifyBinding(next,binding);
            long seq=positive(next,"sequence"),rev=positive(next,"revision"),tick=nonnegative(next,"ticks"),age=nonnegative(next,"tickAgeMs"),requestSeq=nonnegative(next,"requestSequence");
            ServerLifecycleState.Operation operation=ServerLifecycleState.Operation.valueOf(next.getProperty("operation"));String stop=next.getProperty("stopRequested");if(!stop.equals("true")&&!stop.equals("false"))throw new IOException("LIFECYCLE_STOP_SHAPE");
            if(stop.equals("true")&&(operation!=ServerLifecycleState.Operation.SAVE_AND_STOP||!Set.of("STOPPING","STOPPED").contains(next.getProperty("phase"))||!next.getProperty("outcome").equals("COMPLETED")))throw new IOException("LIFECYCLE_STOP_SHAPE");
            ServerLifecycleState.Phase phase=ServerLifecycleState.Phase.valueOf(next.getProperty("phase"));ServerLifecycleState.Outcome outcome=ServerLifecycleState.Outcome.valueOf(next.getProperty("outcome"));
            nonnegative(next,"saveMs");String request=next.getProperty("request");if(requestSeq==0? !request.isEmpty()||outcome!=ServerLifecycleState.Outcome.NONE : !ServerFiles.canonicalUuid(request)||outcome==ServerLifecycleState.Outcome.NONE)throw new IOException("LIFECYCLE_REQUEST_SHAPE");
            if(phase==ServerLifecycleState.Phase.STOP_ARMED&&(operation!=ServerLifecycleState.Operation.SAVE_AND_STOP||outcome!=ServerLifecycleState.Outcome.COMPLETED)||phase==ServerLifecycleState.Phase.READY&&tick==0||(phase==ServerLifecycleState.Phase.CHECKPOINTING)!=(outcome==ServerLifecycleState.Outcome.RUNNING)||!next.getProperty("error").matches("[A-Z_]{0,96}"))throw new IOException("LIFECYCLE_STATE_SHAPE");
            if(!next.getProperty("pid").equals(guardian.getProperty("child.pid"))||!next.getProperty("start").equals(guardian.getProperty("child.start")))throw new IOException("LIFECYCLE_CHILD_MISMATCH");
            if(seq<sequence||rev<revision||tick<ticks)throw new IOException("LIFECYCLE_FRAME_REGRESSED");
            if(seq==sequence){if(!next.equals(frame))throw new IOException("LIFECYCLE_SEQUENCE_REUSED");return;}
            sequence=seq;revision=rev;ticks=tick;frame=next;lastSeen=now;error="";
        }catch(IOException|IllegalArgumentException invalid){error="INVALID_LIFECYCLE_REPORT";}
    }
    boolean fresh(boolean childAlive,long now){return error.isEmpty()&&childAlive&&sequence>0&&TimeUnit.NANOSECONDS.toMillis(Math.max(0,now-lastSeen))<=STALE_MS;}
    boolean ready(boolean childAlive,long now){return fresh(childAlive,now)&&frame.getProperty("phase","").equals("READY")&&number("tickAgeMs",Long.MAX_VALUE)<=STALE_MS-TimeUnit.NANOSECONDS.toMillis(Math.max(0,now-lastSeen));}
    Map<String,Object> describe(boolean childAlive,long now){
        Map<String,Object> result=new LinkedHashMap<>();result.put("lifecycleState",frame.getProperty("phase","UNREPORTED"));result.put("lifecycleFresh",fresh(childAlive,now));result.put("lifecycleError",error);result.put("minecraftReady",ready(childAlive,now));result.put("lifecycleTicks",ticks);result.put("lifecycleTickAgeMs",frame.isEmpty()?-1:number("tickAgeMs",Long.MAX_VALUE));result.put("lifecycleLastSeenMs",sequence==0?-1:TimeUnit.NANOSECONDS.toMillis(Math.max(0,now-lastSeen)));result.put("checkpointReportedRequest",frame.getProperty("request",""));result.put("checkpointReportedOutcome",frame.getProperty("outcome","NONE"));result.put("checkpointDurationMs",number("saveMs",0));result.put("checkpointError",frame.getProperty("error",""));result.put("lifecycleOperation",frame.getProperty("operation","CHECKPOINT"));result.put("gameStopRequested",frame.getProperty("stopRequested","false").equals("true"));result.put("worldQuiesced",false);result.put("containmentVerified",false);return result;
    }
    boolean stopCallUncertain(UUID request){return request!=null&&request.toString().equals(frame.getProperty("request"))&&frame.getProperty("error","").equals("STOP_CALL_OUTCOME_UNKNOWN");}
    long observedFrameSequence(){return sequence;}
    long observedRequestSequence(){return number("requestSequence",0);}
    boolean stopEvidence(UUID request,long requestSequence){return error.isEmpty()&&number("requestSequence",-1)==requestSequence&&request!=null&&request.toString().equals(frame.getProperty("request"))&&frame.getProperty("operation","").equals("SAVE_AND_STOP")&&frame.getProperty("outcome","").equals("COMPLETED")&&frame.getProperty("stopRequested","").equals("true");}
    boolean completed(UUID request,boolean childAlive,long now){return fresh(childAlive,now)&&request.toString().equals(frame.getProperty("request"))&&frame.getProperty("outcome","").equals("COMPLETED");}
    String outcome(UUID request){return request!=null&&request.toString().equals(frame.getProperty("request"))?frame.getProperty("outcome","NONE"):"NONE";}
    private long number(String key,long fallback){try{return ServerFiles.number(frame,key,fallback);}catch(IOException impossible){return fallback;}}
    private static long positive(Properties p,String key)throws IOException{long n=ServerFiles.number(p,key,0);if(n<1)throw new IOException("LIFECYCLE_NUMBER");return n;}
    private static long nonnegative(Properties p,String key)throws IOException{long n=ServerFiles.number(p,key,-1);if(n<0)throw new IOException("LIFECYCLE_NUMBER");return n;}
}
