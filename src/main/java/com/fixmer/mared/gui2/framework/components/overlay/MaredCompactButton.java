package com.fixmer.mared.gui2.framework.components.overlay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Компактная кнопка MaRed.
 *
 * 0.3.0: перенос из gui.common. Использует gui2.framework.render.Render.
 * 0.3.2 (accessibility):
 *   updateWidgetNarration больше не пустая.
 *   Раньше кнопка была невидима для screen reader'а: Minecraft
 *   вызывает updateWidgetNarration при focus/hover, и пустая
 *   реализация означала, что кнопка "молчит".
 *
 *   Реализация — стандартный defaultButtonNarrationText из
 *   AbstractWidget: озвучивает label и подсказку
 *   (focused/hovered). Для кнопки этого достаточно.
 */
//110: standard button keyboard activation and visible keyboard focus.
public class MaredCompactButton extends AbstractButton {

    private final Runnable onClick;
    private final int accentColor;


    public MaredCompactButton(int x, int y, int width, int height,
                              Component label, int accentColor, Runnable onClick) {
        super(x, y, width, height, label);
        this.onClick = onClick;
        this.accentColor = accentColor;
    }

    public MaredCompactButton(int x, int y, int width, int height,
                              Component label, Runnable onClick) {
        this(x, y, width, height, label, 0xFF4A4A4A, onClick);
    }

    @Override
    public void onPress() {
        if (onClick != null) onClick.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY,
                                float partialTick) {
        com.fixmer.mared.gui2.development.DevelopmentButton129.draw(g, getX(), getY(), width, height,
            getMessage(), active, isHoveredOrFocused(), false);
        if (active && accentColor != 0xFF4A4A4A) g.renderOutline(getX(), getY(), width, height, accentColor);
    }

    /**
     * 0.3.2: narration contract.
     * defaultButtonNarrationText озвучивает:
     *   - label (getMessage()),
     *   - подсказку: focused / hovered.
     */
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
