package com.fixmer.mared.gui2.framework.render.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.render.MaredColor;
import com.fixmer.mared.gui2.framework.render.FrameworkPalette;
import com.fixmer.mared.gui2.framework.render.legacy.MaredUi;

/**
 * 0.3.0 (Phase B): вынесены виджет-примитивы из MaredUi.
 * Тонкая логика рендера кнопок, чекбоксов, слайдеров, скроллбара.
 * Все методы — static, без состояния.
 */
public final class UiControls {

    private UiControls() {}

    // ---- Кнопки ----

    public static void button(GuiGraphics g, Font f, int x, int y, int w, int h,
                              String label, int bg, int border,
                              boolean hovered, int textColor) {
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, border);
        TextUtils.centered(g, f, label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    public static void buttonGradient(GuiGraphics g, Font f, int x, int y,
                                      int w, int h, String label, int bg,
                                      int topColor, int bottomColor, int textColor) {
        g.fill(x, y, x + w, y + h, bg);
        Render.outlineGradient(g, x, y, w, h, topColor, bottomColor);
        TextUtils.centered(g, f, label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    public static void buttonGhost(GuiGraphics g, Font f, int x, int y, int w, int h,
                                   String label, int accent, boolean hovered) {
        int bg = hovered ? MaredColor.withAlpha(accent, 40) : 0;
        if (bg != 0) g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, MaredColor.withAlpha(accent, 100));
        TextUtils.centered(g, f, label, x + w / 2, y + (h - 8) / 2, accent);
    }

    // ---- Чекбоксы / радио ----

    public static void drawCheckbox(GuiGraphics g, int x, int y, int sz,
                                    boolean checked, int border, int fill) {
        g.fill(x, y, x + sz, y + sz, FrameworkPalette.SUNKEN_BG);
        g.renderOutline(x, y, sz, sz, border);
        if (checked) g.fill(x + 3, y + 3, x + sz - 3, y + sz - 3, fill);
    }

    public static void drawCheckMark(GuiGraphics g, int x, int y, int sz,
                                     boolean checked, int border, int fill) {
        g.fill(x, y, x + sz, y + sz, FrameworkPalette.SUNKEN_BG);
        g.renderOutline(x, y, sz, sz, border);
        if (checked) {
            int cx = x + sz / 2, cy = y + sz / 2, arm = 3;
            g.fill(cx - arm, cy, cx + arm + 1, cy + 1, fill);
            g.fill(cx, cy - arm, cx + 1, cy + arm + 1, fill);
        }
    }

    public static void drawRadio(GuiGraphics g, int x, int y, int sz,
                                 boolean selected, int border, int fill) {
        g.fill(x, y, x + sz, y + sz, FrameworkPalette.SUNKEN_BG);
        g.renderOutline(x, y, sz, sz, border);
        if (selected) g.fill(x + 3, y + 3, x + sz - 3, y + sz - 3, fill);
    }

    // ---- Спец-кнопки ----

    public static void drawDelButton(GuiGraphics g, int x, int y, int sz, boolean hovered) {
        int bg = hovered ? 0xFF663333 : 0xFF3A2020;
        g.fill(x, y, x + sz, y + sz, bg);
        g.renderOutline(x, y, sz, sz, FrameworkPalette.DANGER);
        int cx = x + sz / 2, cy = y + sz / 2, arm = sz / 2 - 2;
        g.fill(cx - arm, cy, cx + arm + 1, cy + 1, FrameworkPalette.DANGER);
    }

    public static void drawUnlockButton(GuiGraphics g, Font f, int x, int y,
                                        int sz, boolean hovered) {
        int bg = hovered ? 0xFF336633 : 0xFF203A20;
        g.fill(x, y, x + sz, y + sz, bg);
        g.renderOutline(x, y, sz, sz, FrameworkPalette.SUCCESS);
        TextUtils.centered(g, f, "U", x + sz / 2, y + sz / 2 - 4, FrameworkPalette.SUCCESS);
    }

    // ---- Слайдер ----

    public static void slider(GuiGraphics g, int x, int y, int w, int h,
                              float value01, int trackColor, int thumbColor) {
        if (w <= 0 || h <= 0) return;
        float v = Math.max(0f, Math.min(1f, value01));
        int trackH = Math.max(2, h / 4);
        int trackY = y + (h - trackH) / 2;
        g.fill(x, trackY, x + w, trackY + trackH, trackColor);

        int filled = (int) (w * v);
        g.fill(x, trackY, x + filled, trackY + trackH, thumbColor);

        int thumbW = Math.max(4, h / 2);
        int thumbX = x + Math.max(0, Math.min(w - thumbW, filled - thumbW / 2));
        g.fill(thumbX, y, thumbX + thumbW, y + h, thumbColor);
    }

    // ---- Скроллбар ----

    public static void scrollbarTrack(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, FrameworkPalette.SCROLL_TRACK);
    }

    public static void scrollbarThumb(GuiGraphics g, int x, int y, int w, int h,
                                      int topColor, int bottomColor) {
        Render.gradientV(g, x, y, x + w, y + h, topColor, bottomColor);
    }

    public static void drawScrollbar(GuiGraphics g, int trackX, int trackY,
                                     int trackW, int trackH,
                                     int thumbY, int thumbH,
                                     int accentTop, int accentBottom) {
        g.fill(trackX, trackY, trackX + trackW, trackY + trackH, FrameworkPalette.SCROLL_TRACK);
        if (thumbH > 0) {
            Render.gradientV(g, trackX, thumbY, trackX + trackW, thumbY + thumbH,
                accentTop, accentBottom);
        }
    }

    // ---- Scissor ----

    public static void scissorOn(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    public static void scissorOff(GuiGraphics g) {
        g.disableScissor();
    }
}