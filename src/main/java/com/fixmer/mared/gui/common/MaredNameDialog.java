package com.fixmer.mared.gui.common;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fixmer.mared.MaredLang;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MaredNameDialog extends Screen {

    private static final int TEXT         = MaredUi.TEXT;
    private static final int TEXT_DIM     = MaredUi.TEXT_DIM;
    private static final int CHECK_ON     = MaredUi.SUCCESS;
    private static final int CHECK_BRD    = 0xFF4A4A4A;
    private static final int ERROR_COLOR  = 0xFFFF5555;

    private static final int MAX_NAME_LEN = 64;
    private static final long ERROR_BLINK_DURATION_MS = 3000;
    private static final long ERROR_BLINK_HALF_PERIOD_MS = 300;

    private static final int PANEL_W = 300;
    private static final int PANEL_H_NO_PERSIST  = 150;
    private static final int PANEL_H_WITH_PERSIST = 180;
    private static final int BTN_W = 120;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 20;

    private final Screen parent;
    private final Consumer<String> onAcceptSimple;
    private final BiConsumer<String, Boolean> onAcceptWithFlag;
    private final int accentColor;
    private final boolean allowSlash;
    private final boolean showPersistentOption;
    private final Function<String, String> nameValidator;

    private EditBox nameBox;
    private AbstractWidget okBtn;
    private AbstractWidget cancelBtn;

    private boolean persistent = false;
    private int checkX, checkY;
    private static final int CHECK_SIZE = 14;

    private String errorMessage = "";
    private long errorBlinkStart = 0;

    public MaredNameDialog(Screen parent, String title, Consumer<String> onAccept,
                           int accentColor, boolean allowSlash) {
        this(parent, title, onAccept, null, accentColor, allowSlash, false, null);
    }

    public MaredNameDialog(Screen parent, String title, BiConsumer<String, Boolean> onAccept,
                           int accentColor, boolean allowSlash) {
        this(parent, title, null, onAccept, accentColor, allowSlash, true, null);
    }

    public MaredNameDialog(Screen parent, String title, Consumer<String> onAccept,
                           int accentColor, boolean allowSlash,
                           Function<String, String> nameValidator) {
        this(parent, title, onAccept, null, accentColor, allowSlash, false, nameValidator);
    }

    public MaredNameDialog(Screen parent, String title, BiConsumer<String, Boolean> onAccept,
                           int accentColor, boolean allowSlash,
                           Function<String, String> nameValidator) {
        this(parent, title, null, onAccept, accentColor, allowSlash, true, nameValidator);
    }

    private MaredNameDialog(Screen parent, String title,
                            Consumer<String> onAcceptSimple,
                            BiConsumer<String, Boolean> onAcceptWithFlag,
                            int accentColor, boolean allowSlash,
                            boolean showPersistentOption,
                            Function<String, String> nameValidator) {
        super(Component.literal(title));
        this.parent = parent;
        this.onAcceptSimple = onAcceptSimple;
        this.onAcceptWithFlag = onAcceptWithFlag;
        this.accentColor = accentColor;
        this.allowSlash = allowSlash;
        this.showPersistentOption = showPersistentOption;
        this.nameValidator = nameValidator;
    }

    private int panelH() {
        return showPersistentOption ? PANEL_H_WITH_PERSIST : PANEL_H_NO_PERSIST;
    }

    @Override
    protected void init() {
        super.init();
        MaredLang.reload();

        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - panelH()) / 2;

        int inputX = panelX + 20;
        int inputY = panelY + 50;
        int inputW = PANEL_W - 40;

        nameBox = new EditBox(this.font, inputX, inputY, inputW, 18, Component.literal("Name"));
        nameBox.setMaxLength(MAX_NAME_LEN);
        nameBox.setBordered(true);
        nameBox.setFocused(true);
        nameBox.setTextColor(0xFFFFFFFF);
        nameBox.setTextColorUneditable(0xFFAAAAAA);
        nameBox.setResponder(s -> {
            if (!errorMessage.isEmpty()) {
                errorMessage = "";
                errorBlinkStart = 0;
            }
        });
        addRenderableWidget(nameBox);

        if (showPersistentOption) {
            checkX = panelX + 20;
            checkY = panelY + 100;
        }

        int btnY = panelY + panelH() - 40;

        cancelBtn = addRenderableWidget(new MaredCompactButton(
            panelX + PANEL_W / 2 - BTN_W - BTN_GAP / 2, btnY, BTN_W, BTN_H,
            Component.literal(MaredLang.get("mared.dialog.cancel")),
            0xFFFF5555, this::onCancel));

        okBtn = addRenderableWidget(new MaredCompactButton(
            panelX + PANEL_W / 2 + BTN_GAP / 2, btnY, BTN_W, BTN_H,
            Component.literal(MaredLang.get("mared.dialog.create")),
            0xFF55FF88, this::onOk));
    }

    private String validateBase(String name) {
        if (name == null || name.isEmpty()) return MaredLang.get("mared.dialog.error.empty");
        if (name.length() > MAX_NAME_LEN) return MaredLang.format("mared.dialog.error.too_long", MAX_NAME_LEN);
        if (name.equals(".") || name.equals("..")) return MaredLang.get("mared.dialog.error.reserved");
        if (!allowSlash && name.contains("/")) return MaredLang.get("mared.dialog.error.slash_forbidden");
        if (name.contains("\\")) return MaredLang.get("mared.dialog.error.backslash_forbidden");
        if (name.startsWith(" ") || name.endsWith(" ")) return MaredLang.get("mared.dialog.error.whitespace");
        if (!name.matches("[a-zA-Z0-9_\\-\\.]+")) return MaredLang.get("mared.dialog.error.charset");
        return null;
    }

    private void showError(String err) {
        errorMessage = err;
        errorBlinkStart = System.currentTimeMillis();
    }

    private void onOk() {
        String name = nameBox.getValue().trim();

        String err = validateBase(name);
        if (err != null) { showError(err); return; }

        if (nameValidator != null) {
            String external = nameValidator.apply(name);
            if (external != null) { showError(external); return; }
        }

        errorMessage = "";
        errorBlinkStart = 0;
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
        MaredUi.dialogBackground(g, this.width, this.height);

        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - panelH()) / 2;
        MaredUi.dialogPanel(g, panelX, panelY, PANEL_W, panelH(), accentColor);

        MaredUi.text(g, this.font, this.title.getString(), panelX + 20, panelY + 20, accentColor);
        MaredUi.text(g, this.font, MaredLang.get("mared.dialog.name_label"),
            panelX + 20, panelY + 38, TEXT_DIM);

        if (!errorMessage.isEmpty()) {
            long elapsed = System.currentTimeMillis() - errorBlinkStart;
            boolean blinking = elapsed < ERROR_BLINK_DURATION_MS;
            boolean visible = !blinking || ((elapsed / ERROR_BLINK_HALF_PERIOD_MS) % 2 == 0);
            if (visible) {
                MaredUi.text(g, this.font, errorMessage, panelX + 20, panelY + 72, ERROR_COLOR);
            }
        }

        if (showPersistentOption) {
            boolean hovered = mouseX >= checkX && mouseX < checkX + CHECK_SIZE
                           && mouseY >= checkY && mouseY < checkY + CHECK_SIZE;

            MaredUi.drawCheckbox(g, checkX, checkY, CHECK_SIZE, persistent,
                hovered ? accentColor : CHECK_BRD, CHECK_ON);

            MaredUi.text(g, this.font, MaredLang.get("mared.dialog.persistent"),
                checkX + CHECK_SIZE + 6, checkY + 3,
                persistent ? CHECK_ON : TEXT_DIM);

            MaredUi.text(g, this.font, MaredLang.get("mared.dialog.persistent_hint"),
                checkX, checkY + CHECK_SIZE + 6, TEXT_DIM);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (showPersistentOption && button == 0
            && mx >= checkX && mx < checkX + CHECK_SIZE
            && my >= checkY && my < checkY + CHECK_SIZE) {
            persistent = !persistent;
            return true;
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

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
}