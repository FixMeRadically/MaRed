package com.fixmer.mared.gui2.development;

import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Standard Button input/narration, with theme colors and bounded labels. */
public final class DevelopmentButton129 extends Button {
    private final boolean primary;
    private int icon = -1;
    public void icon(int value) { icon = value; }
    public DevelopmentButton129(int x, int y, int width, Component label, boolean primary, Runnable action) {
        super(x, y, width, 24, label, b -> action.run(), DEFAULT_NARRATION);
        this.primary = primary;
    }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
        draw(g, getX(), getY(), getWidth(), getHeight(), icon < 0 ? getMessage() : Component.empty(), active,
            isHoveredOrFocused(), primary);
        if (icon >= 0) com.fixmer.mared.technology.editor.LogicRailIcons.draw(g, icon,
            getX() + (getWidth() - 12) / 2, getY() + (getHeight() - 10) / 2, MaredThemeRegistry.active().text);
    }
    public static void draw(GuiGraphics g, int x, int y, int w, int h, Component label,
                            boolean active, boolean focused, boolean primary) {
        var t = MaredThemeRegistry.active();
        var font = Minecraft.getInstance().font;
        int bg = !active ? t.bgSunken : focused ? t.bgHover : primary ? t.bgPanelRaised : t.bgPanelRaised;
        g.fill(x, y, x + w, y + h, bg);
        int border = active && (focused || primary) ? 0xFFFFFFFF : t.border;
        g.renderOutline(x, y, w, h, border);
        if (focused && active) g.renderOutline(x + 2, y + 2, Math.max(0, w - 4), Math.max(0, h - 4), 0xFFAFB7C4);
        int available = Math.max(0, w - 12);
        String text = label.getString();
        if (font.width(text) > available) text = font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…";
        g.enableScissor(x + 3, y, x + Math.max(3, w - 3), y + h);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - font.lineHeight) / 2,
            active ? t.text : t.textFaint, false);
        g.disableScissor();
    }
}
