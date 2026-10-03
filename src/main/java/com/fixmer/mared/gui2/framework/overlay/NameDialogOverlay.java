package com.fixmer.mared.gui2.framework.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.render.widgets.UiControls;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Модальный overlay ввода имени.
 *
 * 0.3.2 (audit #71):
 *   Раньше панель была фиксированной (300×150 или 300×180) — длинные
 *   локализации title/error/кнопок не помещались, а placeholder был
 *   обрезан.
 *
 *   Теперь размер адаптивный:
 *     - ширина = max(300, ширина самого длинного элемента + отступы),
 *       клампнутая в [280 .. screenW - 80];
 *     - высота = сумма блоков (title, label, input, error, persistent,
 *       buttons) + padding.
 *
 *   Позиционирование — центр экрана.
 */
public final class NameDialogOverlay implements Overlay {

    // ---- Ограничения размера ----
    private static final int PANEL_W_MIN = 280;
    private static final int PANEL_W_MAX = 640;
    private static final int PANEL_W_PAD = 40;
    private static final int SCREEN_EDGE = 40;

    // ---- Вертикальные отступы ----
    private static final int PAD_TOP = 20;
    private static final int PAD_BOTTOM = 20;
    private static final int PAD_H = 20;

    private static final int ROW_TITLE_H = 20;
    private static final int ROW_LABEL_H = 18;
    private static final int INPUT_H = 18;
    private static final int ROW_ERROR_H = 16;
    private static final int ROW_CHECK_H = 16;
    private static final int ROW_HINT_H = 14;
    private static final int BTN_ROW_H = 24;
    private static final int ROW_GAP = 6;

    private static final int BTN_W_MIN = 100;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 20;

    private static final int CHECK_SIZE = 14;

    private static final int TEXT         = 0xFFFFFFFF;
    private static final int TEXT_DIM     = 0xFFAAAAAA;
    private static final int ERROR_COLOR  = 0xFFFF5555;
    private static final int INPUT_BG     = 0xFF0A0A10;
    private static final int INPUT_BORDER = 0xFF4A4A4A;
    private static final int CURSOR_COLOR = 0xFFFFFFFF;
    private static final int CHECK_ON     = 0xFF55FF88;
    private static final int CHECK_BRD    = 0xFF4A4A4A;

    private static final long ERROR_BLINK_DURATION_MS = 3000;
    private static final long ERROR_BLINK_HALF_PERIOD_MS = 300;

    // ---- Config ----
    private final String title;
    private final Consumer<String> onAcceptSimple;
    private final BiConsumer<String, Boolean> onAcceptWithFlag;
    private final int accentColor;
    private final boolean showPersistentOption;
    private final Function<String, String> nameValidator;

    // ---- Состояние ----
    private final StringBuilder value = new StringBuilder();
    private int cursor = 0;
    private int selAnchor = -1;
    private int selCursor = -1;

    private boolean persistent = false;
    private String errorMessage = "";
    private long errorBlinkStart = 0;

    private boolean closeRequested = false;

    // ---- Layout (рассчитывается в render) ----
    private int panelX, panelY, panelW, panelH;
    private int inputX, inputY, inputW;
    private int checkX, checkY, checkLabelW;
    private int okX, okY, okW, cancelX, cancelY, cancelW;

    // ============================================================
    //  Constructors
    // ============================================================

    public NameDialogOverlay(String title,
                             Consumer<String> onAccept,
                             int accentColor,
                             boolean showPersistentOption) {
        this(title, onAccept, null, accentColor, showPersistentOption, null);
    }

    public NameDialogOverlay(String title,
                             BiConsumer<String, Boolean> onAccept,
                             int accentColor,
                             boolean showPersistentOption) {
        this(title, null, onAccept, accentColor, showPersistentOption, null);
    }

    public NameDialogOverlay(String title,
                             Consumer<String> onAccept,
                             int accentColor,
                             boolean showPersistentOption,
                             Function<String, String> nameValidator) {
        this(title, onAccept, null, accentColor, showPersistentOption,
            nameValidator);
    }

    public NameDialogOverlay(String title,
                             BiConsumer<String, Boolean> onAccept,
                             int accentColor,
                             boolean showPersistentOption,
                             Function<String, String> nameValidator) {
        this(title, null, onAccept, accentColor, showPersistentOption,
            nameValidator);
    }

    private NameDialogOverlay(String title,
                              Consumer<String> onAcceptSimple,
                              BiConsumer<String, Boolean> onAcceptWithFlag,
                              int accentColor,
                              boolean showPersistentOption,
                              Function<String, String> nameValidator) {
        this.title = title != null ? title : "";
        this.onAcceptSimple = onAcceptSimple;
        this.onAcceptWithFlag = onAcceptWithFlag;
        this.accentColor = accentColor;
        this.showPersistentOption = showPersistentOption;
        this.nameValidator = nameValidator;
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
    //  Layout — адаптивно
    // ============================================================

    private void computeLayout(Font font, int screenW, int screenH) {
        String cancelText = MaredLang.get("mared.dialog.cancel");
        String okText     = MaredLang.get("mared.dialog.create");
        String labelText  = MaredLang.get("mared.dialog.name_label");
        String checkText  = showPersistentOption
            ? MaredLang.get("mared.dialog.persistent") : "";
        String hintText   = showPersistentOption
            ? MaredLang.get("mared.dialog.persistent_hint") : "";

        // Ширина — максимум из (title, error-message, label, check+hint, кнопок).
        int needed = 0;
        needed = Math.max(needed, font.width(title) + PAD_H * 2);
        needed = Math.max(needed, font.width(labelText) + PAD_H * 2);

        if (!errorMessage.isEmpty()) {
            needed = Math.max(needed, font.width(errorMessage) + PAD_H * 2);
        }
        if (showPersistentOption) {
            int checkRow = CHECK_SIZE + 6 + font.width(checkText);
            needed = Math.max(needed, checkRow + PAD_H * 2);
            needed = Math.max(needed, font.width(hintText) + PAD_H * 2);
        }

        int btnsRow = font.width(cancelText) + 30
                    + font.width(okText) + 30
                    + BTN_GAP
                    + PAD_H * 2;
        needed = Math.max(needed, btnsRow);

        int maxAllowed = Math.max(PANEL_W_MIN,
            Math.min(PANEL_W_MAX, screenW - SCREEN_EDGE * 2));
        panelW = clamp(needed + PANEL_W_PAD, PANEL_W_MIN, maxAllowed);

        // Высота — сумма блоков.
        int h = PAD_TOP;
        h += ROW_TITLE_H + ROW_GAP;
        h += ROW_LABEL_H;
        h += INPUT_H + ROW_GAP;
        if (!errorMessage.isEmpty()) h += ROW_ERROR_H;
        if (showPersistentOption) {
            h += ROW_GAP;
            h += ROW_CHECK_H + ROW_GAP;
            h += ROW_HINT_H;
        }
        h += ROW_GAP + BTN_ROW_H + PAD_BOTTOM;
        panelH = h;

        panelX = (screenW - panelW) / 2;
        panelY = (screenH - panelH) / 2;

        // Общие X/W для содержимого.
        int innerX = panelX + PAD_H;
        int innerW = panelW - PAD_H * 2;

        // Кнопки — снизу панели.
        int btnY = panelY + panelH - PAD_BOTTOM - BTN_H;
        int cancelW = Math.max(BTN_W_MIN,
            font.width(cancelText) + 30);
        int okW = Math.max(BTN_W_MIN,
            font.width(okText) + 30);
        int totalW = cancelW + BTN_GAP + okW;
        int startX = panelX + (panelW - totalW) / 2;

        this.cancelX = startX;
        this.cancelY = btnY;
        this.cancelW = cancelW;
        this.okX = startX + cancelW + BTN_GAP;
        this.okY = btnY;
        this.okW = okW;

        // Input — фиксированная позиция от верха.
        inputX = innerX;
        inputY = panelY + PAD_TOP + ROW_TITLE_H + ROW_GAP + ROW_LABEL_H;
        inputW = innerW;

        // Checkbox.
        if (showPersistentOption) {
            int checkTop = inputY + INPUT_H + ROW_GAP;
            if (!errorMessage.isEmpty()) checkTop += ROW_ERROR_H;
            checkX = innerX;
            checkY = checkTop + ROW_GAP;
            checkLabelW = font.width(checkText);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    public void render(GuiGraphics g, Font font,
                       int screenW, int screenH,
                       int mouseX, int mouseY) {
        computeLayout(font, screenW, screenH);

        Render.dialogBackground(g, screenW, screenH);
        Render.dialogPanel(g, panelX, panelY, panelW, panelH, accentColor);

        // Title
        Render.text(g, font, title,
            panelX + PAD_H, panelY + PAD_TOP, accentColor);

        // Label
        Render.text(g, font, MaredLang.get("mared.dialog.name_label"),
            panelX + PAD_H,
            panelY + PAD_TOP + ROW_TITLE_H + ROW_GAP, TEXT_DIM);

        drawInput(g, font);

        // Error
        if (!errorMessage.isEmpty()) {
            long elapsed = System.currentTimeMillis() - errorBlinkStart;
            boolean blinking = elapsed < ERROR_BLINK_DURATION_MS;
            boolean visible = !blinking
                || ((elapsed / ERROR_BLINK_HALF_PERIOD_MS) % 2 == 0);
            if (visible) {
                int errY = inputY + INPUT_H + ROW_GAP;
                Render.text(g, font, errorMessage,
                    panelX + PAD_H, errY, ERROR_COLOR);
            }
        }

        // Persistent checkbox + hint.
        if (showPersistentOption) {
            int hitW = CHECK_SIZE + 6 + checkLabelW;
            boolean hovered = Render.hovered(mouseX, mouseY,
                checkX, checkY, hitW, CHECK_SIZE);
            UiControls.drawCheckbox(g, checkX, checkY, CHECK_SIZE,
                persistent,
                hovered ? accentColor : CHECK_BRD, CHECK_ON);

            Render.text(g, font, MaredLang.get("mared.dialog.persistent"),
                checkX + CHECK_SIZE + 6, checkY + 3,
                persistent ? CHECK_ON : TEXT_DIM);

            Render.text(g, font, MaredLang.get("mared.dialog.persistent_hint"),
                checkX, checkY + CHECK_SIZE + 4, TEXT_DIM);
        }

        // Buttons.
        boolean cancelHover = Render.hovered(mouseX, mouseY,
            cancelX, cancelY, cancelW, BTN_H);
        UiControls.button(g, font, cancelX, cancelY, cancelW, BTN_H,
            MaredLang.get("mared.dialog.cancel"),
            0xFF3A2020,
            cancelHover ? 0xFFFF5555 : 0xFF4A4A4A,
            cancelHover, TEXT);

        boolean okHover = Render.hovered(mouseX, mouseY,
            okX, okY, okW, BTN_H);
        UiControls.button(g, font, okX, okY, okW, BTN_H,
            MaredLang.get("mared.dialog.create"),
            0xFF203A20,
            okHover ? 0xFF55FF88 : 0xFF4A4A4A,
            okHover, TEXT);
    }

    private void drawInput(GuiGraphics g, Font font) {
        Render.rect(g, inputX, inputY, inputX + inputW, inputY + INPUT_H,
            INPUT_BG);
        Render.outline(g, inputX, inputY, inputW, INPUT_H, INPUT_BORDER);

        String text = value.toString();
        int textY = inputY + (INPUT_H - 8) / 2;

        if (hasSelection()) {
            int lo = selLo();
            int hi = selHi();
            int xLo = inputX + 4 + font.width(text.substring(0, lo));
            int xHi = inputX + 4 + font.width(text.substring(0, hi));
            Render.rect(g, xLo, inputY + 2, xHi, inputY + INPUT_H - 2,
                0x804466CC);
        }

        Render.textNoShadow(g, font, text, inputX + 4, textY, TEXT);

        long now = System.currentTimeMillis();
        if ((now / 500) % 2 == 0) {
            int cx = inputX + 4 + font.width(text.substring(0, cursor));
            Render.rect(g, cx, inputY + 3, cx + 1, inputY + INPUT_H - 3,
                CURSOR_COLOR);
        }
    }

    // ============================================================
    //  Selection
    // ============================================================

    private boolean hasSelection() {
        return selAnchor >= 0 && selCursor >= 0 && selAnchor != selCursor;
    }

    private int selLo() { return Math.min(selAnchor, selCursor); }
    private int selHi() { return Math.max(selAnchor, selCursor); }

    private void clearSelection() {
        selAnchor = -1;
        selCursor = -1;
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int lo = selLo();
        int hi = selHi();
        value.delete(lo, hi);
        cursor = lo;
        clearSelection();
    }

    // ============================================================
    //  Char / Key
    // ============================================================

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint < 32) return true;
        if (value.length() >= MaredCommandStorage.MAX_NAME_LEN) return true;

        deleteSelection();
        value.insert(cursor, codePoint);
        cursor++;
        clearError();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl  = (modifiers & 2) != 0;
        boolean shift = (modifiers & 1) != 0;

        if (keyCode == 256) {
            closeRequested = true;
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            onOk();
            return true;
        }
        if (ctrl) {
            switch (keyCode) {
                case 65: selectAll(); return true;
                case 67: copySelection(); return true;
                case 86: pasteFromClipboard(); return true;
                case 88: cutSelection(); return true;
            }
            return true;
        }

        switch (keyCode) {
            case 259: // backspace
                if (hasSelection()) { deleteSelection(); clearError(); return true; }
                if (cursor > 0) {
                    value.deleteCharAt(cursor - 1);
                    cursor--;
                    clearError();
                }
                return true;
            case 261: // delete
                if (hasSelection()) { deleteSelection(); clearError(); return true; }
                if (cursor < value.length()) {
                    value.deleteCharAt(cursor);
                    clearError();
                }
                return true;
            case 263: // left
                if (hasSelection() && !shift) { cursor = selLo(); clearSelection(); }
                else if (cursor > 0) {
                    cursor--;
                    if (shift) {
                        if (selAnchor < 0) selAnchor = cursor + 1;
                        selCursor = cursor;
                    } else clearSelection();
                }
                return true;
            case 262: // right
                if (hasSelection() && !shift) { cursor = selHi(); clearSelection(); }
                else if (cursor < value.length()) {
                    cursor++;
                    if (shift) {
                        if (selAnchor < 0) selAnchor = cursor - 1;
                        selCursor = cursor;
                    } else clearSelection();
                }
                return true;
            case 268: // home
                cursor = 0;
                if (shift) {
                    if (selAnchor < 0) selAnchor = value.length();
                    selCursor = 0;
                } else clearSelection();
                return true;
            case 269: // end
                cursor = value.length();
                if (shift) {
                    if (selAnchor < 0) selAnchor = 0;
                    selCursor = cursor;
                } else clearSelection();
                return true;
        }
        return false;
    }

    // ============================================================
    //  Clipboard
    // ============================================================

    private void selectAll() {
        selAnchor = 0;
        selCursor = value.length();
        cursor = value.length();
    }

    private void copySelection() {
        if (!hasSelection()) return;
        String sel = value.substring(selLo(), selHi());
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(sel);
        } catch (Throwable ignored) { }
    }

    private void cutSelection() {
        if (!hasSelection()) return;
        copySelection();
        deleteSelection();
        clearError();
    }

    private void pasteFromClipboard() {
        String clip;
        try {
            clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        } catch (Throwable t) {
            return;
        }
        if (clip == null || clip.isEmpty()) return;

        deleteSelection();

        int remaining = MaredCommandStorage.MAX_NAME_LEN - value.length();
        if (remaining <= 0) return;
        String insert = clip.length() > remaining
            ? clip.substring(0, remaining) : clip;
        insert = insert.replace("\n", "").replace("\r", "");

        value.insert(cursor, insert);
        cursor += insert.length();
        clearError();
    }

    // ============================================================
    //  Mouse
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;

        if (showPersistentOption) {
            int hitW = CHECK_SIZE + 6 + checkLabelW;
            if (mx >= checkX && mx < checkX + hitW
                && my >= checkY && my < checkY + CHECK_SIZE) {
                persistent = !persistent;
                return true;
            }
        }

        if (mx >= inputX && mx < inputX + inputW
            && my >= inputY && my < inputY + INPUT_H) {
            placeCursorFromMouse(mx);
            clearSelection();
            return true;
        }

        if (mx >= cancelX && mx < cancelX + cancelW
            && my >= cancelY && my < cancelY + BTN_H) {
            closeRequested = true;
            return true;
        }
        if (mx >= okX && mx < okX + okW
            && my >= okY && my < okY + BTN_H) {
            onOk();
            return true;
        }
        return true;
    }

    private void placeCursorFromMouse(double mx) {
        Font font = Minecraft.getInstance().font;
        int relX = (int) mx - (inputX + 4);
        if (relX < 0) { cursor = 0; return; }
        String text = value.toString();
        int best = text.length();
        for (int i = 0; i <= text.length(); i++) {
            int w = font.width(text.substring(0, i));
            if (w >= relX) { best = i; break; }
        }
        cursor = best;
    }

    // ============================================================
    //  Validation
    // ============================================================

    private void onOk() {
        String name = value.toString().trim();

        String err = validateBase(name);
        if (err != null) { showError(err); return; }

        if (nameValidator != null) {
            String external = nameValidator.apply(name);
            if (external != null) { showError(external); return; }
        }

        errorMessage = "";
        errorBlinkStart = 0;

        final boolean flag = persistent;
        final String finalName = name;

        if (showPersistentOption && onAcceptWithFlag != null) {
            try { onAcceptWithFlag.accept(finalName, flag); }
            catch (Throwable ignored) { }
        } else if (onAcceptSimple != null) {
            try { onAcceptSimple.accept(finalName); }
            catch (Throwable ignored) { }
        }

        closeRequested = true;
    }

    private String validateBase(String name) {
        String code = MaredCommandStorage.validateName(name);
        if (code == null) return null;
        return switch (code) {
            case "empty"      -> MaredLang.get("mared.dialog.error.empty");
            case "too_long"   -> MaredLang.format("mared.dialog.error.too_long",
                                    MaredCommandStorage.MAX_NAME_LEN);
            case "reserved"   -> MaredLang.get("mared.dialog.error.reserved");
            case "slash"      -> MaredLang.get("mared.dialog.error.slash_forbidden");
            case "hidden"     -> MaredLang.get("mared.dialog.error.hidden");
            case "whitespace" -> MaredLang.get("mared.dialog.error.whitespace");
            default           -> MaredLang.get("mared.dialog.error.charset");
        };
    }

    private void showError(String err) {
        errorMessage = err;
        errorBlinkStart = System.currentTimeMillis();
    }

    private void clearError() {
        if (!errorMessage.isEmpty()) {
            errorMessage = "";
            errorBlinkStart = 0;
        }
    }
}