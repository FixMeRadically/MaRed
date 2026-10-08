package com.fixmer.mared.gui2.framework.render;

/**
 * Framework-wide palette constants shared by framework renderers
 * (UiControls, UiPanels) and legacy facades (MaredUi).
 *
 * Breaks MaredUi <-> UiControls/UiPanels import cycle.
 */
public final class FrameworkPalette {

    private FrameworkPalette() {}

    public static int SCREEN_BG(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgScreen;}
    public static int PANEL_BG(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgPanel;}
    public static int PANEL_RAISED(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgPanelRaised;}
    public static int SUNKEN_BG(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgSunken;}
    public static int TEXT(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().text;}
    public static int TEXT_DIM(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().textDim;}
    public static int DANGER(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().danger;}
    public static int SUCCESS(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().success;}
    public static int WARN(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().warn;}
    public static int ACCENT_BLUE(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().accent;}
    public static int ACCENT_PURPLE(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().accentAlt;}
    public static int DIVIDER(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().divider;}
    public static int SCROLL_TRACK(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().scrollTrack;}


    public static final int SCREEN_BG     = 0xFF0A0A10;
    public static final int PANEL_BG      = 0xFF14141C;
    public static final int PANEL_RAISED  = 0xFF1A1A24;
    public static final int SUNKEN_BG     = 0xFF0A0A10;
    public static final int TEXT          = 0xFFFFFFFF;
    public static final int TEXT_DIM      = 0xFFAAAAAA;
    public static final int DANGER        = 0xFFFF4444;
    public static final int SUCCESS       = 0xFF55FF88;
    public static final int WARN          = 0xFFFFAA00;
    public static final int ACCENT_BLUE   = 0xFF55AAFF;
    public static final int ACCENT_PURPLE = 0xFFFF55FF;
    public static final int DIVIDER       = 0xFF333344;
    public static final int SCROLL_TRACK  = 0xFF15151E;
}