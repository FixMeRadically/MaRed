package com.fixmer.mared.gui.common;

import com.fixmer.mared.MaredLang;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MaredConfirmDialog extends Screen {

    private static final int TEXT   = MaredUi.TEXT;
    private static final int DANGER = MaredUi.DANGER;
    private static final int WARN   = MaredUi.WARN;

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 140;
    private static final int BTN_W   = 120;
    private static final int BTN_H   = 20;
    private static final int BTN_GAP = 20;

    private final Screen parent;
    private final String message;
    private final Runnable onConfirm;
    private final boolean warning;

    private AbstractWidget confirmBtn;
    private AbstractWidget cancelBtn;

    public MaredConfirmDialog(Screen parent, String title, String message, Runnable onConfirm) {
        this(parent, title, message, onConfirm, false);
    }

    public MaredConfirmDialog(Screen parent, String title, String message,
                              Runnable onConfirm, boolean warning) {
        super(Component.literal(title));
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
        this.warning = warning;
    }

    @Override
    protected void init() {
        super.init();
        MaredLang.reload();

        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - PANEL_H) / 2;
        int btnY = panelY + PANEL_H - 40;
        int accent = warning ? WARN : DANGER;

        cancelBtn = addRenderableWidget(new MaredCompactButton(
            panelX + PANEL_W / 2 - BTN_W - BTN_GAP / 2, btnY, BTN_W, BTN_H,
            Component.literal(MaredLang.get("mared.dialog.cancel")),
            0xFF55FF88, this::onCancel));

        confirmBtn = addRenderableWidget(new MaredCompactButton(
            panelX + PANEL_W / 2 + BTN_GAP / 2, btnY, BTN_W, BTN_H,
            Component.literal(MaredLang.get("mared.dialog.confirm")),
            accent, this::onConfirmInternal));
    }

    private void onConfirmInternal() { onConfirm.run(); onClose(); }
    private void onCancel()          { onClose(); }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MaredUi.dialogBackground(g, this.width, this.height);

        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - PANEL_H) / 2;
        int accent = warning ? WARN : DANGER;
        MaredUi.dialogPanel(g, panelX, panelY, PANEL_W, PANEL_H, accent);

        MaredUi.text(g, this.font, this.title.getString(),
            panelX + 20, panelY + 20, accent);
        MaredUi.wrapped(g, this.font, message,
            panelX + 20, panelY + 50, PANEL_W - 40, TEXT);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { onConfirmInternal(); return true; }
        if (keyCode == 256) { onCancel(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
}