package com.fixmer.mared.gui2.settings.tabs;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.gui2.settings.MaredSettingsTab;
import com.fixmer.mared.gui2.settings.SettingsContext;
import com.fixmer.mared.technology.server.setup.ServerSetupLauncher59;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** One explicit action; preparation lives independently of the game being closed. */
public final class MaredServerTab59 implements MaredSettingsTab {
    @Override public String id() { return "server59"; }
    @Override public String displayName() { return MaredLang.get("mared.settings.tab.server59"); }
    @Override public int accentColor() { return MaredThemeRegistry.active().accent; }
    @Override public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
            SettingsContext ctx, int mouseX, int mouseY) {
        var theme = MaredThemeRegistry.active();
        g.enableScissor(x, y, x + w, y + h);
        int buttonWidth = Math.max(1, Math.min(270, w - 24));
        boolean hovered = mouseX >= x + 12 && mouseX < x + 12 + buttonWidth && mouseY >= y + 12 && mouseY < y + 36;
        g.fill(x + 12, y + 12, x + 12 + buttonWidth, y + 36,
            hovered ? theme.border : theme.bgPanelRaised);
        String label = font.plainSubstrByWidth(MaredLang.get("mared.server59.open"), Math.max(1, buttonWidth - 12));
        g.drawString(font, label, x + 18, y + 20, theme.text, false);
        int top = y + 48;
        for (String text : new String[] {ServerSetupLauncher59.status(), MaredLang.get("mared.server59.description"), MaredLang.get("mared.server59.requirements")}) {
            for (var line : font.split(Component.literal(text), Math.max(20, w - 24))) {
                g.drawString(font, line, x + 12, top, theme.textDim, false); top += font.lineHeight + 3;
            }
            top += 10;
        }
        g.disableScissor();
    }
    @Override public boolean mouseClicked(double mx, double my, int button,
            int x, int y, int w, int h, SettingsContext ctx) {
        if (button != 0 || mx < x + 12 || mx >= x + 12 + Math.min(270, w - 24)
                || my < y + 12 || my >= Math.min(y + 36, y + h)) return false;
        ServerSetupLauncher59.open();
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers, SettingsContext ctx) {
        if (key != 257 && key != 335) return false;
        ServerSetupLauncher59.open();
        return true;
    }
}
