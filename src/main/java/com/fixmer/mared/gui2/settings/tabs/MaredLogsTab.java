package com.fixmer.mared.gui2.settings.tabs;

import java.util.List;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.render.legacy.MaredUi;
import com.fixmer.mared.gui2.settings.LogsSettingsView;
import com.fixmer.mared.gui2.settings.MaredSettingsTab;
import com.fixmer.mared.gui2.settings.SettingsContext;
import com.fixmer.mared.services.logging.LogSettings;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Logs tab.
 *
 * 0.3.2: работа с LogsSettingsView (вынесен из SettingsContext).
 */
public final class MaredLogsTab implements MaredSettingsTab {

    private int scroll = 0;
    private int contentHeight = 0;

    private int chatY;
    private int verboseY;
    private int levelsY0;
    private int categoriesY0;

    private static final int LEVEL_W = 70;
    private static final int CAT_W = 100;

    @Override public String id() { return "logs"; }
    @Override public String displayName() {
        return MaredLang.get("mared.settings.tab.logs");
    }
    @Override public int accentColor() { return 0xFF55FF88; }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       SettingsContext ctx, int mouseX, int mouseY) {
        LogsSettingsView lg = ctx.logs();
        contentHeight = computeHeight(w, lg);

        g.enableScissor(x, y, x + w, y + h);

        int cy = y - scroll;

        MaredUi.text(g, font, MaredLang.get("mared.settings.logs.options"),
            x, cy, 0xFFFFAA00);
        cy += 16;

        chatY = cy;
        cy = drawCheckbox(g, font, x, cy, w,
            MaredLang.get("mared.settings.logs.chat"), lg.logChatToEditor());

        verboseY = cy;
        cy = drawCheckbox(g, font, x, cy, w,
            MaredLang.get("mared.settings.logs.verbose"), lg.verboseScriptLog());

        cy += 12;

        MaredUi.text(g, font, MaredLang.get("mared.log.filter.levels"),
            x, cy, 0xFFFFAA00);
        cy += 16;
        levelsY0 = cy;

        LogSettings.Level[] levels = lg.allLevels();
        int cols = Math.max(1, (w - 8) / (LEVEL_W + 6));
        for (int i = 0; i < levels.length; i++) {
            LogSettings.Level lvl = levels[i];
            int col = i % cols;
            int row = i / cols;
            int lx = x + col * (LEVEL_W + 6);
            int ly = cy + row * 16;
            boolean enabled = lg.levelEnabled(lvl);
            MaredUi.drawCheckbox(g, lx, ly + 1, 12, enabled, 0xFF4A4A4A,
                0xFF55FF88);
            MaredUi.text(g, font, lvl.name(), lx + 16, ly + 3,
                enabled ? 0xFFFFFFFF : 0xFF888888);
        }
        int levelRows = (levels.length + cols - 1) / cols;
        cy += levelRows * 16 + 12;

        MaredUi.text(g, font, MaredLang.get("mared.log.filter.categories"),
            x, cy, 0xFFFFAA00);
        cy += 16;
        categoriesY0 = cy;

        List<String> cats = lg.allCategories();
        int catCols = Math.max(1, (w - 8) / (CAT_W + 6));
        for (int i = 0; i < cats.size(); i++) {
            String cat = cats.get(i);
            int col = i % catCols;
            int row = i / catCols;
            int lx = x + col * (CAT_W + 6);
            int ly = cy + row * 16;
            boolean enabled = lg.categoryEnabled(cat);
            MaredUi.drawCheckbox(g, lx, ly + 1, 12, enabled, 0xFF4A4A4A,
                0xFF55FF88);
            MaredUi.text(g, font, "[" + cat + "]", lx + 16, ly + 3,
                enabled ? 0xFFFFFFFF : 0xFF888888);
        }

        g.disableScissor();
    }

    private int computeHeight(int w, LogsSettingsView lg) {
        int cols = Math.max(1, (w - 8) / (LEVEL_W + 6));
        int levelRows = (lg.allLevels().length + cols - 1) / cols;
        int catCols = Math.max(1, (w - 8) / (CAT_W + 6));
        int catRows = (lg.allCategories().size() + catCols - 1) / catCols;
        return 16 + 2 * 18 + 12 + 16 + levelRows * 16 + 12 + 16 + catRows * 16 + 20;
    }

    private int drawCheckbox(GuiGraphics g, Font font, int x, int y, int maxW,
                             String label, boolean checked) {
        MaredUi.drawCheckbox(g, x, y + 2, 14, checked, 0xFF4A4A4A, 0xFF55FF88);
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
        LogsSettingsView lg = ctx.logs();

        if (my >= chatY && my < chatY + 18) {
            lg.setLogChatToEditor(!lg.logChatToEditor());
            return true;
        }
        if (my >= verboseY && my < verboseY + 18) {
            lg.setVerboseScriptLog(!lg.verboseScriptLog());
            return true;
        }

        LogSettings.Level[] levels = lg.allLevels();
        int cols = Math.max(1, (w - 8) / (LEVEL_W + 6));
        for (int i = 0; i < levels.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int ly = levelsY0 + row * 16;
            int lx = x + col * (LEVEL_W + 6);
            if (my >= ly && my < ly + 16 && mx >= lx && mx < lx + LEVEL_W) {
                lg.toggleLevel(levels[i]);
                return true;
            }
        }

        List<String> cats = lg.allCategories();
        int catCols = Math.max(1, (w - 8) / (CAT_W + 6));
        for (int i = 0; i < cats.size(); i++) {
            int col = i % catCols;
            int row = i / catCols;
            int ly = categoriesY0 + row * 16;
            int lx = x + col * (CAT_W + 6);
            if (my >= ly && my < ly + 16 && mx >= lx && mx < lx + CAT_W) {
                lg.toggleCategory(cats.get(i));
                return true;
            }
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
}