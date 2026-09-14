package com.fixmer.mared.event;

import com.fixmer.mared.Mared;
import com.fixmer.mared.script.MaredPersistentLoader;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class MaredClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        Mared.LOGGER.info("[Mared] Client setup — loading persistent scripts.");
        MaredPersistentLoader.loadAll();
    }
}