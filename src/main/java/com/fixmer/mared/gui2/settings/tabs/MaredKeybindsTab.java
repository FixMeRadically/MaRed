package com.fixmer.mared.gui2.settings.tabs;

import java.util.List;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.input.KeybindItem;
import com.fixmer.mared.gui2.framework.components.overlay.MaredToast;
import com.fixmer.mared.gui2.framework.render.MaredUi;
import com.fixmer.mared.gui2.settings.MaredSettingsTab;
import com.fixmer.mared.gui2.settings.SettingsContext;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Phase B5): перенос legacy gui.settings.tabs.MaredKeybindsTab.
 * 0.3.0 (Phase F8): работает через KeybindItem DTO.
 * 0.3.0 (Phase F2): данные — через ctx.keybinds().
 * 0.3.0 (Phase F3a): reset — draft (requestReset), применяется на Save.
 */
public final class MaredKeybindsTab implements MaredSettingsTab {

    private int scroll = 0;
    private int contentHeight = 0;
    private int resetBtnY = 0;

    private static final int ROW_H = 20;

    @Override public String id() { return "keybinds"; }
    @Override public String displayName() {
        return MaredLang.get("mared.settings.tab.keybinds");
    }
    @Override public int accentColor() { return 0xFF55AAFF; }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       SettingsContext ctx, int mouseX, int mouseY) {
        List<KeybindItem> binds = ctx.keybinds().allBinds();
        contentHeight = 40 + binds.size() * ROW_H + 40;

        g.enableScissor(x, y, x + w, y + h);

        int cy = y - scroll;

        MaredUi.text(g, font, MaredLang.get("mared.settings.keybinds.title"),
            x, cy, 0xFFFFAA00);
        cy += 14;
        MaredUi.text(g, font, MaredLang.get("mared.settings.keybinds.hint"),
            x, cy, 0xFF888888);
        cy += 18;

        if (binds.isEmpty()) {
            MaredUi.text(g, font, MaredLang.get("mared.settings.keybinds.empty"),
                x + 8, cy, 0xFFAAAAAA);
            cy += ROW_H;
        } else {
            for (KeybindItem b : binds) {
                drawBindRow(g, font, x, cy, w - 8, b);
                cy += ROW_H;
            }
        }

        cy += 16;
        resetBtnY = cy;

        boolean resetHover = MaredUi.hovered(mouseX, mouseY, x, cy,
            MaredUi.px(160), MaredUi.px(20));
        MaredUi.button3D(g, font, x, cy, MaredUi.px(160), MaredUi.px(20),
            MaredLang.get("mared.settings.keybinds.reset_all"),
            resetHover ? 0xFF663333 : 0xFF3A2020,
            0xFFFF5555, 0xFFFFFFFF, resetHover);

        if (ctx.keybinds().isResetRequested()) {
            MaredUi.text(g, font, "● reset pending — press Save",
                x + MaredUi.px(170), cy + 6, 0xFFFFAA00);
        }

        g.disableScissor();
    }

    private void drawBindRow(GuiGraphics g, Font font, int x, int y,
                             int rowW, KeybindItem b) {
        MaredUi.rect(g, x, y, x + rowW, y + ROW_H - 2, 0xFF1A1A22);

        int keyW = MaredUi.px(80);
        MaredUi.rect(g, x, y, x + keyW, y + ROW_H - 2, 0xFF23232E);
        MaredUi.text(g, font, b.key(), x + 6, y + 4, 0xFFFF55FF);

        MaredUi.text(g, font, b.label(), x + keyW + 8, y + 4, 0xFFFFFFFF);

        if (b.blocking()) {
            MaredUi.text(g, font, "[BLOCK]", x + rowW - 54, y + 4,
                0xFFFF5555);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button,
                                int x, int y, int w, int h,
                                SettingsContext ctx) {
        if (button != 0) return false;
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;

        if (MaredUi.hovered(mx, my, x, resetBtnY,
            MaredUi.px(160), MaredUi.px(20))) {
            ctx.keybinds().requestReset();
            MaredToast.info(MaredLang.get("mared.settings.keybinds.reset_done"));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta,
                                 int x, int y, int w, int h,
                                 SettingsContext ctx) {
        int maxScroll = Math.max(0, contentHeight - h);
        if (delta < 0) scroll = Math.min(maxScroll, scroll + 12);
        else if (delta > 0) scroll = Math.max(0, scroll - 12);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        return true;
    }
}