package com.fixmer.mared.gui2.framework.render.patterns;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Диагональный узор.
 *
 * 0.3.0 (Phase E2b): вынесено из MaredPatterns.
 */
public final class DiagonalPattern {

    private DiagonalPattern() {}

    public static void diagonal(GuiGraphics g, int x, int y, int w, int h,
                                int spacing, int color) {
        if (spacing <= 0) return;
        int a = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        for (int d = -h; d < w; d += spacing) {
            for (int i = 0; i < h; i++) {
                int px = d + i;
                if (px >= 0 && px < w) {
                    g.fill(x + px, y + i, x + px + 1, y + i + 1,
                        (a << 24) | rgb);
                }
            }
        }
    }
}