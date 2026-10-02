package com.fixmer.mared.gui2.docking.render;

import com.fixmer.mared.gui2.docking.DockPanel;
import com.fixmer.mared.gui2.docking.style.DockStyle;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.gui2.theme.ModuleColorRegistry;
import com.fixmer.mared.gui2.theme.ModuleTheme;

/**
 * Рендер Dock панели MaRed Studio.
 *
 * 0.3.0: тема из MaredThemeRegistry (единый источник).
 */
public final class DockPanelRenderer {

    private DockPanelRenderer() {}

    public static void render(DockPanel panel, MaredRenderContext context,
                              int x, int y, int width, int height) {

        MaredTheme theme = MaredThemeRegistry.active();
        int accent = theme.accent;

        ModuleTheme moduleTheme = ModuleColorRegistry.get(panel.moduleType());
        if (moduleTheme != null) accent = moduleTheme.accent();

        context.graphics().fill(x, y, x + width, y + height, theme.bgPanel);
        context.graphics().fill(x, y, x + width, y + DockStyle.HEADER_HEIGHT, theme.bgPanelRaised);
        context.graphics().fill(x, y, x + width, y + DockStyle.ACCENT_HEIGHT, accent);

        context.graphics().fill(
            x + DockStyle.ACCENT_INDICATOR_X,
            y + DockStyle.ACCENT_INDICATOR_Y,
            x + DockStyle.ACCENT_INDICATOR_X + DockStyle.ACCENT_INDICATOR_W,
            y + DockStyle.ACCENT_INDICATOR_Y + DockStyle.ACCENT_INDICATOR_H,
            accent);

        context.graphics().drawString(context.font(), panel.title(),
            x + DockStyle.TITLE_OFFSET_X, y + DockStyle.TITLE_OFFSET_Y, 0xFFFFFFFF);

        drawMenuButton(context, x + width - DockStyle.MENU_BUTTON_OFFSET_RIGHT,
            y + DockStyle.MENU_BUTTON_OFFSET_Y);

        if (panel.closable()) {
            drawCloseButton(context, x + width - DockStyle.CLOSE_BUTTON_OFFSET_RIGHT,
                y + DockStyle.CLOSE_BUTTON_OFFSET_Y);
        }

        drawBorder(context, x, y, width, height, theme.border);
    }

    private static void drawMenuButton(MaredRenderContext context, int x, int y) {
        context.graphics().drawString(context.font(), "⋮", x, y, 0xFFAAAAAA);
    }

    private static void drawCloseButton(MaredRenderContext context, int x, int y) {
        context.graphics().drawString(context.font(), "×", x, y, 0xFFAAAAAA);
    }

    private static void drawBorder(MaredRenderContext context,
                                   int x, int y, int width, int height, int color) {
        context.graphics().fill(x, y, x + width, y + 1, color);
        context.graphics().fill(x, y + height - 1, x + width, y + height, color);
        context.graphics().fill(x, y, x + 1, y + height, color);
        context.graphics().fill(x + width - 1, y, x + width, y + height, color);
    }
}