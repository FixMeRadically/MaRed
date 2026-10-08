package com.fixmer.mared.gui2.welcome.render.environment.core;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 1.5.37.4.2 Cinematic Pass.
 *
 * РњСЏРіС‡Рµ, С‡РµРј Р±С‹Р»Рѕ:
 *   - РѕР±С‰Р°СЏ РїСѓР»СЊСЃР°С†РёСЏ scale;
 *   - halo-СЃРІРµС‡РµРЅРёРµ РїРѕРґ РѕРєС‚Р°СЌРґСЂРѕРј;
 *   - РІРјРµСЃС‚Рѕ hard-jitter РІ STABILIZING - РїР»Р°РІРЅРѕРµ sin-РёСЃРєР°Р¶РµРЅРёРµ
 *     РєР°Р¶РґРѕР№ РІРµСЂС€РёРЅС‹.
 *
 * Р¦РІРµС‚ - СЃС‚СЂСѓРєС‚СѓСЂРЅС‹Р№ С…РѕР»РѕРґРЅС‹Р№ (Р·Р°РґР°С‘С‚СЃСЏ СЃРЅР°СЂСѓР¶Рё), accent С‚РѕР»СЊРєРѕ
 * СЃР»РµРіРєР° РµРіРѕ С‚РѕРЅРёСЂСѓРµС‚.
 */
public final class CorePrototype {

    private static final float ROT_SPEED = 0.35f;

    public void render(GuiGraphics g,
                       int cx, int cy,
                       float R,
                       float timeSec,
                       CoreState state,
                       float stateProgress,
                       int accent,
                       int structuralRgb,
                       int glowRgb) {

        if (!CoreStateMachine.prototypeVisible(state)) return;

        float alpha;
        float scale;
        float pulse;

        switch (state) {
            case BUILDING -> {
                scale = 0.2f + 0.8f * CoreGeometry.smoothstep(stateProgress);
                alpha = 220f * CoreGeometry.smoothstep(stateProgress);
                pulse = 0f;
            }
            case STABILIZING -> {
                scale = 1.0f;
                pulse = 0.05f * (float)Math.sin(timeSec * 8f);
                alpha = 220f + 30f * (float)Math.sin(timeSec * 12f);
            }
            case READY -> {
                float fade = stateProgress < 0.6f
                    ? 1f
                    : 1f - (stateProgress - 0.6f) / 0.4f;
                scale = 1.0f;
                pulse = 0.03f * (float)Math.sin(timeSec * 3f);
                alpha = 240f * fade;
            }
            default -> { return; }
        }

        if (alpha <= 4f || scale <= 0.01f) return;

        float actualScale = scale * (1f + pulse);
        float rotY = timeSec * ROT_SPEED;

        // --- Halo-СЃРІРµС‡РµРЅРёРµ РїРѕРґ РѕРєС‚Р°СЌРґСЂРѕРј ---
        int haloAlpha = (int)(alpha * 0.22f);
        if (haloAlpha > 6) {
            int haloR = (int)(R * 1.25f * actualScale);
            CoreGeometry.drawSoftEllipse(g, cx, cy, haloR, haloR,
                                         glowRgb, 12, haloAlpha);
        }

        int edgeAlpha = (int)alpha;
        int edgeColor = (edgeAlpha << 24) | (structuralRgb & 0x00FFFFFF);
        int dotColor  = (edgeAlpha << 24) | 0x00FFFFFF;

        float[][] verts = CorePrototypeShape.VERTICES;
        int n = verts.length;
        int[] px = new int[n];
        int[] py = new int[n];

        float radius = R * 0.85f * actualScale;
        float cosY = (float)Math.cos(rotY);
        float sinY = (float)Math.sin(rotY);

        for (int i = 0; i < n; i++) {
            float x = verts[i][0];
            float y = verts[i][1];
            float z = verts[i][2];

            float xr = x * cosY - z * sinY;

            // РњСЏРіРєРѕРµ sin-РёСЃРєР°Р¶РµРЅРёРµ РІ STABILIZING РІРјРµСЃС‚Рѕ hard-jitter
            if (state == CoreState.STABILIZING) {
                float w = (float)Math.sin(timeSec * 5f + i * 1.4f) * 0.03f;
                xr += w;
                y += w;
            }

            px[i] = cx + (int)(xr * radius);
            py[i] = cy + (int)(y * radius * 0.9f);
        }

        // --- Р’РЅСѓС‚СЂРµРЅРЅРёР№ СѓР·РµР» ---
        int innerAlpha = (int)(alpha * 0.28f);
        if (innerAlpha > 8) {
            int innerColor = (innerAlpha << 24) | (structuralRgb & 0x00FFFFFF);
            for (int i = 0; i < n; i++) {
                CoreGeometry.drawLine(g, cx, cy, px[i], py[i], innerColor, 1);
            }
        }

        // --- Р С‘Р±СЂР° ---
        int[][] edges = CorePrototypeShape.EDGES;
        for (int i = 0; i < edges.length; i++) {
            if (state == CoreState.STABILIZING) {
                float ph = timeSec * 2.4f + i * 0.7f;
                if (Math.sin(ph) > 0.7f) continue;
            }
            int a = edges[i][0];
            int b = edges[i][1];
            CoreGeometry.drawLine(g, px[a], py[a], px[b], py[b], edgeColor, 1);
        }

        // --- Р’РµСЂС€РёРЅС‹ ---
        for (int i = 0; i < n; i++) {
            g.fill(px[i] - 1, py[i] - 1, px[i] + 2, py[i] + 2, dotColor);
        }

        // --- Р¦РµРЅС‚СЂ ---
        int centerAlpha = (int)alpha;
        int centerColor = (centerAlpha << 24) | 0x00FFFFFF;
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, centerColor);
    }
}