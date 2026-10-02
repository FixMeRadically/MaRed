package com.fixmer.mared.gui2.settings.tabs;

import java.util.List;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.render.MaredUi;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.settings.MaredSettingsTab;
import com.fixmer.mared.gui2.settings.SettingsContext;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Phase B5): перенос legacy gui.settings.tabs.MaredThemeTab.
 * 0.3.0 (Phase E2a): работает через ctx.theme().
 * 0.3.0 (Phase F2): не дёргает MaredThemeRegistry.
 * 0.3.0 (Phase F3b): px-размеры, tornEdges checkbox, button guard, scroll clamp.
 * 0.3.0 (fix): убран двойной пересчёт scroll при hit-test.
 */
public final class MaredThemeTab implements MaredSettingsTab {

    private int scroll = 0;
    private int contentHeight = 0;

    private int firstCardY = 0;
    private int monotoneY = 0;
    private int patternsY = 0;
    private int tornEdgesY = 0;

    private static final int CARD_H = 90;
    private static final int CARD_GAP = 8;
    private static final int CARDS_PER_ROW = 3;

    @Override public String id() { return "theme"; }
    @Override public String displayName() {
        return MaredLang.get("mared.settings.tab.theme");
    }
    @Override public int accentColor() { return 0xFFFF55FF; }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       SettingsContext ctx, int mouseX, int mouseY) {
        var th = ctx.theme();
        contentHeight = computeHeight(w, th.themeCount());

        g.enableScissor(x, y, x + w, y + h);

        int cy = y - scroll;

        MaredUi.text(g, font, MaredLang.get("mared.settings.theme.pick"),
            x, cy, 0xFFFFAA00);
        cy += 16;
        firstCardY = cy;

        List<MaredTheme> themes = th.availableThemes();
        String currentId = th.themeId();

        int cardW = MaredUi.px(200);
        int cardH = MaredUi.px(CARD_H);
        int cardGap = MaredUi.px(CARD_GAP);
        int cols = Math.max(1, Math.min(CARDS_PER_ROW,
            (w - 4) / (cardW + cardGap)));

        for (int i = 0; i < themes.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + cardGap);
            int cyCard = firstCardY + row * (cardH + cardGap);
            MaredTheme t = themes.get(i);
            boolean active = t.id.equals(currentId);
            boolean hover = MaredUi.hovered(mouseX, mouseY, cx, cyCard, cardW, cardH);
            drawThemeCard(g, font, t, cx, cyCard, cardW, cardH, active, hover);
        }

        int rows = (themes.size() + cols - 1) / cols;
        cy = firstCardY + rows * (cardH + cardGap) + 20;

        MaredUi.text(g, font, MaredLang.get("mared.settings.theme.options"),
            x, cy, 0xFFFFAA00);
        cy += 16;

        monotoneY = cy;
        cy = drawCheckbox(g, font, x, cy, w,
            MaredLang.get("mared.settings.theme.monotone"), th.monotoneTabs());

        patternsY = cy;
        cy = drawCheckbox(g, font, x, cy, w,
            MaredLang.get("mared.settings.theme.patterns"), th.patternsEnabled());

        tornEdgesY = cy;
        cy = drawCheckbox(g, font, x, cy, w,
            MaredLang.get("mared.settings.theme.torn_edges"), th.tornEdgesEnabled());

        contentHeight = cy - (y - scroll) + 20;

        g.disableScissor();
    }

    private void drawThemeCard(GuiGraphics g, Font font, MaredTheme t,
                               int x, int y, int w, int h,
                               boolean active, boolean hover) {
        int border = active ? t.accent
            : (hover ? MaredUi.lighten(t.accent, 0.2f) : 0xFF333344);

        MaredUi.rect(g, x, y, x + w, y + h, t.bgPanel);
        MaredUi.outline(g, x, y, w, h, border);

        int previewY = y + 4;
        int previewH = 24;
        MaredUi.rect(g, x + 4, previewY, x + w - 4, previewY + previewH, t.bgScreen);
        MaredUi.text(g, font, "Aa", x + 8, previewY + 8, t.text);

        int palY = previewY + previewH + 4;
        int[] colors = { t.accent, t.success, t.warn, t.danger, t.info };
        int sw = 14;
        for (int i = 0; i < colors.length; i++) {
            MaredUi.rect(g, x + 8 + i * (sw + 3), palY,
                x + 8 + i * (sw + 3) + sw, palY + 12, colors[i]);
        }

        MaredUi.text(g, font, t.displayName, x + 8, y + h - 22, t.text);
        MaredUi.text(g, font, t.id, x + 8, y + h - 12, t.textDim);

        if (active) {
            MaredUi.rect(g, x + w - 24, y + 6, x + w - 6, y + 20, t.accent);
            MaredUi.centered(g, font, "✓", x + w - 15, y + 9, 0xFF000000);
        }
    }

    private int drawCheckbox(GuiGraphics g, Font font, int x, int y, int maxW,
                             String label, boolean checked) {
        MaredUi.drawCheckbox(g, x, y + 2, 14, checked, 0xFF4A4A4A, 0xFFFF55FF);
        MaredUi.text(g, font, label, x + 20, y + 5,
            checked ? 0xFFFFFFFF : 0xFFAAAAAA);
        return y + 18;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button,
                                int x, int y, int w, int h,
                                SettingsContext ctx) {
        if (button != 0) return false;
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;

        var th = ctx.theme();
        List<MaredTheme> themes = th.availableThemes();

        int cardW = MaredUi.px(200);
        int cardH = MaredUi.px(CARD_H);
        int cardGap = MaredUi.px(CARD_GAP);
        int cols = Math.max(1, Math.min(CARDS_PER_ROW,
            (w - 4) / (cardW + cardGap)));
        for (int i = 0; i < themes.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + cardGap);
            int cy = firstCardY + row * (cardH + cardGap);
            if (MaredUi.hovered(mx, my, cx, cy, cardW, cardH)) {
                th.setThemeId(themes.get(i).id);
                return true;
            }
        }

        if (my >= monotoneY && my < monotoneY + 18) {
            th.setMonotoneTabs(!th.monotoneTabs());
            return true;
        }
        if (my >= patternsY && my < patternsY + 18) {
            th.setPatternsEnabled(!th.patternsEnabled());
            return true;
        }
        if (my >= tornEdgesY && my < tornEdgesY + 18) {
            th.setTornEdgesEnabled(!th.tornEdgesEnabled());
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta,
                                 int x, int y, int w, int h,
                                 SettingsContext ctx) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        int maxScroll = Math.max(0, contentHeight - h);
        if (delta < 0) scroll = Math.min(maxScroll, scroll + 12);
        else if (delta > 0) scroll = Math.max(0, scroll - 12);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        return true;
    }

    private int computeHeight(int w, int themeCount) {
        int cardW = MaredUi.px(200);
        int cardH = MaredUi.px(CARD_H);
        int cardGap = MaredUi.px(CARD_GAP);
        int cols = Math.max(1, Math.min(CARDS_PER_ROW,
            (w - 4) / (cardW + cardGap)));
        int rows = (themeCount + cols - 1) / cols;
        return 16 + rows * (cardH + cardGap) + 20 + 16 + 3 * 18 + 20;
    }
}