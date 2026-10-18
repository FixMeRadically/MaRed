package com.fixmer.mared.commands.hooks.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.DevMode;
import com.fixmer.mared.dev.client.DevRestartService;
import com.fixmer.mared.dev.DevSessionState;
import com.fixmer.mared.commands.events.MaredPersistentLoader;
import com.fixmer.mared.plugin.PluginLoader;
import com.fixmer.mared.services.threading.MaredThreading;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Клиентская инициализация MaRed.
 */
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public class MaredClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        boolean dev = DevMode.isEnabled();
        boolean quick = dev && DevSessionState.isQuickRestart();

        Mared.LOGGER.info("========================================");
        Mared.LOGGER.info("  Mared {} — starting{}{}",
            Mared.VERSION,
            dev ? " [DEV]" : "",
            quick ? " [QUICK-RESTART]" : "");
        Mared.LOGGER.info("  Fast-path expressions + frame pool");

        if (dev) {
            Mared.LOGGER.info("  Dev mode: extra logging, running marker");
            if (quick) {
                Mared.LOGGER.info("  Quick restart expected: title screen will be skipped, world auto-loaded");
            }
            if (DevRestartService.isUnderSupervisor()) {
                Mared.LOGGER.info("  Automatic restart after successful server apply ({}), or /mared dev restart",
                    DevRestartService.describe());
            } else {
                Mared.LOGGER.info("  Successful server apply automatically enables dev mode and starts the supervisor");
            }
        }
        Mared.LOGGER.info("========================================");

        long t0 = System.nanoTime();
        MaredThreading.init();
        long tThreading = System.nanoTime();
        MaredPersistentLoader.loadAll();
        long tPersistent = System.nanoTime();
        PluginLoader.loadAll();
        long tPlugins = System.nanoTime();

        com.fixmer.mared.dev.DevTelemetry.report("mared_threading",(tThreading-t0)/1_000_000);
        com.fixmer.mared.dev.DevTelemetry.report("mared_persistent",(tPersistent-tThreading)/1_000_000);
        com.fixmer.mared.dev.DevTelemetry.report("mared_plugins",(tPlugins-tPersistent)/1_000_000);
        com.fixmer.mared.dev.DevTelemetry.report("mared_setup",(tPlugins-t0)/1_000_000);

        if (dev) {
            Mared.LOGGER.info(
                "[Mared] client init timings: threading={} ms, persistent={} ms, plugins={} ms, total={} ms",
                (tThreading - t0) / 1_000_000,
                (tPersistent - tThreading) / 1_000_000,
                (tPlugins - tPersistent) / 1_000_000,
                (tPlugins - t0) / 1_000_000);
        }

        DevMode.markRunning("client_ready");
    }
}