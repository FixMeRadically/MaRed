package com.fixmer.mared.technology.runtime;

import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;

/** Bound client-to-server tasks without retaining a stopped server through global values. */
public final class ScriptDispatch {
    private ScriptDispatch() {}
    private static final WeakHashMap<MinecraftServer,AtomicInteger> QUEUES=new WeakHashMap<>();
    public static boolean submit(MinecraftServer server,Runnable callback){
        if(server==null){callback.run();return true;}
        AtomicInteger queued;
        synchronized(QUEUES){queued=QUEUES.computeIfAbsent(server,s->new AtomicInteger());}
        int count;
        do{count=queued.get();if(count>=256)return false;}while(!queued.compareAndSet(count,count+1));
        var released=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable release=()->{if(released.compareAndSet(false,true))queued.decrementAndGet();};
        try{server.execute(()->{try{callback.run();}finally{release.run();}});}
        catch(RuntimeException | Error error){release.run();throw error;}
        return true;
    }
}
