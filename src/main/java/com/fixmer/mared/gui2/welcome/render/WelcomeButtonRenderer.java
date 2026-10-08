package com.fixmer.mared.gui2.welcome.render;

import com.fixmer.mared.gui2.welcome.hero.WelcomeModuleButton;
import com.fixmer.mared.gui2.welcome.layout.WelcomeBounds;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

public final class WelcomeButtonRenderer {

    public void render(
            GuiGraphics graphics,
            Font font,
            WelcomeModuleButton button,
            WelcomeBounds bounds
    ) {
        int x = bounds.x();
        int y = bounds.y();
        int w = bounds.width();
        int h = bounds.height();

        int alpha = 80 + (int)(button.hover() * 100);
        int color = (alpha << 24) | (button.color() & 0xFFFFFF);

        graphics.fill(x, y, x + w, y + h, color);
        graphics.drawString(font, button.title(), x + 15, y + 15, 0xFFFFFFFF);
    }
}