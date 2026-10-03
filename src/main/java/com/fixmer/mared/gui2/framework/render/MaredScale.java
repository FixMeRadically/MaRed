package com.fixmer.mared.gui2.framework.render;

import com.fixmer.mared.gui2.framework.core.UiContext;

/**
 * Универсальный масштабировщик — статический фасад над "текущим"
 * UiContext экрана.
 *
 * 0.3.0 (Phase A): введён bridge над UiContext.
 * 0.3.1: 
 *   - context() больше не создаёт fallback-контекст. Если bind() не
 *     был вызван — IllegalStateException. Раньше fallback (1920×1080,
 *     scale=1) молча маскировал ошибку: scaling "работал", но не
 *     реагировал на реальное разрешение.
 *   - update(guiW, guiH) удалён. Один путь: bind() с готовым UiContext.
 *   - unbind() сделан явным. Раньше после removed() контекст оставался
 *     от предыдущего экрана — следующий Screen начинал рендер с
 *     чужими размерами, пока не вызовет update.
 *
 * @deprecated after 0.4.0 — новый код должен принимать UiContext явно
 * (через MaredRenderContext или параметр). Фасад удаляется вместе с
 * MaredDraw/MaredText/MaredWidgets.
 */
@Deprecated
public final class MaredScale {

    private MaredScale() {}

    public static final int   BASE_W = UiContext.BASE_W;
    public static final int   BASE_H = UiContext.BASE_H;
    public static final float SCALE_MIN = UiContext.SCALE_MIN;
    public static final float SCALE_MAX = UiContext.SCALE_MAX;

    public static final int THRESHOLD_COMPACT = UiContext.THRESHOLD_COMPACT;
    public static final int THRESHOLD_NARROW  = UiContext.THRESHOLD_NARROW;
    public static final int THRESHOLD_TINY    = UiContext.THRESHOLD_TINY;

    private static volatile UiContext current;

    /**
     * 0.3.1: bind обязателен перед первым использованием MaredScale.
     * Вызывается в Screen.init().
     */
    public static void bind(UiContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("UiContext must not be null");
        }
        current = ctx;
    }

    /**
     * 0.3.1: unbind обязателен в Screen.removed(). Иначе следующий
     * экран унаследует чужой контекст.
     */
    public static void unbind() {
        current = null;
    }

    /**
     * 0.3.1: без fallback. Если bind не был вызван — это ошибка
     * жизненного цикла, не нормальная ситуация.
     */
    public static UiContext context() {
        UiContext c = current;
        if (c == null) {
            throw new IllegalStateException(
                "UiContext not bound. Call MaredScale.bind(ctx) in Screen.init()");
        }
        return c;
    }

    /** true, если контекст привязан. Для диагностики. */
    public static boolean isBound() {
        return current != null;
    }

    public static int screenW() { return context().screenW(); }
    public static int screenH() { return context().screenH(); }
    public static int physW()   { return context().physW(); }
    public static int physH()   { return context().physH(); }
    public static int mcGuiScale() { return context().mcGuiScale(); }
    public static float scale() { return context().scale(); }

    public static UiContext.Level level() { return context().level(); }
    public static boolean isFull()    { return context().isFull(); }
    public static boolean isCompact() { return context().isCompact(); }
    public static boolean isNarrow()  { return context().isNarrow(); }
    public static boolean isTiny()    { return context().isTiny(); }

    public static int px(int base)        { return context().px(base); }
    public static int pxMin(int b, int m) { return context().pxMin(b, m); }
    public static int pxMax(int b, int m) { return context().pxMax(b, m); }
    public static int pxClamp(int b, int minPx, int maxPx) {
        return context().pxClamp(b, minPx, maxPx);
    }

    public static int percentW(float pct) { return context().percentW(pct); }
    public static int percentH(float pct) { return context().percentH(pct); }

    public static int propW(float pct, int minBase, int maxBase) {
        return context().propW(pct, minBase, maxBase);
    }
    public static int propH(float pct, int minBase, int maxBase) {
        return context().propH(pct, minBase, maxBase);
    }
}