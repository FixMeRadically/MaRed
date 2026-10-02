package com.fixmer.mared.gui2.framework.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Фасад над Render.
 *
 * 0.3.0 (Phase B): перенос legacy-интерфейса MaredDraw для совместимости
 * с MaredLogPanel и другими переносимыми компонентами. Внутри —
 * делегирование к gui2.framework.render.Render.
 *
 * @deprecated after 0.4.0 — использовать Render/UiPanels напрямую.
 * Фасад удаляется после переписывания MaredLogPanel на прямые вызовы
 * (это уже gui2, но всё ещё использует MaredDraw.* для переноса).
 */
@Deprecated
public final class MaredDraw {

    private MaredDraw() {}

    // ---- Примитивы ----

    public static void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        Render.rect(g, x1, y1, x2, y2, color);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        Render.outline(g, x, y, w, h, color);
    }

    public static void lineH(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    public static void lineV(GuiGraphics g, int x, int y, int h, int color) {
        g.fill(x, y, x + 1, y + h, color);
    }

    // ---- Градиенты ----

    public static void gradientV(GuiGraphics g, int x1, int y1, int x2, int y2,
                                 int topColor, int bottomColor) {
        Render.gradientV(g, x1, y1, x2, y2, topColor, bottomColor);
    }

    public static void gradientH(GuiGraphics g, int x1, int y1, int x2, int y2,
                                 int leftColor, int rightColor) {
        int w = x2 - x1;
        if (w <= 0) return;
        if (w == 1) { g.fill(x1, y1, x1 + 1, y2, leftColor); return; }
        int denom = w - 1;
        for (int i = 0; i < w; i++) {
            g.fill(x1 + i, y1, x1 + i + 1, y2,
                MaredColor.lerpColor(leftColor, rightColor, (float) i / denom));
        }
    }

    public static void outlineGradient(GuiGraphics g, int x, int y, int w, int h,
                                       int topColor, int bottomColor) {
        Render.outlineGradient(g, x, y, w, h, topColor, bottomColor);
    }

    // ---- Панели ----

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        Render.panel(g, x, y, w, h, bg, border);
    }

    public static void panelGradient(GuiGraphics g, int x, int y, int w, int h,
                                     int bg, int topColor, int bottomColor) {
        Render.panelGradient(g, x, y, w, h, bg, topColor, bottomColor);
    }

    public static void panelLit(GuiGraphics g, int x, int y, int w, int h, int baseColor) {
        int top = MaredColor.lighten(baseColor, 0.06f);
        int bottom = MaredColor.darken(baseColor, 0.10f);
        Render.gradientV(g, x, y, x + w, y + h, top, bottom);
    }

    public static void panelSunken(GuiGraphics g, int x, int y, int w, int h) {
        var t = com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active();
        g.fill(x, y, x + w, y + h, t.bgSunken);
        g.fill(x, y, x + w, y + 1, MaredColor.darken(t.border, 0.4f));
        g.fill(x, y, x + 1, y + h, MaredColor.darken(t.border, 0.4f));
        g.fill(x + w - 1, y, x + w, y + h, MaredColor.lighten(t.border, 0.15f));
        g.fill(x, y + h - 1, x + w, y + h, MaredColor.lighten(t.border, 0.15f));
    }

    public static void fillPanel(GuiGraphics g, int x, int y, int w, int h,
                                 String tab, boolean raised) {
        var t = com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active();
        int bg = raised ? t.bgPanelRaised : t.bgPanel;
        g.fill(x, y, x + w, y + h, bg);

        int accent = (tab != null) ? MaredTabStyles.topColor(tab) : t.accent;
        g.fill(x, y, x + w, y + 1,
            MaredColor.withAlpha(MaredColor.lighten(accent, 0.3f), 100));
        g.fill(x, y + h - 1, x + w, y + h,
            MaredColor.withAlpha(MaredColor.darken(accent, 0.5f), 100));

        if (com.fixmer.mared.MaredSettings.isPatternsEnabled() && tab != null) {
            MaredTabStyles.drawBackgroundPattern(g, x, y + 1, w, h - 2, tab, 12);
            if (com.fixmer.mared.MaredSettings.isTornEdgesEnabled()) {
                MaredTabStyles.drawTornEdges(g, x, y, w, h, tab);
            }
        }
    }

    public static void fillPanel(GuiGraphics g, int x, int y, int w, int h, boolean raised) {
        fillPanel(g, x, y, w, h, null, raised);
    }

    public static void fillPanelRounded(GuiGraphics g, int x, int y, int w, int h,
                                        String tab, boolean raised, int radius) {
        var t = com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active();
        int bg = raised ? t.bgPanelRaised : t.bgPanel;
        Render.roundedRect(g, x, y, w, h, radius, bg);
        int accent = (tab != null) ? MaredTabStyles.topColor(tab) : t.accent;
        Render.roundedOutline(g, x, y, w, h, radius, MaredColor.withAlpha(accent, 100));
    }

    // ---- Эффекты ----

    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int size, int color) {
        if (size <= 0) return;
        int a = (color >>> 24) & 0xFF;
        for (int i = 1; i <= size; i++) {
            int alpha = a * (size - i + 1) / (size + 1);
            int c = (alpha << 24) | (color & 0x00FFFFFF);
            g.fill(x - i, y + h, x + w + i, y + h + i, c);
            g.fill(x + w, y - i, x + w + i, y + h + i, c);
        }
    }

    public static void scissorOn(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    public static void scissorOff(GuiGraphics g) {
        g.disableScissor();
    }

    // ---- Диалоговый фон ----

    public static void dialogBackground(GuiGraphics g, int width, int height) {
        var t = com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active();
        g.fill(0, 0, width, height, t.overlayBg);
    }

    public static void dialogPanel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        var t = com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active();
        Render.panelGradient(g, x, y, w, h, t.bgPanel, accent,
            MaredColor.darken(accent, 0.4f));
    }

    // ---- Скроллбар ----

    public static void drawDivider(GuiGraphics g, int x, int y, int w, int colorCenter) {
        int transparent = MaredColor.withAlpha(colorCenter, 0);
        gradientH(g, x, y, x + w, y + 1, transparent, colorCenter);
    }

    public static void drawScrollbar(GuiGraphics g, int trackX, int trackY,
                                     int trackW, int trackH,
                                     int thumbY, int thumbH,
                                     int accentTop, int accentBottom) {
        var t = com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active();
        g.fill(trackX, trackY, trackX + trackW, trackY + trackH, t.scrollTrack);
        if (thumbH > 0) {
            gradientV(g, trackX, thumbY, trackX + trackW, thumbY + thumbH,
                accentTop, accentBottom);
        }
    }

    public static void text(GuiGraphics g, Font f, String s, int x, int y, int c) {
        g.drawString(f, s, x, y, c, true);
    }
}