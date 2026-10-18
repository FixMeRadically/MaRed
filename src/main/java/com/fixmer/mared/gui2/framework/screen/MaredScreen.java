package com.fixmer.mared.gui2.framework.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Custom MaRed screens own their background. Vanilla Screen.render must never blur their completed frame. */
public abstract class MaredScreen extends Screen {
    protected MaredScreen(Component title) { super(title); }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
}
