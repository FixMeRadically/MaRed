package com.fixmer.mared.gui.editor;
import com.fixmer.mared.gui.common.MaredLogPanel;

/**
 * Централизованная геометрия редактора + uiScale.
 * Все размеры — пропорциональны от базового разрешения 1920x1080.
 */
public class MaredEditorLayout {

    public static final MaredEditorLayout INSTANCE = new MaredEditorLayout();

    public MaredEditorLayout() {}

    // ---- базовое разрешение ----
    public static final int BASE_W = 1920;
    public static final int BASE_H = 1080;
    public static final float SCALE_MIN = 0.75f;
    public static final float SCALE_MAX = 2.0f;

    // ---- состояние экрана ----
    private static int screenW = BASE_W;
    private static int screenH = BASE_H;
    private static float scale = 1.0f;

    // ---- константы ----
    private static final int BASE_PAD               = 6;
    private static final int BASE_TAB_W             = 30;
    private static final int BASE_TAB_H             = 30;
    private static final int BASE_TOOLBAR_H         = 22;
    private static final int BASE_STRIP_W           = 36;
    private static final int BASE_BTN_SZ            = 18;
    private static final int BASE_ITEM_H            = 14;
    private static final int BASE_SCROLLBAR_W       = 6;
    private static final int BASE_EDITOR_HDR        = 18;
    private static final int BASE_FILE_SECTION_H    = 140;
    private static final int BASE_FILTER_H          = 14;
    private static final int BASE_FILE_DEL_SZ       = 12;
    private static final int BASE_TOGGLE_W          = 22;
    private static final int BASE_TOGGLE_H          = 12;
    private static final int BASE_BIND_DEL_SZ       = 10;
    private static final int BASE_PERSISTENT_STRIPE = 2;
    private static final int BASE_INFO_COLLAPSED_W  = 24;
    private static final int BASE_GAP_EDITOR_LOG    = 6;

    // ---- пропорциональные от ширины ----
    private static final float ACTIVE_BINDS_PCT = 0.16f;
    private static final int   ACTIVE_BINDS_MIN = 180;
    private static final int   ACTIVE_BINDS_MAX = 320;

    private static final float INFO_PCT  = 0.20f;
    private static final int   INFO_MIN  = 240;
    private static final int   INFO_MAX  = 420;

    private static final float SIDEBAR_PCT = 0.16f;
    private static final int   SIDEBAR_MIN = 180;
    private static final int   SIDEBAR_MAX = 320;

    // ---- пропорциональная высота лога ----
    // log H = clamp(screenH * PCT, MIN, MAX) с учётом scale
    private static final float LOG_H_PCT      = 0.20f;   // 20% высоты экрана
    private static final int   LOG_H_MIN_BASE = 90;      // в базовых px
    private static final int   LOG_H_MAX_BASE = 220;

    // ---- цвета границ ----
    public static final int BORDER_TOP    = 0x40FFFFFF;
    public static final int BORDER_BOTTOM = 0x20000000;

    // ============================================================
    //  Обновление
    // ============================================================

    public static void update(int w, int h) {
        screenW = Math.max(320, w);
        screenH = Math.max(240, h);
        float sw = screenW / (float) BASE_W;
        float sh = screenH / (float) BASE_H;
        scale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, Math.min(sw, sh)));
    }

    public static int screenW() { return screenW; }
    public static int screenH() { return screenH; }
    public static float scale() { return scale; }

    public static int px(int base) { return Math.max(1, Math.round(base * scale)); }

    public static int percentW(float pct, int minBase, int maxBase) {
        int raw = Math.round(screenW * pct);
        int min = px(minBase);
        int max = px(maxBase);
        return Math.max(min, Math.min(max, raw));
    }

    // ============================================================
    //  Геттеры
    // ============================================================

    public static int PAD()              { return px(BASE_PAD); }
    public static int TAB_W()            { return px(BASE_TAB_W); }
    public static int TAB_H()            { return px(BASE_TAB_H); }
    public static int TOOLBAR_H()        { return px(BASE_TOOLBAR_H); }
    public static int STRIP_W()          { return px(BASE_STRIP_W); }
    public static int BTN_SZ()           { return px(BASE_BTN_SZ); }
    public static int ITEM_H()           { return px(BASE_ITEM_H); }
    public static int SCROLLBAR_W()      { return px(BASE_SCROLLBAR_W); }
    public static int EDITOR_HDR()       { return px(BASE_EDITOR_HDR); }
    public static int FILE_SECTION_H()   { return px(BASE_FILE_SECTION_H); }
    public static int FILTER_H()         { return px(BASE_FILTER_H); }
    public static int FILE_DEL_SZ()      { return px(BASE_FILE_DEL_SZ); }
    public static int TOGGLE_W()         { return px(BASE_TOGGLE_W); }
    public static int TOGGLE_H()         { return px(BASE_TOGGLE_H); }
    public static int BIND_DEL_SZ()      { return px(BASE_BIND_DEL_SZ); }
    public static int PERSISTENT_STRIPE(){ return px(BASE_PERSISTENT_STRIPE); }
    public static int INFO_COLLAPSED_W() { return px(BASE_INFO_COLLAPSED_W); }
    public static int GAP_EDITOR_LOG()   { return px(BASE_GAP_EDITOR_LOG); }

    public static int ACTIVE_BINDS_W() {
        return percentW(ACTIVE_BINDS_PCT, ACTIVE_BINDS_MIN, ACTIVE_BINDS_MAX);
    }

    public static int INFO_W() {
        return percentW(INFO_PCT, INFO_MIN, INFO_MAX);
    }

    public static int SIDEBAR_W() {
        return percentW(SIDEBAR_PCT, SIDEBAR_MIN, SIDEBAR_MAX);
    }

    public static int logWidth() { return screenW - ACTIVE_BINDS_W() - 1; }

    /** Пропорциональная высота развёрнутого лога. */
    public static int logPanelH() {
        int raw = Math.round(screenH * LOG_H_PCT);
        int min = px(LOG_H_MIN_BASE);
        int max = px(LOG_H_MAX_BASE);
        return Math.max(min, Math.min(max, raw));
    }

    public static int logHeight(boolean collapsed) {
        return collapsed ? MaredLogPanel.LOG_HEADER : logPanelH();
    }

    public static int logTop(boolean collapsed) {
        return screenH - logHeight(collapsed);
    }

    public static int sidebarBottom(boolean collapsed) {
        return logTop(collapsed);
    }

    public static int editorBottom(boolean collapsed) {
        return logTop(collapsed) - GAP_EDITOR_LOG();
    }

    public static int infoPanelX(boolean collapsed) {
        return collapsed ? screenW - INFO_COLLAPSED_W() : screenW - INFO_W();
    }

    public static int infoWidth(boolean collapsed, boolean visible) {
        if (!visible) return 0;
        return collapsed ? INFO_COLLAPSED_W() : INFO_W();
    }

    public static int arrowY() {
        return PAD() + 1;
    }

    public static int sidebarWidth(SidebarState state, boolean supported) {
        if (!supported) return 0;
        return switch (state) {
            case STRIP -> STRIP_W();
            case FULL -> SIDEBAR_W();
            default -> 0;
        };
    }

    public enum SidebarState { CLOSED, STRIP, FULL }
}