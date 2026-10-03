package com.fixmer.mared.gui2.framework.render.patterns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Кэш прямоугольников для процедурных patterns.
 *
 * 0.3.2 (performance):
 *   - grid/dots/diamonds/diagonal перестают генерироваться заново
 *     каждый кадр. Key = (pattern, w, h, параметры, color).
 *   - clear() — вызывается при смене темы, чтобы не рисовать
 *     устаревшие цвета.
 *
 * Класс public: MaredThemeRegistry.setActive() сбрасывает кэш.
 * Остальные методы — package-private, используются только внутри
 * patterns/*.
 */
public final class PatternCache {

    private PatternCache() {}

    private static final int MAX_ENTRIES = 64;

    private static final Map<Long, int[]> CACHE =
        new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
                return size() > MAX_ENTRIES;
            }
        };

    static synchronized int[] get(long key, Supplier<int[]> compute) {
        int[] cached = CACHE.get(key);
        if (cached != null) return cached;
        int[] computed = compute.get();
        CACHE.put(key, computed);
        return computed;
    }

    static long key(Object... parts) {
        long h = 17L;
        for (Object p : parts) {
            h = h * 31L + (p == null ? 0 : p.hashCode());
        }
        return h;
    }

    static final int STRIDE = 5;

    static void blit(GuiGraphics g, int dx, int dy, int[] rects) {
        int n = rects.length;
        for (int i = 0; i < n; i += STRIDE) {
            g.fill(dx + rects[i],     dy + rects[i + 1],
                   dx + rects[i + 2], dy + rects[i + 3],
                   rects[i + 4]);
        }
    }

    static int put(int[] buf, int idx, int x1, int y1, int x2, int y2, int color) {
        buf[idx]     = x1;
        buf[idx + 1] = y1;
        buf[idx + 2] = x2;
        buf[idx + 3] = y2;
        buf[idx + 4] = color;
        return idx + STRIDE;
    }

    static int[] trim(int[] buf, int used) {
        if (used == buf.length) return buf;
        int[] out = new int[used];
        System.arraycopy(buf, 0, out, 0, used);
        return out;
    }

    /** Полная очистка. Вызывается при смене активной темы. */
    public static synchronized void clear() {
        CACHE.clear();
    }

    public static synchronized int size() {
        return CACHE.size();
    }
}