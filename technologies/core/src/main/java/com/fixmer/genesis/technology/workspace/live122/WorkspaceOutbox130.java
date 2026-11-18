package com.fixmer.genesis.technology.workspace.live122;

import java.util.*;
import java.util.function.*;

/** User commands wait behind the current transport frame instead of being silently ignored. */
public final class WorkspaceOutbox130 {
    public record Entry(Map<String,String> request,Consumer<Map<String,String>> callback){}
    private final ArrayDeque<Entry> queue=new ArrayDeque<>();
    public boolean offer(Map<String,String> request,Consumer<Map<String,String>> callback){
        WorkspaceHub122.request(request);if(queue.size()>=16)return false;
        queue.addLast(new Entry(Map.copyOf(request),callback));return true;
    }
    public Entry poll(){return queue.pollFirst();}
    public boolean any(Predicate<Map<String,String>> predicate){return queue.stream().anyMatch(e->predicate.test(e.request()));}
    public void clear(){queue.clear();}
    public int size(){return queue.size();}
}
