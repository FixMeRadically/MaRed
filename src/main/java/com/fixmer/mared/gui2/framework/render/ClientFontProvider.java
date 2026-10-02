package com.fixmer.mared.gui2.framework.render;

import net.minecraft.client.Minecraft;

/**
 * Реализация FontProvider для Minecraft-клиента.
 *
 * Единственное место в gui2.framework.render, знающее про Minecraft.
 * Никакие методы наружу Minecraft-типы не возвращают.
 */
public final class ClientFontProvider implements FontProvider {

    @Override
    public int width(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Minecraft.getInstance().font.width(text);
    }

    @Override
    public int lineHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }
}