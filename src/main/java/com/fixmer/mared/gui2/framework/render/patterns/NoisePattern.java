package com.fixmer.mared.gui2.framework.render.patterns;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Шумовые узоры.
 *
 * 0.3.2: area cap. Noise генерирует (w/step)×(h/step) прямоугольников
 * на кадр — на full-screen panel это ~2000+ fill'ов. Ограничиваем
 * площадь генерации до PATTERN_MAX_AREA.
 *
 * Детерминированный seeded-шум — узор не дрожит между кадрами.
 */
public final class NoisePattern {

    private NoisePattern() {}

    /** Максимальная площадь (в px²) для процедурной генерации. */
    static final long PATTERN_MAX_AREA = 200_000L;

    public static void noise(GuiGraphics g, int x, int y, int w, int h,
                             int seed, float density, int alpha) {
        if (w <= 0 || h <= 0) return;
        if ((long) w * h > PATTERN_MAX_AREA) return;

        int step = Math.max(2, (int) (1f / Math.max(0.01f, density)));
        int a = PatternUtils.clamp255(alpha);
        for (int px = 0; px < w; px += step) {
            for (int py = 0; py < h; py += step) {
                int hash = PatternUtils.hash(px + x, py + y, seed);
                int v = hash & 0xFF;
                if (v < 76) {
                    g.fill(x + px, y + py, x + px + 1, y + py + 1,
                        (a << 24) | 0x00FFFFFF);
                } else if (v > 200) {
                    g.fill(x + px, y + py, x + px + 1, y + py + 1,
                        ((a / 2) << 24));
                }
            }
        }
    }

    public static void paperTexture(GuiGraphics g, int x, int y, int w, int h,
                                    int seed, int intensity) {
        if (w <= 0 || h <= 0) return;
        if ((long) w * h > PATTERN_MAX_AREA) return;

        int a = Math.min(60, Math.max(5, intensity));
        for (int px = 0; px < w; px += 2) {
            for (int py = 0; py < h; py += 2) {
                int hash = PatternUtils.hash(px + x, py + y, seed);
                if ((hash & 0xFF) < 40) {
                    int v = ((hash >> 8) & 0xFF) > 128 ? 0xFFFFFF : 0x000000;
                    g.fill(x + px, y + py, x + px + 1, y + py + 1,
                        (a << 24) | v);
                }
            }
        }
    }

    public static void tornCorner(GuiGraphics g, int x, int y, int w, int h,
                                  int corner, int size, int seed, int color) {
        if (size <= 0) return;
        if ((long) w * h > PATTERN_MAX_AREA) return;

        int cx = switch (corner) {
            case 1 -> x + w - size;
            case 2 -> x;
            case 3 -> x + w - size;
            default -> x;
        };
        int cy = switch (corner) {
            case 2, 3 -> y + h - size;
            default -> y;
        };
        int step = Math.max(1, size / 12);
        for (int px = 0; px < size; px += step) {
            for (int py = 0; py < size; py += step) {
                int distToCorner = switch (corner) {
                    case 0 -> px + py;
                    case 1 -> (size - px) + py;
                    case 2 -> px + (size - py);
                    default -> (size - px) + (size - py);
                };
                if (distToCorner > size) continue;
                int hash = PatternUtils.hash(cx + px, cy + py, seed);
                if ((hash & 0xFF) < 60) {
                    g.fill(cx + px, cy + py, cx + px + 2, cy + py + 2, color);
                }
            }
        }
    }
}