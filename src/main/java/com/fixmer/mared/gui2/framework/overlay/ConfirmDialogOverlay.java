package com.fixmer.mared.gui2.framework.overlay;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.render.widgets.UiControls;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Модальный overlay подтверждения.
 *
 * 0.3.2:
 *   Заменяет MaredConfirmDialog-Screen. Overlay не имеет своего
 *   Screen-стека, parent не нужен — закрытие через OverlayManager.
 *
 *   Enter:
 *     - warning=true  → Cancel (safety).
 *     - warning=false → Confirm.
 *   Escape всегда Cancel.
 *
 *   Кнопки — свои, нарисованные через UiControls. AbstractWidget не
 *   используется, потому что overlay не регистрируется в Screen.
 */
public final class ConfirmDialogOverlay implements Overlay {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 140;
    private static final int BTN_W   = 120;
    private static final int BTN_H   = 20;
    private static final int BTN_GAP = 20;

    private static final int TEXT   = 0xFFFFFFFF;
    private static final int DANGER = 0xFFFF4444;
    private static final int WARN   = 0xFFFFAA00;

    private final String title;
    private final String message;
    private final Runnable onConfirm;
    private final boolean warning;

    private int panelX, panelY;
    private int cancelX, cancelY;
    private int confirmX, confirmY;

    private boolean closeRequested = false;

    public ConfirmDialogOverlay(String title, String message,
                                Runnable onConfirm, boolean warning) {
        this.title = title != null ? title : "";
        this.message = message != null ? message : "";
        this.onConfirm = onConfirm;
        this.warning = warning;
    }

    @Override public OverlayLayer layer() { return OverlayLayer.MODAL; }
    @Override public boolean isInputBarrier() { return true; }
    @Override public boolean closeOnEscape() { return true; }

    @Override
    public boolean consumeCloseRequest() {
        if (closeRequested) { closeRequested = false; return true; }
        return false;
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    public void render(GuiGraphics g, Font font,
                       int screenW, int screenH,
                       int mouseX, int mouseY) {
        panelX = (screenW - PANEL_W) / 2;
        panelY = (screenH - PANEL_H) / 2;
        int btnY = panelY + PANEL_H - 40;
        int cancelX = panelX + PANEL_W / 2 - BTN_W - BTN_GAP / 2;
        int confirmX = panelX + PANEL_W / 2 + BTN_GAP / 2;
        this.cancelX = cancelX;
        this.cancelY = btnY;
        this.confirmX = confirmX;
        this.confirmY = btnY;

        int accent = warning ? WARN : DANGER;

        // Затемнение всего фона.
        Render.dialogBackground(g, screenW, screenH);
        Render.dialogPanel(g, panelX, panelY, PANEL_W, PANEL_H, accent);

        Render.text(g, font, title, panelX + 20, panelY + 20, accent);
        TextUtils.wrapped(g, font, message,
            panelX + 20, panelY + 50, PANEL_W - 40, TEXT);

        // Кнопки — свои, не AbstractWidget.
        boolean cancelHover = Render.hovered(mouseX, mouseY,
            cancelX, btnY, BTN_W, BTN_H);
        UiControls.button(g, font, cancelX, btnY, BTN_W, BTN_H,
            MaredLang.get("mared.dialog.cancel"),
            0xFF3A2020,
            cancelHover ? 0xFF55FF88 : 0xFF4A4A4A,
            cancelHover, TEXT);

        boolean confirmHover = Render.hovered(mouseX, mouseY,
            confirmX, btnY, BTN_W, BTN_H);
        UiControls.button(g, font, confirmX, btnY, BTN_W, BTN_H,
            MaredLang.get("mared.dialog.confirm"),
            warning ? 0xFF663333 : 0xFF336633,
            confirmHover ? accent : 0xFF4A4A4A,
            confirmHover, TEXT);
    }

    // ============================================================
    //  Input
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;

        if (Render.hovered(mx, my, cancelX, cancelY, BTN_W, BTN_H)) {
            cancel();
            return true;
        }
        if (Render.hovered(mx, my, confirmX, confirmY, BTN_W, BTN_H)) {
            confirm();
            return true;
        }
        return true; // barrier — клики вне панели поглощаются
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC — OverlayManager закроет и вызовет onClose()? Нет, не вызовет.
        // Для confirm нам важно — перехватить Escape как Cancel.
        if (keyCode == 256) {
            cancel();
            return true;
        }
        // Enter: warning → Cancel; иначе Confirm.
        if (keyCode == 257 || keyCode == 335) {
            if (warning) cancel(); else confirm();
            return true;
        }
        return false;
    }

    private void confirm() {
        if (onConfirm != null) {
            try { onConfirm.run(); }
            catch (Throwable ignored) { }
        }
        closeRequested = true;
    }

    private void cancel() {
        closeRequested = true;
    }
}