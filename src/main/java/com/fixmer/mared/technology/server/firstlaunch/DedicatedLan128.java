package com.fixmer.mared.technology.server.firstlaunch;

import com.fixmer.mared.Mared;
import com.fixmer.genesis.technology.server.remote.ServerLan128;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import java.net.InetAddress;

/** NeoForge's single default route can differ between client/server; announce on both families per interface. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class DedicatedLan128 {
    private static MinecraftServer owner;
    private static ServerLan128 pinger;
    private DedicatedLan128(){}
    public static boolean advertise(String bind,boolean dedicated,boolean disabled)throws Exception{
        return dedicated&&!disabled&&(bind.isBlank()||InetAddress.getByName(bind).isAnyLocalAddress()||!InetAddress.getByName(bind).isLoopbackAddress());
    }
    @SubscribeEvent public static synchronized void started(ServerStartedEvent event){
        MinecraftServer server=event.getServer();try{
            if(!advertise(server.getLocalIp(),server.isDedicatedServer(),Boolean.getBoolean("genesis.server.lan.disabled"))||!net.neoforged.neoforge.common.NeoForgeConfig.SERVER.advertiseDedicatedServerToLan.get())return;
            if(pinger!=null)pinger.close();
            String motd=server.getMotd().replace('[','(').replace(']',')');
            pinger=new ServerLan128(motd,server.getPort(),server.getLocalIp());owner=server;
            Mared.LOGGER.info("[Genesis LAN128] Advertising Minecraft game port {} to the local network",server.getPort());
        }catch(Exception unavailable){Mared.LOGGER.warn("[Genesis LAN128] LAN discovery unavailable; direct connection remains available",unavailable);}
    }
    @SubscribeEvent public static synchronized void stopped(ServerStoppedEvent event){if(owner==event.getServer()){if(pinger!=null)pinger.close();pinger=null;owner=null;}}
}
