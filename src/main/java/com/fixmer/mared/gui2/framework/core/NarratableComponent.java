package com.fixmer.mared.gui2.framework.core;

import net.minecraft.network.chat.Component;

/**
 * Компонент, умеющий озвучить своё состояние для screen reader'а.
 *
 * 0.3.2 (accessibility):
 *   Кастомные компоненты MaRed не наследуют AbstractWidget и
 *   поэтому не участвуют в vanilla narration contract'е. Этот
 *   интерфейс — opt-in: компонент возвращает Component, который
 *   FocusTraversal передаёт в Minecraft narrator при смене фокуса.
 *
 * Не вызывается автоматически на hover — screen reader получает
 * текст только при focus change через Tab/Shift+Tab.
 */
public interface NarratableComponent {

    /**
     * Текст для narrator'а. null или пустая строка — не озвучивать.
     * Не должен бросать исключений.
     */
    Component narrationText();
}