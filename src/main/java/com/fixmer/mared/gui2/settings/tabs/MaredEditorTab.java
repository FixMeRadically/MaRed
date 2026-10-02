package com.fixmer.mared.gui2.settings.tabs;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.framework.render.MaredUi;
import com.fixmer.mared.gui2.settings.MaredSettingsTab;
import com.fixmer.mared.gui2.settings.SettingsContext;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Phase B5): перенос legacy gui.settings.tabs.MaredEditorTab в gui2.
 * 0.3.0 (Phase E2a): работает через ctx.editor() вместо ctx.snapshot.
 */
public final class MaredEditorTab implements MaredSettingsTab {

    private static final int ROW_H = 20;
    private static final int CB_SZ = 14;

    private int aiY0;
    private int indentY0;
    private int backspaceY;

    @Override public String id() { return "editor"; }
    @Override public String displayName() {
        return MaredLang.get("mared.settings.tab.editor");
    }
    @Override public int accentColor() { return 0xFFFFAA00; }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       SettingsContext ctx, int mouseX, int mouseY) {
        var ed = ctx.editor();
        g.enableScissor(x, y, x + w, y + h);

        int cy = y;

        MaredUi.text(g, font, MaredLang.get("mared.settings.auto_indent"),
            x, cy, 0xFFFFAA00);
        cy += 16;
        aiY0 = cy;

        for (MaredSettings.AutoIndent mode : MaredSettings.AutoIndent.values()) {
            boolean on = ed.autoIndent() == mode;
            MaredUi.drawRadio(g, x, cy + 2, CB_SZ, on, 0xFFFFAA00, 0xFFFFAA00);
            MaredUi.text(g, font, autoLabel(mode), x + CB_SZ + 6, cy + 5,
                on ? 0xFFFFFFFF : 0xFFAAAAAA);
            cy += ROW_H;
        }
        cy += 8;

        MaredUi.text(g, font, MaredLang.get("mared.settings.indent_style"),
            x, cy, 0xFFFFAA00);
        cy += 16;
        indentY0 = cy;

        for (MaredSettings.IndentStyle style : MaredSettings.IndentStyle.values()) {
            boolean on = ed.indentStyle() == style;
            MaredUi.drawRadio(g, x, cy + 2, CB_SZ, on, 0xFFFFAA00, 0xFFFFAA00);
            MaredUi.text(g, font, indentLabel(style), x + CB_SZ + 6, cy + 5,
                on ? 0xFFFFFFFF : 0xFFAAAAAA);
            cy += ROW_H;
        }
        cy += 8;

        MaredUi.text(g, font, MaredLang.get("mared.settings.backspace"),
            x, cy, 0xFFFFAA00);
        cy += 16;
        backspaceY = cy;
        MaredUi.drawCheckMark(g, x, cy + 2, CB_SZ, ed.backspaceRemovesIndent(),
            0xFFFFAA00, 0xFFFFAA00);
        MaredUi.text(g, font,
            MaredLang.get("mared.settings.backspace_whole_indent"),
            x + CB_SZ + 6, cy + 5,
            ed.backspaceRemovesIndent() ? 0xFFFFFFFF : 0xFFAAAAAA);
        cy += ROW_H + 16;

        MaredUi.text(g, font, MaredLang.get("mared.settings.hint"),
            x, cy, 0xFFFFAA00);
        cy += 12;
        MaredUi.wrapped(g, font, MaredLang.get("mared.settings.hint_text"),
            x, cy, w - 20, 0xFFAAAAAA);

        g.disableScissor();
    }

    private String autoLabel(MaredSettings.AutoIndent mode) {
        return switch (mode) {
            case OFF -> MaredLang.get("mared.settings.auto_indent.off");
            case SIMPLE -> MaredLang.get("mared.settings.auto_indent.simple");
            case SMART -> MaredLang.get("mared.settings.auto_indent.smart");
            case FULL -> MaredLang.get("mared.settings.auto_indent.full");
        };
    }

    private String indentLabel(MaredSettings.IndentStyle style) {
        return switch (style) {
            case TAB -> MaredLang.get("mared.settings.indent_style.tab");
            case SPACES_4 -> MaredLang.get("mared.settings.indent_style.spaces4");
            case SPACES_2 -> MaredLang.get("mared.settings.indent_style.spaces2");
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button,
                                int x, int y, int w, int h,
                                SettingsContext ctx) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        var ed = ctx.editor();

        for (int i = 0; i < MaredSettings.AutoIndent.values().length; i++) {
            int rowY = aiY0 + i * ROW_H;
            if (my >= rowY && my < rowY + ROW_H) {
                ed.setAutoIndent(MaredSettings.AutoIndent.values()[i]);
                return true;
            }
        }

        for (int i = 0; i < MaredSettings.IndentStyle.values().length; i++) {
            int rowY = indentY0 + i * ROW_H;
            if (my >= rowY && my < rowY + ROW_H) {
                ed.setIndentStyle(MaredSettings.IndentStyle.values()[i]);
                return true;
            }
        }

        if (my >= backspaceY && my < backspaceY + ROW_H) {
            ed.setBackspaceRemovesIndent(!ed.backspaceRemovesIndent());
            return true;
        }

        return true;
    }
}