package com.fixmer.mared.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.fixmer.mared.script.MaredLang;

import java.util.function.Consumer;

public class MaredNameDialog extends Screen {

    private static final int PANEL      = 0xFF1E1E2A;
    private static final int TEXT_DIM   = 0xFFAAAAAA;

    private final Screen parent;
    private final String title;
    private final Consumer<String> onAccept;
    private final int accentColor;
    private final boolean allowSlash;

    private EditBox nameBox;
    private AbstractWidget okBtn;
    private AbstractWidget cancelBtn;

    public MaredNameDialog(Screen parent, String title, Consumer<String> onAccept,
                           int accentColor, boolean allowSlash) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.onAccept = onAccept;
        this.accentColor = accentColor;
        this.allowSlash = allowSlash;
    }

    @Override
    protected void init() {
        super.init();

        MaredLang.reload();

        int panelW = 300;
        int panelH = 140;
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
        onAccept.accept(name);
        onClose();
    }

    private void onCancel() { onClose(); }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x80000000);

        int panelW = 300;
        int panelH = 140;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        MaredUi.panel(g, panelX, panelY, panelW, panelH, PANEL, accentColor);

        MaredUi.text(g, this.font, title, panelX + 20, panelY + 20, accentColor);
        MaredUi.text(g, this.font, MaredLang.get("mared.dialog.name_label"), panelX + 20, panelY + 38, TEXT_DIM);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            onOk();
            return true;
        }
        if (keyCode == 256) {
            onCancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
}