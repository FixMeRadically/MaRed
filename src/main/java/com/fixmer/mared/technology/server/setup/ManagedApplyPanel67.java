package com.fixmer.mared.technology.server.setup;

import com.fixmer.genesis.technology.server.firstlaunch.LocalApply67;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Client of the existing local owner. It never launches a second supervisor or stops a PID. */
public final class ManagedApplyPanel67 {
    private static volatile LocalApply67 bridge;private static volatile boolean monitor,requestSent,cancelPending,localFailure;private static volatile boolean busy=true;
    private static volatile String phase="CONNECTING",nonce="",jar="",sha="",side="BOTH";
    private static volatile JsonObject state=new JsonObject();private static volatile long sent;
    private static final Set<String> PHASES=Set.of("ACTIVE","CHECKED","FAILED","CANCELLED","COMPLETE","VALIDATING","PREPARING","READY","CONSENT","RESTARTING","STARTING");
    private ManagedApplyPanel67(){}
    public static String reviewToken(){return phase.equals("READY")?nonce:"";}
    public static boolean available(){return System.getenv("GENESIS_PANEL67_ROOT")!=null;}
    public static boolean busy(){ensure();return busy;}
    public static String phase(){ensure();return phase;}
    public static JsonObject state(){ensure();return state.deepCopy();}
    private static synchronized void ensure(){
        if(monitor||!available())return;monitor=true;
        Thread t=new Thread(()->{
            while(true){try{
                if(localFailure){Thread.sleep(200);continue;}
                if(bridge==null)bridge=LocalApply67.open(System.getenv());var v=bridge.state();String next=v.get("phase");
                if(!PHASES.contains(next))throw new IllegalStateException("Unknown local phase");
                if(!nonce.isEmpty()&&!nonce.equals(v.get("nonce"))){if(!localFailure&&System.currentTimeMillis()-sent>120000)phase="STALLED";Thread.sleep(200);continue;}
                JsonObject s=new JsonObject();s.addProperty("phase",next);s.addProperty("reportDirectory",v.get("directory"));s.add("delta",JsonParser.parseString(v.get("delta")));if(v.containsKey("failureReason"))s.addProperty("failureReason",v.get("failureReason"));
                synchronized(ManagedApplyPanel67.class){
                    if(localFailure||!nonce.isEmpty()&&!nonce.equals(v.get("nonce")))continue;state=s;
                    boolean idle=Set.of("ACTIVE","CHECKED","FAILED","CANCELLED","COMPLETE").contains(next);
                    if(idle)cancelPending=false;
                    boolean stale=System.currentTimeMillis()-Long.parseLong(v.get("updated"))>120000;phase=stale?"STALLED":cancelPending?"CANCELLING":next;busy=stale||!idle;
                }Thread.sleep(200);
            }catch(InterruptedException ended){Thread.currentThread().interrupt();return;}
            catch(Exception unavailable){synchronized(ManagedApplyPanel67.class){if(!localFailure)phase="STALLED";}try{Thread.sleep(1000);}catch(InterruptedException ended){Thread.currentThread().interrupt();return;}}}
        },"genesis-managed-panel67");t.setDaemon(true);t.start();
    }
    public static synchronized void start(JsonObject prefs,boolean check){
        ensure();if(busy)return;
        final String account=Minecraft.getInstance().getUser().getProfileId().toString();
        final String path=prefs.has("jar")?prefs.get("jar").getAsString():"";
        final String target=prefs.has("side")?prefs.get("side").getAsString():"BOTH";
        localFailure=false;state=new JsonObject();requestSent=false;cancelPending=false;nonce=UUID.randomUUID().toString();sent=System.currentTimeMillis();busy=true;phase="VALIDATING";final String request=nonce;
        Thread t=new Thread(()->{String stage="bridge";try{
            var b=LocalApply67.open(System.getenv());if(b==null||!account.equals(b.account))throw new IllegalStateException("Local account binding");
            stage="jar_read";String filename=path.isBlank()?"":FirstLaunchFiles.safe(Path.of(path)).toString();
            if(!filename.isEmpty()){Path file=Path.of(filename);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new java.io.IOException("JAR_MISSING68");long bytes=Files.size(file);if(bytes<1||bytes>64L*1024*1024)throw new java.io.IOException("JAR_SIZE68");}
            String digest=filename.isEmpty()?"":FirstLaunchFiles.hash(Path.of(filename),64L*1024*1024);
            synchronized(ManagedApplyPanel67.class){
                if(!request.equals(nonce))return;
                jar=filename;sha=digest;side=target;bridge=b;stage="bridge";b.send(request,check?"CHECK":"PREPARE",filename,digest,target);requestSent=true;
            }
        }catch(Exception bad){synchronized(ManagedApplyPanel67.class){if(request.equals(nonce)){localFailure=true;JsonObject s=new JsonObject();s.addProperty("failureKey",bad instanceof InvalidPathException?"jar_path":bad instanceof NoSuchFileException||"JAR_MISSING68".equals(bad.getMessage())?"jar_missing":"JAR_SIZE68".equals(bad.getMessage())?"jar_size":stage);state=s;phase="FAILED";busy=false;}}}},"genesis-apply-request67");t.setDaemon(true);t.start();
    }
    public static synchronized void command(String action){
        if(!Set.of("ARM","CANCEL").contains(action)||nonce.isEmpty())return;
        if(action.equals("CANCEL")&&!requestSent){nonce="";phase="CANCELLED";busy=false;return;}
        if(bridge==null)return;
        if(action.equals("ARM")&&!phase.equals("READY"))return;
        try{bridge.send(nonce,action,jar,sha,side);if(action.equals("CANCEL")){cancelPending=true;phase="CANCELLING";busy=true;}}catch(Exception bad){phase="STALLED";busy=true;}
    }
}
