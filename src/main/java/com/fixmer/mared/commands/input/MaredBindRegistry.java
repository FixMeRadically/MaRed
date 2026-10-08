package com.fixmer.mared.commands.input;

import java.util.*;
import com.fixmer.genesis.technology.runtime.ExecutionScope;
import com.fixmer.mared.commands.engine.*;
import net.minecraft.server.MinecraftServer;

/** Snapshot reads; callbacks and server dispatch occur outside the binding lock. */
public final class MaredBindRegistry {
    private MaredBindRegistry() {}
    public enum BindMode { PRESS, HOLD, RELEASE }
    public static final class Entry {
        public volatile String key;
        public final List<MaredScriptCommand> body;
        public final MaredScriptContext ctx;
        public final boolean blockVanilla;
        public final BindMode mode;
        private ExecutionScope.Lease lease;
        public Entry(String key,List<MaredScriptCommand> body,MaredScriptContext ctx,boolean blockVanilla,BindMode mode){
            this.key=key;this.body=List.copyOf(body);this.ctx=ctx;this.blockVanilla=blockVanilla;this.mode=mode;
        }
    }
    private static final Map<String,List<Entry>> BINDINGS=new LinkedHashMap<>();
    private static final Map<Integer,List<String>> BY_KEYCODE=new HashMap<>();
    private static final Set<String> ACTIVE_HOLDS=new LinkedHashSet<>();
    private static boolean indexDirty=true;
    private static final class BlockClaim {
        final ExecutionScope owner;final int code;ExecutionScope.Lease lease;
        BlockClaim(ExecutionScope owner,int code){this.owner=owner;this.code=code;}
    }
    private static final Map<ExecutionScope,Map<Integer,BlockClaim>> BLOCKS=new IdentityHashMap<>();
    private static ExecutionScope owner(MaredScriptContext ctx){return ctx==null?null:ctx.executionScope();}
    private static boolean alive(MaredScriptContext ctx){return owner(ctx)==null||!owner(ctx).cancelled();}
    private static String canonical(String key){var parsed=MaredKeyNames.parseAny(key);return parsed==null?key:MaredKeyNames.display(parsed);}
    private static int code(String key){var parsed=MaredKeyNames.parseAny(key);return parsed==null?Integer.MIN_VALUE:parsed.keyCode;}
    public static synchronized List<String> keysForCode(int keyCode){
        if(indexDirty){BY_KEYCODE.clear();for(String key:BINDINGS.keySet())BY_KEYCODE.computeIfAbsent(code(key),k->new ArrayList<>()).add(key);indexDirty=false;}
        return List.copyOf(BY_KEYCODE.getOrDefault(keyCode,List.of()));
    }
    public static void replace(String key,List<MaredScriptCommand> body,MaredScriptContext ctx,boolean blockVanilla,BindMode mode){
        synchronized(MaredBindRegistry.class){
            if(!alive(ctx))return;
            for(var entry:entries(key))if(owner(ctx)==null||owner(entry.ctx)==owner(ctx))removeEntry(entry);
            add(key,body,ctx,blockVanilla,mode);
        }
    }
    public static synchronized void add(String key,List<MaredScriptCommand> body,MaredScriptContext ctx,boolean blockVanilla,BindMode mode){
        if(!alive(ctx))return;
        key=canonical(key);
        var entry=new Entry(key,body,ctx,blockVanilla,mode);
        BINDINGS.computeIfAbsent(key,k->new ArrayList<>()).add(entry);indexDirty=true;
        if(blockVanilla)MaredKeyBlocker.claim(code(key),entry,key);
        try{if(owner(ctx)!=null)entry.lease=owner(ctx).own(ExecutionScope.Kind.BIND,()->removeEntry(entry));}
        catch(RuntimeException error){removeEntry(entry);throw error;}
    }
    private static synchronized void removeEntry(Entry entry){
        var list=BINDINGS.get(entry.key);if(list==null||!list.remove(entry))return;
        if(entry.blockVanilla)MaredKeyBlocker.release(code(entry.key),entry);
        if(entry.lease!=null)entry.lease.close();
        if(list.isEmpty())BINDINGS.remove(entry.key);
        if(list.stream().noneMatch(e->e.mode==BindMode.HOLD))ACTIVE_HOLDS.remove(entry.key);indexDirty=true;
    }
    public static void clear(String key){clear(key,null);}
    public static synchronized void clear(String key,MaredScriptContext ctx){
        for(var entry:entries(key))if(owner(ctx)==null||owner(entry.ctx)==owner(ctx))removeEntry(entry);
        if(owner(ctx)==null)MaredKeyBlocker.unblock(code(key));
    }
    public static synchronized boolean rename(String oldKey,String newKey){
        oldKey=canonical(oldKey);newKey=canonical(newKey);
        if(oldKey==null||newKey==null||oldKey.equals(newKey)||!BINDINGS.containsKey(oldKey)||BINDINGS.containsKey(newKey))return false;
        var list=BINDINGS.remove(oldKey);
        for(var entry:list){if(entry.blockVanilla)MaredKeyBlocker.release(code(oldKey),entry);entry.key=newKey;if(entry.blockVanilla)MaredKeyBlocker.claim(code(newKey),entry,newKey);}
        BINDINGS.put(newKey,list);ACTIVE_HOLDS.remove(oldKey);indexDirty=true;return true;
    }
    public static void unblock(String key){unblock(key,null);}
    public static synchronized void unblock(String key,MaredScriptContext ctx){
        for(var entry:entries(key))if(entry.blockVanilla&&(owner(ctx)==null||owner(entry.ctx)==owner(ctx)))removeEntry(entry);
        if(owner(ctx)==null)MaredKeyBlocker.unblock(code(key));
        else {var map=BLOCKS.get(owner(ctx));if(map!=null){var claim=map.get(code(key));if(claim!=null)removeBlock(claim);}}
    }
    public static void block(String key){block(key,null);}
    public static synchronized void block(String key,MaredScriptContext ctx){
        if(!alive(ctx))return;int code=code(key);if(code==Integer.MIN_VALUE||code==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)return;
        if(owner(ctx)==null){MaredKeyBlocker.block(code,key);return;}
        var map=BLOCKS.computeIfAbsent(owner(ctx),k->new HashMap<>());
        if(map.containsKey(code))return;
        var claim=new BlockClaim(owner(ctx),code);map.put(code,claim);MaredKeyBlocker.claim(code,claim,key);
        try{claim.lease=owner(ctx).own(ExecutionScope.Kind.BLOCK,()->removeBlock(claim));}
        catch(RuntimeException error){removeBlock(claim);throw error;}
    }
    private static synchronized void removeBlock(BlockClaim claim){
        var map=BLOCKS.get(claim.owner);if(map==null||map.remove(claim.code)!=claim)return;
        if(map.isEmpty())BLOCKS.remove(claim.owner);MaredKeyBlocker.release(claim.code,claim);
        if(claim.lease!=null)claim.lease.close();
    }
    public static synchronized boolean blockedBy(String key,MaredScriptContext ctx){
        if(owner(ctx)==null)return MaredKeyBlocker.isBlocked(code(key));
        var map=BLOCKS.get(owner(ctx));
        return map!=null&&map.containsKey(code(key))||entries(key).stream().anyMatch(e->e.blockVanilla&&owner(e.ctx)==owner(ctx));
    }
    public static synchronized void clearAll(){
        for(var list:List.copyOf(BINDINGS.values()))for(var entry:List.copyOf(list))removeEntry(entry);
        for(var map:List.copyOf(BLOCKS.values()))for(var claim:List.copyOf(map.values()))removeBlock(claim);
        ACTIVE_HOLDS.clear();MaredKeyBlocker.clear();
    }
    public static synchronized List<String> keys(){return List.copyOf(BINDINGS.keySet());}
    public static synchronized List<Entry> entries(String key){return List.copyOf(BINDINGS.getOrDefault(canonical(key),List.of()));}
    public static synchronized Map<String,List<Entry>> all(){var copy=new LinkedHashMap<String,List<Entry>>();BINDINGS.forEach((k,v)->copy.put(k,List.copyOf(v)));return Collections.unmodifiableMap(copy);}
    public static synchronized int totalCount(){return BINDINGS.values().stream().mapToInt(List::size).sum();}
    public static boolean hasBlocking(String key){return entries(key).stream().anyMatch(e->e.blockVanilla);}
    public static void fire(String key,MinecraftServer server){dispatch(key,server,BindMode.PRESS);}
    public static void fireRelease(String key,MinecraftServer server){dispatch(key,server,BindMode.RELEASE);}
    public static void startHold(String key,MinecraftServer server){
        key=canonical(key);
        synchronized(MaredBindRegistry.class){if(entries(key).stream().noneMatch(e->e.mode==BindMode.HOLD))return;ACTIVE_HOLDS.add(key);}
        dispatch(key,server,BindMode.HOLD);
    }
    public static synchronized void stopAllHolds(){ACTIVE_HOLDS.clear();}
    public static synchronized void stopHold(String key){ACTIVE_HOLDS.remove(canonical(key));}
    public static void tickHolds(MinecraftServer server){
        List<String> keys;synchronized(MaredBindRegistry.class){keys=List.copyOf(ACTIVE_HOLDS);}
        for(String key:keys)dispatch(key,server,BindMode.HOLD);
    }
    private static void dispatch(String key,MinecraftServer server,BindMode mode){
        String capturedKey=canonical(key);
        for(var entry:entries(capturedKey))if(entry.mode==mode){
            Runnable callback=()->{
                synchronized(MaredBindRegistry.class){if(!capturedKey.equals(entry.key)||!entries(capturedKey).contains(entry)||!alive(entry.ctx))return;}
                if(owner(entry.ctx)!=null&&entry.ctx.getServer()!=server)return;
                try{
                    var base=entry.ctx==null?new MaredScriptContext(null,server,msg->{}):entry.ctx;
                    var context=base.forkFor(base.getInitiator(),server);
                    context.setEventEntry(null); // A key invocation is not the on-handler that created it.
                    MaredScriptRunner.start(new MaredScriptExecutor(context,entry.body));
                }catch(Throwable error){if(owner(entry.ctx)!=null)owner(entry.ctx).fail(error);com.fixmer.mared.Mared.LOGGER.error("[Mared] Bind dispatch failed",error);}
            };
            if(!com.fixmer.mared.technology.runtime.ScriptDispatch.submit(server,callback)&&entry.ctx!=null)entry.ctx.log("[warn] bind skipped: server dispatch queue full");
        }
    }
    public static synchronized void remove(String key,int index){var list=entries(key);if(index>=0&&index<list.size())removeEntry(list.get(index));}
}
