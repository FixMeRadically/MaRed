package com.fixmer.mared.gui2.framework.render;

/**
 * Абстракция над источником шрифта.
 *
 * 0.3.0: чистый контракт. Никаких Minecraft-типов в интерфейсе.
 * Компоненты получают FontProvider через MaredRenderContext.
 *
 * Для legacy-компонентов (MaredLogPanel), которые требуют
 * net.minecraft.client.gui.Font — использовать LegacyFontBridge
 * из gui2.framework.render.legacy. Он единственный знает про Minecraft.
 */
public interface FontProvider {

    /** Ширина строки в пикселях. */
    int width(String text);

    /** Высота строки шрифта. */
    int lineHeight();
}