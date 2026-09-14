package com.fixmer.mared.event;

import java.util.HashMap;
import java.util.Map;

import com.fixmer.mared.Mared;
import com.fixmer.mared.script.MaredBindRegistry;
import com.fixmer.mared.script.MaredEventRegistry;
import com.fixmer.mared.script.MaredGlobalStorage;
import com.fixmer.mared.script.MaredKeyBlocker;
import com.fixmer.mared.script.MaredScriptRunner;
import com.fixmer.mared.script.MaredTicks;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Mared.MOD_ID)
public class MaredServerEvents {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MaredTicks.increment();
        MaredScriptRunner.tick(event.getServer());
    }

    /**
     * on join — срабатывает КАЖДЫЙ заход игрока в мир.
     * Идёт по игровой шине (не MOD), поэтому виден на серверной стороне.
     * В одиночке сервер встроен в клиент, поэтому событие вызывается.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!MaredEventRegistry.has("join")) {
            Mared.LOGGER.info("[Mared] PlayerLoggedInEvent — no 'join' handlers registered");
            return;
        }

        Mared.LOGGER.info("[Mared] PlayerLoggedInEvent — firing 'join' for {}",
            event.getEntity().getName().getString());

        MinecraftServer server = event.getEntity().getServer();
        if (server == null) {
            server = event.getEntity().getServer();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("player", event.getEntity().getName().getString());
        data.put("self", event.getEntity().getName().getString());
        data.put("uuid", event.getEntity().getUUID().toString());

        MaredEventRegistry.fire("join", server, data);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MaredScriptRunner.stopAll();
        MaredGlobalStorage.clear();
        MaredEventRegistry.clearAll();   // удаляет только НЕ-persistent
        MaredBindRegistry.clearAll();
        MaredKeyBlocker.clear();
        MaredTicks.reset();
    }
}