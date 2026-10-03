package com.fixmer.mared.gui2.framework.render;

import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Базовые примитивы рендера gui2.
 *
 * 0.3.2:
 *   - textNoShadow.
 *   - gradientV делегирует в GuiGraphics.fillGradient — один draw
 *     call с vertex-цветами вместо N fill().
 *     Раньше панель высотой 500px = 500 fill() вызовов. Теперь = 1.
 */
public final class Render {

    private Render() {}

    public static boolean hovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.renderOutline(x, y, w, h, color);
    }

    public static void text(GuiGraphics g, Font f, String s, int x, int y, int color) {
        TextUtils.text(g, f, s, x, y, color);
    }

    public static void textNoShadow(GuiGraphics g, Font f, String s,
                                    int x, int y, int color) {
        TextUtils.textNoShadow(g, f, s, x, y, color);
    }

    public static void centered(GuiGraphics g, Font f, String s, int cx, int y, int color) {
        TextUtils.centered(g, f, s, cx, y, color);
    }

    // ============================================================
    //  Градиенты
    // ============================================================

    /**
     * 0.3.2: batching через GuiGraphics.fillGradient.
     * Один draw call — quad с интерполяцией вершин.
     */
    public static void gradientV(GuiGraphics g, int x1, int y1, int x2, int y2,
                                 int topColor, int bottomColor) {
        if (y2 <= y1 || x2 <= x1) return;
        if (topColor == bottomColor) {
            g.fill(x1, y1, x2, y2, topColor);
            return;
        }
        g.fillGradient(x1, y1, x2, y2, topColor, bottomColor);
    }

    public static void outlineGradient(GuiGraphics g, int x, int y, int w, int h,
                                       int topColor, int bottomColor) {
        g.fill(x, y, x + w, y + 1, topColor);
        g.fill(x, y + h - 1, x + w, y + h, bottomColor);
        gradientV(g, x, y, x + 1, y + h, topColor, bottomColor);
        gradientV(g, x + w - 1, y, x + w, y + h, topColor, bottomColor);
    }

    // ============================================================
    //  Панели
    // ============================================================

    public static void panel(GuiGraphics g, int x, int y, int w, int h,
                             int bg, int border) {
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, border);
    }

    public static void panelGradient(GuiGraphics g, int x, int y, int w, int h,
                                     int bg, int topColor, int bottomColor) {
        g.fill(x, y, x + w, y + h, bg);
        outlineGradient(g, x, y, w, h, topColor, bottomColor);
    }

    // ============================================================
    //  Скругления
    // ============================================================

    public static void roundedRect(GuiGraphics g, int x, int y, int w, int h,
                                   int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(Math.max(0, radius), Math.min(w / 2, h / 2));
        if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int inset = r - i - 1;
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    public static void roundedOutline(GuiGraphics g, int x, int y, int w, int h,
                                      int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(Math.max(0, radius), Math.min(w / 2, h / 2));
        if (r <= 0) { g.renderOutline(x, y, w, h, color); return; }
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int inset = r - i - 1;
            g.fill(x + inset, y + i, x + inset + 1, y + i + 1, color);
            g.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + inset + 1, y + h - i, color);
            g.fill(x + w - inset - 1, y + h - i - 1,
                   x + w - inset, y + h - i, color);
        }
    }

    // ============================================================
    //  Диалоги
    // ============================================================

    public static void dialogBackground(GuiGraphics g, int screenW, int screenH) {
        MaredTheme t = MaredThemeRegistry.active();
        g.fill(0, 0, screenW, screenH, t.overlayBg);
    }

    public static void dialogPanel(GuiGraphics g, int x, int y, int w, int h,
                                   int accent) {
        MaredTheme t = MaredThemeRegistry.active();
        panelGradient(g, x, y, w, h, t.bgPanel, accent,
            MaredColor.darken(accent, 0.4f));
    }

    // ============================================================
    //  Чекбоксы
    // ============================================================

    public static void drawCheckbox(GuiGraphics g, int x, int y, int sz,
                                    boolean checked, int border, int fill,
                                    int sunkenBg) {
        rect(g, x, y, x + sz, y + sz, sunkenBg);
        outline(g, x, y, sz, sz, border);
        if (checked) rect(g, x + 3, y + 3, x + sz - 3, y + sz - 3, fill);
    }

    public static void drawCheckMark(GuiGraphics g, int x, int y, int sz,
                                     boolean checked, int border, int fill,
                                     int sunkenBg) {
        rect(g, x, y, x + sz, y + sz, sunkenBg);
        outline(g, x, y, sz, sz, border);
        if (checked) {
            int cx = x + sz / 2, cy = y + sz / 2, arm = 3;
            rect(g, cx - arm, cy, cx + arm + 1, cy + 1, fill);
            rect(g, cx, cy - arm, cx + 1, cy + arm + 1, fill);
        }
    }

    // ============================================================
    //  Кнопки 3D
    // ============================================================

    public static void button3D(GuiGraphics g, Font f, int x, int y, int w, int h,
                                String label, int bg, int accent, int textColor,
                                boolean hovered) {
        int border = hovered ? accent : MaredColor.darken(accent, 0.3f);
        int top = MaredColor.lighten(border, 0.15f);
        int bottom = MaredColor.darken(border, 0.25f);
        g.fill(x, y, x + w, y + h, bg);
        outlineGradient(g, x, y, w, h, top, bottom);
        centered(g, f, label, x + w / 2, y + (h - 8) / 2, textColor);
    }
}