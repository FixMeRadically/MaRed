package com.fixmer.mared;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.fixmer.mared.command.MaredCommands;
import com.fixmer.mared.event.MaredServerEvents;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Mared.MOD_ID)
public class Mared {

    public static final String MOD_ID = "mared";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Mared(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Mared loading...");
        // MaredCommands и MaredServerEvents подписываются автоматически
        // через @EventBusSubscriber.
    }
}