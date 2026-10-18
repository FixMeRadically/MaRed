package com.fixmer.mared.dev.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.DevMode;
import com.fixmer.mared.dev.DevPaths;
import com.fixmer.mared.dev.world.DevPlayerState;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;

/** One accepted application -> one checkpoint -> one confirmed client restart. Never invoked for stop/rollback. */
public final class DevAutoRestart {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private DevAutoRestart() { }
    public static CompletableFuture<Boolean> afterSuccessfulApply(String project) { return afterSuccessfulApply(project, () -> true); }
    public static CompletableFuture<Boolean> afterSuccessfulApply(String project, java.util.function.BooleanSupplier targetCurrent) {
        if (!targetCurrent.getAsBoolean()) { DevActivationService.lastStatus = "Restart cancelled: world/player changed"; return CompletableFuture.completedFuture(false); }
        if (!BUSY.compareAndSet(false,true)) return CompletableFuture.completedFuture(false);
        var result = new CompletableFuture<Boolean>();
        Minecraft mc = Minecraft.getInstance();
        DevMode.enableForSession();
        DevRestartOverlay.show("mared.dev.restart.animation.saving");
        // Preparing world/player state must happen on its owning server thread, never on the HTTP worker.
        var server = mc.getSingleplayerServer();
        var owner = mc.player == null ? null : mc.player.getUUID();
        CompletableFuture<Void> checkpoint = new CompletableFuture<>();
        try {
            DevPaths.migrateLegacy();
            if (server == null) checkpoint.complete(null);
            else server.execute(() -> {
                try {
                    if (mc.getSingleplayerServer() != server || owner == null || server.isPublished())
                        throw new IllegalStateException("World changed while preparing restart");
                    var player = server.getPlayerList().getPlayer(owner);
                    if (player == null) throw new IllegalStateException("Player left the world before restart");
                    long savedAt=System.nanoTime();
                    server.saveEverything(true,true,true);
                    Mared.LOGGER.info("[Mared dev] World flush before restart: {} ms",(System.nanoTime()-savedAt)/1_000_000);
                    if (!DevPlayerState.capture(player,server)) throw new IllegalStateException("Player checkpoint was not written");
                    checkpoint.complete(null);
                } catch (Exception e) { checkpoint.completeExceptionally(e); }
            });
        } catch (Exception e) { checkpoint.completeExceptionally(e); }
        checkpoint.orTimeout(10,TimeUnit.SECONDS).whenComplete((ignored,error) -> mc.execute(() -> {
            if (error != null || !targetCurrent.getAsBoolean()) {
                DevActivationService.lastStatus = "Restart checkpoint failed: " + (error == null ? "World/player changed" : error.getMessage());
                Mared.LOGGER.error("[Mared dev] Restart checkpoint failed",error);
                DevRestartOverlay.clear();
                BUSY.set(false); result.complete(false); return;
            }
            Mared.LOGGER.info("[Mared dev] Applied project {}; restarting the client",project);
            CompletableFuture<Boolean> request = DevRestartService.isUnderSupervisor()
                ? DevRestartService.restartAsync() : DevActivationService.activate();
            request.whenComplete((success,failure) -> {
                boolean accepted = failure == null && Boolean.TRUE.equals(success);
                if (!accepted) {BUSY.set(false);DevRestartOverlay.clear();}
                result.complete(accepted);
            });
        }));
        return result;
    }
}
