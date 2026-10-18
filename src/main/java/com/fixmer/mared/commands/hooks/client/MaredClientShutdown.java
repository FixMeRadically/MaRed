package com.fixmer.mared.commands.hooks.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.DevMode;
import com.fixmer.mared.plugin.PluginRegistry;
import com.fixmer.mared.services.threading.MaredThreading;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * Корректное завершение MaRed при выходе из клиента.
 *
 * Порядок:
 *   1. Плагины (могут держать ссылки на GUI-структуры).
 *   2. Threading-пул.
 *   3. Снять dev-маркер (последним, чтобы он не исчез раньше,
 *      чем клиент действительно готов завершиться).
 */
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public class MaredClientShutdown {

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        Mared.LOGGER.info("[Mared] game shutting down...");

        try {
            PluginRegistry.unloadAll();
        } catch (Throwable t) {
            Mared.LOGGER.warn("[Mared] plugin unload failed", t);
        }

        MaredThreading.shutdown();

        DevMode.clearRunning();
    }
}