package com.fixmer.mared;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Серверные события: тик и остановка.
 */
@EventBusSubscriber(modid = Mared.MOD_ID)
public class MaredServerEvents {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MaredScriptRunner.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MaredScriptRunner.stopAll();
    }
}