package com.fixmer.mared.gui2.settings.tabs;

import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.render.legacy.MaredUi;
import com.fixmer.mared.gui2.settings.MaredSettingsTab;
import com.fixmer.mared.gui2.settings.SettingsContext;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Stage B5): перенос legacy gui.settings.tabs.MaredAboutTab в gui2.
 */
public final class MaredAboutTab implements MaredSettingsTab {

    @Override public String id() { return "about"; }
    @Override public String displayName() {
        return MaredLang.get("mared.settings.tab.about");
    }
    @Override public int accentColor() { return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().success; }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       SettingsContext ctx, int mouseX, int mouseY) {
        g.enableScissor(x, y, x + w, y + h);

        int cy = y;

        int logoY = cy + 20;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + w / 2.0, logoY, 0);
        pose.scale(3f, 3f, 1f);
        int tw = font.width("MARED");
        g.drawString(font, "MARED", -tw / 2, 0, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().accent, true);
        pose.popPose();
        cy += 60;

        MaredUi.centered(g, font,
            MaredLang.format("mared.settings.about.version", Mared.VERSION),
            x + w / 2, cy, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().text);
        cy += 20;

        MaredUi.centered(g, font,
            MaredLang.get("mared.settings.about.tagline"),
            x + w / 2, cy, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().textDim);
        cy += 30;

        int infoX = x + 20;
        int infoW = w - 40;

        cy = drawInfo(g, font, infoX, cy, infoW,
            MaredLang.get("mared.settings.about.minecraft"), "1.21.1");
        cy = drawInfo(g, font, infoX, cy, infoW,
            MaredLang.get("mared.settings.about.loader"), "NeoForge");
        cy = drawInfo(g, font, infoX, cy, infoW,
            MaredLang.get("mared.settings.about.side"), "Client-only");

        cy += 16;

        MaredUi.text(g, font, MaredLang.get("mared.settings.about.links"),
            infoX, cy, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().warn);
        cy += 16;

        cy = drawLink(g, font, infoX, cy, infoW,
            "github.com/FixMeRadically/MAPPET");
        cy = drawLink(g, font, infoX, cy, infoW,
            MaredLang.get("mared.settings.about.docs"));

        cy += 20;

        MaredUi.text(g, font,
            MaredLang.get("mared.settings.about.thanks"),
            x + 20, cy, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().textFaint);

        g.disableScissor();
    }

    private int drawInfo(GuiGraphics g, Font font, int x, int y, int w,
                         String label, String value) {
        MaredUi.rect(g, x, y, x + w, y + 20, 0xFF1A1A22);
        MaredUi.text(g, font, label, x + 8, y + 6, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().textDim);
        int vw = font.width(value);
        MaredUi.text(g, font, value, x + w - vw - 8, y + 6, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().text);
        return y + 24;
    }

    private int drawLink(GuiGraphics g, Font font, int x, int y, int w,
                         String url) {
        MaredUi.text(g, font, "→ " + url, x + 8, y + 4, com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().accent);
        return y + 16;
    }
}