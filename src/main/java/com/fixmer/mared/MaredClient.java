package com.fixmer.mared;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = Mared.MOD_ID, dist = Dist.CLIENT)
public class MaredClient {

    public MaredClient() {}

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Mared.LOGGER.info("Mared client ready. Player: {}",
            Minecraft.getInstance().getUser().getName());
    }
}