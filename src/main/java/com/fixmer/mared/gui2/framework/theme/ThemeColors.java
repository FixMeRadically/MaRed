package com.fixmer.mared.gui2.framework.theme;

/**
 * Статические шорткаты к активной теме.
 * Для мест без MaredRenderContext.
 */
public final class ThemeColors {

    private ThemeColors() {}

    public static int background() { return MaredThemeRegistry.active().bgScreen; }
    public static int panel()      { return MaredThemeRegistry.active().bgPanel; }
    public static int hover()      { return MaredThemeRegistry.active().bgHover; }
    public static int border()     { return MaredThemeRegistry.active().border; }
    public static int accent()     { return MaredThemeRegistry.active().accent; }
    public static int text()       { return MaredThemeRegistry.active().text; }
    public static int muted()      { return MaredThemeRegistry.active().textDim; }
}