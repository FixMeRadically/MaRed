package com.fixmer.mared.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class MaredCompactButton extends AbstractWidget {

    private final Runnable onClick;
    private final int accentColor;

    private static final int COLOR_BG_NORMAL  = 0xFF2D2D2D;
    private static final int COLOR_BG_HOVER   = 0xFF3E3E42;
    private static final int COLOR_BORDER     = 0xFF4A4A4A;
    private static final int COLOR_TEXT       = 0xFFDDDDDD;
    private static final int COLOR_TEXT_HOVER = 0xFFFFFFFF;

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
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHovered();

        int border = hovered ? accentColor : COLOR_BORDER;
        int bg = hovered ? COLOR_BG_HOVER : COLOR_BG_NORMAL;

        // Фон
        g.fill(getX(), getY(), getX() + width, getY() + height, bg);

        // FIX: градиентная рамка (3D-эффект) вместо плоского renderOutline
        int top = MaredUi.lighten(border, 0.15f);
        int bottom = MaredUi.darken(border, 0.25f);
        MaredUi.outlineGradient(g, getX(), getY(), width, height, top, bottom);

        // FIX: единая формула центрирования без +1
        // Для height=18 baseline = (18-8)/2 = 5, для height=12 = 2. Всё правильно.
        String text = getMessage().getString();
        int color = hovered ? COLOR_TEXT_HOVER : COLOR_TEXT;
        int tw = Minecraft.getInstance().font.width(text);
        int tx = getX() + (width - tw) / 2;
        int ty = getY() + (height - 8) / 2;
        g.drawString(Minecraft.getInstance().font, text, tx, ty, color, true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        // Empty.
    }
}