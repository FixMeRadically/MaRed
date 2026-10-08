package com.fixmer.mared.gui2.framework.render.widgets;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

import net.minecraft.client.gui.GuiGraphics;
import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.MaredColor;
import com.fixmer.mared.gui2.framework.render.MaredTabStyles;
import com.fixmer.mared.gui2.framework.render.FrameworkPalette;
import com.fixmer.mared.gui2.framework.render.legacy.MaredUi;

/**
 * 0.3.0 (Phase B): вынесены панель-функции из MaredUi.
 * fillPanel с паттернами, panelLit, dialogBackground.
 * Все методы — static, без состояния.
 */
public final class UiPanels {

    private UiPanels() {}

    public static void fillPanel(GuiGraphics g, int x, int y, int w, int h,
                                 String tab, boolean raised) {
        MaredTheme t = MaredThemeRegistry.active();
        int bg = raised ? t.bgPanelRaised : t.bgPanel;
        g.fill(x, y, x + w, y + h, bg);

        int accent = (tab != null) ? MaredTabStyles.topColor(tab) : t.accent;
        g.fill(x, y, x + w, y + 1,
            MaredColor.withAlpha(MaredColor.lighten(accent, 0.30f), 100));
        g.fill(x, y + h - 1, x + w, y + h,
            MaredColor.withAlpha(MaredColor.darken(accent, 0.50f), 100));

        if (MaredSettings.isPatternsEnabled() && tab != null) {
            MaredTabStyles.drawBackgroundPattern(g, x, y + 1, w, h - 2, tab, 12);
            if (MaredSettings.isTornEdgesEnabled()) {
                MaredTabStyles.drawTornEdges(g, x, y, w, h, tab);
            }
        }
    }

    public static void fillPanel(GuiGraphics g, int x, int y, int w, int h,
                                 boolean raised) {
        fillPanel(g, x, y, w, h, null, raised);
    }

    public static void fillPanelRounded(GuiGraphics g, int x, int y, int w, int h,
                                        String tab, boolean raised, int radius) {
        MaredTheme t = MaredThemeRegistry.active();
        int bg = raised ? t.bgPanelRaised : t.bgPanel;
        Render.roundedRect(g, x, y, w, h, radius, bg);
        int accent = (tab != null) ? MaredTabStyles.topColor(tab) : t.accent;
        Render.roundedOutline(g, x, y, w, h, radius, MaredColor.withAlpha(accent, 100));

        if (MaredSettings.isPatternsEnabled() && tab != null) {
            MaredTabStyles.drawBackgroundPattern(g, x + 1, y + 1, w - 2, h - 2, tab, 10);
        }
    }

    public static void fillOverlay(GuiGraphics g, int x, int y, int w, int h, int radius) {
        MaredTheme t = MaredThemeRegistry.active();
        Render.roundedRect(g, x, y, w, h, radius, t.bgPanel);
        Render.roundedOutline(g, x, y, w, h, radius, t.border);
    }

    public static void panelLit(GuiGraphics g, int x, int y, int w, int h, int baseColor) {
        int top    = MaredColor.lighten(baseColor, 0.06f);
        int bottom = MaredColor.darken(baseColor, 0.10f);
        Render.gradientV(g, x, y, x + w, y + h, top, bottom);
    }

    public static void panelLitBordered(GuiGraphics g, int x, int y, int w, int h,
                                        int baseColor, int border) {
        panelLit(g, x, y, w, h, baseColor);
        g.renderOutline(x, y, w, h, border);
    }

    public static void panel3D(GuiGraphics g, int x, int y, int w, int h,
                               int bg, int accentTop, int accentBottom) {
        g.fill(x, y, x + w, y + h, bg);
        Render.outlineGradient(g, x, y, w, h, accentTop, accentBottom);
    }

    public static void dialogBackground(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, FrameworkPalette.SCREEN_BG());
    }

    public static void dialogPanel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        Render.panelGradient(g, x, y, w, h, FrameworkPalette.PANEL_BG(), accent,
            MaredColor.darken(accent, 0.4f));
    }

    public static void dashedLine(GuiGraphics g, int x1, int y, int x2, int color) {
        int x = x1;
        while (x < x2) {
            int e = Math.min(x + 4, x2);
            g.fill(x, y, e, y + 1, color);
            x += 7;
        }
    }

    public static void dashedLineGradient(GuiGraphics g, int x1, int y, int x2,
                                          int leftColor, int rightColor) {
        int x = x1;
        int total = Math.max(1, x2 - x1);
        while (x < x2) {
            int e = Math.min(x + 4, x2);
            g.fill(x, y, e, y + 1, MaredColor.lerpColor(leftColor, rightColor,
                (float) (x - x1) / total));
            x += 7;
        }
    }

    public static void shadowAll(GuiGraphics g, int x, int y, int w, int h,
                                 int size, int color) {
        if (size <= 0) return;
        int a = (color >>> 24) & 0xFF;
        for (int i = 1; i <= size; i++) {
            int alpha = a * (size - i + 1) / (size + 1);
            int c = (alpha << 24) | (color & 0x00FFFFFF);
            g.fill(x - i, y - i, x + w + i, y - i + 1, c);
            g.fill(x - i, y + h + i - 1, x + w + i, y + h + i, c);
            g.fill(x - i, y - i + 1, x - i + 1, y + h + i - 1, c);
            g.fill(x + w + i - 1, y - i + 1, x + w + i, y + h + i - 1, c);
        }
    }
}