package com.fixmer.mared.technology.server.firstlaunch;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchSession;
import com.fixmer.genesis.technology.server.waiting.GameConsentBridge.View;
import com.fixmer.mared.Mared;
import com.fixmer.mared.technology.server.consent.GenesisConsentPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in, one dedicated server in a freshly cloned first58 profile. No adoption or LIVE install API. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class FirstLaunchServer58 {
    private static volatile Actor actor;
    private static final UUID ZERO=new UUID(0,0);
    private static final class Actor {
        final MinecraftServer server;final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Genesis-first58-server");t.setDaemon(true);return t;});
        volatile FirstLaunchSession session;volatile boolean failed,stopping,saved;volatile long sequence;volatile UUID request=ZERO;Binding binding;int ticks;
        Actor(MinecraftServer server){this.server=server;}
    }
    private static final class Binding {final ServerPlayer player;final UUID offer=UUID.randomUUID();final long deadline=System.nanoTime()+30_000_000_000L;String decision="PENDING";boolean recorded;Binding(ServerPlayer p){player=p;}}
    private FirstLaunchServer58(){}
    static List<Path> loaded(){return ModList.get().getModFiles().stream().map(m->m.getFile().getFilePath()).toList();}
    static String version(String id){for(var info:ModList.get().getMods())if(info.getModId().equals(id))return info.getVersion().toString();return "missing";}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!"server".equals(System.getenv("GENESIS_FIRST58_ROLE")))return;
        Actor a=new Actor(event.getServer());actor=a;List<Path> paths=loaded();String mc=version("minecraft"),neo=version("neoforge");
        a.io.execute(()->{try{
            a.session=FirstLaunchSession.open(System.getenv(),"server");if(a.session==null||!a.server.isDedicatedServer()||!a.server.usesAuthentication())throw new IllegalStateException("Authenticated dedicated first58 server required");
            a.session.verifyRuntime(paths,mc,neo);a.session.status("READY",0,ZERO);
            a.io.scheduleWithFixedDelay(()->poll(a),250,250,TimeUnit.MILLISECONDS);
        }catch(Exception bad){fail(a);}});
    }
    private static void fail(Actor a){a.failed=true;Mared.LOGGER.warn("[Genesis first58] Server pilot suspended; inspect private controller report");try{if(a.session!=null)a.session.status("ERROR",a.sequence,a.request);}catch(Exception ignored){}}
    private static void poll(Actor a){
        if(actor!=a||a.failed||a.stopping)return;
        try{var command=a.session.command();if(command.isEmpty())return;long seq=Long.parseLong(command.get("sequence"));if(seq<=a.sequence)return;
            if(!command.get("phase").equals("STOP"))throw new IllegalStateException("Unsupported server command");a.sequence=seq;a.request=UUID.fromString(command.get("request"));
            a.server.execute(()->{if(actor!=a||a.failed||a.stopping)return;a.stopping=true;try{
                if(!a.server.saveEverything(true,true,true))throw new IllegalStateException("Save not confirmed");a.saved=true;
                a.io.execute(()->{try{a.session.status("SAVED",a.sequence,a.request);a.server.execute(()->{if(actor==a&&!a.failed)a.server.halt(false);});}catch(Exception bad){fail(a);}});
            }catch(Exception bad){fail(a);}});
        }catch(Exception bad){fail(a);}
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        Actor a=actor;if(a==null||a.failed||a.session==null||a.session.generation()!=0||!(event.getEntity() instanceof ServerPlayer p)||p.getServer()!=a.server||!p.getUUID().equals(a.session.account()))return;
        if(!p.connection.hasChannel(GenesisConsentPayloads.Offer.TYPE.id())||!p.connection.hasChannel(GenesisConsentPayloads.Choice.TYPE.id()))return;
        a.binding=new Binding(p);offer(a,a.binding);
    }
    private static void offer(Actor a,Binding b){
        if(a.server.getPlayerList().getPlayer(b.player.getUUID())!=b.player)return;
        PacketDistributor.sendToPlayer(b.player,new GenesisConsentPayloads.Offer(new View(a.session.run(),a.session.update(),a.session.serverPackage(),b.offer,b.recorded?1:0,Math.max(0,(b.deadline-System.nanoTime())/1000000),b.recorded?b.decision:"PENDING",b.recorded?"WAITING":"OFFERING",false,false)));
    }
    public static boolean choose(ServerPlayer player,GenesisConsentPayloads.Choice choice){
        Actor a=actor;if(a==null)return false;
        Binding b=a.binding;if(a.failed||a.session==null||b==null||b.player!=player||a.server.getPlayerList().getPlayer(player.getUUID())!=player)return true;
        if(!choice.update().equals(a.session.update())||!choice.packageSha256().equals(a.session.serverPackage())||!choice.offer().equals(b.offer)||!b.decision.equals("PENDING")||System.nanoTime()>=b.deadline)return true;
        b.decision=choice.accept()?"ACCEPTED":"DECLINED";String decision=b.decision;
        a.io.execute(()->{try{a.session.consent(decision,b.offer);a.server.execute(()->{if(actor==a&&a.binding==b){b.recorded=true;offer(a,b);if(!choice.accept())player.connection.disconnect(Component.translatable("mared.consent57.disconnected"));}});}catch(Exception bad){fail(a);}});return true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){Actor a=actor;if(a==null||a.server!=event.getServer()||a.failed||a.binding==null)return;Binding b=a.binding;if(++a.ticks%40==0)offer(a,b);
        if(b.decision.equals("PENDING")&&System.nanoTime()>=b.deadline){b.decision="EXPIRED";a.io.execute(()->{try{a.session.consent("EXPIRED",b.offer);}catch(Exception bad){fail(a);}});if(a.server.getPlayerList().getPlayer(b.player.getUUID())==b.player)b.player.connection.disconnect(Component.translatable("mared.consent57.disconnected"));}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){Actor a=actor;if(a==null||a.server!=event.getServer())return;actor=null;
        try{a.io.submit(()->{try{if(a.session!=null)a.session.status(a.saved&&!a.failed?"STOPPED_SAVED":"STOPPED",a.sequence,a.request);}catch(Exception bad){Mared.LOGGER.warn("[Genesis first58] Final stop acknowledgement unavailable");}}).get(3,TimeUnit.SECONDS);}catch(Exception ignored){}finally{a.io.shutdownNow();}
    }
}
