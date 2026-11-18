package com.fixmer.mared;

import com.fixmer.mared.dev.DevMode;


import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Mared.MOD_ID)
public class Mared {

    public static final String MOD_ID  = "mared";
    public static final String VERSION = "0.4.0";
    public static final Logger LOGGER  = LogUtils.getLogger();

    public Mared(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Mared {} loading...", VERSION);
        DevMode.logStatus();
        com.fixmer.mared.dev.DevTelemetry.mark("mod_loaded");
    }
}