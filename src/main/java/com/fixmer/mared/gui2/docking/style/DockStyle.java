package com.fixmer.mared.gui2.docking.style;

/**
 * Параметры Dock интерфейса MaRed.
 *
 * 0.3.0: все «магические» числа из DockPanelRenderer переехали сюда —
 * чтобы смена стиля была в одном месте.
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

    /** Отступ заголовка от левого края панели. */
    public static final int TITLE_OFFSET_X = 16;

    /** Вертикальный отступ заголовка от верха панели. */
    public static final int TITLE_OFFSET_Y = 8;

    // ============================================================
    //  Accent indicator (цветная полоска слева от заголовка)
    // ============================================================

    public static final int ACCENT_INDICATOR_X = 6;
    public static final int ACCENT_INDICATOR_Y = 7;
    public static final int ACCENT_INDICATOR_W = 3;
    public static final int ACCENT_INDICATOR_H = 13;

    // ============================================================
    //  Кнопки в header
    // ============================================================

    /** Отступ меню-кнопки (⋮) от правого края. */
    public static final int MENU_BUTTON_OFFSET_RIGHT = 30;
    public static final int MENU_BUTTON_OFFSET_Y     = 5;

    /** Отступ close-кнопки (×) от правого края. */
    public static final int CLOSE_BUTTON_OFFSET_RIGHT = 16;
    public static final int CLOSE_BUTTON_OFFSET_Y     = 5;
}