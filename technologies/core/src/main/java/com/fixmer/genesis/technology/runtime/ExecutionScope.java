package com.fixmer.genesis.technology.runtime;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded ownership of a run's resources. Callbacks never execute under the scope lock. */
public final class ExecutionScope implements AutoCloseable {
    public enum Kind { EXECUTOR, EVENT, TIMER, BIND, BLOCK, ACTION }
    public record Snapshot(boolean cancelled, Map<Kind,Integer> counts, Throwable failure) {
        public int count(Kind kind) { return counts.getOrDefault(kind,0); }
        public int resources() { return counts.values().stream().mapToInt(Integer::intValue).sum(); }
    }
    public final class Lease implements AutoCloseable {
        private final Kind kind;
        private final Runnable cancel;
        private boolean active=true, notified;
        private Lease(Kind kind, Runnable cancel) { this.kind=kind; this.cancel=cancel; }
        public boolean active() { synchronized(ExecutionScope.this){return active;} }
        @Override public void close() { synchronized(ExecutionScope.this){active=false;if(leases.remove(this)!=null){counts.computeIfPresent(kind,(k,n)->n==1?null:n-1);}} }
        private void notifyCancellation() {
            synchronized(ExecutionScope.this) {
                if (!active || notified) return;
                notified=true;
            }
            try { cancel.run(); }
            catch (Throwable error) { recordFailure(error); close(); }
            finally { if(kind!=Kind.EXECUTOR)close(); }
        }
    }
    private final Map<Lease,Boolean> leases=new IdentityHashMap<>();
    private final EnumMap<Kind,Integer> counts=new EnumMap<>(Kind.class);
    private final int limit;
    private volatile boolean cancelled;
    private volatile Throwable failure;
    public ExecutionScope(){this(1024);}
    public ExecutionScope(int limit){if(limit<1)throw new IllegalArgumentException("Resource limit must be positive");this.limit=limit;}
    public boolean cancelled(){return cancelled;}
    public Lease own(Kind kind, Runnable cancel) {
        Lease lease=new Lease(Objects.requireNonNull(kind),Objects.requireNonNull(cancel));
        boolean stopped;
        synchronized(this){
            stopped=cancelled;
            if(!stopped){
                if(leases.size()>=limit)throw new IllegalStateException("Run resource limit exceeded: "+limit);
                leases.put(lease,Boolean.TRUE);counts.merge(kind,1,Integer::sum);
            }
        }
        if(stopped){lease.notifyCancellation();lease.close();}
        return lease;
    }
    private synchronized void recordFailure(Throwable error){if(failure==null)failure=Objects.requireNonNull(error);}
    public void fail(Throwable error){recordFailure(error);close();}
    @Override public void close(){
        List<Lease> owned;
        synchronized(this){if(cancelled)return;cancelled=true;owned=List.copyOf(leases.keySet());}
        for(Lease lease:owned)lease.notifyCancellation();
    }
    public synchronized Snapshot snapshot(){
        return new Snapshot(cancelled,Map.copyOf(counts),failure);
    }
}
