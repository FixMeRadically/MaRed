package com.fixmer.mared.event;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui.MaredCompactButton;
import com.fixmer.mared.gui.MaredEditorScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Mared.MOD_ID, value = Dist.CLIENT)
public class MaredClientEvents {

    private static final int MRED_COLOR = 0xFFFF55FF;
    private static final int BTN_W = 100;
    private static final int BTN_H = 20;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen)) return;

        // Правый верхний угол с отступом
        int x = screen.width - BTN_W - 10;
        int y = 10;

        MaredCompactButton btn = new MaredCompactButton(
            x, y, BTN_W, BTN_H,
            Component.literal("Mared"),
            MRED_COLOR,
            () -> Minecraft.getInstance().setScreen(new MaredEditorScreen())
        );

        event.addListener(btn);
    }
}