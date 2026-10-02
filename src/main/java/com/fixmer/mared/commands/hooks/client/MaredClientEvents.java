package com.fixmer.mared.commands.hooks.client;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.framework.components.overlay.MaredCompactButton;
import com.fixmer.mared.gui2.launcher.MaredScreenManager;

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
 * 0.3.0 (Stage B7): открывает gui2.MaredStudioScreen вместо legacy
 * gui.editor.MaredEditorScreen. Импорт MaredCompactButton — из gui2,
 * не из gui.common.
 */
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public class MaredClientEvents {

    private static final int MRED_COLOR = 0xFFFF55FF;
    private static final int BTN_W = 100;
    private static final int BTN_H = 20;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        var screen = event.getScreen();

        if (screen instanceof PauseScreen) {
            addButton(event, screen.width, 10);
        } else if (screen instanceof TitleScreen) {
            addButton(event, screen.width, 10);
        }
    }

    private static void addButton(ScreenEvent.Init.Post event,
                                  int screenWidth, int y) {
        int x = screenWidth - BTN_W - 10;

        MaredCompactButton btn = new MaredCompactButton(
            x, y, BTN_W, BTN_H,
            Component.literal("MaRed"),
            MRED_COLOR,
            MaredScreenManager::openStudio
        );

        event.addListener(btn);
    }
}