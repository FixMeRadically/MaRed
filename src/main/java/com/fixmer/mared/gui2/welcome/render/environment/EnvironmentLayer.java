package com.fixmer.mared.gui2.welcome.render.environment;

import net.minecraft.client.gui.GuiGraphics;

public interface EnvironmentLayer {

    void render(GuiGraphics graphics,
                int width,
                int height,
                EnvironmentTime time,
                EnvironmentPalette palette,
                EnvironmentCamera camera);

    default boolean isEnabled() { return true; }
}