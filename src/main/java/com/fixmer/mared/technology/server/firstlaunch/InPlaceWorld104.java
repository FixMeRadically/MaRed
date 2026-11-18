package com.fixmer.mared.technology.server.firstlaunch;
import com.fixmer.genesis.technology.server.remote.ServerGate104;import com.fixmer.mared.Mared;
import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.neoforge.event.server.*;
/** Common-side only: a dedicated machine never needs client classes. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class InPlaceWorld104 {
 private static ServerGate104 gate;private InPlaceWorld104(){}
 @SubscribeEvent public static void before(ServerAboutToStartEvent e){if(!"104".equals(System.getenv("GENESIS_INPLACE_VERSION")))return;try{if(!e.getServer().isDedicatedServer()||gate!=null)throw new IllegalStateException();gate=ServerGate104.beforeWorld(System.getenv(),e.getServer().getServerDirectory());}catch(Exception failed){throw new IllegalStateException("Genesis server folder admission failed",failed);}}
 @SubscribeEvent public static void stopped(ServerStoppedEvent e){ServerGate104 value=gate;gate=null;if(value!=null)try{value.close();}catch(Exception ignored){}}
}
