package com.fixmer.mared.gui.common;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import com.fixmer.mared.MaredLang;

public class MaredNameDialog extends Screen {

    private static final int TEXT     = MaredUi.TEXT;
    private static final int TEXT_DIM = MaredUi.TEXT_DIM;
    private static final int CHECK_ON = MaredUi.SUCCESS;
    private static final int CHECK_BRD= 0xFF4A4A4A;
    private static final int ERROR_COLOR = 0xFFFF5555;

    private static final int MAX_NAME_LEN = 64;

    /** FIX 0.2.6: мигание ошибки — 3 секунды, потом статично. */
    private static final long ERROR_BLINK_DURATION_MS = 3000;
    private static final long ERROR_BLINK_HALF_PERIOD_MS = 300;

    private final Screen parent;
    private final String title;
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
    private int checkX, checkY, checkSize = 14;

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
        this.title = title;
        this.onAcceptSimple = onAcceptSimple;
        this.onAcceptWithFlag = onAcceptWithFlag;
        this.accentColor = accentColor;
        this.allowSlash = allowSlash;
        this.showPersistentOption = showPersistentOption;
        this.nameValidator = nameValidator;
    }

    @Override
    protected void init() {
        super.init();
        MaredLang.reload();

        int panelW = 300;
        int panelH = showPersistentOption ? 180 : 150;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        int inputX = panelX + 20;
        int inputY = panelY + 50;
        int inputW = panelW - 40;

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

        int btnW = 120;
        int btnY = panelY + panelH - 40;
        int btnGap = 20;

        // FIX 0.2.6: Отмена — СЛЕВА, Создать — СПРАВА.
        cancelBtn = addRenderableWidget(new MaredCompactButton(
            panelX + panelW / 2 - btnW - btnGap / 2, btnY, btnW, 20,
            Component.literal(MaredLang.get("mared.dialog.cancel")),
            0xFFFF5555, this::onCancel));

        okBtn = addRenderableWidget(new MaredCompactButton(
            panelX + panelW / 2 + btnGap / 2, btnY, btnW, 20,
            Component.literal(MaredLang.get("mared.dialog.create")),
            0xFF55FF88, this::onOk));
    }

    private String validateBase(String name) {
        if (name == null || name.isEmpty()) {
            return MaredLang.get("mared.dialog.error.empty");
        }
        if (name.length() > MAX_NAME_LEN) {
            return MaredLang.format("mared.dialog.error.too_long", MAX_NAME_LEN);
        }
        if (name.equals(".") || name.equals("..")) {
            return MaredLang.get("mared.dialog.error.reserved");
        }
        if (!allowSlash && name.contains("/")) {
            return MaredLang.get("mared.dialog.error.slash_forbidden");
        }
        if (name.contains("\\")) {
            return MaredLang.get("mared.dialog.error.backslash_forbidden");
        }
        if (name.startsWith(" ") || name.endsWith(" ")) {
            return MaredLang.get("mared.dialog.error.whitespace");
        }
        if (!name.matches("[a-zA-Z0-9_\\-\\.]+")) {
            return MaredLang.get("mared.dialog.error.charset");
        }
        return null;
    }

    private void onOk() {
        String name = nameBox.getValue().trim();

        String err = validateBase(name);
        if (err != null) {
            errorMessage = err;
            errorBlinkStart = System.currentTimeMillis();
            return;
        }

        if (nameValidator != null) {
            String external = nameValidator.apply(name);
            if (external != null) {
                errorMessage = external;
                errorBlinkStart = System.currentTimeMillis();
                return;
            }
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

        int panelW = 300;
        int panelH = showPersistentOption ? 180 : 150;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        MaredUi.dialogPanel(g, panelX, panelY, panelW, panelH, accentColor);

        MaredUi.text(g, this.font, title, panelX + 20, panelY + 20, accentColor);
        MaredUi.text(g, this.font, MaredLang.get("mared.dialog.name_label"),
            panelX + 20, panelY + 38, TEXT_DIM);

        // FIX 0.2.6: мигание ошибки
        if (!errorMessage.isEmpty()) {
            long elapsed = System.currentTimeMillis() - errorBlinkStart;
            boolean blinking = elapsed < ERROR_BLINK_DURATION_MS;
            boolean visible = !blinking
                || ((elapsed / ERROR_BLINK_HALF_PERIOD_MS) % 2 == 0);
            if (visible) {
                MaredUi.text(g, this.font, errorMessage,
                    panelX + 20, panelY + 72, ERROR_COLOR);
            }
        }

        if (showPersistentOption) {
            boolean hovered = mouseX >= checkX && mouseX < checkX + checkSize
                && mouseY >= checkY && mouseY < checkY + checkSize;

            MaredUi.drawCheckbox(g, checkX, checkY, checkSize, persistent,
                hovered ? accentColor : CHECK_BRD, CHECK_ON);

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

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
}