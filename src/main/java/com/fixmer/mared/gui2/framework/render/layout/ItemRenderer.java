package com.fixmer.mared.gui2.framework.render.layout;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Phase B): вынесен из MaredUi в top-level.
 */
public interface ItemRenderer {
    void render(GuiGraphics g, Font f, int idx, int x, int y, int w, int h,
                boolean hovered, boolean selected);
}