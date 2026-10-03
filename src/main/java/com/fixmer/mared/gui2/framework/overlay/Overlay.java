package com.fixmer.mared.gui2.framework.overlay;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Базовый overlay MaRed Studio.
 *
 * 0.3.2:
 *   - charTyped() — ввод символов.
 *   - mouseScrolled() — скролл для палитр/списков.
 *   - consumeCloseRequest() — overlay просит закрыться.
 */
public interface Overlay {

    OverlayLayer layer();
    default boolean isInputBarrier() { return false; }
    default boolean closeOnEscape() { return true; }

    default void onOpen() {}
    default void onClose() {}

    void render(GuiGraphics g, Font font,
                int screenW, int screenH,
                int mouseX, int mouseY);

    default boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    default boolean mouseScrolled(double mx, double my,
                                  double scrollX, double scrollY) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    default boolean consumeCloseRequest() { return false; }
}