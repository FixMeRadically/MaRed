package com.fixmer.mared.gui2.framework.render.patterns;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Сетчатые и точечные узоры.
 *
 * 0.3.2: grid/dots/diamonds кэшируются через PatternCache.
 *        Раньше каждый кадр генерировались заново.
 */
public final class GridPattern {

    private GridPattern() {}

    // ============================================================
    //  Grid
    // ============================================================

    public static void grid(GuiGraphics g, int x, int y, int w, int h,
                            int cellSize, int color) {
        if (cellSize <= 0 || w <= 0 || h <= 0) return;
        long key = PatternCache.key("grid", w, h, cellSize, color);
        int[] rects = PatternCache.get(key,
            () -> computeGrid(w, h, cellSize, color));
        PatternCache.blit(g, x, y, rects);
    }

    private static int[] computeGrid(int w, int h, int cellSize, int color) {
        int vert = (w + cellSize - 1) / cellSize;
        int horz = (h + cellSize - 1) / cellSize;
        int[] buf = new int[(vert + horz) * PatternCache.STRIDE];
        int idx = 0;
        for (int px = 0; px < w; px += cellSize) {
            idx = PatternCache.put(buf, idx, px, 0, px + 1, h, color);
        }
        for (int py = 0; py < h; py += cellSize) {
            idx = PatternCache.put(buf, idx, 0, py, w, py + 1, color);
        }
        return PatternCache.trim(buf, idx);
    }

    // ============================================================
    //  Dots
    // ============================================================

    public static void dots(GuiGraphics g, int x, int y, int w, int h,
                            int spacing, int dotSize, int color) {
        if (spacing <= 0 || dotSize <= 0 || w <= 0 || h <= 0) return;
        long key = PatternCache.key("dots", w, h, spacing, dotSize, color);
        int[] rects = PatternCache.get(key,
            () -> computeDots(w, h, spacing, dotSize, color));
        PatternCache.blit(g, x, y, rects);
    }

    private static int[] computeDots(int w, int h, int spacing,
                                     int dotSize, int color) {
        int cols = w / spacing + 1;
        int rows = h / spacing + 1;
        int[] buf = new int[cols * rows * PatternCache.STRIDE];
        int idx = 0;
        for (int px = spacing; px < w; px += spacing) {
            for (int py = spacing; py < h; py += spacing) {
                idx = PatternCache.put(buf, idx,
                    px, py, px + dotSize, py + dotSize, color);
            }
        }
        return PatternCache.trim(buf, idx);
    }

    // ============================================================
    //  Diamonds
    // ============================================================

    public static void diamonds(GuiGraphics g, int x, int y, int w, int h,
                                int spacing, int size, int color) {
        if (spacing <= 0 || size <= 0 || w <= 0 || h <= 0) return;
        long key = PatternCache.key("diamonds", w, h, spacing, size, color);
        int[] rects = PatternCache.get(key,
            () -> computeDiamonds(w, h, spacing, size, color));
        PatternCache.blit(g, x, y, rects);
    }

    private static int[] computeDiamonds(int w, int h, int spacing,
                                         int size, int color) {
        int cols = w / spacing + 1;
        int rows = h / spacing + 1;
        // 4 точки на каждый "row" в diamond (i от 0 до size) — 4 rect на i.
        int maxRects = cols * rows * (size + 1) * 4;
        int[] buf = new int[maxRects * PatternCache.STRIDE];
        int idx = 0;
        for (int cx = spacing / 2; cx < w; cx += spacing) {
            for (int cy = spacing / 2; cy < h; cy += spacing) {
                for (int i = 0; i <= size; i++) {
                    int dx = size - i;
                    int px1 = cx - dx, py1 = cy - i;
                    int px2 = cx + dx, py2 = cy + i;
                    // 4 точки
                    if (px1 >= 0 && px1 < w && py1 >= 0 && py1 < h)
                        idx = PatternCache.put(buf, idx,
                            px1, py1, px1 + 1, py1 + 1, color);
                    if (px2 >= 0 && px2 < w && py1 >= 0 && py1 < h)
                        idx = PatternCache.put(buf, idx,
                            px2, py1, px2 + 1, py1 + 1, color);
                    if (px1 >= 0 && px1 < w && py2 >= 0 && py2 < h)
                        idx = PatternCache.put(buf, idx,
                            px1, py2, px1 + 1, py2 + 1, color);
                    if (px2 >= 0 && px2 < w && py2 >= 0 && py2 < h)
                        idx = PatternCache.put(buf, idx,
                            px2, py2, px2 + 1, py2 + 1, color);
                }
            }
        }
        return PatternCache.trim(buf, idx);
    }

    // ============================================================
    //  Hex grid — не кэшируется
    // ============================================================

    public static void hexGrid(GuiGraphics g, int x, int y, int w, int h,
                               int size, int color) {
        if (size <= 0 || w <= 0 || h <= 0) return;
        // Area cap — full-screen hex не поддерживаем.
        if ((long) w * h > 200_000L) return;

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