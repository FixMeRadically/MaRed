package com.fixmer.mared.gui2.framework.overlay;


import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.technology.editor.EditorDialogStyle;
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
    private static final int BTN_GAP = EditorDialogStyle.BUTTON_GAP;

    private final String title;
    private final String message;
    private final Runnable onConfirm;
    private final boolean warning;
    private final Integer categoryColor;
    private final boolean destructive;
    private final String confirmLabelKey;

    private int panelX, panelY, panelW, panelH, buttonW;
    private int cancelX, cancelY;
    private int confirmX, confirmY;

    private boolean closeRequested = false;

    public ConfirmDialogOverlay(String title, String message,
                                Runnable onConfirm, boolean warning) {
        this(title, message, onConfirm, warning, null, warning, "mared.dialog.confirm");
    }

    public ConfirmDialogOverlay(String title, String message, Runnable onConfirm,
                                boolean warning, Integer categoryColor,
                                boolean destructive, String confirmLabelKey) {
        this.title = title != null ? title : "";
        this.message = message != null ? message : "";
        this.onConfirm = onConfirm;
        this.warning = warning;
        this.categoryColor = categoryColor;
        this.destructive = destructive;
        this.confirmLabelKey = java.util.Objects.requireNonNull(confirmLabelKey);
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
        panelW = Math.min(PANEL_W, Math.max(1, screenW - 16));
        int messageHeight = TextUtils.wrappedHeight(font, message,
            Math.max(1, panelW - EditorDialogStyle.PADDING * 2));
        panelH = Math.max(PANEL_H, 50 + messageHeight + BTN_H + EditorDialogStyle.PADDING * 2);
        panelX = (screenW - panelW) / 2;
        panelY = (screenH - panelH) / 2;
        buttonW = Math.min(BTN_W, Math.max(1, (panelW - EditorDialogStyle.PADDING * 2 - BTN_GAP) / 2));
        int btnY = panelY + panelH - EditorDialogStyle.PADDING - BTN_H;
        int cancelX = panelX + panelW / 2 - buttonW - BTN_GAP / 2;
        int confirmX = panelX + panelW / 2 + BTN_GAP / 2;
        this.cancelX = cancelX;
        this.cancelY = btnY;
        this.confirmX = confirmX;
        this.confirmY = btnY;

        MaredTheme theme = MaredThemeRegistry.active();
        EditorDialogStyle style = EditorDialogStyle.from(theme,
            categoryColor == null ? theme.accent : categoryColor);

        // Затемнение всего фона.
        Render.dialogBackground(g, screenW, screenH);
        style.panel(g, panelX, panelY, panelW, panelH);

        Render.text(g, font, TextUtils.ellipsize(font, title, panelW - EditorDialogStyle.PADDING * 2), panelX + EditorDialogStyle.PADDING, panelY + EditorDialogStyle.PADDING, style.text());
        TextUtils.wrapped(g, font, message,
            panelX + EditorDialogStyle.PADDING, panelY + 50,
            panelW - EditorDialogStyle.PADDING * 2, style.text());

        // Кнопки — свои, не AbstractWidget.
        boolean cancelHover = Render.hovered(mouseX, mouseY,
            cancelX, btnY, buttonW, BTN_H);
        style.button(g, font, cancelX, btnY, buttonW, BTN_H,
            MaredLang.get("mared.dialog.cancel"), cancelHover, false, false);

        boolean confirmHover = Render.hovered(mouseX, mouseY,
            confirmX, btnY, buttonW, BTN_H);
        style.button(g, font, confirmX, btnY, buttonW, BTN_H,
            MaredLang.get(confirmLabelKey), confirmHover, true, destructive);
    }

    // ============================================================
    //  Input
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;

        if (Render.hovered(mx, my, cancelX, cancelY, buttonW, BTN_H)) {
            cancel();
            return true;
        }
        if (Render.hovered(mx, my, confirmX, confirmY, buttonW, BTN_H)) {
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