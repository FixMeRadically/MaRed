package com.fixmer.mared;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MaredConfirmDialog extends Screen {

    private static final int COLOR_BG         = 0xFF1E1E2A;
    private static final int COLOR_SCREEN_BG  = 0xFF0E0E14;
    private static final int COLOR_DANGER     = 0xFFFF4444;
    private static final int COLOR_TEXT       = 0xFFFFFFFF;
    private static final int COLOR_DIM        = 0xFFAAAAAA;
    private static final int COLOR_SHADOW     = 0x80000000;

    private final Screen parent;
    private final String title;
    private final String message;
    private final Runnable onConfirm;

    public MaredConfirmDialog(Screen parent, String title, String message, Runnable onConfirm) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int w = 280;
        int h = 100;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        this.addRenderableWidget(new MaredCompactButton(
            x + 10, y + h - 28, 120, 18,
            Component.literal("Удалить"), COLOR_DANGER, () -> {
                onConfirm.run();
                this.minecraft.setScreen(parent);
            }));
        this.addRenderableWidget(new MaredCompactButton(
            x + w - 130, y + h - 28, 120, 18,
            Component.literal("Отмена"), 0xFFAAAAAA,
            () -> this.minecraft.setScreen(parent)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. Непрозрачный тёмный фон на весь экран.
        graphics.fill(0, 0, this.width, this.height, COLOR_SCREEN_BG);

        int w = 280;
        int h = 100;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // 2. Тень.
        graphics.fill(x + 3, y + 3, x + w + 3, y + h + 3, COLOR_SHADOW);
        // 3. Тело диалога.
        graphics.fill(x, y, x + w, y + h, COLOR_BG);
        // 4. Красная рамка.
        graphics.renderOutline(x, y, w, h, COLOR_DANGER);

        // 5. Текст.
        graphics.drawString(this.font, title, x + 10, y + 10, COLOR_TEXT, true);
        graphics.drawString(this.font, message, x + 10, y + 30, COLOR_DIM, true);
        graphics.drawString(this.font, "Это действие нельзя отменить.", x + 10, y + 46, COLOR_DANGER, true);

        // 6. Кнопки.
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
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
        // Пусто — фон рисуем в render().
    }
}