package com.fixmer.mared.dev.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredLang;

import com.fixmer.mared.dev.storage.DevResumeStore;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Reconnect once to the server the user explicitly chose to restart from. */
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public final class DevSessionResume {
    private static DevResumeStore.Boot boot;
    private static boolean attempted;
    private static boolean waitingRemote;
    private DevSessionResume() { }
    private static Path path() { return com.fixmer.mared.dev.DevPaths.file("resume.properties"); }
    public static DevResumeStore.Target captureCurrent() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) {
            var server = mc.getSingleplayerServer();
            String id = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
            return DevResumeStore.Target.local(id);
        }
        if (mc.getConnection() == null) return null;
        ServerData server = mc.getCurrentServer();
        if (server == null || server.ip == null || server.ip.isBlank())
            throw new IllegalStateException(MaredLang.get("mared.dev.menu.no_address"));
        if (server.isRealm()) throw new IllegalStateException(MaredLang.get("mared.dev.menu.realm"));
        return new DevResumeStore.Target(server.name, server.ip, server.getResourcePackStatus().name());
    }
    public static String prepare(DevResumeStore.Target target) throws IOException {
        com.fixmer.mared.dev.DevPaths.migrateLegacy();
        return DevResumeStore.prepare(path(), target, ProcessHandle.current().pid(), System.currentTimeMillis());
    }
    public static void cancel(String ticket) {
        try { DevResumeStore.cancel(path(), ticket); }
        catch (IOException e) { Mared.LOGGER.warn("[Mared dev] Could not remove restart intent", e); }
    }
    public static synchronized boolean skipLocalWorld() {
        if (boot == null) {
            if (!DevRestartService.isUnderSupervisor()) return false;
            try { boot = DevResumeStore.consume(path(), ProcessHandle.current().pid(), System.currentTimeMillis()); }
            catch (IOException e) { boot = new DevResumeStore.Boot(true, null); Mared.LOGGER.warn("[Mared dev] Could not consume restart intent", e); }
        }
        return boot.skipLocalWorld();
    }
    public static boolean isPendingReturn(){
        if(!DevRestartService.isUnderSupervisor()||LocalRecovery91.blocksAutoOpen())return false;
        if(waitingRemote)return true;
        return !attempted&&skipLocalWorld()&&boot.target()!=null;
    }
    @SubscribeEvent public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if(LocalRecovery91.blocksAutoOpen())return;
        if (!DevRestartService.isUnderSupervisor() || !skipLocalWorld()) return;
        if(waitingRemote && (mc.player!=null || mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen || mc.screen instanceof TitleScreen)) {
            waitingRemote=false;
            com.fixmer.mared.dev.DevTelemetry.mark(mc.player!=null?"world_ready":"return_failed");
        }
        if(attempted)return;
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || mc.getConnection() != null) return;
        attempted = true;
        var target = boot.target();
        if(target==null){com.fixmer.mared.dev.DevTelemetry.mark("title_ready");return;}
        if (target.localWorld()) {
            if (!com.fixmer.mared.dev.world.DevWorldConfig.worldExists(target.world())) {
                com.fixmer.mared.dev.DevTelemetry.mark("return_failed");
                Mared.LOGGER.error("[Mared dev] Restart world no longer exists; keeping title screen"); return;
            }
            DevWorldProfile.prepare();
            com.fixmer.mared.dev.DevTelemetry.mark("resources_ready");
            com.fixmer.mared.dev.DevTelemetry.mark("world_loading");
            try { mc.createWorldOpenFlows().openWorld(target.world(), () -> {DevWorldProfile.restore();mc.setScreen(new TitleScreen());com.fixmer.mared.dev.DevTelemetry.mark("return_failed");}); }
            catch (RuntimeException e) { DevWorldProfile.restore();com.fixmer.mared.dev.DevTelemetry.mark("return_failed");Mared.LOGGER.error("[Mared dev] World reopen failed",e); }
            return;
        }
        ServerData data = new ServerData(target.name(), target.address(), ServerData.Type.OTHER);
        try { data.setResourcePackStatus(ServerData.ServerPackStatus.valueOf(target.packPolicy())); }
        catch (IllegalArgumentException e) { data.setResourcePackStatus(ServerData.ServerPackStatus.PROMPT); }
        Mared.LOGGER.info("[Mared dev] Reconnecting to the server after client restart");
        try { ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(target.address()), data, false, null); waitingRemote=true; }
        catch (RuntimeException e) {
            com.fixmer.mared.dev.DevTelemetry.mark("return_failed");
            Mared.LOGGER.error("[Mared dev] Reconnect failed; keeping menu", e);
            DevActivationService.lastStatus = MaredLang.get("mared.dev.menu.reconnect_failed");
        }
    }
}
