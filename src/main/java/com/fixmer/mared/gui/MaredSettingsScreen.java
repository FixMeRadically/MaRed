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

    private static final int BG        = 0xFF0A0A10;
    private static final int PANEL     = 0xFF14141C;
    private static final int BORDER    = 0xFFFFAA00;
    private static final int TEXT      = 0xFFFFFFFF;
    private static final int TEXT_DIM  = 0xFFAAAAAA;
    private static final int TEXT_WARN = 0xFFFFAA00;
    private static final int CB_BG     = 0xFF1A1A24;
    private static final int CB_SEL    = 0xFFFFAA00;

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

    /** FIX: единая функция layout, используется в render и mouseClicked. */
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
        g.fill(0, 0, this.width, this.height, BG);

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();

        MaredUi.panelLitBordered(g, px, py, pw, ph, PANEL, BORDER);

        int x = px + PAD;
        int y = py + PAD;

        MaredUi.text(g, this.font, MaredLang.get("mared.settings.title"), x, y, BORDER);
        y += 22;

        MaredUi.text(g, this.font, MaredLang.get("mared.settings.auto_indent"), x, y, TEXT_WARN);
        y += 14;
        for (MaredSettings.AutoIndent mode : MaredSettings.AutoIndent.values()) {
            drawRadio(g, x, y, draftAutoIndent == mode);
            MaredUi.text(g, this.font, autoIndentLabel(mode), x + CB_SZ + 6, y + 3, TEXT);
            y += ROW_H;
        }
        y += 8;

        MaredUi.text(g, this.font, MaredLang.get("mared.settings.indent_style"), x, y, TEXT_WARN);
        y += 14;
        for (MaredSettings.IndentStyle style : MaredSettings.IndentStyle.values()) {
            drawRadio(g, x, y, draftIndentStyle == style);
            MaredUi.text(g, this.font, indentLabel(style), x + CB_SZ + 6, y + 3, TEXT);
            y += ROW_H;
        }
        y += 8;

        MaredUi.text(g, this.font, MaredLang.get("mared.settings.backspace"), x, y, TEXT_WARN);
        y += 14;
        drawCheckbox(g, x, y, draftBackspaceRemovesIndent);
        MaredUi.text(g, this.font, MaredLang.get("mared.settings.backspace_whole_indent"),
            x + CB_SZ + 6, y + 3, TEXT);
        y += ROW_H + 16;

        MaredUi.text(g, this.font, MaredLang.get("mared.settings.hint"), x, y, TEXT_WARN);
        y += 12;
        MaredUi.wrapped(g, this.font, MaredLang.get("mared.settings.hint_text"),
            x, y, pw - PAD * 2, TEXT_DIM);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawRadio(GuiGraphics g, int x, int y, boolean selected) {
        MaredUi.rect(g, x, y + 2, x + CB_SZ, y + 2 + CB_SZ, CB_BG);
        MaredUi.outline(g, x, y + 2, CB_SZ, CB_SZ, BORDER);
        if (selected) {
            MaredUi.rect(g, x + 3, y + 5, x + CB_SZ - 3, y + 2 + CB_SZ - 3, CB_SEL);
        }
    }

    /** FIX: крестик вручную, точно по центру. */
    private void drawCheckbox(GuiGraphics g, int x, int y, boolean checked) {
        MaredUi.rect(g, x, y + 2, x + CB_SZ, y + 2 + CB_SZ, CB_BG);
        MaredUi.outline(g, x, y + 2, CB_SZ, CB_SZ, BORDER);
        if (checked) {
            int cx = x + CB_SZ / 2;
            int cy = y + 2 + CB_SZ / 2;
            int arm = 3;
            MaredUi.rect(g, cx - arm, cy, cx + arm + 1, cy + 1, CB_SEL);
            MaredUi.rect(g, cx, cy - arm, cx + 1, cy + arm + 1, CB_SEL);
        }
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
            case TAB -> MaredLang.get("mared.settings.indent_tab");
            case SPACES_4 -> MaredLang.get("mared.settings.indent_4");
            case SPACES_2 -> MaredLang.get("mared.settings.indent_2");
        };
    }

    @Override
    public boolean isPauseScreen() { return false; }
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
}