package com.fixmer.mared.dev.world;

import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.DevMode;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * Шаг 5: триггеры сохранения/восстановления состояния игрока.
 *
 * Три места сохранения:
 *   1. Раз в 40 тиков (2 секунды) — страховка от жёсткого kill.
 *   2. PlayerLoggedOutEvent — штатный выход.
 *   3. ServerStoppingEvent — штатная остановка сервера.
 *
 * Одно место восстановления:
 *   PlayerLoggedInEvent, отложено на следующий тик через
 *   server.execute(), чтобы ванильный спавн-пакет уже ушёл клиенту.
 *
 * Всё работает только в dev-режиме и только если мир/UUID совпадают.
 */
@EventBusSubscriber(modid = Mared.MOD_ID)
public class DevPlayerStateTracker {

    private static final int SAVE_INTERVAL_TICKS = 40;

    private static int tickCounter = 0;

    // ============================================================
    //  Periodic save
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!DevMode.isEnabled()) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        if (++tickCounter < SAVE_INTERVAL_TICKS) return;
        tickCounter = 0;

        ServerPlayer player = firstPlayer(server);
        if (player == null) return;

        DevPlayerState.capture(player, server);
    }

    // ============================================================
    //  Restore on login
    // ============================================================

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!DevMode.isEnabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        MinecraftServer server = sp.getServer();
        if (server == null) return;

        Mared.LOGGER.info("[Mared] dev: scheduling player state restore for {}",
            sp.getName().getString());

        server.execute(() -> {
            try {
                if (!sp.isRemoved() && sp.isAlive()) {
                    long restoreAt=System.nanoTime();
                    DevPlayerState.restore(sp, server);
                    com.fixmer.mared.dev.DevTelemetry.report("player_restore",(System.nanoTime()-restoreAt)/1_000_000);
                    com.fixmer.mared.dev.DevTelemetry.mark("player_restored");
                } else {
                    Mared.LOGGER.info(
                        "[Mared] dev: player removed before restore, skipping");
                }
            } catch (Throwable t) {
                Mared.LOGGER.warn("[Mared] deferred player state restore failed", t);
            }
        });
    }

    // ============================================================
    //  Capture on logout
    // ============================================================

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!DevMode.isEnabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        MinecraftServer server = sp.getServer();
        if (server == null) return;

        Mared.LOGGER.info("[Mared] dev: PlayerLoggedOut, capturing state");
        DevPlayerState.capture(sp, server);
    }

    // ============================================================
    //  Capture on server stop
    // ============================================================

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (!DevMode.isEnabled()) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        ServerPlayer player = firstPlayer(server);
        if (player == null) return;

        Mared.LOGGER.info("[Mared] dev: ServerStopping, capturing state");
        DevPlayerState.capture(player, server);
    }

    private static ServerPlayer firstPlayer(MinecraftServer server) {
        try {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return null;
            return players.get(0);
        } catch (Throwable t) {
            return null;
        }
    }
}