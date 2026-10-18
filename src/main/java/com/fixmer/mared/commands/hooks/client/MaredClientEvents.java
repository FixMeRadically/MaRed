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
 * Клиентские хуки GUI MaRed.
 *
 * Добавляет кнопку "MaRed" на PauseScreen и TitleScreen.
 * Служебных кнопок Welcome Preview и Dev Mode здесь нет.
 */
@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public final class MaredClientEvents {

    private MaredClientEvents() {}

    private static final int BTN_W = 100;
    private static final int BTN_H = 20;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        var screen = event.getScreen();
        boolean isTitle = screen instanceof TitleScreen;
        boolean isPause = screen instanceof PauseScreen;
        if (!isTitle && !isPause) return;
        // This title screen is only a handover point; do not initialize the editor/settings UI for it.
        if(isTitle&&(com.fixmer.mared.dev.client.DevQuickStart.isPending()
            ||com.fixmer.mared.dev.client.DevSessionResume.isPendingReturn()))return;

        addButtons(event, screen.width, screen.height, isTitle);
    }

    private static void addButtons(ScreenEvent.Init.Post event,
                                    int screenWidth, int screenHeight,
                                    boolean isTitle) {
        ButtonCorner corner = MaredSettings.getMaRedButtonCorner();
        int offset = MaredSettings.getMaRedButtonOffset();

        int x, y;
        switch (corner) {
            case TOP_LEFT     -> { x = offset; y = offset; }
            case BOTTOM_LEFT  -> { x = offset; y = screenHeight - BTN_H - offset; }
            case BOTTOM_RIGHT -> { x = screenWidth - BTN_W - offset;
                                    y = screenHeight - BTN_H - offset; }
            default           -> { x = screenWidth - BTN_W - offset; y = offset; }
        }

        MaredCompactButton shellButton = new MaredCompactButton(
            x, y, BTN_W, BTN_H,
            Component.literal("MaRed"),
            0xFFFF55FF,
            MaredScreenManager::openStudio);
        event.addListener(shellButton);

    }
}
