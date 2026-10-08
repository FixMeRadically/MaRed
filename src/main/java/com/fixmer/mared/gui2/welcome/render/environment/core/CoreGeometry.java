package com.fixmer.mared.gui2.welcome.render.environment.core;

import net.minecraft.client.gui.GuiGraphics;

public final class CoreGeometry {

    private CoreGeometry() {}

    public static final float[] POLYGON_ANGLES = {
        0.00f, 0.91f, 1.85f, 2.75f, 3.80f, 4.55f, 5.65f
    };

    /** РљР»Р°СЃСЃРёС‡РµСЃРєРёР№ smoothstep. РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РІРµР·РґРµ РґР»СЏ easing. */
    public static float smoothstep(float t) {
        if (t < 0f) return 0f;
        if (t > 1f) return 1f;
        return t * t * (3f - 2f * t);
    }

    public static float vertexWobble(int index, float timeSec) {
        float freq  = 0.40f + index * 0.05f;
        float phase = index * 2.4f;
        return (float)Math.sin(timeSec * freq + phase) * 0.6f
             + (float)Math.sin(timeSec * freq * 1.7f + phase * 1.3f) * 0.4f;
    }

    public static float[] polygonVertex(int index, float R,
                                        float timeSec, float wobble) {
        float angle = POLYGON_ANGLES[index % POLYGON_ANGLES.length];
        float r = R + vertexWobble(index, timeSec) * wobble;
        return new float[]{
            (float)(Math.cos(angle) * r),
            (float)(Math.sin(angle) * r)
        };
    }

    public static float[] orbitPoint(float radius, float squashY, float theta) {
        return new float[]{
            (float)(Math.cos(theta) * radius),
            (float)(Math.sin(theta) * radius * squashY)
        };
    }

    public static void drawPolygonOutline(GuiGraphics g,
                                          int[] xs, int[] ys,
                                          int color, int thickness) {
        int n = xs.length;
        if (n < 3) return;
        int t = Math.max(1, thickness);

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            drawLine(g, xs[i], ys[i], xs[j], ys[j], color, t);
        }
    }

    public static void fillPolygon(GuiGraphics g,
                                   int[] xs, int[] ys,
                                   int color) {
        int n = xs.length;
        if (n < 3) return;

        int yMin = ys[0], yMax = ys[0];
        for (int i = 1; i < n; i++) {
            if (ys[i] < yMin) yMin = ys[i];
            if (ys[i] > yMax) yMax = ys[i];
        }

        for (int y = yMin; y <= yMax; y++) {
            int leftX  = Integer.MAX_VALUE;
            int rightX = Integer.MIN_VALUE;

            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                int y1 = ys[i], y2 = ys[j];
                if ((y1 <= y && y2 > y) || (y2 <= y && y1 > y)) {
                    float t = (float)(y - y1) / (y2 - y1);
                    int x = (int)(xs[i] + t * (xs[j] - xs[i]));
                    if (x < leftX)  leftX  = x;
                    if (x > rightX) rightX = x;
                }
            }

            if (leftX <= rightX) {
                g.fill(leftX, y, rightX + 1, y + 1, color);
            }
        }
    }

    public static void fillDisc(GuiGraphics g,
                                int cx, int cy, int radius,
                                int rgb, int maxAlpha, int rowStep) {
        if (radius <= 0) return;
        long r2 = (long) radius * radius;
        for (int dy = -radius; dy <= radius; dy += rowStep) {
            long d2 = (long) dy * dy;
            if (d2 > r2) continue;
            int half = (int) Math.sqrt((double)(r2 - d2));
            float dNorm = (float) Math.abs(dy) / radius;
            float falloff = 1f - dNorm;
            falloff *= falloff;
            int alpha = (int)(falloff * maxAlpha);
            if (alpha <= 0) continue;
            g.fill(cx - half, cy + dy, cx + half, cy + dy + rowStep,
                   (alpha << 24) | rgb);
        }
    }

    public static void drawSoftEllipse(GuiGraphics g,
                                       int cx, int cy,
                                       int rx, int ry,
                                       int rgb, int rings, int totalAlpha) {
        if (rx <= 0 || ry <= 0 || rings <= 0) return;
        int perRing = totalAlpha / rings;
        if (perRing <= 0) return;
        int color = (perRing << 24) | (rgb & 0x00FFFFFF);

        for (int k = 0; k < rings; k++) {
            float frac = 1f - k / (float) rings;
            int crx = (int)(rx * frac);
            int cry = (int)(ry * frac);
            if (crx <= 0 || cry <= 0) continue;
            fillEllipse(g, cx, cy, crx, cry, 2, color);
        }
    }

    public static void fillEllipse(GuiGraphics g,
                                   int cx, int cy,
                                   int rx, int ry,
                                   int rowStep, int color) {
        if (rx <= 0 || ry <= 0) return;
        long ry2 = (long) ry * ry;
        for (int dy = -ry; dy <= ry; dy += rowStep) {
            long d2 = (long) dy * dy;
            if (d2 > ry2) continue;
            double ratio = 1.0 - (double) d2 / (double) ry2;
            if (ratio < 0.0) continue;
            int half = (int)(rx * Math.sqrt(ratio));
            if (half <= 0) continue;
            g.fill(cx - half, cy + dy, cx + half, cy + dy + rowStep, color);
        }
    }

    public static void drawEllipseOutline(GuiGraphics g,
                                          int cx, int cy,
                                          int rx, int ry, int color) {
        if (rx <= 0 || ry <= 0) return;
        long ry2 = (long) ry * ry;
        for (int dy = -ry; dy <= ry; dy++) {
            long d2 = (long) dy * dy;
            if (d2 > ry2) continue;
            double ratio = 1.0 - (double) d2 / (double) ry2;
            if (ratio < 0.0) continue;
            int half = (int)(rx * Math.sqrt(ratio));
            g.fill(cx - half,     cy + dy, cx - half + 1, cy + dy + 1, color);
            g.fill(cx + half - 1, cy + dy, cx + half,     cy + dy + 1, color);
        }
    }

    public static void drawDiamond(GuiGraphics g, int x, int y,
                                   int size, int color) {
        for (int i = 0; i < size; i++) {
            g.fill(x - i, y - (size - i), x + i + 1, y - (size - i) + 1, color);
            g.fill(x - i, y + (size - i) - 1, x + i + 1, y + (size - i), color);
        }
    }

    public static void drawLine(GuiGraphics g,
                                int x1, int y1, int x2, int y2,
                                int color, int thickness) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int t = Math.max(1, thickness);
        int half = t / 2;

        while (true) {
            if (t == 1) {
                g.fill(x1, y1, x1 + 1, y1 + 1, color);
            } else {
                g.fill(x1 - half, y1 - half,
                       x1 - half + t, y1 - half + t, color);
            }
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 <  dx) { err += dx; y1 += sy; }
        }
    }
}