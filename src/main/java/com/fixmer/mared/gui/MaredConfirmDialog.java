package com.fixmer.mared.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.fixmer.mared.script.MaredLang;

public class MaredConfirmDialog extends Screen {

    private static final int TEXT   = MaredUi.TEXT;
    private static final int DANGER = MaredUi.DANGER;

    private final Screen parent;
    private final String title;
    private final String message;
    private final Runnable onConfirm;

    private AbstractWidget confirmBtn;
    private AbstractWidget cancelBtn;

    public MaredConfirmDialog(Screen parent, String title, String message, Runnable onConfirm) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        super.init();
        MaredLang.reload();

        int panelW = 320;
        int panelH = 140;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        int btnW = 120;
        int btnY = panelY + panelH - 40;
        int btnGap = 20;

        confirmBtn = addRenderableWidget(new MaredCompactButton(
            panelX + panelW / 2 - btnW - btnGap / 2, btnY, btnW, 20,
            Component.literal(MaredLang.get("mared.dialog.delete")), DANGER, this::onConfirm));

        cancelBtn = addRenderableWidget(new MaredCompactButton(
            panelX + panelW / 2 + btnGap / 2, btnY, btnW, 20,
            Component.literal(MaredLang.get("mared.dialog.cancel")), 0xFF55FF88, this::onCancel));
    }

    private void onConfirm() { onConfirm.run(); onClose(); }
    private void onCancel() { onClose(); }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Фон экрана + панель — как в настройках, без blur.
        MaredUi.dialogBackground(g, this.width, this.height);

        int panelW = 320;
        int panelH = 140;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        MaredUi.dialogPanel(g, panelX, panelY, panelW, panelH, DANGER);

        MaredUi.text(g, this.font, title, panelX + 20, panelY + 20, DANGER);
        MaredUi.wrapped(g, this.font, message, panelX + 20, panelY + 50, panelW - 40, TEXT);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { onConfirm(); return true; }
        if (keyCode == 256) { onCancel(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
}