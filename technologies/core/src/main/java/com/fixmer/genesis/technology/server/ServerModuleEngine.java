package com.fixmer.genesis.technology.server;

import java.net.URLClassLoader;
import java.util.*;
import java.util.function.LongConsumer;

/** Single-server-thread lifecycle. Each project retains active plus ONE previous bundle. */
public final class ServerModuleEngine implements AutoCloseable {
    public record Result(boolean success,String message,long generation) {}
    public record Status(long generation,boolean running,boolean canRollback,int callbacks,String error) {}
    private final ServerModuleHost host;
    private final Map<String,Slot> slots=new LinkedHashMap<>();
    private long nextGeneration,tick;private int tickStart;
    private boolean closed;
    private static final class Slot {Loaded active;RuntimeBundle previous;String error="";}
    private static final class Loaded {
        boolean released;final RuntimeBundle bundle;final URLClassLoader loader;final Context context;final ServerModule module;
        Loaded(RuntimeBundle bundle,URLClassLoader loader,Context context,ServerModule module){this.bundle=bundle;this.loader=loader;this.context=context;this.module=module;}
    }
    private record Mutation(Runnable validate,Runnable apply,java.util.function.Supplier<Runnable> undo){}
    private final class Callback implements ServerContext.Subscription {
        final Context owner;final long due;final LongConsumer action;boolean removed;
        Callback(Context owner,long due,LongConsumer action){this.owner=owner;this.due=due;this.action=action;}
        @Override public void close(){requireThread();removed=true;owner.callbacks.remove(this);}
    }
    private final class Context implements ServerContext {
        final String project;final long generation;final Map<String,String> state;
        final List<Callback> callbacks=new ArrayList<>();final List<Mutation> staged=new ArrayList<>();
        boolean alive=true,active;
        Context(String project,long generation,Map<String,String> prior){this.project=project;this.generation=generation;state=new LinkedHashMap<>(prior);}
        void check(){requireThread();if(!alive||closed||!host.allowed())throw new IllegalStateException("Runtime context expired or permission denied");}
        void mutate(Runnable action){mutate(()->{},action,()->null);}
        void mutate(Runnable validation,Runnable action,java.util.function.Supplier<Runnable> undo){check();if(active){validation.run();action.run();}else {if(staged.size()>=1024)throw new IllegalStateException("Too many activation actions");staged.add(new Mutation(validation,action,undo));}}
        void revoke(){alive=false;active=false;callbacks.clear();staged.clear();}
        @Override public UUID owner(){check();return host.owner();}
        @Override public String worldSession(){check();return host.worldSession();}
        @Override public long generation(){check();return generation;}
        @Override public void log(String text){check();host.log("["+project+" #"+generation+"] "+limited(text,2048));}
        @Override public void broadcast(String text){String value=limited(text,512);mutate(()->host.validateBroadcast(value),()->host.broadcast(value),()->null);}
        @Override public void setDayTime(String dimension,long ticks){String id=dimension(dimension);if(ticks<0||ticks>Long.MAX_VALUE-24000)throw new IllegalArgumentException("Invalid day time");mutate(()->host.validateDayTime(id,ticks),()->host.setDayTime(id,ticks),()->host.undoDayTime(id));}
        @Override public void setWeather(int duration,boolean rain,boolean thunder){if(duration<1||duration>1200000||thunder&&!rain)throw new IllegalArgumentException("Invalid weather");mutate(()->host.validateWeather(duration,rain,thunder),()->host.setWeather(duration,rain,thunder),host::undoWeather);}
        @Override public void setBlock(String dimension,int x,int y,int z,String block){check();String id=dimension(dimension),name=dimension(block);host.validateBlock(id,x,y,z,name);mutate(()->host.validateBlock(id,x,y,z,name),()->host.setBlock(id,x,y,z,name),()->host.undoBlock(id,x,y,z));}
        @Override public String state(String key){check();return state.get(key(key));}
        @Override public void state(String key,String value){check();key=key(key);if(value==null){state.remove(key);return;}if(value.length()>4096||!state.containsKey(key)&&state.size()>=128)throw new IllegalArgumentException("Runtime state limit");state.put(key,value);}
        @Override public Subscription onTick(LongConsumer action){return add(-1,Objects.requireNonNull(action));}
        @Override public Subscription afterTicks(int delay,Runnable action){if(delay<1||delay>1200000)throw new IllegalArgumentException("Invalid delay");Objects.requireNonNull(action);return add(tick+delay,n->action.run());}
        Subscription add(long due,LongConsumer action){check();if(callbacks.size()>=256)throw new IllegalStateException("Too many runtime callbacks");var callback=new Callback(this,due,action);callbacks.add(callback);return callback;}
        @Override public Object nativeServer(){check();return host.nativeServer();}
    }
    public ServerModuleEngine(ServerModuleHost host){this.host=Objects.requireNonNull(host);}
    private void requireThread(){if(!host.isServerThread())throw new IllegalStateException("Use the server owner thread");}
    private static String limited(String s,int limit){return Objects.requireNonNull(s).substring(0,Math.min(limit,s.length()));}
    private static String key(String s){if(s==null||s.isBlank()||s.length()>128)throw new IllegalArgumentException("Invalid state key");return s;}
    private static String dimension(String s){if(s==null||s.length()>256||!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Use namespace:path");return s;}
    public Result apply(String project,RuntimeBundle bundle){return apply(project,bundle,()->false);}
    public Result apply(String project,RuntimeBundle bundle,java.util.function.BooleanSupplier cancelled){return apply(project,bundle,false,cancelled);}
    private Result apply(String project,RuntimeBundle bundle,boolean restoring,java.util.function.BooleanSupplier cancelled){
        requireThread();RuntimeBundle.retryCleanup();Objects.requireNonNull(bundle);
        if(!slots.containsKey(project)&&slots.size()>=16)for(var e:List.copyOf(slots.entrySet()))if(!e.getValue().active.context.alive){stop(e.getKey());break;}
        if(closed||cancelled.getAsBoolean()||!host.allowed()||project==null||project.isBlank()||project.length()>192||!slots.containsKey(project)&&slots.size()>=16){if(!restoring)bundle.close();return new Result(false,"World/permission unavailable or module limit",0);}
        ClassLoader threadLoader=Thread.currentThread().getContextClassLoader();long activationStarted=System.nanoTime();int[] commits={0},irreversible={0};var compensations=new ArrayList<Runnable>();Slot slot=slots.get(project);Loaded candidate=null;URLClassLoader loader=null;Context context=null;ServerModule module=null;
        try {
            loader=bundle.loader(ServerModule.class.getClassLoader());Thread.currentThread().setContextClassLoader(loader);Class<?> type=Class.forName(bundle.entry(),true,loader);
            if(type.getClassLoader()!=loader||!ServerModule.class.isAssignableFrom(type))throw new IllegalArgumentException("Entry must implement Genesis ServerModule; ordinary @Mod/main is not a runtime module");
            module=(ServerModule)type.getConstructor().newInstance();if(cancelled.getAsBoolean()||!host.allowed())throw new IllegalStateException("Cancelled / world changed during class initialization");
            context=new Context(project,++nextGeneration,slot==null||slot.active==null?Map.of():slot.active.context.state);
            candidate=new Loaded(bundle,loader,context,module);if(System.nanoTime()-activationStarted>200_000_000L)throw new IllegalStateException("Class initialization / constructor exceeded 200 ms after returning");module.onEnable(context);
            if(System.nanoTime()-activationStarted>200_000_000L)throw new IllegalStateException("Activation exceeded 200 ms after returning; in-process code cannot be forcibly interrupted");
            if(cancelled.getAsBoolean()||!host.allowed())throw new IllegalStateException("Cancelled / world changed during activation");
            // Old callbacks cannot interleave: commit is on the same server thread. A world mutation is not transactional.
            for(Mutation action:List.copyOf(context.staged))action.validate().run();
            if(cancelled.getAsBoolean()||!host.allowed())throw new IllegalStateException("Cancelled before world commit");
            for(Mutation action:List.copyOf(context.staged)){Runnable undo=action.undo().get();if(undo!=null)compensations.add(undo);else irreversible[0]++;commits[0]++;action.apply().run();}context.staged.clear();
            if(slot==null){slot=new Slot();slots.put(project,slot);}
            Loaded old=slot.active;RuntimeBundle obsolete=restoring?null:slot.previous;
            if(old!=null)release(old);
            slot.previous=old==null?null:old.bundle;slot.active=candidate;slot.error="";context.active=true;
            if(obsolete!=null&&obsolete!=bundle)obsolete.close();
            host.log("["+project+"] applied #"+context.generation);return new Result(true,"APPLIED",context.generation);
        } catch(Throwable error) {
            Collections.reverse(compensations);int rollbackErrors=0;for(Runnable undo:compensations)try{undo.run();}catch(Throwable failure){rollbackErrors++;host.log("World compensation failed: "+failure);if(failure instanceof VirtualMachineError fatal)throw fatal;}
            if(candidate!=null)release(candidate);else {if(context!=null)context.revoke();if(loader!=null)try{loader.close();}catch(Exception ignored){}}
            if(!restoring)bundle.close();
            String message=(commits[0]>0&&(irreversible[0]>0||rollbackErrors>0)?"PARTIAL_APPLY: "+commits[0]+" world actions attempted; "+irreversible[0]+" non-compensatable, "+rollbackErrors+" rollback failures. ":"")+error.getClass().getSimpleName()+": "+Objects.toString(error.getMessage(),"");host.log("["+project+"] apply failed: "+limited(message,2048));
            if(error instanceof VirtualMachineError fatal)throw fatal;
            return new Result(false,message,slot==null||slot.active==null?0:slot.active.context.generation);
        }finally{Thread.currentThread().setContextClassLoader(threadLoader);}
    }
    private void release(Loaded loaded){
        // Revoke first: onDisable cannot create surviving callbacks or mutate the world through its expired context.
        if(loaded.released)return;loaded.released=true;loaded.context.revoke();ClassLoader before=Thread.currentThread().getContextClassLoader();long disableStarted=System.nanoTime();try{Thread.currentThread().setContextClassLoader(loaded.loader);loaded.module.onDisable();if(System.nanoTime()-disableStarted>200_000_000L)host.log("onDisable exceeded 200 ms after returning; in-process code cannot be forcibly interrupted");}catch(Throwable bad){host.log("onDisable: "+limited(bad.toString(),2048));if(bad instanceof VirtualMachineError fatal)throw fatal;}
        finally{Thread.currentThread().setContextClassLoader(before);try{loaded.loader.close();}catch(Exception bad){host.log("Classloader close: "+bad.getMessage());}}
    }
    public Result rollback(String project){requireThread();Slot slot=slots.get(project);if(slot==null||slot.previous==null)return new Result(false,"No previous successful bundle",0);return apply(project,slot.previous,true,()->false);}
    public Result stop(String project){requireThread();Slot slot=slots.remove(project);if(slot==null)return new Result(false,"Module not running",0);long generation=slot.active.context.generation;release(slot.active);slot.active.bundle.close();if(slot.previous!=null)slot.previous.close();return new Result(true,"STOPPED",generation);}
    public void tick(){
        requireThread();RuntimeBundle.retryCleanup();if(closed)return;if(!host.allowed()){close();return;}tick++;
        var entries=List.copyOf(slots.entrySet());int count=entries.size();long totalStarted=System.nanoTime();int first=count==0?0:Math.floorMod(tickStart,count);
        for(int offset=0;offset<count;offset++){
            int index=(first+offset)%count;var entry=entries.get(index);tickStart=(index+1)%count;
            Loaded loaded=entry.getValue().active;Context ctx=loaded.context;if(!ctx.alive)continue;long started=System.nanoTime();
            ClassLoader before=Thread.currentThread().getContextClassLoader();try {Thread.currentThread().setContextClassLoader(loaded.loader);for(Callback callback:List.copyOf(ctx.callbacks)){
                if(callback.removed||!ctx.alive)continue;if(callback.due>=0&&tick<callback.due)continue;
                if(callback.due>=0)callback.close();callback.action.accept(tick);
                if(System.nanoTime()-started>10_000_000L)throw new IllegalStateException("Runtime tick exceeded 10 ms after returning");
            }}catch(Throwable bad){entry.getValue().error=limited(bad.toString(),2048);host.log("["+entry.getKey()+"] disabled: "+entry.getValue().error);release(loaded);if(bad instanceof VirtualMachineError fatal)throw fatal;}finally{Thread.currentThread().setContextClassLoader(before);}
            if(System.nanoTime()-totalStarted>20_000_000L)break;
        }
    }
    public Map<String,Status> snapshot(){requireThread();var result=new LinkedHashMap<String,Status>();slots.forEach((id,s)->result.put(id,new Status(s.active.context.generation,s.active.context.alive,s.previous!=null,s.active.context.callbacks.size(),s.error)));return Map.copyOf(result);}
    @Override public void close(){requireThread();if(closed)return;for(String id:List.copyOf(slots.keySet()))stop(id);closed=true;}
}
