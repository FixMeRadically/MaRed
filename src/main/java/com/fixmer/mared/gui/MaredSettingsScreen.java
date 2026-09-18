package com.fixmer.mared.gui;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.script.MaredLang;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MaredSettingsScreen extends Screen {

    private static final int BG        = MaredUi.SCREEN_BG;
    private static final int PANEL     = MaredUi.PANEL_BG;
    private static final int BORDER    = MaredUi.WARN;
    private static final int TEXT      = MaredUi.TEXT;
    private static final int TEXT_DIM  = MaredUi.TEXT_DIM;
    private static final int TEXT_WARN = MaredUi.WARN;
    private static final int CB_BG     = MaredUi.SUNKEN_BG;
    private static final int CB_SEL    = MaredUi.WARN;

    private static final int PAD = 10;
    private static final int ROW_H = 20;
    private static final int CB_SZ = 14;

    private final Screen parent;

    private MaredSettings.AutoIndent draftAutoIndent;
    private MaredSettings.IndentStyle draftIndentStyle;
    private boolean draftBackspaceRemovesIndent;

    private AbstractWidget saveBtn;
    private AbstractWidget cancelBtn;

    public MaredSettingsScreen(Screen parent) {
        super(Component.literal("Mared Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        MaredLang.reload();
        MaredSettings.load();
        draftAutoIndent = MaredSettings.getAutoIndent();
        draftIndentStyle = MaredSettings.getIndentStyle();
        draftBackspaceRemovesIndent = MaredSettings.isBackspaceRemovesIndent();

        int btnW = 100;
        int btnY = this.height - 40;
        int centerX = this.width / 2;

        saveBtn = addRenderableWidget(new MaredCompactButton(
            centerX - btnW - 10, btnY, btnW, 20,
            Component.literal(MaredLang.get("mared.settings.save")), 0xFF55FF88, this::onSave));

        cancelBtn = addRenderableWidget(new MaredCompactButton(
            centerX + 10, btnY, btnW, 20,
            Component.literal(MaredLang.get("mared.settings.cancel")), 0xFFFF5555, this::onCancel));
    }

    private void onSave() {
        MaredSettings.setAutoIndent(draftAutoIndent);
        MaredSettings.setIndentStyle(draftIndentStyle);
        MaredSettings.setBackspaceRemovesIndent(draftBackspaceRemovesIndent);
        onClose();
    }

    private void onCancel() { onClose(); }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    private int panelX() { return this.width / 2 - 220; }
    private int panelY() { return 40; }
    private int panelW() { return 440; }
    private int panelH() { return this.height - 100; }

    private List<long[]> computeLayout() {
        List<long[]> rows = new ArrayList<>();
        int x = panelX() + PAD;
        int y = panelY() + PAD;
        y += 22;
        y += 14;
        for (int i = 0; i < MaredSettings.AutoIndent.values().length; i++) {
            rows.add(new long[]{0, x, y, 400, i});
            y += ROW_H;
        }
        y += 8;
        y += 14;
        for (int i = 0; i < MaredSettings.IndentStyle.values().length; i++) {
            rows.add(new long[]{1, x, y, 400, i});
            y += ROW_H;
        }
        y += 8;
        y += 14;
        rows.add(new long[]{2, x, y, 400, 0});
        return rows;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        List<long[]> rows = computeLayout();
        for (long[] row : rows) {
            int type = (int) row[0];
            int rx = (int) row[1];
            int ry = (int) row[2];
            int rw = (int) row[3];
            int idx = (int) row[4];

            if (mx >= rx && mx < rx + rw && my >= ry && my < ry + ROW_H - 2) {
                switch (type) {
                    case 0 -> draftAutoIndent = MaredSettings.AutoIndent.values()[idx];
                    case 1 -> draftIndentStyle = MaredSettings.IndentStyle.values()[idx];
                    case 2 -> draftBackspaceRemovesIndent = !draftBackspaceRemovesIndent;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // КРИТИЧНО: свой фон, без super.renderBackground.
        g.fill(0, 0, this.width, this.height, BG);

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();

        MaredUi.panelGradient(g, px, py, pw, ph, PANEL, BORDER, MaredUi.darken(BORDER, 0.4f));

        int x = px + PAD;
        int y = py + PAD;

        MaredUi.text(g, this.font, MaredLang.get("mared.settings.title"), x, y, BORDER);
        y += 22;

        MaredUi.text(g, this.font, MaredLang.get("mared.settings.auto_indent"), x, y, TEXT_WARN);
        y += 14;
        for (MaredSettings.AutoIndent mode : MaredSettings.AutoIndent.values()) {
            MaredUi.drawRadio(g, x, y + 2, CB_SZ, draftAutoIndent == mode, BORDER, CB_SEL);
            MaredUi.text(g, this.font, autoIndentLabel(mode), x + CB_SZ + 6, y + 5, TEXT);
            y += ROW_H;
        }
        y += 8;

        MaredUi.text(g, this.font, MaredLang.get("mared.settings.indent_style"), x, y, TEXT_WARN);
        y += 14;
        for (MaredSettings.IndentStyle style : MaredSettings.IndentStyle.values()) {
            MaredUi.drawRadio(g, x, y + 2, CB_SZ, draftIndentStyle == style, BORDER, CB_SEL);
            MaredUi.text(g, this.font, indentLabel(style), x + CB_SZ + 6, y + 5, TEXT);
            y += ROW_H;
        }
        y += 8;

        MaredUi.text(g, this.font, MaredLang.get("mared.settings.backspace"), x, y, TEXT_WARN);
        y += 14;
        MaredUi.drawCheckMark(g, x, y + 2, CB_SZ, draftBackspaceRemovesIndent, BORDER, CB_SEL);
        MaredUi.text(g, this.font, MaredLang.get("mared.settings.backspace_whole_indent"),
            x + CB_SZ + 6, y + 5, TEXT);
        y += ROW_H + 16;

        MaredUi.text(g, this.font, MaredLang.get("mared.settings.hint"), x, y, TEXT_WARN);
        y += 12;
        MaredUi.wrapped(g, this.font, MaredLang.get("mared.settings.hint_text"),
            x, y, pw - PAD * 2, TEXT_DIM);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private String autoIndentLabel(MaredSettings.AutoIndent mode) {
        return switch (mode) {
            case OFF -> MaredLang.get("mared.settings.auto_indent.off");
            case SIMPLE -> MaredLang.get("mared.settings.auto_indent.simple");
            case SMART -> MaredLang.get("mared.settings.auto_indent.smart");
            case FULL -> MaredLang.get("mared.settings.auto_indent.full");
        };
    }

    private String indentLabel(MaredSettings.IndentStyle style) {
        return switch (style) {
            case TAB      -> MaredLang.get("mared.settings.indent_style.tab");
            case SPACES_4 -> MaredLang.get("mared.settings.indent_style.spaces4");
            case SPACES_2 -> MaredLang.get("mared.settings.indent_style.spaces2");
        };
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Пусто — не даём MC рисовать blur.
    }
}