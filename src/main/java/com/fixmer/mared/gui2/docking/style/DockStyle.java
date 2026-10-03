package com.fixmer.mared.gui2.docking.style;

/**
 * Параметры Dock интерфейса MaRed.
 *
 * 0.3.0: все «магические» числа из DockPanelRenderer переехали сюда.
 * 0.3.2: константы для collapsed-режима.
 */
public final class DockStyle {

    private DockStyle() {}

    // ============================================================
    //  Общие
    // ============================================================

    public static final int HEADER_HEIGHT = 32;
    public static final int PADDING       = 8;
    public static final int BORDER_SIZE   = 1;
    public static final int ACCENT_HEIGHT = 2;

    // ============================================================
    //  Header
    // ============================================================

    public static final int TITLE_OFFSET_X = 16;
    public static final int TITLE_OFFSET_Y = 8;

    // ============================================================
    //  Accent indicator
    // ============================================================

    public static final int ACCENT_INDICATOR_X = 6;
    public static final int ACCENT_INDICATOR_Y = 7;
    public static final int ACCENT_INDICATOR_W = 3;
    public static final int ACCENT_INDICATOR_H = 13;

    // ============================================================
    //  Кнопки в header
    // ============================================================

    public static final int MENU_BUTTON_OFFSET_RIGHT = 30;
    public static final int MENU_BUTTON_OFFSET_Y     = 5;

    public static final int CLOSE_BUTTON_OFFSET_RIGHT = 16;
    public static final int CLOSE_BUTTON_OFFSET_Y     = 5;

    // ============================================================
    //  Collapse (0.3.2)
    // ============================================================

    /** Ширина свёрнутой LEFT/RIGHT-панели. */
    public static final int COLLAPSED_SIZE_SIDE = 28;

    /** Высота свёрнутой BOTTOM-панели. */
    public static final int COLLAPSED_SIZE_BOTTOM = 22;

    /** Хит-зона стрелки collapse в header'е. */
    public static final int COLLAPSE_HIT_W = 16;
    public static final int COLLAPSE_HIT_H = HEADER_HEIGHT;

    /**
     * Отступ стрелки от левого края header'а (сразу после
     * accent indicator).
     */
    public static final int COLLAPSE_ARROW_OFFSET_X =
        ACCENT_INDICATOR_X + ACCENT_INDICATOR_W + 2;
}