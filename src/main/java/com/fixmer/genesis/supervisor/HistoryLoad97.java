package com.fixmer.genesis.supervisor;
import java.nio.file.Path;import java.util.concurrent.*;import java.util.function.BooleanSupplier;
/** Per-screen asynchronous reader. Closing or a newer revision invalidates queued publication. */
public final class HistoryLoad97 implements AutoCloseable {
    private static final ExecutorService IO=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"genesis-history97");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final Path profile;private final Executor publish;private final BooleanSupplier visible;private final ExecutorService worker;
    private long revision;private Future<?> task;private String state="IDLE";private RestartHistory97.Snapshot snapshot=RestartHistory97.Snapshot.empty();
    public HistoryLoad97(Path profile,Executor publish,BooleanSupplier visible){this(profile,publish,visible,IO);}
    HistoryLoad97(Path profile,Executor publish,BooleanSupplier visible,ExecutorService worker){this.profile=profile;this.publish=publish;this.visible=visible;this.worker=worker;}
    public synchronized String state(){return state;}public synchronized RestartHistory97.Snapshot snapshot(){return snapshot;}
    public synchronized boolean refresh(){
        if(state.equals("CLOSED")||state.equals("READING"))return false;long current=++revision;state="READING";
        try{task=worker.submit(()->{
            RestartHistory97.Snapshot loaded=null;boolean failed=false;try{loaded=RestartHistory97.read(profile);}catch(Exception error){failed=true;}
            var result=loaded;var error=failed;
            try{publish.execute(()->{synchronized(this){if(revision!=current||state.equals("CLOSED"))return;try{if(!visible.getAsBoolean()){state="IDLE";return;}}
            catch(RuntimeException visibilityFailure){state="ERROR";return;}if(!error)snapshot=result;state=error?"ERROR":"READY";}});}catch(RuntimeException ignored){synchronized(this){if(revision==current&&!state.equals("CLOSED"))state="ERROR";}}
        });return true;}catch(RuntimeException busy){state="ERROR";return false;}
    }
    @Override public synchronized void close(){if(state.equals("CLOSED"))return;revision++;state="CLOSED";if(task!=null){
        task.cancel(true);
        if(worker instanceof ThreadPoolExecutor pool&&task instanceof Runnable queued)pool.remove(queued);
        task=null;
    }}
}
