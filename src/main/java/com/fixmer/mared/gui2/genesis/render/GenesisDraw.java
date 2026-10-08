package com.fixmer.mared.gui2.genesis.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * 1.5.38.1: Р С—РЎР‚Р С‘Р СР С‘РЎвЂљР С‘Р Р†РЎвЂ№ Genesis.
 *
 * Р С™Р В»РЎР‹РЎвЂЎР ВµР Р†РЎвЂ№Р Вµ Р С‘Р В·Р СР ВµР Р…Р ВµР Р…Р С‘РЎРЏ:
 *   - SEG_COUNT Р Т‘Р В»РЎРЏ Р С”Р С•Р В»Р ВµРЎвЂ /Р С•Р С”РЎР‚РЎС“Р В¶Р Р…Р С•РЎРѓРЎвЂљР ВµР в„– = 48 (Р С—Р В»Р В°Р Р†Р Р…Р ВµР Вµ).
 *   - Р вЂўР Т‘Р С‘Р Р…РЎвЂ№Р в„– Р С”РЎРЊРЎв‚¬ COS48/SIN48 РІР‚вЂќ РЎвЂљРЎР‚Р С‘Р С–Р С•Р Р…Р С•Р СР ВµРЎвЂљРЎР‚Р С‘РЎРЏ Р Т‘Р В»РЎРЏ Р С”Р С•Р В»Р ВµРЎвЂ  Р Р…Р Вµ Р С—Р ВµРЎР‚Р ВµРЎРѓРЎвЂЎР С‘РЎвЂљРЎвЂ№Р Р†Р В°Р ВµРЎвЂљРЎРѓРЎРЏ.
 *   - line() Р В±Р С•Р В»РЎРЉРЎв‚¬Р Вµ Р Р…Р Вµ РЎРѓР Р…Р В°Р С—Р С‘РЎвЂљРЎРѓРЎРЏ РІР‚вЂќ Р С—Р В»Р В°Р Р†Р Р…РЎвЂ№Р Вµ Р В»Р С‘Р Р…Р С‘Р С‘ Р В±Р ВµР В· "Р В»РЎС“РЎвЂЎР ВµР в„–".
 *   - pixelLine() Р С•РЎРѓРЎвЂљР В°Р Р†Р В»Р ВµР Р… РЎвЂљР С•Р В»РЎРЉР С”Р С• Р Т‘Р В»РЎРЏ Р С•РЎР‚Р В±Р С‘РЎвЂљ-РЎвЂљР С•РЎвЂЎР ВµР С” Р С‘ beams.
 *   - pixelCircle/pixelSoftCircle Р С‘РЎРѓР С—Р С•Р В»РЎРЉР В·РЎС“Р ВµРЎвЂљ 1-px РЎв‚¬Р В°Р С– Р С—Р С• Y, Р Р…Р С• РЎРѓР Р…Р В°Р С—Р С‘РЎвЂљ X
 *     Р С” РЎвЂЎРЎвЂРЎвЂљР Р…Р С•Р СРЎС“ РІР‚вЂќ РЎРѓР С•РЎвЂ¦РЎР‚Р В°Р Р…РЎРЏР ВµРЎвЂљ "Р С—Р С‘Р С”РЎРѓР ВµР В»РЎРЉР Р…РЎвЂ№Р в„–" РЎРѓРЎвЂљР С‘Р В»РЎРЉ, Р Р…Р С• Р Р…Р Вµ РЎР‚Р Р†РЎвЂРЎвЂљ Р В·Р В°Р В»Р С‘Р Р†Р С”РЎС“.
 *   - glowCircle Р В±Р С•Р В»РЎРЉРЎв‚¬Р Вµ Р Р…Р Вµ Р С—Р С‘РЎв‚¬Р ВµРЎвЂљ "Р С—Р С•РЎРЏРЎРѓР В°" РЎвЂЎР ВµРЎР‚Р ВµР В· 2 ring-РЎРѓРЎвЂљРЎР‚РЎС“Р С”РЎвЂљРЎС“РЎР‚РЎвЂ№ РІР‚вЂќ Р С—Р В»Р В°Р Р†Р Р…РЎвЂ№Р в„–
 *     3-РЎвЂљР С•РЎвЂЎР ВµРЎвЂЎР Р…РЎвЂ№Р в„– Р С—РЎР‚Р С•РЎвЂћР С‘Р В»РЎРЉ.
 */
public final class GenesisDraw {

    private GenesisDraw() {}

