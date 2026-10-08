package com.fixmer.genesis.technology.runtime;

import java.util.*;
import java.util.function.Consumer;

/** Thread-safe bounded claims and pending host actions. Host effects run only in drain's caller thread. */
public final class OwnedActions<K,T> {
    private static final Object LEGACY=new Object();
    private final int limit;
    private final Map<Object,Map<K,Claim>> claims=new IdentityHashMap<>();
    private final ArrayDeque<Pending> pending=new ArrayDeque<>();
    private int held;
    private final class Claim { final Object owner; final K key; ExecutionScope.Lease lease; Claim(Object owner,K key){this.owner=owner;this.key=key;} }
    private final class Pending { final ExecutionScope owner; final T value; ExecutionScope.Lease lease; Pending(ExecutionScope owner,T value){this.owner=owner;this.value=value;} }
    public OwnedActions(int limit){if(limit<1)throw new IllegalArgumentException("Positive limit required");this.limit=limit;}
    private Object owner(ExecutionScope scope){return scope==null?LEGACY:scope;}
    public synchronized void press(ExecutionScope scope,K key){
        Objects.requireNonNull(key);if(scope!=null&&scope.cancelled())return;
        Object owner=owner(scope);var mine=claims.get(owner);if(mine!=null&&mine.containsKey(key))return;
        if(held+pending.size()>=limit)throw new IllegalStateException("Action limit exceeded");
        if(mine==null){mine=new HashMap<>();claims.put(owner,mine);}
        Claim claim=new Claim(owner,key);mine.put(key,claim);held++;
        try {if(scope!=null)claim.lease=scope.own(ExecutionScope.Kind.ACTION,()->remove(claim));}
        catch(RuntimeException error){remove(claim);throw error;}
    }
    private synchronized void remove(Claim claim){
        var mine=claims.get(claim.owner);
        if(mine!=null&&mine.remove(claim.key,claim)){held--;if(mine.isEmpty())claims.remove(claim.owner);}
        if(claim.lease!=null)claim.lease.close();
    }
    public synchronized void release(ExecutionScope scope,K key){var mine=claims.get(owner(scope));if(mine!=null){var claim=mine.get(key);if(claim!=null)remove(claim);}}
    public synchronized boolean pressed(ExecutionScope scope,K key){var mine=claims.get(owner(scope));return mine!=null&&mine.containsKey(key);}
    public synchronized Set<K> activeKeys(){var result=new HashSet<K>();for(var mine:claims.values())result.addAll(mine.keySet());return Set.copyOf(result);}
    public synchronized void enqueue(ExecutionScope scope,T value){
        Objects.requireNonNull(value);if(scope!=null&&scope.cancelled())return;
        if(held+pending.size()>=limit)throw new IllegalStateException("Action limit exceeded");
        Pending item=new Pending(scope,value);pending.addLast(item);
        try{if(scope!=null)item.lease=scope.own(ExecutionScope.Kind.ACTION,()->remove(item));}
        catch(RuntimeException error){remove(item);throw error;}
    }
    private synchronized void remove(Pending item){pending.remove(item);if(item.lease!=null)item.lease.close();}
    public void drain(int budget,Consumer<T> host){
        Objects.requireNonNull(host);
        for(int i=0;i<budget;i++){
            Pending item;synchronized(this){item=pending.pollFirst();}
            if(item==null)return;
            try{if(item.owner==null||!item.owner.cancelled())host.accept(item.value);}
            catch(RuntimeException error){if(item.owner!=null)item.owner.fail(error);else throw error;}
            finally{if(item.lease!=null)item.lease.close();}
        }
    }
    public synchronized void stop(ExecutionScope scope){
        var mine=claims.get(owner(scope));if(mine!=null)for(Claim claim:List.copyOf(mine.values()))remove(claim);
        for(Pending item:List.copyOf(pending))if(item.owner==scope)remove(item);
    }
    public synchronized void stopAll(){for(var mine:List.copyOf(claims.values()))for(Claim claim:List.copyOf(mine.values()))remove(claim);for(Pending item:List.copyOf(pending))remove(item);}
    public synchronized int pendingCount(){return pending.size();}
}
