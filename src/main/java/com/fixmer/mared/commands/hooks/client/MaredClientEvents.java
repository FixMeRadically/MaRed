package com.fixmer.mared.commands.hooks.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.framework.components.overlay.MaredCompactButton;
import com.fixmer.mared.gui2.launcher.MaredScreenManager;
import com.fixmer.mared.services.settings.LayoutSettings.ButtonCorner;

import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Кнопка "MaRed" на PauseScreen / TitleScreen.
 *
 * 0.3.2 (audit #114):
 *   Позиция кнопки настраивается: corner + offset.
 *   Corner: TOP_RIGHT / TOP_LEFT / BOTTOM_RIGHT / BOTTOM_LEFT.
 *   Offset: 0..100 px от края. Default: TOP_RIGHT + 10.
 */
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public class MaredClientEvents {

    private static final int MRED_COLOR = 0xFFFF55FF;
    private static final int BTN_W = 100;
    private static final int BTN_H = 20;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        var screen = event.getScreen();

        if (screen instanceof PauseScreen || screen instanceof TitleScreen) {
            addButton(event, screen.width, screen.height);
        }
    }

    private static void addButton(ScreenEvent.Init.Post event,
                                  int screenWidth, int screenHeight) {
        ButtonCorner corner = MaredSettings.getMaRedButtonCorner();
        int offset = MaredSettings.getMaRedButtonOffset();

        int x, y;
        switch (corner) {
            case TOP_LEFT -> {
                x = offset;
                y = offset;
            }
            case BOTTOM_RIGHT -> {
                x = screenWidth - BTN_W - offset;
                y = screenHeight - BTN_H - offset;
            }
            case BOTTOM_LEFT -> {
                x = offset;
                y = screenHeight - BTN_H - offset;
            }
            default -> { // TOP_RIGHT
                x = screenWidth - BTN_W - offset;
                y = offset;
            }
        }

        MaredCompactButton btn = new MaredCompactButton(
            x, y, BTN_W, BTN_H,
            Component.literal("MaRed"),
            MRED_COLOR,
            MaredScreenManager::openStudio
        );

        event.addListener(btn);
    }
}