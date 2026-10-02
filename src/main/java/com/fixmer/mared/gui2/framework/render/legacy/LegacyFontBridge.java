package com.fixmer.mared.gui2.framework.render.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * Мост между gui2 и legacy-компонентами, требующими raw Font.
 *
 * 0.3.0: единственное легальное место, где новый код может получить
 * Minecraft Font. Нужно только для legacy MaredLogPanel, пока он не
 * переписан. После Console rewrite (0.3.5) — удалить.
 *
 * ПРАВИЛО: любой новый код, использующий этот класс, должен иметь
 * причину. Если причина "мне лень" — использовать FontProvider.
 */
public final class LegacyFontBridge {

    private LegacyFontBridge() {}

    /**
     * Raw Minecraft Font. Не кэшировать — Font может меняться при
     * смене языка / ресурсов.
     */
    public static Font font() {
        return Minecraft.getInstance().font;
    }
}