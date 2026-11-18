package com.fixmer.mared.technology.server.firstlaunch;
import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.mared.Mared;
import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.neoforge.event.server.ServerStartedEvent;
/** The normal in-game install path also enables future BAT starts, without a manual server-JAR copy. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class ServerAutoManaged106 {
 private ServerAutoManaged106(){}
 @SubscribeEvent public static void started(ServerStartedEvent e){if(!"104".equals(System.getenv("GENESIS_INPLACE_VERSION")))return;var server=e.getServer();Thread worker=new Thread(()->{try{var app=ServerBoot106.currentApp(server.getServerDirectory());if(app!=null){ServerAgent106.install(server.getServerDirectory(),app,ServerBootstrap106.key(server.getServerDirectory()));Mared.LOGGER.info("[Genesis] Ordinary run.bat startup is configured.");}}catch(Exception failed){ServerApp104.diagnostic(server.getServerDirectory(),failed);Mared.LOGGER.warn("[Genesis] Future automatic BAT startup could not be configured; current server is still running.",failed);}},"genesis-autosetup106");worker.setDaemon(true);worker.start();}
}