    /** Р В Р В°Р В·Р СР ВµРЎР‚ Р С—Р С‘Р С”РЎРѓР ВµР В»РЎРЉР Р…Р С•Р С–Р С• Р В±Р В»Р С•Р С”Р В° Р Т‘Р В»РЎРЏ Р В»Р С‘Р Р…Р С‘Р в„–/Р С”Р Р†Р В°Р Т‘Р С•Р Р†. */
    public static final int PIXEL_UNIT = 2;

    public static final int GLOW_SEG = 24;
    public static final int RING_SEG = 48;

    private static final float[] COS48 = new float[RING_SEG];
    private static final float[] SIN48 = new float[RING_SEG];

    static {
        for (int i = 0; i < RING_SEG; i++) {
            double a = Math.PI * 2 * i / RING_SEG;
            COS48[i] = (float) Math.cos(a);
            SIN48[i] = (float) Math.sin(a);
        }
    }

    public static float cos48(int i) { return COS48[((i % RING_SEG) + RING_SEG) % RING_SEG]; }
    public static float sin48(int i) { return SIN48[((i % RING_SEG) + RING_SEG) % RING_SEG]; }

    private static float snap(float v) {
        return Math.round(v / PIXEL_UNIT) * PIXEL_UNIT;
    }

    // ============================================================
    //  Glow РІР‚вЂќ Р С—Р В»Р В°Р Р†Р Р…РЎвЂ№Р в„– Р С–РЎР‚Р В°Р Т‘Р С‘Р ВµР Р…РЎвЂљ
    // ============================================================

    public static void glowCircle(VertexConsumer vc, Matrix4f m,
                                   float cx, float cy, float radius,
                                   float r, float g, float b, float maxAlpha) {
        if (radius <= 1f || maxAlpha <= 0.004f) return;

        float rMid = radius * 0.45f;
        float aMid = maxAlpha * 0.55f;

        for (int i = 0; i < GLOW_SEG; i++) {
            int j = (i + 1) % GLOW_SEG;
            float cos0 = cos48(i * RING_SEG / GLOW_SEG);
            float sin0 = sin48(i * RING_SEG / GLOW_SEG);
            float cos1 = cos48(j * RING_SEG / GLOW_SEG);
            float sin1 = sin48(j * RING_SEG / GLOW_SEG);

            float mx0 = cx + cos0 * rMid, my0 = cy + sin0 * rMid;
            float mx1 = cx + cos1 * rMid, my1 = cy + sin1 * rMid;
            float ex0 = cx + cos0 * radius, ey0 = cy + sin0 * radius;
            float ex1 = cx + cos1 * radius, ey1 = cy + sin1 * radius;

            vc.addVertex(m, cx, cy, 0).setColor(r, g, b, maxAlpha);
            vc.addVertex(m, mx0, my0, 0).setColor(r, g, b, aMid);
            vc.addVertex(m, mx1, my1, 0).setColor(r, g, b, aMid);

            vc.addVertex(m, mx0, my0, 0).setColor(r, g, b, aMid);
            vc.addVertex(m, ex0, ey0, 0).setColor(r, g, b, 0f);
            vc.addVertex(m, ex1, ey1, 0).setColor(r, g, b, 0f);

            vc.addVertex(m, mx0, my0, 0).setColor(r, g, b, aMid);
            vc.addVertex(m, ex1, ey1, 0).setColor(r, g, b, 0f);
            vc.addVertex(m, mx1, my1, 0).setColor(r, g, b, aMid);
        }
    }

    // ============================================================
    //  Mist ellipse РІР‚вЂќ Р С—Р В»Р С•РЎРѓР С”Р С‘Р в„– Р С—РЎР‚Р С•РЎвЂћР С‘Р В»РЎРЉ Р Т‘Р В»РЎРЏ РЎвЂљРЎС“Р СР В°Р Р…Р Р…Р С•РЎРѓРЎвЂљР ВµР в„–
    // ============================================================

