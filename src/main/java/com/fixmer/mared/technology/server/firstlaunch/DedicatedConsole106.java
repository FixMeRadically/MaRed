package com.fixmer.mared.technology.server.firstlaunch;
import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.mared.Mared;
import net.minecraft.server.MinecraftServer;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.neoforge.event.server.*;
import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
/** The mailbox is local OS-owner input only. Minecraft commands execute on the server thread. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class DedicatedConsole106 {
 private static Actor actor;private static final class Actor{final MinecraftServer server;final Path root;final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"genesis-console106");t.setDaemon(true);return t;});volatile long sequence;volatile boolean pending;volatile String lastError="";volatile long logged;Actor(MinecraftServer s){server=s;root=Path.of(System.getenv("GENESIS_DEDICATED74_ROOT"));}}
 private DedicatedConsole106(){}
 @SubscribeEvent public static void started(ServerStartedEvent e){if(!"104".equals(System.getenv("GENESIS_INPLACE_VERSION")))return;Actor a=new Actor(e.getServer());actor=a;a.io.scheduleWithFixedDelay(()->poll(a),200,200,TimeUnit.MILLISECONDS);}
 private static void poll(Actor a){if(actor!=a||a.pending)return;try{var d=ServerConsole106.command(a.root,a.server.getServerDirectory(),System.getenv(),a.sequence);if(d.isEmpty())return;a.pending=true;a.server.execute(()->{try{if(actor!=a)return;a.server.getCommands().performPrefixedCommand(a.server.createCommandSourceStack(),d.get("text"));a.sequence=Long.parseLong(d.get("sequence"));a.io.execute(()->{try{ServerConsole106.ack(a.root,d);}catch(Exception failed){Mared.LOGGER.warn("Genesis console106 acknowledgement failed",failed);}finally{a.pending=false;}});}catch(Exception failed){a.pending=false;Mared.LOGGER.warn("Genesis console106 command failed",failed);}});}catch(Exception failed){String error=failed.getClass().getName()+":"+failed.getMessage();long now=System.nanoTime();if(!error.equals(a.lastError)||now-a.logged>30_000_000_000L){a.lastError=error;a.logged=now;Mared.LOGGER.warn("Genesis console106 mailbox refused",failed);}}}
 @SubscribeEvent public static void stopped(ServerStoppedEvent e){Actor a=actor;if(a!=null&&a.server==e.getServer()){actor=null;a.io.shutdownNow();}}
}
