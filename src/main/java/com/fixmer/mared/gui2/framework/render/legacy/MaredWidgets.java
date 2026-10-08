package com.fixmer.mared.gui2.framework.render.legacy;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.MaredColor;

/**
 * Фасад над Render.
 *
 * 0.3.0 (Phase B): перенос legacy-интерфейса MaredWidgets. Делегирует
 * к gui2.framework.render.Render.
 *
 * ВАЖНО: MaredWidgets не должен становиться частью публичного API.
 * Это временный bridge над Render. Новый код должен использовать
 * Render напрямую или UiControls.
 *
 * @deprecated after 0.4.0 — использовать Render/UiControls напрямую.
 */
@Deprecated
public final class MaredWidgets {

    private MaredWidgets() {}

    public static void button(GuiGraphics g, Font f, int x, int y, int w, int h,
                              String label, int bg, int border,
                              boolean hovered, int textColor) {
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, border);
        Render.centered(g, f, label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    public static void button3D(GuiGraphics g, Font f, int x, int y, int w, int h,
                                String label, int bg, int accent, int textColor,
                                boolean hovered) {
        Render.button3D(g, f, x, y, w, h, label, bg, accent, textColor, hovered);
    }

    public static void buttonGhost(GuiGraphics g, Font f, int x, int y, int w, int h,
                                   String label, int accent, boolean hovered) {
        int bg = hovered ? MaredColor.withAlpha(accent, 40) : 0;
        if (bg != 0) g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, MaredColor.withAlpha(accent, 100));
        Render.centered(g, f, label, x + w / 2, y + (h - 8) / 2, accent);
    }

    public static void drawCheckbox(GuiGraphics g, int x, int y, int sz,
                                    boolean checked, int border, int fill) {
        Render.drawCheckbox(g, x, y, sz, checked, border, fill, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgSunken);
    }

    public static void drawCheckMark(GuiGraphics g, int x, int y, int sz,
                                     boolean checked, int border, int fill) {
        Render.drawCheckMark(g, x, y, sz, checked, border, fill, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgSunken);
    }

    public static void drawRadio(GuiGraphics g, int x, int y, int sz,
                                 boolean selected, int border, int fill) {
        Render.rect(g, x, y, x + sz, y + sz, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgSunken);
        Render.outline(g, x, y, sz, sz, border);
        if (selected) Render.rect(g, x + 3, y + 3, x + sz - 3, y + sz - 3, fill);
    }

    public static void slider(GuiGraphics g, int x, int y, int w, int h,
                              float value01, int trackColor, int thumbColor) {
        if (w <= 0 || h <= 0) return;
        int trackH = Math.max(2, h / 4);
        int trackY = y + (h - trackH) / 2;
        g.fill(x, trackY, x + w, trackY + trackH, trackColor);

        int filled = (int) (w * MaredColor.clamp01(value01));
        g.fill(x, trackY, x + filled, trackY + trackH, thumbColor);

        int thumbW = Math.max(4, h / 2);
        int thumbX = x + Math.max(0, Math.min(w - thumbW, filled - thumbW / 2));
        g.fill(thumbX, y, thumbX + thumbW, y + h, thumbColor);
    }

    public static void drawDelButton(GuiGraphics g, int x, int y, int sz, boolean hovered) {
        int bg = hovered ? com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgHover : com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgPanelRaised;
        Render.rect(g, x, y, x + sz, y + sz, bg);
        Render.outline(g, x, y, sz, sz, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().danger);
        int cx = x + sz / 2, cy = y + sz / 2, arm = sz / 2 - 2;
        Render.rect(g, cx - arm, cy, cx + arm + 1, cy + 1, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().danger);
    }

    public static void drawUnlockButton(GuiGraphics g, Font f, int x, int y, int sz, boolean hovered) {
        int bg = hovered ? com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgHover : com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgPanelRaised;
        Render.rect(g, x, y, x + sz, y + sz, bg);
        Render.outline(g, x, y, sz, sz, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().success);
        Render.centered(g, f, "U", x + sz / 2, y + sz / 2 - 4, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().success);
    }
}