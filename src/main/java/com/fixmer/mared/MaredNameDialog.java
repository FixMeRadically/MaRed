package com.fixmer.mared;

import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Маленький диалог ввода имени скрипта.
 * Открывается поверх родительского экрана.
 */
public class MaredNameDialog extends Screen {

    private static final int COLOR_BG     = 0xFF1E1E2A;
    private static final int COLOR_BORDER = 0xFFFF55FF;
    private static final int COLOR_TEXT   = 0xFFFFFFFF;
    private static final int COLOR_HINT   = 0xFFAAAAAA;

    private final Screen parent;
    private final String title;
    private final Consumer<String> onConfirm;

    private EditBox input;
    private String error = null;

    public MaredNameDialog(Screen parent, String title, Consumer<String> onConfirm) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int w = 220;
        int h = 80;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        input = new EditBox(this.font, x + 10, y + 30, w - 20, 18, Component.literal("Имя"));
        input.setMaxLength(32);
        input.setFocused(true);
        this.addRenderableWidget(input);

        this.addRenderableWidget(new MaredCompactButton(
            x + 10, y + 52, 90, 18, Component.literal("Создать"), COLOR_BORDER,
            this::confirm));
        this.addRenderableWidget(new MaredCompactButton(
            x + w - 100, y + 52, 90, 18, Component.literal("Отмена"), 0xFFAAAAAA,
            () -> this.minecraft.setScreen(parent)));
    }

    private void confirm() {
        String name = input.getValue().trim();
        if (!MaredScriptStorage.isValidName(name)) {
            error = "Только a-z, A-Z, 0-9, _ и -";
            return;
        }
        if (MaredScriptStorage.listScripts().contains(name)) {
            error = "Скрипт с таким именем уже есть";
            return;
        }
        onConfirm.accept(name);
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Затемняем фон.
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);

        int w = 220;
        int h = 80;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        graphics.fill(x, y, x + w, y + h, COLOR_BG);
        graphics.renderOutline(x, y, w, h, COLOR_BORDER);
        graphics.drawString(this.font, title, x + 10, y + 10, COLOR_TEXT, true);

        if (error != null) {
            graphics.drawString(this.font, error, x + 10, y + 72, 0xFFFF5555, true);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            confirm();
            return true;
        }
        if (keyCode == 256) { // Escape
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Пусто.
    }
}