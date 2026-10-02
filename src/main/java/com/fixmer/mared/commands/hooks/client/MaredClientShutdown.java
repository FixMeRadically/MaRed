package com.fixmer.mared.commands.hooks.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.services.threading.MaredThreading;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * Корректное завершение MaRed при выходе из клиента/сервера.
 *
 * 0.3.0: останавливаем threading-пул — чтобы in-flight задачи
 * получили шанс завершиться до JVM-exit. Daemon-потоки и так умрут,
 * но лучше явно.
 *
 * bus = GAME — GameShuttingDownEvent публикуется в game bus NeoForge,
 * а не в mod bus.
 */
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public class MaredClientShutdown {

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        Mared.LOGGER.info("[Mared] game shutting down, stopping threading...");
        MaredThreading.shutdown();
    }
}