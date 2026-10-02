package com.fixmer.mared.gui2.framework.render.patterns;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Сетчатые и точечные узоры.
 *
 * 0.3.0 (Phase E2b): вынесено из MaredPatterns.
 */
public final class GridPattern {

    private GridPattern() {}

    public static void grid(GuiGraphics g, int x, int y, int w, int h,
                            int cellSize, int color) {
        if (cellSize <= 0) return;
        int a = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        for (int px = 0; px < w; px += cellSize) {
            g.fill(x + px, y, x + px + 1, y + h, (a << 24) | rgb);
        }
        for (int py = 0; py < h; py += cellSize) {
            g.fill(x, y + py, x + w, y + py + 1, (a << 24) | rgb);
        }
    }

    public static void dots(GuiGraphics g, int x, int y, int w, int h,
                            int spacing, int dotSize, int color) {
        if (spacing <= 0) return;
        int a = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        for (int px = spacing; px < w; px += spacing) {
            for (int py = spacing; py < h; py += spacing) {
                g.fill(x + px, y + py, x + px + dotSize, y + py + dotSize,
                    (a << 24) | rgb);
            }
        }
    }

    public static void diamonds(GuiGraphics g, int x, int y, int w, int h,
                                int spacing, int size, int color) {
        if (spacing <= 0 || size <= 0) return;
        int a = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        int lineColor = (a << 24) | rgb;
        for (int cx = spacing / 2; cx < w; cx += spacing) {
            for (int cy = spacing / 2; cy < h; cy += spacing) {
                for (int i = 0; i <= size; i++) {
                    int dx = size - i;
                    int px1 = x + cx - dx, py1 = y + cy - i;
                    int px2 = x + cx + dx, py2 = y + cy + i;
                    if (px1 >= x && px1 < x + w && py1 >= y && py1 < y + h)
                        g.fill(px1, py1, px1 + 1, py1 + 1, lineColor);
                    if (px2 >= x && px2 < x + w && py1 >= y && py1 < y + h)
                        g.fill(px2, py1, px2 + 1, py1 + 1, lineColor);
                    if (px1 >= x && px1 < x + w && py2 >= y && py2 < y + h)
                        g.fill(px1, py2, px1 + 1, py2 + 1, lineColor);
                    if (px2 >= x && px2 < x + w && py2 >= y && py2 < y + h)
                        g.fill(px2, py2, px2 + 1, py2 + 1, lineColor);
                }
            }
        }
    }

    public static void hexGrid(GuiGraphics g, int x, int y, int w, int h,
                               int size, int color) {
        if (size <= 0) return;
        int a = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        int lineColor = (a << 24) | rgb;
        int hexH = (int) (size * 1.732f);
        int colSpacing = size * 3;
        for (int col = 0; col * colSpacing < w + size; col++) {
            int cx = col * colSpacing;
            int yOffset = (col % 2 == 0) ? 0 : hexH / 2;
            for (int row = -1; row * hexH < h + hexH; row++) {
                int cy = row * hexH + yOffset;
                drawHex(g, x + cx, y + cy, size, x, y, w, h, lineColor);
            }
        }
    }

    private static void drawHex(GuiGraphics g, int cx, int cy, int size,
                                int clipX, int clipY, int clipW, int clipH,
                                int color) {
        int[][] pts = {
            {0, -size}, {size, -size / 2}, {size, size / 2},
            {0, size}, {-size, size / 2}, {-size, -size / 2}
        };
        for (int i = 0; i < 6; i++) {
            PatternUtils.drawLine(g,
                cx + pts[i][0], cy + pts[i][1],
                cx + pts[(i + 1) % 6][0], cy + pts[(i + 1) % 6][1],
                color, clipX, clipY, clipW, clipH);
        }
    }
}