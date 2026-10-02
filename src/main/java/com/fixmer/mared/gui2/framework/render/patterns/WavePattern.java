package com.fixmer.mared.gui2.framework.render.patterns;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Волновые и зигзагообразные узоры.
 *
 * 0.3.0 (Phase E2b): вынесено из MaredPatterns.
 */
public final class WavePattern {

    private WavePattern() {}

    public static void waves(GuiGraphics g, int x, int y, int w, int h,
                             int amplitude, int period, int color) {
        if (period <= 0 || amplitude <= 0) return;
        int a = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        int lineColor = (a << 24) | rgb;
        int vSpacing = amplitude * 3;
        for (int base = 0; base < h + amplitude * 2; base += vSpacing) {
            for (int px = 0; px < w; px++) {
                double phase = (px + x) * 2.0 * Math.PI / period;
                int py = base + (int) (Math.sin(phase) * amplitude);
                if (py >= 0 && py < h) {
                    g.fill(x + px, y + py, x + px + 1, y + py + 1, lineColor);
                }
            }
        }
    }

    public static void zigzag(GuiGraphics g, int x, int y, int w, int h,
                              int step, int offset, int color) {
        if (step <= 0) return;
        int a = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        int lineColor = (a << 24) | rgb;
        int vSpacing = offset * 2 + 8;
        for (int base = 0; base < h; base += vSpacing) {
            int dir = 0;
            int py = base;
            for (int px = 0; px < w; px += step) {
                int nextY = py + dir;
                int steps = Math.max(1, Math.abs(nextY - py) + step);
                for (int s = 0; s < steps; s++) {
                    float t = s / (float) steps;
                    int ix = x + px + (int) (step * t);
                    int iy = y + py + (int) ((nextY - py) * t);
                    if (iy >= y && iy < y + h) {
                        g.fill(ix, iy, ix + 1, iy + 1, lineColor);
                    }
                }
                py = nextY;
                dir = dir == 0 ? -offset : (dir == -offset ? offset : 0);
                if (dir == 0) dir = offset;
            }
        }
    }
}