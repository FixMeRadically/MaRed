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
    public static final String VERSION = "0.5.0";
    public static final Logger LOGGER  = LogUtils.getLogger();

    public Mared(IEventBus modEventBus, ModContainer modContainer) {
        try{com.fixmer.genesis.supervisor.ProfileLease92.start(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get());}catch(java.io.IOException busy){throw new IllegalStateException("Genesis: this profile is already in use, recovery is in progress, or early admission was not installed. Close the other game or finish recovery before reopening this profile.",busy);}
        LOGGER.info("Mared {} loading...", VERSION);
        DevMode.logStatus();
        com.fixmer.mared.dev.DevTelemetry.mark("mod_loaded");
    }
}
