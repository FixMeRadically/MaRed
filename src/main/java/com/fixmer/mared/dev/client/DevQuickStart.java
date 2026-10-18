package com.fixmer.mared.dev.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.DevMode;
import com.fixmer.mared.dev.world.DevWorldConfig;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Fallback local quickstart. Explicit resume wins; loading resources must complete before opening a world. */
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public final class DevQuickStart {
    private static final AtomicBoolean TRIGGERED = new AtomicBoolean();
    private static String pendingWorld;
    private static boolean titleChecked;
    private DevQuickStart() { }
    public static boolean intercept() {
        if (TRIGGERED.get() || DevSessionResume.skipLocalWorld() || !DevMode.isEnabled() || !DevWorldConfig.autoLoadEnabled()) return false;
        String world = DevWorldConfig.worldToLoad();
        if (world == null || !DevWorldConfig.worldExists(world) || !TRIGGERED.compareAndSet(false,true)) return false;
        pendingWorld = world;
        return true;
    }
    public static boolean isPending(){return pendingWorld!=null;}
    @SubscribeEvent public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TitleScreen)) { titleChecked = false; return; }
        if (pendingWorld == null && !titleChecked && mc.getOverlay() == null && mc.getConnection() == null) {
            titleChecked = true; intercept();
        }
        if (pendingWorld == null || !(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || mc.getConnection() != null) return;
        String world = pendingWorld; pendingWorld = null;
        try {
            Mared.LOGGER.info("[Mared dev] Opening configured world after resource loading");
            DevWorldProfile.prepare();
            com.fixmer.mared.dev.DevTelemetry.mark("resources_ready");
            com.fixmer.mared.dev.DevTelemetry.mark("world_loading");
            mc.createWorldOpenFlows().openWorld(world, () -> {DevWorldProfile.restore();com.fixmer.mared.dev.DevTelemetry.mark("return_failed");mc.setScreen(new TitleScreen());});
        } catch (RuntimeException error) { DevWorldProfile.restore();com.fixmer.mared.dev.DevTelemetry.mark("return_failed");Mared.LOGGER.error("[Mared dev] Quickstart failed; keeping title screen",error); }
    }
}
