package com.fixmer.mared.gui2.framework.render;

import com.fixmer.mared.gui2.framework.core.UiContext;

import net.minecraft.client.Minecraft;

/**
 * Универсальный масштабировщик.
 *
 * 0.3.0 (Phase A): рефакторинг. Глобальное mutable state удалено —
 * теперь MaredScale это статический фасад над "текущим" UiContext.
 * Сам UiContext создаётся в init() экрана (через update()).
 *
 * @deprecated after 0.4.0 — все call-site должны принимать UiContext
 * явно. Фасад удаляется вместе с MaredDraw/MaredText/MaredWidgets.
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

    /** Привязать контекст. Вызывается в init() экрана. */
    public static void bind(UiContext ctx) {
        if (ctx == null) throw new IllegalArgumentException("UiContext must not be null");
        current = ctx;
    }

    public static void unbind() {
        current = null;
    }

    public static UiContext context() {
        UiContext c = current;
        if (c == null) {
            // Fallback: если кто-то вызвал до bind() — создаём дефолт,
            // чтобы не падать. В нормальном lifecycle этого не происходит.
            c = new UiContext(BASE_W, BASE_H, BASE_W, BASE_H);
            current = c;
        }
        return c;
    }

    /** Совместимость: пересоздать контекст по размерам экрана. */
    public static void update(int guiW, int guiH) {
        int pw = guiW, ph = guiH;
        try {
            var win = Minecraft.getInstance().getWindow();
            pw = win.getWidth();
            ph = win.getHeight();
        } catch (Throwable ignored) {}
        bind(new UiContext(guiW, guiH, pw, ph));
    }

    public static int screenW() { return context().screenW(); }
    public static int screenH() { return context().screenH(); }
    public static int physW()   { return context().physW(); }
    public static int physH()   { return context().physH(); }
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