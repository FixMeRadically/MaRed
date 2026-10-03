package com.fixmer.mared.gui2.framework.render.patterns;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Диагональный узор.
 *
 * 0.3.2: кэшируется через PatternCache.
 */
public final class DiagonalPattern {

    private DiagonalPattern() {}

    public static void diagonal(GuiGraphics g, int x, int y, int w, int h,
                                int spacing, int color) {
        if (spacing <= 0 || w <= 0 || h <= 0) return;
        long key = PatternCache.key("diagonal", w, h, spacing, color);
        int[] rects = PatternCache.get(key,
            () -> computeDiagonal(w, h, spacing, color));
        PatternCache.blit(g, x, y, rects);
    }

    private static int[] computeDiagonal(int w, int h, int spacing, int color) {
        int[] buf = new int[(w + h) * PatternCache.STRIDE];
        int idx = 0;
        for (int d = -h; d < w; d += spacing) {
            for (int i = 0; i < h; i++) {
                int px = d + i;
                if (px >= 0 && px < w) {
                    idx = PatternCache.put(buf, idx,
                        px, i, px + 1, i + 1, color);
                }
            }
        }
        return PatternCache.trim(buf, idx);
    }
}