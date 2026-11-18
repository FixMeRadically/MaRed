package com.fixmer.genesis.technology.workspace.live122;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** A bounded transient lane: while the consumer is busy, retain only the newest value per key. */
public final class LatestLane154<K,V> implements AutoCloseable {
    private final Executor executor;
    private final Consumer<V> consume;
    private final Consumer<RuntimeException> failure;
    private final int limit;
    private final Map<K,V> latest=new LinkedHashMap<>();
    private boolean scheduled,closed;
    public LatestLane154(Executor executor,int limit,Consumer<V> consume,Consumer<RuntimeException> failure){
        this.executor=Objects.requireNonNull(executor);this.consume=Objects.requireNonNull(consume);this.failure=Objects.requireNonNull(failure);
        if(limit<1)throw new IllegalArgumentException("LIMIT");this.limit=limit;
    }
    public synchronized boolean offer(K key,V value){
        Objects.requireNonNull(key);Objects.requireNonNull(value);
        if(closed||!latest.containsKey(key)&&latest.size()>=limit)return false;
        latest.put(key,value);
        if(!scheduled){scheduled=true;try{executor.execute(this::drain);}catch(RuntimeException rejected){scheduled=false;latest.clear();report(rejected);return false;}}
        return true;
    }
    private void drain(){
        while(true){V value;
            synchronized(this){if(closed||latest.isEmpty()){scheduled=false;return;}var it=latest.entrySet().iterator();value=it.next().getValue();it.remove();}
            try{consume.accept(value);}catch(RuntimeException error){report(error);}
        }
    }
    private void report(RuntimeException error){
        // A failing logger must not strand the lane in its scheduled state.
        try{failure.accept(error);}catch(RuntimeException reportingFailure){
            if(reportingFailure!=error)error.addSuppressed(reportingFailure);
        }
    }
    public synchronized void remove(K key){latest.remove(key);}
    @Override public synchronized void close(){closed=true;latest.clear();}
}
