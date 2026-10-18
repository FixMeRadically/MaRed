package com.fixmer.genesis.supervisor;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/** Bounded maintenance queue. Admission is controlled by client readiness, never by a timer alone. */
public final class StartupWorkQueue implements AutoCloseable {
    private record Job(String id,Runnable action) { }
    private final ArrayDeque<Job> pending=new ArrayDeque<>();
    private final Set<String> admitted=new HashSet<>();
    private final Executor worker;
    private final BiConsumer<String,Throwable> errors;
    private final long quietNs;
    private final int capacity;
    private long readySince;
    private boolean ready,stable,dispatching,closed;
    public StartupWorkQueue(long quietMs,int capacity,Executor worker,BiConsumer<String,Throwable> errors){
        if(quietMs<0||quietMs>30000||capacity<1||capacity>32)throw new IllegalArgumentException();
        this.quietNs=quietMs*1_000_000L;this.capacity=capacity;this.worker=Objects.requireNonNull(worker);this.errors=Objects.requireNonNull(errors);
    }
    public synchronized boolean submit(String id,Runnable action){
        if(closed||id==null||!id.matches("[a-z_]{1,32}")||action==null||admitted.contains(id)||admitted.size()>=capacity)return false;
        admitted.add(id);pending.addLast(new Job(id,action));return true;
    }
    public void tick(boolean canWork,long now){
        boolean dispatch=false;
        synchronized(this){
            if(closed)return;
            if(!canWork){ready=false;stable=false;return;}
            if(!ready){ready=true;readySince=now;}
            stable=now-readySince>=quietNs;
            if(stable&&!dispatching&&!pending.isEmpty()){dispatching=true;dispatch=true;}
        }
        if(dispatch)try{worker.execute(this::drain);}catch(RuntimeException e){synchronized(this){dispatching=false;}report("queue",e);}
    }
    private void drain(){
        try{
            while(true){
                Job job;
                synchronized(this){if(closed||!stable||(job=pending.pollFirst())==null)return;}
                try{job.action.run();}catch(Exception|LinkageError error){report(job.id,error);}
            }
        }finally{synchronized(this){dispatching=false;}}
    }
    private void report(String id,Throwable error){try{errors.accept(id,error);}catch(RuntimeException ignored){}}
    public synchronized boolean stable(){return !closed&&stable;}
    @Override public synchronized void close(){closed=true;stable=false;pending.clear();}
}
