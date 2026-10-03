package com.fixmer.mared.gui2.framework.core;

import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

/**
 * Пери-экранный UI-контекст.
 *
 * 0.3.0 (Phase A): введён, чтобы убрать скрытое глобальное состояние из
 * MaredScale и MaredAnimState. Один экран = один UiContext.
 *
 * 0.3.1 (реальное подключение):
 *   - Убран floor на screenW/screenH через max(320, ...) — layout получал
 *     виртуальный размер и располагал элементы за пределами реального
 *     окна. Теперь screenW/screenH — точные значения из Screen.width/height.
 *   - px(0) возвращает 0 (не 1). Подмена нуля единицей ломала семантику
 *     универсального scale-primitive: layout с padding=0 получал padding=1.
 *     Минимумы применяются отдельно через pxMin().
 *   - scale считается от физического разрешения, но НЕ как
 *     physW/BASE_W напрямую — учитываем, что MC уже применил свой
 *     guiScale. Формула: scale = physW / (BASE_W * mcGuiScale) —
 *     это устраняет двойное масштабирование при guiScale>1.
 *
 * Владелец — MaredStudioScreen (или другой Screen). Создаётся в init(),
 * разрушается в removed().
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
    private final int mcGuiScale;
    private final float scale;
    private final Level level;
    private final AnimationClock clock;
    private final MaredTheme theme;

    /**
     * @param guiW        Screen.width  (уже в GUI-координатах после MC guiScale)
     * @param guiH        Screen.height
     * @param physW       физическая ширина окна (Window.getWidth())
     * @param physH       физическая высота окна
     * @param mcGuiScale  Minecraft Window.getGuiScale() — 1,2,3,4
     */
    public UiContext(int guiW, int guiH,
                     int physW, int physH,
                     int mcGuiScale) {
        this(guiW, guiH, physW, physH, mcGuiScale, MaredThemeRegistry.active());
    }

    public UiContext(int guiW, int guiH,
                     int physW, int physH,
                     int mcGuiScale,
                     MaredTheme theme) {
        this.screenW = Math.max(1, guiW);
        this.screenH = Math.max(1, guiH);
        this.physW   = physW > 0 ? physW : guiW;
        this.physH   = physH > 0 ? physH : guiH;
        this.mcGuiScale = Math.max(1, mcGuiScale);
        this.theme   = theme != null ? theme : MaredThemeRegistry.active();
        this.clock   = new AnimationClock();

        // 0.3.1: убираем двойное масштабирование.
        // Если MC уже отдал нам GUI-space в mcGuiScale раз меньший, то
        // сравнивать physW с BASE_W напрямую нельзя — получим layout,
        // который в GUI-координатах выглядит в mcGuiScale раз больше
        // задуманного.
        float effectivePhysicalW = this.physW / (float) this.mcGuiScale;
        float effectivePhysicalH = this.physH / (float) this.mcGuiScale;

        float sw = effectivePhysicalW / (float) BASE_W;
        float sh = effectivePhysicalH / (float) BASE_H;
        this.scale = clamp(SCALE_MIN, Math.min(sw, sh), SCALE_MAX);

        if      (this.screenW >= THRESHOLD_COMPACT) this.level = Level.FULL;
        else if (this.screenW >= THRESHOLD_NARROW)  this.level = Level.COMPACT;
        else if (this.screenW >= THRESHOLD_TINY)    this.level = Level.NARROW;
        else                                        this.level = Level.TINY;
    }

    public int screenW()     { return screenW; }
    public int screenH()     { return screenH; }
    public int physW()       { return physW; }
    public int physH()       { return physH; }
    public int mcGuiScale()  { return mcGuiScale; }
    public float scale()     { return scale; }
    public Level level()     { return level; }
    public AnimationClock clock() { return clock; }
    public MaredTheme theme() { return theme; }

    public boolean isFull()    { return level == Level.FULL; }
    public boolean isCompact() { return level == Level.COMPACT; }
    public boolean isNarrow()  { return level == Level.NARROW; }
    public boolean isTiny()    { return level == Level.TINY; }

    /**
     * 0.3.1: px(0) == 0.
     * Раньше через Math.max(1, ...) ноль превращался в единицу, что
     * ломало layout с padding=0 и создавало "фантомные" отступы.
     */
    public int px(int base) {
        if (base == 0) return 0;
        return Math.round(base * scale);
    }

    /** px с явным минимумом. */
    public int pxMin(int base, int minPx) {
        int v = px(base);
        return v < minPx ? minPx : v;
    }

    public int pxMax(int base, int maxPx) {
        int v = px(base);
        return v > maxPx ? maxPx : v;
    }

    public int pxClamp(int base, int minPx, int maxPx) {
        int v = px(base);
        if (v < minPx) return minPx;
        if (v > maxPx) return maxPx;
        return v;
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