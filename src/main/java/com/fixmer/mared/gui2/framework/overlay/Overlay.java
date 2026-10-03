package com.fixmer.mared.gui2.framework.overlay;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Базовый overlay MaRed Studio.
 *
 * Overlay — элемент, который рисуется поверх содержимого экрана и
 * может перехватывать input. Управляется OverlayManager (per-screen).
 *
 * Контракт:
 *   - layer() определяет порядок render/dispatch.
 *   - isInputBarrier() — если true, все input-события останавливаются
 *     на этом overlay'е, даже если клик вне его визуальной области.
 *   - closeOnEscape() — если true, Escape закрывает overlay.
 *   - mouseClicked/keyPressed возвращают true, если событие поглощено.
 *
 * onOpen/onClose вызываются OverlayManager при push/remove — overlay
 * может ставить/снимать подписки, менять курсор и т.п.
 */
public interface Overlay {

    OverlayLayer layer();

    /**
     * Если true — все input-события останавливаются на этом overlay'е.
     * Используется модальными диалогами.
     */
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

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
}