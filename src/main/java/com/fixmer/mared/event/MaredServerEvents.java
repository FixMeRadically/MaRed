package com.fixmer.mared.event;

import com.fixmer.mared.Mared;
import com.fixmer.mared.script.MaredScriptRunner;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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