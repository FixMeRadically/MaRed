package com.fixmer.genesis.technology.server.process;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

/** Owns server stdin and the direct Process even if its supervisor disappears. */
public final class ServerProcessGuardian {
    private final ServerProcessConfig config;
    private final Path run;
    private final long parentPid;
    private final String parentStart;
    private final Properties status=new Properties();
    private final Map<Long,String> descendants=new LinkedHashMap<>();
    private final AtomicBoolean externalStop=new AtomicBoolean(),ownerChannelLost=new AtomicBoolean(),done=new AtomicBoolean();
    private final AtomicReference<String> logError=new AtomicReference<>("");
    private Process child;
    private ServerLaunchHandoff handoff;
    private Writer input;
    private String childStart="",phase="PREPARING",reason="",error="";
    private long stopAt=-1,forceAt=-1,lastWrite;
    private boolean launchIntent,stopSent,forceSent,parentLost,uncertain;
    private int exitCode;

    private ServerProcessGuardian(ServerProcessConfig config,Path run,long parentPid,String parentStart){this.config=config;this.run=run;this.parentPid=parentPid;this.parentStart=parentStart;}
    public static void main(String[] args){
        if(args.length==2&&args[0].equals("--native-diagnostic")){System.exit(ServerNativeOwnerCli.run(Path.of(args[1])));return;}
        if(args.length!=4){System.err.println("Guardian requires config, run directory and supervisor identity");System.exit(2);return;}
        try{
            var config=ServerProcessConfig.load(Path.of(args[0]));Path run=Path.of(args[1]).toAbsolutePath().normalize();
            if(!run.getParent().equals(config.stateDir().resolve("runs"))||!UUID.fromString(run.getFileName().toString()).toString().equals(run.getFileName().toString()))throw new IOException("Invalid guardian run directory");
            var guardian=new ServerProcessGuardian(config,run,Long.parseLong(args[2]),args[3]);
            try(var lease=new ServerFiles.Lease(run.resolve("guardian.lock"))){
                guardian.initializeBinding();
                ServerFiles.Lease workLease;
                try{workLease=new ServerFiles.Lease(config.workDir().resolve(".genesis-server-process.lock"));}
                catch(IOException busy){guardian.error="SERVER_DIRECTORY_IN_USE_OR_UNWRITABLE";guardian.phase="FAILED";guardian.status.setProperty("guardian.pid",Long.toString(ProcessHandle.current().pid()));guardian.status.setProperty("guardian.start",ServerFiles.start(ProcessHandle.current()));guardian.persist();return;}
                try(workLease){guardian.execute();}
            }
        }catch(Exception bad){System.err.println("Guardian initialization failed: "+bad.getClass().getSimpleName());System.exit(2);}
    }
    private void execute()throws Exception {
        try{executeOwned();}
        finally{
            if(child!=null){
                while(child.isAlive()||descendantsAlive()){
                    beginStop("GUARDIAN_FAILURE");observe();
                    if(Files.exists(run.resolve("force.request"))||!ServerFiles.sameProcess(parentPid,parentStart)&&config.orphanForce()&&elapsed(stopAt)>=config.graceMs())forceOwned();
                    phase=elapsed(stopAt)>=config.graceMs()?"STOP_TIMEOUT":"STOPPING";
                    try{persist();}catch(IOException ignored){}
                    try{Thread.sleep(50);}catch(InterruptedException ignored){externalStop.set(true);}
                }
            }
            done.set(true);
        }
    }
    private void initializeBinding()throws IOException {
        handoff=ServerLaunchHandoff.load(run,parentPid,parentStart);handoff.stamp(status);
        status.setProperty("guardian.pid",Long.toString(ProcessHandle.current().pid()));status.setProperty("guardian.start",ServerFiles.start(ProcessHandle.current()));
        status.setProperty("parent.pid",Long.toString(parentPid));status.setProperty("parent.start",parentStart);status.setProperty("run",run.getFileName().toString());
    }
    private boolean awaitApproval()throws Exception {
        // This pipe belongs to the supervisor object, not merely to its containing JVM.
        var ownerChannel=new Thread(()->{try{System.in.read();}catch(IOException ignored){}finally{ownerChannelLost.set(true);externalStop.set(true);}},"genesis-guardian-owner-channel");ownerChannel.setDaemon(true);ownerChannel.start();
        phase="AWAITING_APPROVAL";persist();
        Properties ready=handoff.ready(ProcessHandle.current().pid(),ServerFiles.start(ProcessHandle.current()));
        ServerFiles.write(run.resolve("handoff.ready"),ready);long started=System.nanoTime();
        while(true){
            if(externalStop.get()||!ServerFiles.sameProcess(parentPid,parentStart)||Files.exists(run.resolve("stop.request"))||Files.exists(run.resolve("force.request"))){reason="CANCELLED_BEFORE_LAUNCH";break;}
            if(elapsed(started)>=ServerLaunchHandoff.TIMEOUT_MS){reason="HANDOFF_APPROVAL_TIMEOUT";break;}
            try{if(handoff.approved(run,ready))return true;}
            catch(IOException invalid){error="HANDOFF_REJECTED";reason="HANDOFF_REJECTED";break;}
            Thread.sleep(25);
        }
        phase="EXITED";persist();done.set(true);return false;
    }
    private void executeOwned()throws Exception {
        if(!awaitApproval())return;
        // Parent identity and cancellation are checked again immediately before the launch intent.
        if(externalStop.get()||!ServerFiles.sameProcess(parentPid,parentStart)||Files.exists(run.resolve("stop.request"))||Files.exists(run.resolve("force.request"))){reason="CANCELLED_BEFORE_LAUNCH";phase="EXITED";persist();done.set(true);return;}
        config.validateLaunch();handoff.unchanged(run);ServerLifecycleChannel.prepare(run);launchIntent=true;persist();
        var command=new ArrayList<String>();command.add(config.javaExecutable().toString());command.addAll(config.arguments());
        var builder=new ProcessBuilder(command).directory(config.workDir().toFile()).redirectErrorStream(true);
        for(String key:List.of("GENESIS_SUPERVISOR","GENESIS_SUPERVISOR_TOKEN","GENESIS_SUPERVISOR_PORT","GENESIS_SESSION_FILE","GENESIS_SHUTDOWN_FILE","GENESIS_TRANSITION_FILE","GENESIS_DEV_MODE"))builder.environment().remove(key);
        builder.environment().put("GENESIS_SERVER_SUPERVISED","1");builder.environment().put("GENESIS_SERVER_RUN_ID",run.getFileName().toString());builder.environment().put("GENESIS_SERVER_RUN_DIR",run.toString());
        try{child=builder.start();}catch(IOException failed){launchIntent=false;phase="FAILED";error="SERVER_SPAWN_FAILED";persist();done.set(true);return;}
        try{childStart=ServerFiles.start(child.toHandle());}catch(IOException failed){uncertain=true;error="CHILD_IDENTITY_UNAVAILABLE";}
        input=new OutputStreamWriter(child.getOutputStream(),StandardCharsets.UTF_8);
        var output=new Thread(this::drainOutput,"genesis-server-output");output.setDaemon(true);output.start();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            externalStop.set(true);long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.graceMs()+config.forceGraceMs()+500);
            while(!done.get()&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}
        },"genesis-server-guardian-shutdown"));
        phase="RUNNING";if(uncertain)beginStop("IDENTITY_FAILURE");
        while(true){
            observe();
            if(stopAt<0)try{if(ServerStopPreparation.shutdownObserved(run,child.pid(),childStart)){stopAt=System.nanoTime();reason="GAME_STOP_REQUESTED";phase="STOPPING";}}catch(IOException invalid){error="INVALID_SHUTDOWN_OBSERVATION";}
            parentLost=!ServerFiles.sameProcess(parentPid,parentStart);
            if(externalStop.get()||parentLost||Files.exists(run.resolve("stop.request")))beginStop(ownerChannelLost.get()?"OWNER_CHANNEL_CLOSED":parentLost?"PARENT_LOST":"STOP_REQUESTED");
            boolean force=Files.exists(run.resolve("force.request"))||parentLost&&config.orphanForce()&&stopAt>=0&&elapsed(stopAt)>=config.graceMs();
            if(force)forceOwned();
            boolean rootAlive=child.isAlive(),descAlive=descendantsAlive();
            if(!rootAlive){exitCode=child.exitValue();if(reason.isEmpty())reason=exitCode==0?"CLEAN_EXIT":"PROCESS_CRASH";}
            if(!rootAlive&&!descAlive){
                if(uncertain&&!Files.exists(run.resolve("acknowledge.request")))phase="OWNERSHIP_UNCERTAIN";
                else{phase="EXITED";break;}
            }else if(!rootAlive)phase="DESCENDANTS_REMAIN";
            else if(forceSent&&elapsed(forceAt)>=config.forceGraceMs())phase="FORCE_TIMEOUT";
            else if(stopAt>=0&&elapsed(stopAt)>=config.graceMs())phase="STOP_TIMEOUT";
            try{if(System.nanoTime()-lastWrite>=TimeUnit.MILLISECONDS.toNanos(250))persist();}
            catch(IOException failed){error="METADATA_WRITE_FAILED";beginStop("METADATA_FAILURE");}
            try{Thread.sleep(50);}catch(InterruptedException interrupted){externalStop.set(true);}
        }
        try{input.close();}catch(IOException ignored){}
        output.join(1000);persist();done.set(true);
    }
    private static long elapsed(long started){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);}
    private void beginStop(String why){
        if(stopAt>=0)return;stopAt=System.nanoTime();reason=why;phase="STOPPING";
        if(child!=null&&child.isAlive())try{if(input==null)input=new OutputStreamWriter(child.getOutputStream(),StandardCharsets.UTF_8);input.write("stop\n");input.flush();stopSent=true;}catch(IOException failed){error="STOP_INPUT_FAILED";}
    }
    private void observe(){
        if(child==null||!child.isAlive())return;
        try{child.toHandle().descendants().limit(65).forEach(handle->{
            if(descendants.containsKey(handle.pid()))return;if(descendants.size()>=64){uncertain=true;return;}
            var start=handle.info().startInstant();if(start.isPresent())descendants.put(handle.pid(),start.get().toString());else uncertain=true;
        });}catch(RuntimeException denied){uncertain=true;}
    }
    private boolean descendantsAlive(){return descendants.entrySet().stream().anyMatch(e->ServerFiles.sameProcess(e.getKey(),e.getValue()));}
    private void forceOwned(){
        if(!forceSent){forceAt=System.nanoTime();forceSent=true;reason="FORCE_REQUESTED";}
        for(var entry:descendants.entrySet())ServerFiles.ownedProcess(entry.getKey(),entry.getValue()).ifPresent(ProcessHandle::destroyForcibly);
        if(child!=null&&child.isAlive())child.destroyForcibly();
    }
    private void persist()throws IOException {
        status.setProperty("phase",phase);status.setProperty("reason",reason);status.setProperty("error",error);status.setProperty("logError",logError.get());
        status.setProperty("launchIntent",Boolean.toString(launchIntent));status.setProperty("child.pid",child==null?"0":Long.toString(child.pid()));status.setProperty("child.start",childStart);
        status.setProperty("ownerChannelLost",Boolean.toString(ownerChannelLost.get()));status.setProperty("stopSent",Boolean.toString(stopSent));status.setProperty("forceSent",Boolean.toString(forceSent));status.setProperty("parentLost",Boolean.toString(parentLost));
        status.setProperty("ownershipUncertain",Boolean.toString(uncertain));status.setProperty("exitCode",Integer.toString(exitCode));status.setProperty("updatedAt",Long.toString(System.currentTimeMillis()));
        status.setProperty("descendants",Integer.toString(descendants.size()));int i=0;for(var entry:descendants.entrySet()){status.setProperty("desc."+i+".pid",Long.toString(entry.getKey()));status.setProperty("desc."+i+".start",entry.getValue());i++;}
        ServerFiles.write(run.resolve("status.properties"),status);lastWrite=System.nanoTime();
    }
    private void drainOutput(){
        Path current=run.resolve("server.log"),previous=run.resolve("server.previous.log");long count=0;
        try(InputStream in=child.getInputStream()){
            OutputStream out=null;boolean discard=false;
            try{
                byte[] bytes=new byte[8192];int n;
                while((n=in.read(bytes))!=-1){
                    if(discard)continue;
                    try{
                        if(out==null){out=Files.newOutputStream(current,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);ServerFiles.privateMode(current,false);}
                        if(count+n>config.maxLogBytes()){
                            out.close();Files.move(current,previous,StandardCopyOption.REPLACE_EXISTING);out=Files.newOutputStream(current,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);ServerFiles.privateMode(current,false);count=0;
                        }
                        int offset=(int)Math.max(0,n-config.maxLogBytes());out.write(bytes,offset,n-offset);out.flush();count+=n-offset;
                    }catch(IOException failed){logError.set("LOG_IO_FAILED");discard=true;if(out!=null)try{out.close();}catch(IOException ignored){}out=null;}
                }
            }finally{if(out!=null)out.close();}
        }catch(IOException failed){logError.compareAndSet("","OUTPUT_PIPE_CLOSED");}
    }
}
