package com.fixmer.mared.gui2.framework.core;

import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

/**
 * Пери-экранный UI-контекст.
 *
 * 0.3.0 (Phase A): введён, чтобы убрать скрытое глобальное состояние
 * из MaredScale и MaredAnimState. Один экран = один UiContext.
 *
 * Владелец — MaredStudioScreen (или другой Screen). Создаётся в init(),
 * уничтожается в removed(). Компоненты получают контекст через
 * render(MaredRenderContext), который внутри держит ссылку на UiContext.
 *
 * До 0.4.0 статический фасад MaredScale делегирует к "текущему"
 * контексту (для совместимости с сотнями call-site). С 0.4.0 фасад
 * удаляется, UiContext передаётся явно.
 */
public final class UiContext {

    public enum Level { FULL, COMPACT, NARROW, TINY }

    public static final int   BASE_W = 1920;
    public static final int   BASE_H = 1080;
    public static final float SCALE_MIN = 0.55f;
    public static final float SCALE_MAX = 2.00f;
    public static final int   THRESHOLD_COMPACT = 1440;
    public static final int   THRESHOLD_NARROW  = 1024;
    public static final int   THRESHOLD_TINY    =  800;

    private final int screenW;
    private final int screenH;
    private final int physW;
    private final int physH;
    private final float scale;
    private final Level level;
    private final AnimationClock clock;
    private final MaredTheme theme;

    public UiContext(int guiW, int guiH, int physW, int physH) {
        this(guiW, guiH, physW, physH, MaredThemeRegistry.active());
    }

    public UiContext(int guiW, int guiH, int physW, int physH, MaredTheme theme) {
        this.screenW = Math.max(320, guiW);
        this.screenH = Math.max(240, guiH);
        this.physW   = physW > 0 ? physW : guiW;
        this.physH   = physH > 0 ? physH : guiH;
        this.theme   = theme != null ? theme : MaredThemeRegistry.active();
        this.clock   = new AnimationClock();

        float sw = this.physW / (float) BASE_W;
        float sh = this.physH / (float) BASE_H;
        this.scale = clamp(SCALE_MIN, Math.min(sw, sh), SCALE_MAX);

        if      (this.physW >= THRESHOLD_COMPACT) this.level = Level.FULL;
        else if (this.physW >= THRESHOLD_NARROW)  this.level = Level.COMPACT;
        else if (this.physW >= THRESHOLD_TINY)    this.level = Level.NARROW;
        else                                      this.level = Level.TINY;
    }

    public int screenW()  { return screenW; }
    public int screenH()  { return screenH; }
    public int physW()    { return physW; }
    public int physH()    { return physH; }
    public float scale()  { return scale; }
    public Level level()  { return level; }
    public AnimationClock clock() { return clock; }
    public MaredTheme theme() { return theme; }

    public boolean isFull()    { return level == Level.FULL; }
    public boolean isCompact() { return level == Level.COMPACT; }
    public boolean isNarrow()  { return level == Level.NARROW; }
    public boolean isTiny()    { return level == Level.TINY; }

    public int px(int base)    { return Math.max(1, Math.round(base * scale)); }
    public int pxMin(int b, int m) { return Math.max(m, px(b)); }
    public int pxMax(int b, int m) { return Math.min(m, px(b)); }

    public int pxClamp(int b, int minPx, int maxPx) {
        int v = px(b);
        return v < minPx ? minPx : (v > maxPx ? maxPx : v);
    }

    public int percentW(float pct) { return Math.round(screenW * pct); }
    public int percentH(float pct) { return Math.round(screenH * pct); }

    public int propW(float pct, int minBase, int maxBase) {
        int raw = Math.round(screenW * pct);
        return clamp(px(minBase), raw, px(maxBase));
    }

    public int propH(float pct, int minBase, int maxBase) {
        int raw = Math.round(screenH * pct);
        return clamp(px(minBase), raw, px(maxBase));
    }

    private static int clamp(int min, int v, int max) {
        return v < min ? min : (v > max ? max : v);
    }
    private static float clamp(float min, float v, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}