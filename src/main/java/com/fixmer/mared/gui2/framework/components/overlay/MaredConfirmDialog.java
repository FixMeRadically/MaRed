package com.fixmer.mared.gui2.framework.components.overlay;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.TextUtils;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Диалог подтверждения.
 *
 * 0.3.0: перенос из gui.common.MaredConfirmDialog. Использует
 * gui2.framework.render.Render/TextUtils вместо MaredUi.
 */
public class MaredConfirmDialog extends Screen {

    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DANGER = 0xFFFF4444;
    private static final int WARN   = 0xFFFFAA00;

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
        Render.dialogBackground(g, this.width, this.height);

        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - PANEL_H) / 2;
        int accent = warning ? WARN : DANGER;
        Render.dialogPanel(g, panelX, panelY, PANEL_W, PANEL_H, accent);

        Render.text(g, this.font, this.title.getString(), panelX + 20, panelY + 20, accent);
        TextUtils.wrapped(g, this.font, message, panelX + 20, panelY + 50, PANEL_W - 40, TEXT);

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