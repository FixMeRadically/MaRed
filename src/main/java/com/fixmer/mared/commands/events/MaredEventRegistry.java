package com.fixmer.mared.commands.events;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import com.fixmer.genesis.technology.runtime.ExecutionScope;
import com.fixmer.mared.Mared;
import com.fixmer.mared.commands.engine.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Registration mutations are serialized; script callbacks always run outside the registry lock. */
public final class MaredEventRegistry {
    private MaredEventRegistry() {}
    public static final class Entry {
        public final String type;
        public final List<MaredScriptCommand> body;
        public final MaredScriptContext ctx;
        public final boolean persistent;
        private ExecutionScope.Lease lease;
        public Entry(String type,List<MaredScriptCommand> body,MaredScriptContext ctx,boolean persistent){
            this.type=Objects.requireNonNull(type);this.body=List.copyOf(body);this.ctx=ctx;this.persistent=persistent;
        }
    }
    private static final class Timer {
        final int period;
        int remaining;
        final List<MaredScriptCommand> body;
        final MaredScriptContext ctx;
        final boolean persistent;
        ExecutionScope.Lease lease;
        Timer(int ticks,boolean repeat,List<MaredScriptCommand> body,MaredScriptContext ctx,boolean persistent){
            period=repeat?ticks:0;remaining=ticks;this.body=List.copyOf(body);this.ctx=ctx;this.persistent=persistent;
        }
    }
    private static final Map<String,CopyOnWriteArrayList<Entry>> REGISTRY=new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Timer> EVERY=new CopyOnWriteArrayList<>(), AFTER=new CopyOnWriteArrayList<>();
    private static final Set<String> FIRING=ConcurrentHashMap.newKeySet();
    private static final AtomicInteger VERSION=new AtomicInteger();
    private static volatile long suppressUntilMs,serverTickCounter;
    private static final ThreadLocal<Entry> CURRENT_ENTRY=new ThreadLocal<>();
    public static int getVersion(){return VERSION.get();}
    public static Entry currentEntry(){return CURRENT_ENTRY.get();}
    public static void setCurrentEntry(Entry entry){if(entry==null)CURRENT_ENTRY.remove();else CURRENT_ENTRY.set(entry);}
    public static void clearCurrentEntry(){CURRENT_ENTRY.remove();}
    public static boolean has(String type){var list=REGISTRY.get(type);return list!=null&&!list.isEmpty();}
    public static boolean hasPersistent(String type){var list=REGISTRY.get(type);return list!=null&&list.stream().anyMatch(e->e.persistent);}
    public static int count(String type){var list=REGISTRY.get(type);return list==null?0:list.size();}
    public static int totalCount(){return REGISTRY.values().stream().mapToInt(List::size).sum()+EVERY.size()+AFTER.size();}
    public static List<String> types(){return new ArrayList<>(REGISTRY.keySet());}
    private static ExecutionScope scope(MaredScriptContext ctx){return ctx==null?null:ctx.executionScope();}
    private static boolean alive(MaredScriptContext ctx){return scope(ctx)==null||!scope(ctx).cancelled();}
    private static boolean matches(MaredScriptContext ctx,MinecraftServer server){
        return alive(ctx)&&(scope(ctx)==null||ctx.getServer()==server);
    }
    public static void register(String type,List<MaredScriptCommand> body,MaredScriptContext ctx,boolean replace){register(type,body,ctx,replace,false);}
    public static synchronized void register(String type,List<MaredScriptCommand> body,MaredScriptContext ctx,boolean replace,boolean persistent){
        if(!alive(ctx))return;
        var entry=new Entry(type,body,ctx,persistent);
        var list=REGISTRY.computeIfAbsent(type,k->new CopyOnWriteArrayList<>());
        if(replace)for(var old:list)if(old.persistent==persistent&&(scope(ctx)==null||scope(old.ctx)==scope(ctx)))removeEntry(old);
        // removeEntry may have removed the previous last entry's map slot.
        list=REGISTRY.computeIfAbsent(type,k->new CopyOnWriteArrayList<>());
        list.add(entry);
        try{if(scope(ctx)!=null)entry.lease=scope(ctx).own(ExecutionScope.Kind.EVENT,()->removeEntry(entry));}
        catch(RuntimeException error){removeEntry(entry);throw error;}
        VERSION.incrementAndGet();
    }
    public static void registerEvery(int period,List<MaredScriptCommand> body,MaredScriptContext ctx,boolean persistent){
        if(period<1)throw new IllegalArgumentException("Every period must be positive");
        registerTimer(new Timer(period,true,body,ctx,persistent),EVERY);
    }
    public static void registerAfter(int delay,List<MaredScriptCommand> body,MaredScriptContext ctx){
        if(delay<0)throw new IllegalArgumentException("After delay must not be negative");
        registerTimer(new Timer(delay,false,body,ctx,ctx!=null&&ctx.isPersistent()),AFTER);
    }
    private static synchronized void registerTimer(Timer timer,CopyOnWriteArrayList<Timer> list){
        if(!alive(timer.ctx))return;list.add(timer);
        try{if(scope(timer.ctx)!=null)timer.lease=scope(timer.ctx).own(ExecutionScope.Kind.TIMER,()->removeTimer(timer,list));}
        catch(RuntimeException error){removeTimer(timer,list);throw error;}
        VERSION.incrementAndGet();
    }
    private static synchronized void removeTimer(Timer timer,CopyOnWriteArrayList<Timer> list){
        if(list.remove(timer)){if(timer.lease!=null)timer.lease.close();VERSION.incrementAndGet();}
    }
    public static synchronized boolean removeEntry(Entry entry){
        if(entry==null)return false;var list=REGISTRY.get(entry.type);
        if(list==null||!list.remove(entry))return false;
        if(entry.lease!=null)entry.lease.close();if(list.isEmpty())REGISTRY.remove(entry.type,list);
        VERSION.incrementAndGet();return true;
    }
    private static synchronized void removeMatching(java.util.function.Predicate<Entry> entries,java.util.function.Predicate<Timer> timers){
        for(var list:REGISTRY.values())for(var entry:list)if(entries.test(entry))removeEntry(entry);
        for(var timer:EVERY)if(timers.test(timer))removeTimer(timer,EVERY);
        for(var timer:AFTER)if(timers.test(timer))removeTimer(timer,AFTER);
    }
    public static void clear(String type){removeMatching(e->e.type.equals(type),t->false);}
    public static void removeAll(String type){clear(type);}
    public static void clearAll(){removeMatching(e->!e.persistent,t->!t.persistent);}
    public static void clearAllPersistent(){removeMatching(e->e.persistent,t->t.persistent);}
    public static void removeAllEvents(){removeMatching(e->true,t->true);}
    public static void removeAllEvery(){for(var timer:EVERY)removeTimer(timer,EVERY);}
    public static void removeAllAfter(){for(var timer:AFTER)removeTimer(timer,AFTER);}
    public static void removeOwned(ExecutionScope owner,String target){
        boolean all="all".equalsIgnoreCase(target)||"*".equals(target);
        removeMatching(e->scope(e.ctx)==owner&&(all||e.type.equals(target)),
            t->scope(t.ctx)==owner&&(all||t.period>0&&"every".equalsIgnoreCase(target)||t.period==0&&"after".equalsIgnoreCase(target)));
    }
    public static void suppressChat(int ms){suppressUntilMs=System.currentTimeMillis()+ms;}
    public static boolean isChatSuppressed(){return System.currentTimeMillis()<suppressUntilMs;}
    public static long getServerTick(){return serverTickCounter;}
    public static void tickServer(MinecraftServer server){
        serverTickCounter++;
        for(var timer:EVERY){
            if(!matches(timer.ctx,server))continue;
            if(--timer.remaining>0)continue;timer.remaining=timer.period;
            launch(timer.ctx,timer.body,server,null,null);
        }
        for(var timer:AFTER){
            if(!matches(timer.ctx,server))continue;
            if(--timer.remaining>0)continue;
            // Claim one-shot before dispatch; it cannot be selected again on a later tick.
            if(!claimAfter(timer))continue;
            try{launch(timer.ctx,timer.body,server,null,null);}
            finally{if(timer.lease!=null)timer.lease.close();}
        }
    }
    private static synchronized boolean claimAfter(Timer timer){
        boolean claimed=AFTER.remove(timer);if(claimed)VERSION.incrementAndGet();return claimed;
    }
    public static void fire(String type,MinecraftServer server,Map<String,Object> data){
        var live=REGISTRY.get(type);if(live==null||live.isEmpty())return;
        var listeners=List.copyOf(live);
        Map<String,Object> captured=data==null?Map.of():new HashMap<>(data);
        Runnable dispatch=()->{
            if(!FIRING.add(type))return;
            try{
                for(var entry:listeners)if(matches(entry.ctx,server))launch(entry.ctx,entry.body,server,captured,entry);
            }finally{FIRING.remove(type);}
        };
        if(!com.fixmer.mared.technology.runtime.ScriptDispatch.submit(server,dispatch)){
            for(var entry:listeners)if(matches(entry.ctx,server)&&entry.ctx!=null)entry.ctx.log("[warn] event skipped: server dispatch queue full");
        }
    }
    private static void launch(MaredScriptContext base,List<MaredScriptCommand> body,MinecraftServer server,Map<String,Object> data,Entry event){
        if(!matches(base,server))return;
        if(event!=null){var list=REGISTRY.get(event.type);if(list==null||!list.contains(event))return;}
        try{
            var initiator=resolveInitiator(server,data);
            var context=base==null?new MaredScriptContext(initiator,server,msg->{}):base.forkFor(initiator!=null?initiator:base.getInitiator(),server);
            context.setEventEntry(event);context.refreshPlayerData();
            if(data!=null&&!data.isEmpty())context.refreshEventData(data);
            MaredScriptRunner.start(new MaredScriptExecutor(context,body));
        }catch(Throwable error){if(scope(base)!=null)scope(base).fail(error);Mared.LOGGER.error("[Mared] Listener dispatch failed",error);}
    }
    private static ServerPlayer resolveInitiator(MinecraftServer server,Map<String,Object> data){
        if(server==null||data==null)return null;
        Object value=data.get("uuid");if(value instanceof String id)try{var player=server.getPlayerList().getPlayer(UUID.fromString(id));if(player!=null)return player;}catch(IllegalArgumentException ignored){}
        value=data.get("player");return value instanceof String name?server.getPlayerList().getPlayerByName(name):null;
    }
}
