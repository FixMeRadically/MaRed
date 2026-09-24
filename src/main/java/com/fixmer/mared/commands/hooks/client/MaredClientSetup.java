package com.fixmer.mared.commands.hooks.client;

import com.fixmer.mared.Mared;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import com.fixmer.mared.commands.events.MaredPersistentLoader;

@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class MaredClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        Mared.LOGGER.info("========================================");
        Mared.LOGGER.info("  Mared {} — starting", Mared.VERSION);
        Mared.LOGGER.info("  Fast-path expressions + frame pool");
        Mared.LOGGER.info("========================================");
        MaredPersistentLoader.loadAll();
    }
}