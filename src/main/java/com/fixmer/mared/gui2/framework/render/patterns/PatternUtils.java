package com.fixmer.mared.gui2.framework.render.patterns;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Внутренние утилиты для pattern-классов.
 *
 * 0.3.0 (Phase E2b): вынесено из MaredPatterns. Package-private —
 * используется только внутри patterns/*, не является публичным API.
 */
final class PatternUtils {

    private PatternUtils() {}

    static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 2147483647;
        h = (h ^ (h >> 13)) * 1274126177;
        return h ^ (h >> 16);
    }

    static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    static void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2,
                         int color, int clipX, int clipY, int clipW, int clipH) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy, x = x1, y = y1;
        while (true) {
            if (x >= clipX && x < clipX + clipW
                && y >= clipY && y < clipY + clipH) {
                g.fill(x, y, x + 1, y + 1, color);
            }
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx)  { err += dx; y += sy; }
        }
    }
}