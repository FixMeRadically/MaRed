package com.fixmer.mared;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// Этот класс загружается только на клиенте.
@Mod(value = Mared.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public class MaredClient {

    public MaredClient() {
        // Пока здесь ничего не нужно.
        // Позже сюда можно добавить регистрацию рендереров, клавиш и т.д.
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Mared.LOGGER.info("Mared client setup завершён");
        Mared.LOGGER.info("Игрок: {}", Minecraft.getInstance().getUser().getName());
    }
}