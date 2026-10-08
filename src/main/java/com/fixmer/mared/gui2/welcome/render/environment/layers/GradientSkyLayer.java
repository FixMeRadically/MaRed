package com.fixmer.mared.gui2.welcome.render.environment.layers;

import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentCamera;
import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentLayer;
import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentPalette;
import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentTime;

import net.minecraft.client.gui.GuiGraphics;

public final class GradientSkyLayer implements EnvironmentLayer {

    @Override
    public void render(GuiGraphics g,
                       int width,
                       int height,
                       EnvironmentTime time,
                       EnvironmentPalette palette,
                       EnvironmentCamera camera) {
        if (width <= 0 || height <= 0) return;

        int halfH = height / 2;

        g.fillGradient(0, 0, width, halfH,
                palette.skyTop, palette.skyMiddle);

        g.fillGradient(0, halfH, width, height,
                palette.skyMiddle, palette.skyBottom);
    }
}