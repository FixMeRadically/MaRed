package com.fixmer.mared.technology.server.setup;

import com.fixmer.genesis.technology.server.remote.RemoteRepeat71;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** UI adapter only. The authenticated existing owner makes every process/commit decision. */
public final class ManagedRemote71 {
    private static volatile RemoteRepeat71 bridge;private static volatile boolean watching,busy=true;
    private static volatile String phase="OWNER_CONNECTING";private static volatile JsonObject state=new JsonObject();private static volatile Path reports,cache,candidate;private static volatile UUID request;private static volatile long sent;
    private static final Set<String> PENDING=Set.of("WAITING_SERVER","VALIDATING","PREPARING","READY","ARMING","ARMED","STARTING","CONNECTING");
    private ManagedRemote71(){}
    public static boolean available(){return System.getenv("GENESIS_REMOTE70_ROOT")!=null;}
    public static boolean busy(){ensure();return busy;}public static String phase(){ensure();return phase;}public static JsonObject state(){ensure();return state.deepCopy();}public static Path reports(){ensure();return reports;}
    private static synchronized void ensure(){
        if(watching||!available())return;watching=true;
        Thread poll=new Thread(()->{
            try{bridge=RemoteRepeat71.open(System.getenv());reports=bridge.root();long started=System.nanoTime();
                while(true){
                    var next=bridge.status();
                    if(!next.isEmpty()){
                        long age=System.currentTimeMillis()-Long.parseLong(next.get("at"));if(age< -30000||age>300000)throw new IllegalStateException("Owner heartbeat expired");
                        UUID id=UUID.fromString(next.get("request"));UUID expected=request;
                        if(expected==null||id.equals(expected)){
                            JsonObject display=new JsonObject();display.addProperty("failureReason",next.get("reason"));display.add("delta",JsonParser.parseString(next.get("delta")).getAsJsonObject());if(next.containsKey("preflight87"))display.add("preflight87",JsonParser.parseString(next.get("preflight87")).getAsJsonObject());if(next.containsKey("auth89"))display.add("auth89",JsonParser.parseString(next.get("auth89")).getAsJsonObject());state=display;reports=Path.of(next.get("reports"));phase=next.get("phase");busy=PENDING.contains(phase);
                        }else if(sent>0&&System.nanoTime()-sent>300_000_000_000L)throw new IllegalStateException("Owner reply expired");
                    }else if(System.nanoTime()-started>30_000_000_000L)throw new IllegalStateException("Owner unavailable");
                    Thread.sleep(200);
                }
            }catch(Exception bad){phase="OWNER_UNAVAILABLE";busy=true;}
        },"genesis-managed-remote71");poll.setDaemon(true);poll.start();
    }
    public static synchronized void start(){start(RemoteService69.download(),null);}
    public static synchronized void start(com.fixmer.genesis.technology.server.remote.RemoteClient69.Download download,byte[] control){
        ensure();if(busy||bridge==null)return;if(download==null){phase="NO_CANDIDATE";return;}
        cache=RemoteService69.root().toAbsolutePath().normalize();candidate=download.directory().toAbsolutePath().normalize();request=UUID.randomUUID();sent=System.nanoTime();phase="VALIDATING";busy=true;state=new JsonObject();
        try{if(control!=null)bridge.control(request,control);bridge.submit(request,"PREPARE",cache,candidate);}catch(Exception bad){phase="OWNER_UNAVAILABLE";busy=true;}
    }
    public static synchronized void command(String action){
        if(!busy||bridge==null||request==null||!Set.of("ARM","CANCEL").contains(action)||action.equals("ARM")&&!phase.equals("READY"))return;
        try{bridge.submit(request,action,cache,candidate);if(action.equals("ARM"))phase="ARMING";}catch(Exception bad){phase="OWNER_UNAVAILABLE";busy=true;}
    }
}
