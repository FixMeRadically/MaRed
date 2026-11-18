package com.fixmer.mared.technology.server.consent;

import com.fixmer.genesis.technology.server.waiting.GameConsentBridge;
import com.fixmer.genesis.technology.server.waiting.GameConsentBridge.*;
import com.fixmer.mared.Mared;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.Path;
import java.util.*;

/** Server-thread connection ownership + bounded asynchronous I/O. Disabled unless explicitly configured. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class GenesisConsentServer {
    private static final Map<MinecraftServer,Actor> ACTORS=new IdentityHashMap<>();
    private static final class Actor {
        final GameConsentBridge bridge;final Map<UUID,Binding> players=new HashMap<>();long sequence,ticks;boolean failed;
        Actor(Path root){bridge=new GameConsentBridge(root);}
        long next(){sequence=Math.incrementExact(sequence);return sequence;}
    }
    private static final class Binding {
        final ServerPlayer player;final Session session;boolean busy;long nextPoll;View view;
        UUID request;Boolean choice;View intended;int attempts;
        Binding(ServerPlayer p,Session s){player=p;session=s;}
    }
    private GenesisConsentServer(){}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String root=System.getenv("GENESIS_WAITING57_ROOT");if(root==null||root.isBlank())return;
        try{ACTORS.put(event.getServer(),new Actor(Path.of(root)));}
        catch(RuntimeException bad){Mared.LOGGER.warn("[Genesis consent57] Configuration refused; no update actions enabled");}
    }
    private static Identity identity(MinecraftServer server,ServerPlayer player){
        if(server.usesAuthentication())return Identity.ONLINE_ACCOUNT;
        if(!server.isDedicatedServer()&&server.getSingleplayerProfile()!=null&&server.getSingleplayerProfile().getId().equals(player.getUUID()))return Identity.INTEGRATED_OWNER;
        return null; // Offline UUIDs/LAN guests are not authenticated remote accounts.
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        MinecraftServer server=player.getServer();Actor actor=ACTORS.get(server);if(actor==null||actor.failed)return;
        Identity identity=identity(server,player);
        if(identity==null||!player.connection.hasChannel(GenesisConsentPayloads.Offer.TYPE.id())||!player.connection.hasChannel(GenesisConsentPayloads.Choice.TYPE.id())||actor.players.size()>=64){
            player.sendSystemMessage(Component.translatable("mared.consent57.unavailable"));return;
        }
        var binding=new Binding(player,new Session(player.getUUID(),UUID.randomUUID(),actor.next(),identity));actor.players.put(player.getUUID(),binding);binding.busy=true;
        if(!actor.bridge.bind(binding.session,reply->server.execute(()->complete(server,actor,binding,reply))))failure(server,actor,binding);
    }
    private static boolean current(MinecraftServer server,Actor actor,Binding binding){return ACTORS.get(server)==actor&&!actor.failed&&actor.players.get(binding.player.getUUID())==binding&&server.getPlayerList().getPlayer(binding.player.getUUID())==binding.player;}
    private static void complete(MinecraftServer server,Actor actor,Binding binding,Reply reply){
        if(!current(server,actor,binding))return;binding.busy=false;binding.nextPoll=actor.ticks+40;
        if(!reply.success()){failure(server,actor,binding);return;}
        binding.view=reply.view();PacketDistributor.sendToPlayer(binding.player,new GenesisConsentPayloads.Offer(binding.view));
        if(binding.view.disconnectRequested()&&!binding.view.paused()&&!binding.view.phase().equals("CANCELLED"))binding.player.connection.disconnect(Component.translatable("mared.consent57.disconnected"));
    }
    private static void failure(MinecraftServer server,Actor actor,Binding binding){
        if(!current(server,actor,binding))return;
        // Unknown decision outcomes cannot be turned into accepted installs or a different choice.
        actor.failed=true;
        for(Binding b:actor.players.values())if(b.view!=null&&server.getPlayerList().getPlayer(b.player.getUUID())==b.player){View v=b.view;PacketDistributor.sendToPlayer(b.player,new GenesisConsentPayloads.Offer(new View(v.instance(),v.update(),v.packageSha256(),v.offer(),v.revision(),0,v.decision(),"SUSPENDED",true,false)));}
        actor.bridge.close();actor.players.clear();
        Mared.LOGGER.warn("[Genesis consent57] Waiting owner/package unavailable; update flow suspended, ordinary server continues");
        for(ServerPlayer player:server.getPlayerList().getPlayers())player.sendSystemMessage(Component.translatable("mared.consent57.suspended"));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;Actor a=ACTORS.get(player.getServer());if(a==null)return;
        Binding b=a.players.get(player.getUUID());if(b==null||b.player!=player)return;
        a.players.remove(player.getUUID());a.bridge.logout(new Session(b.session.player(),b.session.connection(),a.next(),b.session.identity()));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        MinecraftServer server=event.getServer();Actor a=ACTORS.get(server);if(a==null||a.failed)return;a.ticks++;
        for(Binding b:List.copyOf(a.players.values()))if(!b.busy&&a.ticks>=b.nextPoll){b.busy=true;if(!a.bridge.status(b.session,reply->server.execute(()->complete(server,a,b,reply))))failure(server,a,b);}
    }
    public static void choose(ServerPlayer player,GenesisConsentPayloads.Choice choice){
        MinecraftServer server=player.getServer();Actor a=ACTORS.get(server);if(a==null||a.failed)return;Binding b=a.players.get(player.getUUID());
        if(b==null||!current(server,a,b)||b.view==null||b.busy)return;
        if(!b.view.update().equals(choice.update())||!b.view.packageSha256().equals(choice.packageSha256())||!b.view.offer().equals(choice.offer()))return;
        if(b.request==null){if(!b.view.decision().equals("PENDING")||b.view.paused()||b.view.phase().equals("CANCELLED"))return;b.request=choice.request();b.choice=choice.accept();b.intended=b.view;}
        else if(!b.request.equals(choice.request())||b.choice!=choice.accept())return;
        if(++b.attempts>3)return;b.busy=true;
        if(!a.bridge.decide(b.session,b.intended,b.request,b.choice,reply->server.execute(()->complete(server,a,b,reply))))failure(server,a,b);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){dispose(event.getServer());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){dispose(event.getServer());}
    private static void dispose(MinecraftServer server){Actor a=ACTORS.remove(server);if(a!=null){a.players.clear();a.bridge.close();}}
}
