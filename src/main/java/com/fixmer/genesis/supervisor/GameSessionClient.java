package com.fixmer.genesis.supervisor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Lives in the Minecraft JVM. Independent of render ticks, Gradle, HTTP and GUI classes. */
public final class GameSessionClient {
    private static GameSessionClient instance;
    private static volatile boolean frameHandoff;
    private static String requestedFrameKind="";
    private boolean frameReady;
    private boolean guard101,attention101,abort101;
    private String failure101="";
    private final IntegratedState90 integrated90=new IntegratedState90();
    private final Path file;
    private final String id;
    private final long pid=ProcessHandle.current().pid();
    private final long processStart=ProcessHandle.current().info().startInstant().map(java.time.Instant::toEpochMilli).orElse(0L);
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"genesis-session-heartbeat");t.setDaemon(true);return t;});
    private GameLease.Lease lease;
    private long sequence;
    private boolean closing,exiting,ready;
    private String phase="mod_loaded";
    private final long jvmStartMs=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
    private GameSessionClient(Path file,String id){
        this.file=file;this.id=id;
        lease=GameLease.hold(file.resolveSibling("game.lease"),id,pid,processStart);
    }
    public static synchronized void start(){
        if(instance!=null||!"1".equals(System.getenv("GENESIS_SUPERVISOR")))return;
        String raw=System.getenv("GENESIS_SESSION_FILE"),launch=System.getenv("GENESIS_LAUNCH_ID"),shutdown=System.getenv("GENESIS_SHUTDOWN_FILE");
        if(raw==null||launch==null||shutdown==null)return;
        try{
            UUID.fromString(launch);
            Path file=Path.of(raw).toAbsolutePath().normalize(),stop=Path.of(shutdown).toAbsolutePath().normalize();
            if(!file.getFileName().toString().equals("session.properties")||!stop.getFileName().toString().equals("shutdown.request")
                ||!file.getParent().equals(stop.getParent())||!file.getParent().getFileName().toString().equals(launch))return;
            RuntimeFiles.safe(file);
            if(!Files.isDirectory(file.getParent(),LinkOption.NOFOLLOW_LINKS))return;
            GameSessionClient client=new GameSessionClient(file,launch);instance=client;
            Runtime.getRuntime().addShutdownHook(new Thread(client::exiting,"genesis-session-exit"));
            // Finish fallible worker setup before publishing a lease token to the controller.
            client.timer.scheduleWithFixedDelay(client::heartbeat,1,1,TimeUnit.SECONDS);
            client.write();
        }catch(IOException|RuntimeException ignored){if(instance!=null){instance.timer.shutdownNow();if(instance.lease!=null)try{instance.lease.close();}catch(IOException ignoredClose){}}instance=null;}
    }
    public static synchronized boolean supervised90(){return instance!=null;}
    public static synchronized void integrated90(String epoch,String phase){if(instance==null)return;synchronized(instance){try{instance.integrated90.accept(epoch,phase);System.out.println("[Genesis integrated90] "+phase);instance.write();}catch(RuntimeException ignored){}}}
    public static synchronized void frameHandoff(boolean enabled){frameHandoff=enabled;}
    public static synchronized boolean frameRequested(){return !requestedFrameKind.isEmpty();}
    public static synchronized boolean worldFrameRequested(){return "world_ready".equals(requestedFrameKind);}
    public static synchronized void milestone(String phase){
        if(phase==null||!phase.matches("[a-z_]{1,48}"))return;
        if(Set.of("world_ready","title_ready","return_failed").contains(phase))requestedFrameKind=phase;
        if(instance==null)return;
        if("return_failed".equals(phase))failure101("RETURN_FAILED");
        synchronized(instance){instance.phase=phase;if(Set.of("world_ready","title_ready","return_failed").contains(phase)){instance.ready=true;}if("frame_ready".equals(phase))instance.frameReady=true;}
        try{instance.timer.execute(instance::heartbeat);}catch(RejectedExecutionException ignored) { }
    }
    /** Called when Minecraft begins its normal shutdown; hooks alone also run on crashes. */
    public static synchronized void normalClosing(){Handoff96.normalClosing();if(instance!=null)instance.closing();}
    private synchronized void closing(){closing=true;write();}
    private synchronized void exiting(){exiting=true;timer.shutdownNow();write();}
    private synchronized void heartbeat(){if(!exiting){startup101();write();}}
    private void startup101(){
        String nonce=System.getenv("GENESIS_STARTUP_NONCE101"),trial=System.getenv("GENESIS_MOD_TRIAL"),raw=System.getenv("GENESIS_GAME_DIR101");
        if(nonce==null||trial==null||raw==null)return;
        try{
            Path game=Path.of(raw).toAbsolutePath().normalize();
            if(!file.equals(game.resolve("config/mared/dev/runtime").resolve(id).resolve("session.properties")))return;
            if(!guard101)guard101=new ModDeployment(game).acknowledgeStartup101(trial,id,nonce);
            String signal=StartupAbort101.read(file.resolveSibling("startup-stop101.properties"),id,nonce);
            if(!signal.isEmpty()){abort101=true;if(failure101.isEmpty())failure101=signal;}
        }catch(IOException|RuntimeException unavailable){/* Retry capability registration on a later heartbeat. */}
    }
    public static synchronized void failure101(String reason){if(instance==null||!StartupAbort101.reason(reason))return;synchronized(instance){if(instance.failure101.isEmpty())instance.failure101=reason;instance.write();}}
    public static synchronized void attention101(boolean attention){if(instance==null)return;synchronized(instance){instance.attention101=attention;}}
    public static synchronized boolean abortRequested101(){if(instance==null)return false;synchronized(instance){return instance.abort101;}}
    private void write(){
        Path temp=null;
        try{
            RuntimeFiles.safe(file);
            // Never recreate a launch directory that the controller has already cleaned.
            if(!Files.isDirectory(file.getParent(),LinkOption.NOFOLLOW_LINKS)){timer.shutdownNow();return;}
            Properties p=new Properties();p.setProperty("schema","1");p.setProperty("launch",id);p.setProperty("pid",Long.toString(pid));
            p.setProperty("processStart",Long.toString(processStart));p.setProperty("sequence",Long.toString(++sequence));
            p.setProperty("state",exiting?"EXITED":closing?"CLOSING":"RUNNING");p.setProperty("normalClose",Boolean.toString(closing));
            if(lease!=null&&lease.valid())p.setProperty("leaseToken",lease.token);
            if(integrated90.present()){p.setProperty("integrated90",integrated90.phase());p.setProperty("integratedEpoch90",integrated90.epoch());}
            p.setProperty("guard101",Boolean.toString(guard101));p.setProperty("attention101",Boolean.toString(attention101));p.setProperty("failure101",failure101);
            p.setProperty("frameHandoff",Boolean.toString(frameHandoff));p.setProperty("frameReady",Boolean.toString(frameReady));p.setProperty("ready",Boolean.toString(ready));p.setProperty("phase",phase);p.setProperty("jvmStartMs",Long.toString(jvmStartMs));
            temp=Files.createTempFile(file.getParent(),"session-",".tmp");
            try(Writer out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(out,"Genesis game session");}
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException|RuntimeException ignored){/* OS identity remains the primary liveness check. */}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }
}
