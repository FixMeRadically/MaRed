package com.fixmer.mared;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Компактная плоская кнопка в стиле VS Code.
 * Тёмный фон, светлая рамка, при наведении — светлее.
 */
public class MaredCompactButton extends AbstractWidget {

    private final Runnable onClick;
    private final int accentColor;

    // Цвета в стиле VS Code.
    private static final int COLOR_BG_NORMAL   = 0xFF2D2D2D;
    private static final int COLOR_BG_HOVER    = 0xFF3E3E42;
    private static final int COLOR_BORDER      = 0xFF4A4A4A;
    private static final int COLOR_TEXT        = 0xFFDDDDDD;
    private static final int COLOR_TEXT_HOVER  = 0xFFFFFFFF;

    public MaredCompactButton(int x, int y, int width, int height, Component label, int accentColor, Runnable onClick) {
        super(x, y, width, height, label);
        this.onClick = onClick;
        this.accentColor = accentColor;
    }

    public MaredCompactButton(int x, int y, int width, int height, Component label, Runnable onClick) {
        this(x, y, width, height, label, 0xFF4A4A4A, onClick);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (onClick != null) onClick.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHovered();

        // Фон.
        graphics.fill(getX(), getY(), getX() + width, getY() + height,
            hovered ? COLOR_BG_HOVER : COLOR_BG_NORMAL);

        // Рамка.
        graphics.renderOutline(getX(), getY(), width, height, hovered ? accentColor : COLOR_BORDER);

        // Текст по центру.
        String text = getMessage().getString();
        int tw = Minecraft.getInstance().font.width(text);
        int tx = getX() + (width - tw) / 2;
        int ty = getY() + (height - 8) / 2 + 1;
        graphics.drawString(Minecraft.getInstance().font, text, tx, ty,
            hovered ? COLOR_TEXT_HOVER : COLOR_TEXT, true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        // Ничего не нужно.
    }
}