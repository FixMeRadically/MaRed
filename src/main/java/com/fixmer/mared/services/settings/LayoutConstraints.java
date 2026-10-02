package com.fixmer.mared.services.settings;

/**
 * 0.3.0 (Phase F3b): единый источник min/max для layout-размеров.
 * Используется и SettingsService (валидация), и UI (слайдеры).
 * До этого service допускал 80, а UI слайдер начинался с 140 —
 * пользователь не мог выставить допустимые значения.
 */
public final class LayoutConstraints {

    private LayoutConstraints() {}

    public static final int SIDEBAR_MIN    = 140;
    public static final int SIDEBAR_MAX    = 400;
    public static final int SIDEBAR_DEFAULT = 220;

    public static final int RIGHT_PANEL_MIN = 220;
    public static final int RIGHT_PANEL_MAX = 620;
    public static final int RIGHT_PANEL_DEFAULT = 320;

    public static final int CONSOLE_MIN    = 60;
    public static final int CONSOLE_MAX    = 300;
    public static final int CONSOLE_DEFAULT = 140;

    public static int clampSidebar(int v) {
        return Math.max(SIDEBAR_MIN, Math.min(SIDEBAR_MAX, v));
    }
    public static int clampRightPanel(int v) {
        return Math.max(RIGHT_PANEL_MIN, Math.min(RIGHT_PANEL_MAX, v));
    }
    public static int clampConsole(int v) {
        return Math.max(CONSOLE_MIN, Math.min(CONSOLE_MAX, v));
    }
}