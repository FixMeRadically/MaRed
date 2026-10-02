package com.fixmer.mared.gui2.framework.render;

/**
 * Цветовые утилиты gui2.
 *
 * 0.3.0: перенос из gui.common.MaredColor. Пакет другой —
 * чтобы gui2 не зависел от legacy gui.common.
 */
public final class MaredColor {

    private MaredColor() {}

    public static int lighten(int color, float amount) {
        int a = (color >>> 24);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return (a << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    public static int darken(int color, float amount) {
        int a = (color >>> 24);
        int r = (int) (((color >> 16) & 0xFF) * (1 - amount));
        int g = (int) (((color >> 8) & 0xFF) * (1 - amount));
        int b = (int) ((color & 0xFF) * (1 - amount));
        return (a << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    public static int withAlpha(int color, int alpha) {
        return (clamp255(alpha) << 24) | (color & 0x00FFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        if (t < 0) t = 0; else if (t > 1) t = 1;
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    public static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    public static float clamp01(float t) {
        if (t < 0f) return 0f;
        if (t > 1f) return 1f;
        return t;
    }
}