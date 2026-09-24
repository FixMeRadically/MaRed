package com.fixmer.mared.commands.hooks.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.commands.events.MaredPersistentLoader;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
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