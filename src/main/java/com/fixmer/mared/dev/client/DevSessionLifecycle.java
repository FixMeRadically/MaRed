package com.fixmer.mared.dev.client;

import com.fixmer.mared.Mared;
import com.fixmer.genesis.supervisor.GameSessionClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** Minecraft shutdown plus the JDK-only exit hook distinguish the game from its launcher. */
@EventBusSubscriber(modid=Mared.MOD_ID,value=Dist.CLIENT)
public final class DevSessionLifecycle {
    private DevSessionLifecycle() { }
    @SubscribeEvent public static void closing(GameShuttingDownEvent event){GameSessionClient.normalClosing();DevStartupWork.close();}
}