    public static void mistEllipse(VertexConsumer vc, Matrix4f m,
                                    float cx, float cy, float rx, float ry,
                                    float r, float g, float b, float maxAlpha) {
        if (rx <= 1f || ry <= 1f || maxAlpha <= 0.003f) return;

        final float[] radiiF = { 0.00f, 0.40f, 0.70f, 1.00f };
        final float[] alphaF = { 0.60f, 0.65f, 0.30f, 0.00f };

        for (int i = 0; i < 16; i++) {
            float a0 = (float)(Math.PI * 2 * i / 16);
            float a1 = (float)(Math.PI * 2 * (i + 1) / 16);
            float cos0 = (float)Math.cos(a0), sin0 = (float)Math.sin(a0);
            float cos1 = (float)Math.cos(a1), sin1 = (float)Math.sin(a1);

            for (int k = 0; k < 3; k++) {
                float rr0 = radiiF[k], rr1 = radiiF[k+1];
                float aa0 = alphaF[k], aa1 = alphaF[k+1];

                float ix0 = cx + cos0 * rx * rr0, iy0 = cy + sin0 * ry * rr0;
                float ix1 = cx + cos1 * rx * rr0, iy1 = cy + sin1 * ry * rr0;
                float ox0 = cx + cos0 * rx * rr1, oy0 = cy + sin0 * ry * rr1;
                float ox1 = cx + cos1 * rx * rr1, oy1 = cy + sin1 * ry * rr1;

                float a0v = maxAlpha * aa0;
                float a1v = maxAlpha * aa1;

                vc.addVertex(m, ix0, iy0, 0).setColor(r, g, b, a0v);
                vc.addVertex(m, ox0, oy0, 0).setColor(r, g, b, a1v);
                vc.addVertex(m, ox1, oy1, 0).setColor(r, g, b, a1v);

                vc.addVertex(m, ix0, iy0, 0).setColor(r, g, b, a0v);
                vc.addVertex(m, ox1, oy1, 0).setColor(r, g, b, a1v);
                vc.addVertex(m, ix1, iy1, 0).setColor(r, g, b, a0v);
            }
        }
    }

    // ============================================================
    //  Circles / Rings РІР‚вЂќ pixel-stepped Р С—Р С• Y
    // ============================================================

    public static void pixelCircle(VertexConsumer vc, Matrix4f m,
                                    float cx, float cy, float radius,
                                    float r, float g, float b, float alpha) {
        if (radius <= 0.5f || alpha <= 0.004f) return;
        int cxI = Math.round(cx), cyI = Math.round(cy), rI = Math.round(radius);
        if (rI <= 0) return;
        long r2 = (long) rI * rI;

        for (int dy = -rI; dy <= rI; dy++) {
            long d2 = (long) dy * dy;
            if (d2 > r2) continue;
            int half = (int) Math.sqrt((double) (r2 - d2));
            if (half <= 0) continue;

            float x0 = snap(cxI - half);
            float x1 = snap(cxI + half + 1);
            float y0 = snap(cyI + dy);
            float y1 = y0 + PIXEL_UNIT;
            if (x1 <= x0) x1 = x0 + PIXEL_UNIT;

            vc.addVertex(m, x0, y0, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, x1, y0, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, x1, y1, 0).setColor(r, g, b, alpha);

            vc.addVertex(m, x0, y0, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, x1, y1, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, x0, y1, 0).setColor(r, g, b, alpha);
        }
    }

    public static void pixelSoftCircle(VertexConsumer vc, Matrix4f m,
                                        float cx, float cy, float radius,
                                        float r, float g, float b,
                                        float maxAlpha) {
        if (radius <= 0.5f || maxAlpha <= 0.004f) return;
        int cxI = Math.round(cx), cyI = Math.round(cy), rI = Math.round(radius);
        if (rI <= 0) return;
        long r2 = (long) rI * rI;

        for (int dy = -rI; dy <= rI; dy++) {
            long d2 = (long) dy * dy;
            if (d2 > r2) continue;
            int half = (int) Math.sqrt((double) (r2 - d2));
            if (half <= 0) continue;

            float dn = (float) Math.abs(dy) / rI;
            float falloff = 1f - dn;
            falloff *= falloff;
            float a = falloff * maxAlpha;
            if (a <= 0.004f) continue;

            float x0 = snap(cxI - half);
            float x1 = snap(cxI + half + 1);
            float y0 = snap(cyI + dy);
            float y1 = y0 + PIXEL_UNIT;
            if (x1 <= x0) x1 = x0 + PIXEL_UNIT;

            vc.addVertex(m, x0, y0, 0).setColor(r, g, b, a);
            vc.addVertex(m, x1, y0, 0).setColor(r, g, b, a);
            vc.addVertex(m, x1, y1, 0).setColor(r, g, b, a);

            vc.addVertex(m, x0, y0, 0).setColor(r, g, b, a);
            vc.addVertex(m, x1, y1, 0).setColor(r, g, b, a);
            vc.addVertex(m, x0, y1, 0).setColor(r, g, b, a);
        }
    }

