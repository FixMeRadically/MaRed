package com.fixmer.mared.dev.world;

import com.fixmer.mared.Mared;
import com.fixmer.mared.dev.DevMode;
import com.fixmer.mared.dev.DevSessionState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Шаг 4: запоминает id последнего мира в dev-режиме
 * + Шаг 14.5: помечает "мы в мире" для быстрого перезахода.
 */
@EventBusSubscriber(modid = Mared.MOD_ID)
public class DevWorldTracker {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!DevMode.isEnabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        MinecraftServer server = sp.getServer();
        if (server == null) return;

        // Помечаем факт входа — это основа "partial restart" логики.
        DevSessionState.markWorldReady();

        try {
            var root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            String levelId = root.getFileName() == null ? "" : root.getFileName().toString();
            if (levelId.isEmpty()) {
                Mared.LOGGER.warn("[Mared] dev: cannot determine world id from {}", root);
                return;
            }

            Mared.LOGGER.info("[Mared] dev: PlayerLoggedIn, worldId='{}', recording...", levelId);

            if (!DevWorldConfig.updateLastEnabled()) {
                Mared.LOGGER.info("[Mared] dev: updateLast=false, lastWorld not updated");
                return;
            }
            DevWorldConfig.recordLast(levelId);
        } catch (Throwable t) {
            Mared.LOGGER.warn("[Mared] dev: cannot record last world", t);
        }
    }
}