package com.fixmer.mared.technology.server;

import com.fixmer.genesis.technology.server.process.ServerLifecycleReporter;
import com.fixmer.genesis.technology.server.process.ServerLifecycleState;
import com.fixmer.mared.Mared;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

/** Common-side: dedicated, local standalone and integrated servers; no client class linkage. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class GenesisServerLifecycleHooks {
    private static final Map<MinecraftServer,ServerLifecycleReporter> SESSIONS=java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<MinecraftServer,String> REPORTED_ERRORS=java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private GenesisServerLifecycleHooks(){}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        MinecraftServer server=event.getServer();IntegratedWorld90.started(server);if(SESSIONS.containsKey(server))return;
        ServerLifecycleReporter session=null;
        try{
            session=ServerLifecycleReporter.open(System.getenv());
            if(session.supervised()){
                var versions=new java.util.HashMap<String,String>();
                for(var mod:ModList.get().getMods()){
                    String id=mod.getModId();if(id.equals("minecraft")||id.equals("neoforge"))versions.put(id,mod.getVersion().toString());
                    else if(id.equals(Mared.MOD_ID))versions.put("genesis",mod.getVersion().toString());
                }
                session.recordGameRuntime(GenesisServerLifecycleHooks.class,server.getClass().getName(),server.isDedicatedServer(),versions);
            }
            SESSIONS.put(server,session);session.started();
        }
        catch(IOException|RuntimeException failed){if(session!=null)try{session.close();}catch(IOException extra){failed.addSuppressed(extra);}Mared.LOGGER.warn("[Genesis server] Lifecycle reporting unavailable; ordinary server operation continues",failed);}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        MinecraftServer server=event.getServer();var session=SESSIONS.get(server);if(session==null)return;
        session.tick(()->save(server),()->stop(server));
        if(!session.error().isEmpty()&&!session.error().equals(REPORTED_ERRORS.put(server,session.error())))Mared.LOGGER.warn("[Genesis server] Lifecycle reporting failed: {}",session.error());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){IntegratedWorld90.stopping(event.getServer());var session=SESSIONS.get(event.getServer());if(session!=null)session.stopping();}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){
        IntegratedWorld90.stopped(event.getServer());
        var session=SESSIONS.remove(event.getServer());REPORTED_ERRORS.remove(event.getServer());if(session==null)return;
        try{session.stopped();session.close();}catch(IOException failed){Mared.LOGGER.warn("[Genesis server] Failed to close lifecycle report",failed);}
    }
    private static void stop(MinecraftServer server){
        try{server.halt(false);}catch(RuntimeException failed){Mared.LOGGER.warn("[Genesis server] Stop call outcome is unknown",failed);throw failed;}
    }
    private static boolean save(MinecraftServer server){
        try{boolean success=server.saveEverything(true,true,true);if(!success)Mared.LOGGER.warn("[Genesis server] Save call did not confirm completion");return success;}
        catch(RuntimeException failed){Mared.LOGGER.warn("[Genesis server] Save call failed",failed);throw failed;}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("genesisserver").requires(source->source.hasPermission(4))
            .then(Commands.literal("status").executes(context->{
                var session=SESSIONS.get(context.getSource().getServer());if(session==null){context.getSource().sendFailure(Component.translatable("mared.server.lifecycle.unavailable"));return 0;}
                var state=session.snapshot();context.getSource().sendSuccess(()->Component.translatable("mared.server.lifecycle.status",Component.translatable("mared.server.lifecycle.phase."+state.phase().name().toLowerCase(java.util.Locale.ROOT)),state.ticks(),Component.translatable("mared.server.lifecycle.outcome."+state.outcome().name().toLowerCase(java.util.Locale.ROOT)),state.saveMs()),false);return 1;
            }))
            .then(Commands.literal("saveandstop").executes(context->{
                MinecraftServer server=context.getSource().getServer();var session=SESSIONS.get(server);
                if(session==null){context.getSource().sendFailure(Component.translatable("mared.server.lifecycle.unavailable"));return 0;}
                if(session.supervised()){context.getSource().sendFailure(Component.translatable("mared.server.lifecycle.use_supervisor"));return 0;}
                try{var result=session.saveAndStop(()->save(server),()->stop(server));boolean requested=result.stopRequested();
                    if(requested)context.getSource().sendSuccess(()->Component.translatable("mared.server.lifecycle.stop_requested"),false);
                    else context.getSource().sendFailure(Component.translatable("mared.server.lifecycle.save_failed"));return requested?1:0;
                }catch(IOException failed){context.getSource().sendFailure(Component.translatable("mared.server.lifecycle.save_failed"));return 0;}
            }))
            .then(Commands.literal("checkpoint").executes(context->{
                MinecraftServer server=context.getSource().getServer();var session=SESSIONS.get(server);if(session==null){context.getSource().sendFailure(Component.translatable("mared.server.lifecycle.unavailable"));return 0;}
                if(session.supervised()){context.getSource().sendFailure(Component.translatable("mared.server.lifecycle.use_supervisor"));return 0;}
                try{var result=session.checkpoint(()->save(server));boolean success=result.outcome()==ServerLifecycleState.Outcome.COMPLETED;
                    if(success)context.getSource().sendSuccess(()->Component.translatable("mared.server.lifecycle.saved",result.saveMs()),false);
                    else context.getSource().sendFailure(Component.translatable("mared.server.lifecycle.save_failed"));return success?1:0;
                }catch(IOException failed){context.getSource().sendFailure(Component.translatable("mared.server.lifecycle.save_failed"));return 0;}
            })));
    }
}
