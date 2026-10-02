package com.fixmer.mared.gui2.framework.core;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;

import com.fixmer.mared.gui2.framework.render.FontProvider;
import com.fixmer.mared.gui2.framework.render.Fonts;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

/**
 * Контекст рендера MaRed.
 *
 * 0.3.0: тема берётся напрямую из MaredThemeRegistry (без ThemeContext).
 */
public final class MaredRenderContext {

    private final GuiGraphics graphics;
    private final Font font;
    private final FontProvider fontProvider;

    public MaredRenderContext(GuiGraphics graphics) {
        this.graphics = graphics;
        this.font = Minecraft.getInstance().font;
        this.fontProvider = Fonts.provider();
    }

    public GuiGraphics graphics() { return graphics; }
    public Font font() { return font; }
    public FontProvider fontProvider() { return fontProvider; }

    /** Активная тема. Никогда не null. */
    public MaredTheme theme() { return MaredThemeRegistry.active(); }
}