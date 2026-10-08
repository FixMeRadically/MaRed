package com.fixmer.mared.technology.editor;

import com.fixmer.mared.gui2.framework.render.MaredColor;
import com.fixmer.mared.gui2.framework.render.widgets.UiControls;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Host-side dialog renderer. Read the active theme at render time, not at construction. */
public record EditorDialogStyle(int panel, int raised, int input, int hover,
                                int border, int text, int muted, int accent,
                                int danger, int selection) {
    public static final int PADDING = 12;
    public static final int BUTTON_GAP = 8;

    public static EditorDialogStyle active(int categoryColor) {
        return from(MaredThemeRegistry.active(), categoryColor);
    }

    public static EditorDialogStyle from(MaredTheme theme, int categoryColor) {
        int accent = readable(categoryColor, theme.bgPanel, 3.0);
        return new EditorDialogStyle(theme.bgPanel, theme.bgPanelRaised, theme.bgSunken,
            theme.bgHover, theme.border, theme.text, theme.textDim, accent,
            readable(theme.danger, theme.bgPanel, 4.5), theme.bgSelected);
    }

    public void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x60000000);
        g.fill(x, y, x + w, y + h, panel);
        g.renderOutline(x, y, w, h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, raised);
        // Small category marker; no full colored frame or colored background.
        g.fill(x + PADDING, y + 7, x + PADDING + 18, y + 9, accent);
    }

    public void button(GuiGraphics g, Font font, int x, int y, int w, int h,
                       String label, boolean hovered, boolean primary, boolean destructive) {
        int edge = destructive ? danger : primary ? accent : border;
        UiControls.button(g, font, x, y, w, h, label, hovered ? hover : raised,
            edge, hovered, text);
    }

    // Retain hue while making small markers / warning text legible in light themes.
    private static int readable(int color, int background, double minimum) {
        color |= 0xFF000000;
        if (contrast(color, background) >= minimum) return color;
        int end = contrast(0xFF000000, background) > contrast(0xFFFFFFFF, background)
            ? 0xFF000000 : 0xFFFFFFFF;
        for (int step = 1; step <= 20; step++) {
            int candidate = MaredColor.lerpColor(color, end, step / 20.0f);
            if (contrast(candidate, background) >= minimum) return candidate;
        }
        return end;
    }

    private static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static double luminance(int c) {
        return 0.2126 * channel(c >> 16) + 0.7152 * channel(c >> 8) + 0.0722 * channel(c);
    }

    private static double channel(int c) {
        double value = (c & 255) / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
