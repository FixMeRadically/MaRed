package com.fixmer.genesis.technology.server.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** File I/O is off the game thread except the explicit save-operation fences and lifecycle boundaries. */
public final class ServerLifecycleReporter implements AutoCloseable {
    @FunctionalInterface public interface Stop { void run()throws Exception; }
    @FunctionalInterface public interface Save { boolean run()throws Exception; }
    private final Thread owner=Thread.currentThread();
    private final ServerLifecycleState state=new ServerLifecycleState();
    private final ServerLifecycleChannel channel;private final long pid;private final String start;
    private final ScheduledExecutorService worker;
    private volatile String error="";private volatile boolean closed;
    private volatile long lastPublished;private volatile ServerLifecycleChannel.Request pending;
    private ServerLifecycleReporter(ServerLifecycleChannel channel,long pid,String start)throws IOException {
        this.channel=channel;this.pid=pid;this.start=start;
        if(channel==null)worker=null;
        else{
            publish();worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"genesis-server-lifecycle");t.setDaemon(true);return t;});worker.scheduleWithFixedDelay(this::poll,0,250,TimeUnit.MILLISECONDS);
        }
    }
    public static ServerLifecycleReporter open(Map<String,String> env)throws IOException {
        if(!"1".equals(env.get("GENESIS_SERVER_SUPERVISED")))return new ServerLifecycleReporter(null,0,"");
        String id=env.getOrDefault("GENESIS_SERVER_RUN_ID",""),path=env.getOrDefault("GENESIS_SERVER_RUN_DIR","");if(!ServerFiles.canonicalUuid(id)||path.isBlank())throw new IOException("LIFECYCLE_ENVIRONMENT_INCOMPLETE");
        Path run=Path.of(path);if(!run.isAbsolute()||!run.normalize().getFileName().toString().equals(id))throw new IOException("LIFECYCLE_RUN_PATH_MISMATCH");
        ServerLifecycleChannel channel=ServerLifecycleChannel.connect(run);
        try{return new ServerLifecycleReporter(channel,ProcessHandle.current().pid(),ServerFiles.start(ProcessHandle.current()));}catch(IOException|RuntimeException bad){channel.close();throw bad;}
    }
    /** Called by the common NeoForge server adapter, once on its server thread. No Minecraft linkage here. */
    public void recordGameRuntime(Class<?> adapter,String serverClass,boolean dedicated,Map<String,String> versions)throws IOException{
        if(Thread.currentThread()!=owner||closed)throw new IOException("GAME_RUNTIME_SERVER_THREAD_REQUIRED");
        if(channel==null)return;
        if(!adapter.getName().equals("com.fixmer.mared.technology.server.GenesisServerLifecycleHooks")||!versions.keySet().equals(Set.of("minecraft","neoforge","genesis")))throw new IOException("GAME_RUNTIME_ADAPTER_REQUIRED");
        byte[] bytes;
        try(var in=adapter.getResourceAsStream("/"+adapter.getName().replace('.','/')+".class")){
            if(in==null)throw new IOException("GAME_RUNTIME_ADAPTER_BYTES_MISSING");bytes=in.readNBytes(512*1024+1);if(bytes.length>512*1024)throw new IOException("GAME_RUNTIME_ADAPTER_SIZE");
        }
        var facts=new Properties();facts.setProperty("adapter",adapter.getName());
        facts.setProperty("adapterSha256",adapterHash(bytes));
        facts.setProperty("serverClass",serverClass);facts.setProperty("serverKind",dedicated?"DEDICATED":"INTEGRATED");facts.putAll(versions);
        facts.setProperty("pid",Long.toString(pid));facts.setProperty("start",start);channel.recordGameRuntime(facts);
    }
    private static String adapterHash(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    public boolean supervised(){return channel!=null;}
    public String error(){return error;}
    public ServerLifecycleState.Snapshot snapshot(){return state.snapshot();}
    public void started(){if(closed)return;state.started();boundary();}
    public void tick(Save save){tick(save,null);}
    public void tick(Save save,Stop stop){
        if(closed)return;state.tick();if(!error.isEmpty())return;
        var request=pending;if(request==null)return;var current=state.snapshot();if(request.sequence()<current.requestSequence()||request.sequence()==current.requestSequence()&&request.id().equals(current.request()))return;
        // Re-read the mailbox immediately before executing a request on the server thread.
        try{request=channel.request(System.currentTimeMillis());if(request==null)return;execute(request,save,stop);}
        catch(IOException bad){fail("LIFECYCLE_IO_FAILED");}
    }
    public ServerLifecycleState.Snapshot checkpoint(Save save)throws IOException {return local(save,null,ServerLifecycleState.Operation.CHECKPOINT);}
    public ServerLifecycleState.Snapshot saveAndStop(Save save,Stop stop)throws IOException {if(stop==null)throw new IOException("STOP_ADAPTER_UNAVAILABLE");return local(save,stop,ServerLifecycleState.Operation.SAVE_AND_STOP);}
    private ServerLifecycleState.Snapshot local(Save save,Stop stop,ServerLifecycleState.Operation operation)throws IOException {
        if(closed)throw new IOException("LIFECYCLE_CLOSED");if(channel!=null)throw new IOException("USE_SUPERVISOR_CHECKPOINT");if(!error.isEmpty())throw new IOException(error);
        long n=state.snapshot().requestSequence();if(n==Long.MAX_VALUE)throw new IOException("CHECKPOINT_COUNTER_OVERFLOW");long now=System.currentTimeMillis();execute(new ServerLifecycleChannel.Request(n+1,UUID.randomUUID(),now,now+30_000,false,operation),save,stop);return state.snapshot();
    }
    private void execute(ServerLifecycleChannel.Request request,Save save,Stop stop)throws IOException {
        if(request.operation()==ServerLifecycleState.Operation.SAVE_AND_STOP&&stop==null){fail("STOP_ADAPTER_UNAVAILABLE");return;}
        long revision=state.snapshot().revision();boolean begin=state.begin(request.sequence(),request.id(),request.expiresAt(),System.currentTimeMillis(),request.cancelled(),request.operation());
        if(!begin){if(state.snapshot().revision()!=revision)boundary();return;}
        // A failure to record CHECKPOINTING must not call into the world saver.
        try{publish();}catch(IOException bad){state.complete(false,0);fail("LIFECYCLE_IO_FAILED");throw bad;}
        long began=System.nanoTime();boolean success=false;
        try{success=save.run();}catch(Exception failed){success=false;}
        finally{state.complete(success,Math.max(0,(System.nanoTime()-began)/1_000_000));}
        if(success&&request.operation()==ServerLifecycleState.Operation.SAVE_AND_STOP){
            // Cancellation during a long save still prevents the stop when observed before handoff.
            if(channel!=null)try{var latest=channel.request(System.currentTimeMillis());if(latest==null||!latest.id().equals(request.id())||latest.sequence()!=request.sequence()||latest.operation()!=request.operation()||latest.cancelled()||System.currentTimeMillis()>latest.expiresAt()){state.stopCancelled();boundary();return;}}
            catch(IOException failed){state.stopPublicationFailed();fail("LIFECYCLE_IO_FAILED");throw failed;}
            // Confirm the save/stop intent before invoking the irreversible stop adapter.
            try{publish();}catch(IOException failed){state.stopPublicationFailed();fail("LIFECYCLE_IO_FAILED");throw failed;}
            try{stop.run();state.stopRequested();}catch(Exception uncertain){state.stopFailed();}
        }
        boundary();
    }
    public void stopping(){if(closed)return;state.stopping();boundary();}
    public void stopped(){if(closed)return;state.stopped();boundary();}
    private void poll(){
        if(closed||!error.isEmpty())return;
        try{pending=channel.request(System.currentTimeMillis());long now=System.nanoTime();if(now-lastPublished>=TimeUnit.SECONDS.toNanos(1))publish();}
        catch(IOException|RuntimeException bad){fail("LIFECYCLE_IO_FAILED");}
    }
    private void boundary(){if(channel!=null&&error.isEmpty())try{publish();}catch(IOException bad){fail("LIFECYCLE_IO_FAILED");}}
    private void publish()throws IOException {if(channel!=null){channel.publish(state.snapshot(),pid,start);lastPublished=System.nanoTime();}}
    private void fail(String code){error=code;}
    @Override public void close()throws IOException {if(closed)return;closed=true;if(worker!=null)worker.shutdownNow();if(channel!=null)channel.close();}
}
