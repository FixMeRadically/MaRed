package com.fixmer.mared.technology.server;

import com.fixmer.mared.Mared;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Common-side lifecycle. No client classes; never routes through Commands or MR. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class GenesisServerModuleHooks {
    private GenesisServerModuleHooks() {}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){LocalScriptServerBridge.tick(event.getServer());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){LocalScriptServerBridge.stopped(event.getServer());}
}
