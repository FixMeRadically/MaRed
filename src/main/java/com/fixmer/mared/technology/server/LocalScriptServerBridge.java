package com.fixmer.mared.technology.server;

import com.fixmer.genesis.technology.server.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Local transport only. Dedicated/LAN connections are not authorized by this implementation. */
public final class LocalScriptServerBridge {
    private static final Map<MinecraftServer,Session> sessions=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Set<Request>> waiting=new IdentityHashMap<>();
    private static final class Request {
        final RuntimeBundle bundle;final CompletableFuture<ServerModuleEngine.Result> future=new CompletableFuture<>();
        final java.util.concurrent.atomic.AtomicBoolean claimed=new java.util.concurrent.atomic.AtomicBoolean();
        Request(RuntimeBundle bundle){this.bundle=bundle;}
        void cancel(){if(claimed.compareAndSet(false,true)){bundle.close();future.complete(new ServerModuleEngine.Result(false,"World closed / cancelled",0));}}
    }
    private static synchronized void add(MinecraftServer server,Request request){waiting.computeIfAbsent(server,s->new HashSet<>()).add(request);}
    private static synchronized void remove(MinecraftServer server,Request request){var set=waiting.get(server);if(set!=null){set.remove(request);if(set.isEmpty())waiting.remove(server);}}

    private LocalScriptServerBridge() {}
    private static synchronized Session session(MinecraftServer server,UUID owner,BooleanSupplier localOnly) {
        return sessions.computeIfAbsent(server,s->new Session(s,owner,localOnly));
    }
    private static synchronized Session existing(MinecraftServer server){return sessions.get(server);}
    public static CompletableFuture<ServerModuleEngine.Result> apply(MinecraftServer server,UUID owner,BooleanSupplier localOnly,String project,RuntimeBundle bundle) {
        var request=new Request(bundle);var result=request.future;add(server,request);
        result.whenComplete((done,error)->{if(result.isCancelled()){request.cancel();remove(server,request);}});
        try { server.execute(()->{
            if(!request.claimed.compareAndSet(false,true))return;remove(server,request);
            try {
                if(server.isDedicatedServer()||!localOnly.getAsBoolean()){bundle.close();result.complete(new ServerModuleEngine.Result(false,"Singleplayer session changed",0));return;}
                Session host=session(server,owner,localOnly);
                if(!host.owner.equals(owner)||!host.allowed()){bundle.close();result.complete(new ServerModuleEngine.Result(false,"Owner/world unavailable",0));return;}
                result.complete(host.engine.apply(project,bundle,result::isCancelled));host.publish();
            }catch(Throwable bad){bundle.close();result.completeExceptionally(bad);if(bad instanceof VirtualMachineError fatal)throw fatal;}
        }); }catch(RuntimeException bad){request.cancel();remove(server,request);result.completeExceptionally(bad);}
        return result;
    }
    public static CompletableFuture<ServerModuleEngine.Result> manage(MinecraftServer server,UUID owner,String project,boolean rollback) {
        var result=new CompletableFuture<ServerModuleEngine.Result>();
        try {server.execute(()->{
            if(result.isDone())return;Session host=existing(server);try {
                if(host==null||!host.owner.equals(owner)||!host.allowed()){result.complete(new ServerModuleEngine.Result(false,"World/owner unavailable",0));return;}
                result.complete(rollback?host.engine.rollback(project):host.engine.stop(project));host.publish();
            }catch(Throwable bad){result.completeExceptionally(bad);if(bad instanceof VirtualMachineError fatal)throw fatal;}
        });}catch(RuntimeException bad){result.completeExceptionally(bad);}return result;
    }
    public static Map<String,ServerModuleEngine.Status> statuses(MinecraftServer server){Session host=existing(server);return host==null?Map.of():host.statuses;}
    public static String drain(MinecraftServer server){Session host=existing(server);return host==null?"":host.drain();}
    public static void tick(MinecraftServer server){Session host=existing(server);if(host!=null){host.engine.tick();host.publish();}}
    public static synchronized void stopped(MinecraftServer server){var requests=waiting.remove(server);if(requests!=null)for(var request:requests)request.cancel();Session host=sessions.remove(server);if(host!=null){host.open=false;host.engine.close();host.statuses=Map.of();}}
    private static final class Session implements ServerModuleHost {
        final MinecraftServer server;final UUID owner;final BooleanSupplier localOnly;final String id=UUID.randomUUID().toString();
        final ServerModuleEngine engine;final Deque<String> logs=new ArrayDeque<>();
        volatile Map<String,ServerModuleEngine.Status> statuses=Map.of();boolean open=true;
        Session(MinecraftServer server,UUID owner,BooleanSupplier localOnly){this.server=server;this.owner=owner;this.localOnly=localOnly;engine=new ServerModuleEngine(this);}
        void publish(){statuses=engine.snapshot();}
        synchronized String drain(){String value=String.join("\n",logs);logs.clear();return value;}
        @Override public UUID owner(){return owner;}
        @Override public String worldSession(){return id;}
        @Override public boolean isServerThread(){return server.isSameThread();}
        @Override public boolean allowed(){return open&&!server.isDedicatedServer()&&localOnly.getAsBoolean()&&server.getPlayerList().getPlayer(owner)!=null;}
        @Override public synchronized void log(String line){if(logs.size()>=256)logs.removeFirst();logs.addLast(line.substring(0,Math.min(2048,line.length())));}
        @Override public void broadcast(String line){server.getPlayerList().broadcastSystemMessage(Component.literal(line),false);}
        private ServerLevel level(String dimension){ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(dimension)));if(level==null)throw new IllegalArgumentException("Dimension missing: "+dimension);return level;}
        @Override public void validateDayTime(String dimension,long time){level(dimension);}
        @Override public void validateWeather(int duration,boolean rain,boolean thunder){if(server.overworld()==null)throw new IllegalStateException("Overworld unavailable");}
        @Override public Runnable undoDayTime(String dimension){ServerLevel world=level(dimension);long prior=world.getDayTime();return ()->world.setDayTime(prior);}
        @Override public void setDayTime(String dimension,long time){level(dimension).setDayTime(time);}
        @Override public void setWeather(int duration,boolean raining,boolean thundering){server.overworld().setWeatherParameters(raining?0:duration,duration,raining,thundering);}
        @Override public void validateBlock(String dimension,int x,int y,int z,String block){
            ServerLevel world=level(dimension);BlockPos pos=new BlockPos(x,y,z);
            if(Math.abs((long)x)>29999984||Math.abs((long)z)>29999984||world.isOutsideBuildHeight(pos)||!world.hasChunkAt(pos))throw new IllegalArgumentException("Use a loaded chunk and valid block position");
            if(!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(block)))throw new IllegalArgumentException("Unknown block: "+block);
        }
        @Override public void setBlock(String dimension,int x,int y,int z,String block){validateBlock(dimension,x,y,z,block);level(dimension).setBlock(new BlockPos(x,y,z),BuiltInRegistries.BLOCK.get(ResourceLocation.parse(block)).defaultBlockState(),3);}
        @Override public Object nativeServer(){return server;}
    }
}
