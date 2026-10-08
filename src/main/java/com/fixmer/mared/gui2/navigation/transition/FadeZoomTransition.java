package com.fixmer.mared.gui2.navigation.transition;

import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.navigation.MaredSpace;
import com.fixmer.mared.gui2.navigation.SpaceCameraState;
import com.fixmer.mared.gui2.navigation.TransitionKind;

import net.minecraft.client.gui.GuiGraphics;

/**
 * v1 переход: fade + лёгкая виньетка «полёта».
 *
 * Фазы:
 *   0.00 .. 0.45 — уходящее пространство рендерится как есть,
 *                  поверх нарастает чёрная вуаль;
 *   0.45 .. 0.55 — полностью чёрный кадр (дышащая пауза);
 *   0.55 .. 1.00 — приходящее рендерится, вуаль спадает.
 *
 * Виньетка-«полёт»: во время 0..0.5 добавляется тонкое кольцо по
 * краям с растущей alpha — визуально тянет фокус к центру.
 * Во время 0.5..1 то же самое, но убывающее. Это заготовка под
 * настоящее zoom/radial blur, но уже даёт «движение».
 *
 * Расширяется: сделать чёрную вуаль прозрачной и добавить BlurEffect
 * поверх — тогда будет «bloom-through» как в Half-Life.
 */
public final class FadeZoomTransition implements TransitionEffect {

    private static final float DURATION = 0.85f;
    private static final float VEIL_PEAK_AT = 0.5f;

    @Override
    public float duration() { return DURATION; }

    @Override
    public void render(MaredRenderContext rctx,
                       MaredSpace from,
                       MaredSpace to,
                       SpaceCameraState fromCamera,
                       float progress,
                       TransitionKind kind) {

        GuiGraphics g = rctx.graphics();
        int w = rctx.width();
        int h = rctx.height();

        if (progress < VEIL_PEAK_AT) {
            if (from != null) {
                from.render(rctx);
            }
            float veil = smoothstep(0f, 1f, progress / VEIL_PEAK_AT);
            drawVeil(g, w, h, veil);
            drawFlightVignette(g, w, h, veil);
        } else {
            if (to != null) {
                to.render(rctx);
            }
            float remain = (progress - VEIL_PEAK_AT) / (1f - VEIL_PEAK_AT);
            float veil = 1f - smoothstep(0f, 1f, remain);
            drawVeil(g, w, h, veil);
            drawFlightVignette(g, w, h, veil);
        }
    }

    private static void drawVeil(GuiGraphics g, int w, int h, float amount) {
        if (amount <= 0f) return;
        int a = (int)(amount * 255f);
        if (a > 255) a = 255;
        g.fill(0, 0, w, h, a << 24);
    }

    /**
     * Тонкое чёрное кольцо у краёв экрана, имитирующее «туннель».
     * alpha ~ 90 при полной вуали. Не перекрывает центр — там fade
     * задаёт общий эффект.
     */
    private static void drawFlightVignette(GuiGraphics g, int w, int h, float amount) {
        if (amount <= 0.05f) return;

        int rings = 10;
        int maxDepth = Math.min(w, h) / 6;
        int step = Math.max(2, maxDepth / rings);

        int peakAlpha = (int)(amount * 110f);
        for (int i = 0; i < rings; i++) {
            float t = i / (float) rings;
            float falloff = (1f - t) * (1f - t);
            int a = (int)(peakAlpha * falloff);
            if (a <= 0) continue;
            int offset = i * step;
            int c = a << 24;
            g.fill(0, offset, w, offset + step, c);
            g.fill(0, h - offset - step, w, h - offset, c);
            g.fill(offset, 0, offset + step, h, c);
            g.fill(w - offset - step, 0, w - offset, h, c);
        }
    }

    private static float smoothstep(float a, float b, float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float x = (t - a) / (b - a);
        return x * x * (3f - 2f * x);
    }
}