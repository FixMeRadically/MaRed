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
 * v12: расширен width/height/mouseX/mouseY, чтобы MaredSpace мог
 * рендерить внутри без доступа к Screen. Конструкторы совместимы
 * с двумя формами:
 *   new MaredRenderContext(graphics)                 — старый путь;
 *   new MaredRenderContext(graphics, mx, my, w, h)   — новый.
 */
public final class MaredRenderContext {

    private final GuiGraphics graphics;
    private final Font font;
    private final FontProvider fontProvider;
    private final int mouseX;
    private final int mouseY;
    private final int width;
    private final int height;

    public MaredRenderContext(GuiGraphics graphics) {
        this(graphics,
            (int) 0, (int) 0,
            graphics != null ? graphics.guiWidth()  : 0,
            graphics != null ? graphics.guiHeight() : 0);
    }

    public MaredRenderContext(GuiGraphics graphics,
                              int mouseX, int mouseY,
                              int width, int height) {
        this.graphics = graphics;
        this.font = Minecraft.getInstance().font;
        this.fontProvider = Fonts.provider();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.width  = width  > 0 ? width  : (graphics != null ? graphics.guiWidth()  : 0);
        this.height = height > 0 ? height : (graphics != null ? graphics.guiHeight() : 0);
    }

    public GuiGraphics graphics() { return graphics; }
    public Font font() { return font; }
    public FontProvider fontProvider() { return fontProvider; }

    public int mouseX() { return mouseX; }
    public int mouseY() { return mouseY; }
    public int width()  { return width; }
    public int height() { return height; }

    /** Активная тема. Никогда не null. */
    public MaredTheme theme() { return MaredThemeRegistry.active(); }
}