package com.fixmer.genesis.startup;

import java.lang.ref.*;
import java.util.*;
import java.util.function.Function;

/** Indexes immutable generations of parsed AT rules, never transformed class bytes. */
public final class AccessIndex {
    private AccessIndex() {}
    private static final int MAX_OWNERS=4, MAX_RULES=32768;
    private static final ReferenceQueue<Map<?,?>> QUEUE=new ReferenceQueue<>();
    private static final Map<Owner,Generation> CACHE=new LinkedHashMap<>();
    private static long queries,hits,rebuilds,rulesIndexed,fallbacks;
    private static final class Owner extends WeakReference<Map<?,?>> {
        private final int hash;
        Owner(Map<?,?> owner,ReferenceQueue<Map<?,?>> queue){super(owner,queue);hash=System.identityHashCode(owner);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other || other instanceof Owner key && get()!=null && get()==key.get();}
    }
    private record Generation(WeakReference<Object> token,Map<Object,Map<Object,Map<String,Object>>> index) {}
    /** Functions are supplied by AT's own module, using its original lambda call sites. */
    public static Map<Object,Map<String,Object>> lookup(Map<?,?> rules,Object generation,Object type,
            Function<Object,Object> classOf,Function<Object,Object> kindOf,Function<Object,String> nameOf) {
        Objects.requireNonNull(rules);
        if(type==null&&!rules.isEmpty())throw new NullPointerException();
        if(rules.size()>MAX_RULES){synchronized(CACHE){queries++;fallbacks++;}return scan(rules,type,classOf,kindOf,nameOf);}
        Map<Object,Map<Object,Map<String,Object>>> index;
        synchronized(CACHE) {
            queries++;
            for(Reference<? extends Map<?,?>> key;(key=QUEUE.poll())!=null;)CACHE.remove(key);
            Owner lookup=new Owner(rules,null);Generation entry=CACHE.get(lookup);
            if(entry!=null&&entry.token().get()==generation){hits++;index=entry.index();}
            else {
                index=new HashMap<>();
                for(var rule:rules.entrySet()) {
                    Object classType=classOf.apply(rule.getKey());
                    add(index.computeIfAbsent(classType,k->new HashMap<>()),rule.getValue(),kindOf,nameOf);
                }
                if(entry==null&&CACHE.size()>=MAX_OWNERS)CACHE.remove(CACHE.keySet().iterator().next());
                CACHE.put(new Owner(rules,QUEUE),new Generation(new WeakReference<>(generation),index));
                rebuilds++;rulesIndexed+=rules.size();
            }
        }
        return copy(index.get(type));
    }
    private static Map<Object,Map<String,Object>> scan(Map<?,?> rules,Object type,
            Function<Object,Object> classOf,Function<Object,Object> kindOf,Function<Object,String> nameOf) {
        Map<Object,Map<String,Object>> result=new HashMap<>();
        for(var rule:rules.entrySet())if(type.equals(classOf.apply(rule.getKey())))add(result,rule.getValue(),kindOf,nameOf);
        return result;
    }
    private static void add(Map<Object,Map<String,Object>> output,Object rule,Function<Object,Object> kindOf,Function<Object,String> nameOf) {
        Object kind=Objects.requireNonNull(kindOf.apply(rule),"element cannot be mapped to a null key");
        String name=nameOf.apply(rule);Map<String,Object> group=output.computeIfAbsent(kind,k->new HashMap<>());
        if(group.putIfAbsent(name,Objects.requireNonNull(rule))!=null)throw new IllegalStateException("Duplicate AT target");
    }
    private static Map<Object,Map<String,Object>> copy(Map<Object,Map<String,Object>> source) {
        Map<Object,Map<String,Object>> result=new HashMap<>();
        if(source!=null)source.forEach((kind,values)->result.put(kind,new HashMap<>(values)));
        return result;
    }
    public static long[] counts(){synchronized(CACHE){return new long[]{queries,hits,rebuilds,rulesIndexed,fallbacks};}}
}