    public static void ring(VertexConsumer vc, Matrix4f m,
                            float cx, float cy,
                            float innerR, float outerR,
                            float r, float g, float b, float alpha) {
        if (outerR <= innerR || alpha <= 0.004f) return;
        for (int i = 0; i < RING_SEG; i++) {
            int j = (i + 1) % RING_SEG;
            float cos0 = COS48[i], sin0 = SIN48[i];
            float cos1 = COS48[j], sin1 = SIN48[j];

            float ix0 = cx + cos0 * innerR, iy0 = cy + sin0 * innerR;
            float ox0 = cx + cos0 * outerR, oy0 = cy + sin0 * outerR;
            float ix1 = cx + cos1 * innerR, iy1 = cy + sin1 * innerR;
            float ox1 = cx + cos1 * outerR, oy1 = cy + sin1 * outerR;

            vc.addVertex(m, ix0, iy0, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, ox0, oy0, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, ox1, oy1, 0).setColor(r, g, b, alpha);

            vc.addVertex(m, ix0, iy0, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, ox1, oy1, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, ix1, iy1, 0).setColor(r, g, b, alpha);
        }
    }

    public static void pixelRing(VertexConsumer vc, Matrix4f m,
                                  float cx, float cy,
                                  float innerR, float outerR,
                                  float r, float g, float b, float alpha) {
        ring(vc, m, cx, cy, innerR, outerR, r, g, b, alpha);
    }

    // ============================================================
    //  Exact line (Р вЂР вЂўР вЂ” snap РІР‚вЂќ Р С—Р В»Р В°Р Р†Р Р…РЎвЂ№Р Вµ Р В»Р С‘Р Р…Р С‘Р С‘ Р В±Р ВµР В· "Р В»РЎС“РЎвЂЎР ВµР в„–")
    // ============================================================

    public static void line(VertexConsumer vc, Matrix4f m,
                            float fx1, float fy1, float fx2, float fy2,
                            float thickness,
                            float r, float g, float b, float alpha) {
        if (alpha <= 0.004f) return;

        int x1 = Math.round(fx1);
        int y1 = Math.round(fy1);
        int x2 = Math.round(fx2);
        int y2 = Math.round(fy2);

        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int safety = 8192;
        while (safety-- > 0) {
            vc.addVertex(m, x1, y1, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, x1 + 1, y1, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, x1 + 1, y1 + 1, 0).setColor(r, g, b, alpha);

            vc.addVertex(m, x1, y1, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, x1 + 1, y1 + 1, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, x1, y1 + 1, 0).setColor(r, g, b, alpha);

            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 <  dx) { err += dx; y1 += sy; }
        }
    }

    public static void pixelLine(VertexConsumer vc, Matrix4f m,
                                  float fx1, float fy1, float fx2, float fy2,
                                  float r, float g, float b, float alpha) {
        if (alpha <= 0.004f) return;
        int x1 = Math.round(fx1), y1 = Math.round(fy1);
        int x2 = Math.round(fx2), y2 = Math.round(fy2);
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int step = PIXEL_UNIT;

        int safety = 4096;
        while (safety-- > 0) {
            float px = snap(x1), py = snap(y1);
            vc.addVertex(m, px, py, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, px + step, py, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, px + step, py + step, 0).setColor(r, g, b, alpha);

            vc.addVertex(m, px, py, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, px + step, py + step, 0).setColor(r, g, b, alpha);
            vc.addVertex(m, px, py + step, 0).setColor(r, g, b, alpha);

            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 <  dx) { err += dx; y1 += sy; }
        }
    }

    public static void quad(VertexConsumer vc, Matrix4f m,
                            float x, float y, float w, float h,
                            float r, float g, float b, float alpha) {
        if (w <= 0.1f || h <= 0.1f || alpha <= 0.004f) return;
        float x1 = x, y1 = y, x2 = x + w, y2 = y + h;

        vc.addVertex(m, x1, y1, 0).setColor(r, g, b, alpha);
        vc.addVertex(m, x2, y1, 0).setColor(r, g, b, alpha);
        vc.addVertex(m, x2, y2, 0).setColor(r, g, b, alpha);

        vc.addVertex(m, x1, y1, 0).setColor(r, g, b, alpha);
        vc.addVertex(m, x2, y2, 0).setColor(r, g, b, alpha);
        vc.addVertex(m, x1, y2, 0).setColor(r, g, b, alpha);
    }
}