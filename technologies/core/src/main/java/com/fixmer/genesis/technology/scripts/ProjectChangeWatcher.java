package com.fixmer.genesis.technology.scripts;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.*;
/** Polls source fingerprints off the render thread; one stable change produces one build, never a retry loop. */
public final class ProjectChangeWatcher implements AutoCloseable {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r-> {
        Thread t=new Thread(r,"genesis-source-watch");t.setDaemon(true);return t;
    }
    );
    private Future<String> scan;
    private String baseline,candidate;
    private Path root;
    private long nextScan,candidateAt;
    private boolean closed,scheduled;
    public void reset() {
        if(scan!=null)scan.cancel(true);
        scan=null;
        root=null;
        baseline=null;
        candidate=null;
        nextScan=0;scheduled=false;
    }
    /** Seed the last attempted inputs after a JVM restart; saves during downtime remain observable. */
    public void seed(Path directory,String acknowledged) {
        if(acknowledged!=null&&!acknowledged.isEmpty()&&!acknowledged.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid watcher baseline");
        reset();root=directory.toAbsolutePath().normalize();
        baseline=acknowledged==null||acknowledged.isEmpty()?null:acknowledged;
    }
    public String fingerprint() { return baseline==null?"":baseline; }
    /** Used by automation to reject a build whose inputs changed while it ran. */
    public static String capture(Path directory,java.util.function.BooleanSupplier cancelled)throws IOException {
        return BuildInputs.capture(directory,cancelled);
    }
    /** Call only while the selected editor/project is idle. Source hashing does not run in this thread. */
    public boolean poll(Path directory,long now)throws IOException {
        if(closed)return false;
        directory=directory.toAbsolutePath().normalize();
        if(!directory.equals(root)) {
            reset();
            root=directory;
        }
        boolean changed=false;
        if(scan!=null&&scan.isDone()) {
            Future<String> done=scan;
            scan=null;
            String hash;
            try {
                hash=done.get();
            }
            catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Watcher interrupted",e);
            }
            catch(ExecutionException|CancellationException e) {
                throw new IOException("Source watch failed",e);
            }
            if(baseline==null)baseline=hash;
            else if(hash.equals(baseline)) {
                candidate=null;
            }
            else if(!hash.equals(candidate)) {
                candidate=hash;
                candidateAt=now;
            }
            else if(now-candidateAt>=750_000_000L) {
                baseline=hash;
                candidate=null;
                changed=true;
            }
            nextScan=now+1_000_000_000L;
        }
        if(scan==null&&(!scheduled||now-nextScan>=0)) {
            scheduled=true;Path selected=root;
            scan=worker.submit(()->BuildInputs.capture(selected,()->Thread.currentThread().isInterrupted()));
        }
        return changed;
    }
    @Override public void close() {
        closed=true;
        reset();
        worker.shutdownNow();
    }
}
