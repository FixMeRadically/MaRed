package com.fixmer.mared.technology.server.remote;

import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.remote.RemoteConsent72.*;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.mared.Mared;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real server-thread membership/identity. File verification is bounded and off-thread. No installer/STOP. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class RemoteConsentServer72 {
    private static final Map<MinecraftServer,Actor> ACTORS=new IdentityHashMap<>();
    private static final class Bound {final ServerPlayer player;final Session session;long lastChoice=Long.MIN_VALUE;boolean kicked;Bound(ServerPlayer p){player=p;session=new Session(p.getUUID(),UUID.randomUUID());}}
    private static final class Actor {final UUID instance=UUID.randomUUID();final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"genesis-remote72-verify");t.setDaemon(true);return t;});final Map<UUID,Bound> players=new HashMap<>();boolean busy,localTest;long generation,ticks;RemoteOffer69 prepared;Path directory;RemoteConsent72 round;}
    private RemoteConsentServer72(){}private static long now(){return System.nanoTime()/1000000;}
    @SubscribeEvent public static void started(ServerStartedEvent e){ACTORS.put(e.getServer(),new Actor());}
    private static boolean permitted(CommandSourceStack s){return com.fixmer.mared.technology.permissions.MaopServer113.source(s,"STAGE")&&(s.getServer().usesAuthentication()||com.fixmer.mared.technology.permissions.MaopServer113.console(s));}
    private static void tell(CommandSourceStack s,String key,Object...args){s.sendSuccess(()->Component.translatable("mared.remote72."+key,args),false);}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("genesisupdate").requires(RemoteConsentServer72::permitted)
            .then(Commands.literal("prepare").then(Commands.argument("name",StringArgumentType.word()).executes(c->prepare(c.getSource(),StringArgumentType.getString(c,"name"),false))))
            .then(Commands.literal("offer").executes(c->prepare(c.getSource(),null,true)))
            .then(Commands.literal("cancel").executes(c->{Actor a=ACTORS.get(c.getSource().getServer());if(a==null)return 0;try{if(!RemoteControlServer73.canCancel(c.getSource().getServer())){tell(c.getSource(),"commit_locked");return 0;}RemoteControlServer73.cancel(c.getSource().getServer());}catch(Exception failure){tell(c.getSource(),"commit_locked");return 0;}a.generation++;a.prepared=null;a.directory=null;if(a.round!=null){a.round.cancel();broadcast(a,now());}tell(c.getSource(),"cancelled");return 1;}))
            .then(Commands.literal("status").executes(c->{Actor a=ACTORS.get(c.getSource().getServer());if(a==null)return 0;if(a.round==null||a.round.cancelled()&&(a.busy||a.prepared!=null))tell(c.getSource(),a.busy?"verifying":a.prepared==null?"no_offer":"prepared");else{var x=a.round.stats(now());tell(c.getSource(),"stats",x.cohort(),x.accepted(),x.pending(),x.declined(),x.expired(),x.late(),x.cancelled());}return 1;}))
            .then(Commands.literal("local-test").then(Commands.literal("on").executes(c->local(c.getSource(),true))).then(Commands.literal("off").executes(c->local(c.getSource(),false)))));
    }
    private static int local(CommandSourceStack source,boolean value){Actor a=ACTORS.get(source.getServer());if(a==null||source.getEntity()!=null||source.getServer().usesAuthentication()||a.round!=null&&!a.round.cancelled()){tell(source,"local_refused");return 0;}a.localTest=value;tell(source,value?"local_on":"local_off");return 1;}
    public static int prepareAndOffer118(CommandSourceStack source,String name){if(!source.getServer().usesAuthentication()&&local(source,true)==0)return 0;return prepare(source,name,false,true);}
    private static int prepare(CommandSourceStack source,String name,boolean activate){return prepare(source,name,activate,false);}
    private static int prepare(CommandSourceStack source,String name,boolean activate,boolean autoOffer){
        MinecraftServer server=source.getServer();Actor a=ACTORS.get(server);if(System.getenv("GENESIS_FIRST58_ROOT")!=null||System.getenv("GENESIS_WAITING57_ROOT")!=null){tell(source,"managed");return 0;}if(a==null||!server.isDedicatedServer()){tell(source,"dedicated");return 0;}
        if(a.busy||a.round!=null&&!a.round.cancelled()||RemoteControlServer73.blocksNew(server)){tell(source,"busy");return 0;}
        Path path;try{if(activate){if(a.prepared==null||a.directory==null){tell(source,"no_offer");return 0;}path=a.directory;}else{if(!name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,39}"))throw new IllegalArgumentException();path=FirstLaunchFiles.safe(server.getServerDirectory().resolve("config/mared/remote-updates").resolve(name));}}catch(Exception bad){tell(source,"path");return 0;}
        long generation=++a.generation;RemoteOffer69 expected=a.prepared;String neo=net.neoforged.fml.ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString();if(!activate){a.prepared=null;a.directory=null;}a.busy=true;tell(source,"verifying");
        a.io.execute(()->{RemoteOffer69 verified=null;try{verified=RemoteConsent72.verifyPublished(path,"1.21.1",neo);}catch(Exception failure){com.fixmer.mared.Mared.LOGGER.warn("MaRed: server update publication verification failed",failure);}final RemoteOffer69 result=verified;
            server.execute(()->{if(ACTORS.get(server)!=a)return;a.busy=false;if(a.generation!=generation)return;if(!permitted(source)||result==null||activate&&!result.equals(expected)){tell(source,"invalid");return;}if(activate)activate(source,a,result);else{a.prepared=result;a.directory=path;tell(source,"preview",result.release(),result.game(),result.endpoint(),result.signer());if(autoOffer)activate(source,a,result);}});
        });return 1;
    }
    private static boolean eligible(MinecraftServer s,Actor a,ServerPlayer p){
        boolean identity=s.usesAuthentication()||a.localTest&&p.connection.getRemoteAddress() instanceof InetSocketAddress remote&&remote.getAddress()!=null&&remote.getAddress().isLoopbackAddress();
        return identity&&(!RemoteControlServer73.enabled(s)||p.connection.hasChannel(RemoteControlPayloads73.Config.TYPE.id()))&&p.connection.hasChannel(RemoteConsentPayloads72.Offer.TYPE.id())&&p.connection.hasChannel(RemoteConsentPayloads72.Answer.TYPE.id());
    }
    private static void activate(CommandSourceStack source,Actor a,RemoteOffer69 offer){
        try{RemoteConsent72.requireWindow(offer,System.currentTimeMillis());}catch(Exception expired){tell(source,"invalid");return;}MinecraftServer s=source.getServer();List<ServerPlayer> players=List.copyOf(s.getPlayerList().getPlayers());if(players.isEmpty()||players.size()>RemoteConsent72.LIMIT||players.stream().anyMatch(p->!eligible(s,a,p))){tell(source,"unsupported");return;}
        long time=now();Map<UUID,Bound> next=new HashMap<>();List<Session> sessions=new ArrayList<>();for(ServerPlayer p:players){Bound b=new Bound(p);next.put(p.getUUID(),b);sessions.add(b.session);}RemoteConsent72 round=new RemoteConsent72(a.instance,offer,sessions,time);
        try{RemoteControlServer73.create(s,round,sessions);}catch(Exception refused){tell(source,"control_refused");return;}a.players.clear();a.players.putAll(next);a.round=round;broadcast(a,time);tell(source,"offered",players.size());
    }
    private static void deliver(ServerPlayer p,Frame frame){RemoteControlServer73.announce(p,frame);PacketDistributor.sendToPlayer(p,new RemoteConsentPayloads72.Offer(frame));}
    public static void closeControl(MinecraftServer s){Actor a=ACTORS.get(s);if(a!=null&&a.round!=null){a.round.cancel();broadcast(a,now());}}
    private static boolean current(MinecraftServer s,Actor a,Bound b){return !b.kicked&&ACTORS.get(s)==a&&a.players.get(b.player.getUUID())==b&&s.getPlayerList().getPlayer(b.player.getUUID())==b.player;}
    private static void broadcast(Actor a,long time){for(Bound b:List.copyOf(a.players.values()))if(!b.kicked)deliver(b.player,a.round.frame(b.session,time));}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;MinecraftServer s=p.getServer();Actor a=ACTORS.get(s);if(s.getPlayerList().getPlayer(p.getUUID())!=p||a==null||a.round==null||a.round.cancelled())return;
        if(!eligible(s,a,p)){p.connection.disconnect(Component.translatableWithFallback("mared.remote72.client_required","This update requires Genesis72. Update the mod and reconnect."));return;}Bound b=new Bound(p);try{a.round.bind(b.session,now());a.players.put(p.getUUID(),b);deliver(p,a.round.frame(b.session,now()));}catch(IllegalStateException limit){p.connection.disconnect(Component.translatable("mared.remote72.capacity"));}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(!(event.getEntity() instanceof ServerPlayer p))return;Actor a=ACTORS.get(p.getServer());if(a==null)return;Bound b=a.players.get(p.getUUID());if(b!=null&&b.player==p){a.players.remove(p.getUUID());if(a.round!=null)a.round.detach(b.session);}}
    public static void choose(ServerPlayer p,Choice choice){Actor a=ACTORS.get(p.getServer());if(a==null||a.round==null||a.round.cancelled())return;Bound b=a.players.get(p.getUUID());if(b==null||!current(p.getServer(),a,b))return;long time=now();if(b.lastChoice!=Long.MIN_VALUE&&time-b.lastChoice<200)return;b.lastChoice=time;if(a.round.choose(b.session,choice,time))deliver(p,a.round.frame(b.session,time));}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        MinecraftServer s=event.getServer();Actor a=ACTORS.get(s);if(a==null||a.round==null)return;long time=now();if(!a.round.cancelled())try{a.round.offer().validAt(System.currentTimeMillis());}catch(Exception expired){if(RemoteControlServer73.canCancel(s)){a.round.cancel();try{RemoteControlServer73.cancel(s);}catch(Exception ignored){}broadcast(a,time);}}
        a.round.tick(time);a.ticks++;if(a.ticks%20==0)RemoteControlServer73.sync(s,a.round);for(Bound b:List.copyOf(a.players.values()))if(current(s,a,b)){if(a.ticks%20==0)deliver(b.player,a.round.frame(b.session,time));if(a.round.disconnect(b.session,time)){deliver(b.player,a.round.frame(b.session,time));b.kicked=true;b.player.connection.disconnect(Component.translatable("mared.remote72.left"));}}
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){dispose(e.getServer());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){dispose(e.getServer());}
    private static void dispose(MinecraftServer s){Actor a=ACTORS.remove(s);if(a!=null){a.generation++;if(a.round!=null)a.round.cancel();a.players.clear();a.io.shutdownNow();}}
}
