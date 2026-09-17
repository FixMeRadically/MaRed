package com.fixmer.mared.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.fixmer.mared.script.MaredLang;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MaredNameDialog extends Screen {

    private static final int PANEL    = 0xFF1A1A24;
    private static final int TEXT     = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int CHECK_BG = 0xFF0A0A10;
    private static final int CHECK_ON = 0xFF55FF88;
    private static final int CHECK_BRD= 0xFF4A4A4A;

    private final Screen parent;
    private final String title;
    private final Consumer<String> onAcceptSimple;
    private final BiConsumer<String, Boolean> onAcceptWithFlag;
    private final int accentColor;
    private final boolean allowSlash;
    private final boolean showPersistentOption;

    private EditBox nameBox;
    private AbstractWidget okBtn;
    private AbstractWidget cancelBtn;

    private boolean persistent = false;
    private int checkX, checkY, checkSize = 14;

    public MaredNameDialog(Screen parent, String title, Consumer<String> onAccept,
                           int accentColor, boolean allowSlash) {
        this(parent, title, onAccept, null, accentColor, allowSlash, false);
    }

    public MaredNameDialog(Screen parent, String title, BiConsumer<String, Boolean> onAccept,
                           int accentColor, boolean allowSlash) {
        this(parent, title, null, onAccept, accentColor, allowSlash, true);
    }

    private MaredNameDialog(Screen parent, String title,
                            Consumer<String> onAcceptSimple,
                            BiConsumer<String, Boolean> onAcceptWithFlag,
                            int accentColor, boolean allowSlash,
                            boolean showPersistentOption) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.onAcceptSimple = onAcceptSimple;
        this.onAcceptWithFlag = onAcceptWithFlag;
        this.accentColor = accentColor;
        this.allowSlash = allowSlash;
        this.showPersistentOption = showPersistentOption;
    }

    @Override
    protected void init() {
        super.init();
        MaredLang.reload();

        int panelW = 300;
        int panelH = showPersistentOption ? 170 : 140;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        int inputX = panelX + 20;
        int inputY = panelY + 50;
        int inputW = panelW - 40;

        nameBox = new EditBox(this.font, inputX, inputY, inputW, 18, Component.literal("Name"));
        nameBox.setMaxLength(64);
        nameBox.setBordered(true);
        nameBox.setFocused(true);
        addRenderableWidget(nameBox);

        if (showPersistentOption) {
            checkX = panelX + 20;
            checkY = panelY + 90;
        }

        int btnW = 120;
        int btnY = panelY + panelH - 40;
        int btnGap = 20;

        okBtn = addRenderableWidget(new MaredCompactButton(
            panelX + panelW / 2 - btnW - btnGap / 2, btnY, btnW, 20,
            Component.literal(MaredLang.get("mared.dialog.create")), 0xFF55FF88, this::onOk));

        cancelBtn = addRenderableWidget(new MaredCompactButton(
            panelX + panelW / 2 + btnGap / 2, btnY, btnW, 20,
            Component.literal(MaredLang.get("mared.dialog.cancel")), 0xFFFF5555, this::onCancel));
    }

    private void onOk() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) return;
        if (!allowSlash && name.contains("/")) return;
        if (showPersistentOption && onAcceptWithFlag != null) {
            onAcceptWithFlag.accept(name, persistent);
        } else if (onAcceptSimple != null) {
            onAcceptSimple.accept(name);
        }
        onClose();
    }

    private void onCancel() { onClose(); }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // FIX: свой фон первой строкой, без super.renderBackground
        g.fill(0, 0, this.width, this.height, 0xC0000000);

        int panelW = 300;
        int panelH = showPersistentOption ? 170 : 140;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        MaredUi.panel(g, panelX, panelY, panelW, panelH, PANEL, accentColor);

        MaredUi.text(g, this.font, title, panelX + 20, panelY + 20, accentColor);
        MaredUi.text(g, this.font, MaredLang.get("mared.dialog.name_label"),
            panelX + 20, panelY + 38, TEXT_DIM);

        if (showPersistentOption) {
            boolean hovered = mouseX >= checkX && mouseX < checkX + checkSize
                && mouseY >= checkY && mouseY < checkY + checkSize;

            MaredUi.rect(g, checkX, checkY, checkX + checkSize, checkY + checkSize, CHECK_BG);
            MaredUi.outline(g, checkX, checkY, checkSize, checkSize,
                hovered ? accentColor : CHECK_BRD);

            if (persistent) {
                MaredUi.rect(g, checkX + 3, checkY + 3,
                    checkX + checkSize - 3, checkY + checkSize - 3, CHECK_ON);
            }

            MaredUi.text(g, this.font, MaredLang.get("mared.dialog.persistent"),
                checkX + checkSize + 6, checkY + 3,
                persistent ? CHECK_ON : TEXT_DIM);

            MaredUi.text(g, this.font, MaredLang.get("mared.dialog.persistent_hint"),
                checkX, checkY + checkSize + 6, TEXT_DIM);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (showPersistentOption && button == 0) {
            if (mx >= checkX && mx < checkX + checkSize
                && my >= checkY && my < checkY + checkSize) {
                persistent = !persistent;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { onOk(); return true; }
        if (keyCode == 256) { onCancel(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    /** FIX: пусто — иначе ванильный blur размазывает фон. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
}