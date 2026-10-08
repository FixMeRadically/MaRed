package com.fixmer.mared.gui2.welcome.render.environment.core;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/**
 * 1.5.37.4.2 Cinematic Pass.
 *
 * 12 С‡Р°СЃС‚РёС† Р»РµС‚СЏС‚ Рє 6 РІРµСЂС€РёРЅР°Рј РѕРєС‚Р°СЌРґСЂР°. Р‘РµР· trails.
 *
 * РљСЂРёРІР°СЏ РґРІРёР¶РµРЅРёСЏ: smoothstep СЃ СѓСЃРёР»РµРЅРёРµРј РІ РєРѕРЅС†Рµ. Р§Р°СЃС‚РёС†Р°
 * СЃРЅР°С‡Р°Р»Р° РїРѕС‡С‚Рё РЅРµ РґРІРёРіР°РµС‚СЃСЏ (РєСЂСѓР¶РёС‚ СЃРЅР°СЂСѓР¶Рё), РїРѕС‚РѕРј СЂРµР·РєРѕ
 * РІС…РѕРґРёС‚ РІ РІРµСЂС€РёРЅСѓ. Р­С‚Рѕ РґР°С‘С‚ РѕС‰СѓС‰РµРЅРёРµ "РґР°РЅРЅС‹Рµ СЂРµС€РёР»Рё СЃРѕР±СЂР°С‚СЊСЃСЏ".
 */
public final class CorePrototypeAssembly {

    private static final int PARTICLE_COUNT = 12;
    private static final float START_RADIUS = 2.2f;

    private final float[][] starts = new float[PARTICLE_COUNT][3];
    private final int[] targetVertex = new int[PARTICLE_COUNT];

    public CorePrototypeAssembly() {
        Random rng = new Random(0xAB5EED);

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float theta = rng.nextFloat() * (float)(Math.PI * 2.0);
            float phi   = (float)Math.acos(2.0 * rng.nextFloat() - 1.0);

            float sx = (float)(Math.sin(phi) * Math.cos(theta)) * START_RADIUS;
            float sy = (float)(Math.cos(phi)) * START_RADIUS;
            float sz = (float)(Math.sin(phi) * Math.sin(theta)) * START_RADIUS;

            starts[i][0] = sx;
            starts[i][1] = sy;
            starts[i][2] = sz;
            targetVertex[i] = i % 6;
        }
    }

    public void render(GuiGraphics g,
                       int cx, int cy,
                       float R,
                       float timeSec,
                       CoreState state,
                       float stateProgress,
                       int structuralRgb) {

        if (state != CoreState.BUILDING) return;

        // Ease in-out: РјРµРґР»РµРЅРЅРѕ -> Р±С‹СЃС‚СЂРѕ -> РјСЏРіРєРѕ
        float p = CoreGeometry.smoothstep(stateProgress);
        float converge = p * p;  // СѓСЃРєРѕСЂСЏРµС‚СЃСЏ Рє РєРѕРЅС†Сѓ

        float rotY = timeSec * 0.35f;
        float cosY = (float)Math.cos(rotY);
        float sinY = (float)Math.sin(rotY);

        float[][] verts = CorePrototypeShape.VERTICES;
        int n = verts.length;
        int[] vx = new int[n];
        int[] vy = new int[n];

        float radius = R * 0.85f;
        for (int i = 0; i < n; i++) {
            float x = verts[i][0];
            float y = verts[i][1];
            float z = verts[i][2];
            float xr = x * cosY - z * sinY;
            vx[i] = cx + (int)(xr * radius);
            vy[i] = cy + (int)(y * radius * 0.9f);
        }

        // Fade: alpha СЃРЅРёР¶Р°РµС‚СЃСЏ Рє РєРѕРЅС†Сѓ BUILDING
        float fade = 1f - Math.max(0f, (stateProgress - 0.7f) / 0.3f);
        if (fade <= 0.02f) return;

        int alpha = (int)(230 * fade);
        int color = (alpha << 24) | (structuralRgb & 0x00FFFFFF);

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float sx = starts[i][0];
            float sy = starts[i][1];
            float sz = starts[i][2];
            float xr = sx * cosY - sz * sinY;

            int startX = cx + (int)(xr * radius);
            int startY = cy + (int)(sy * radius * 0.9f);

            int tx = vx[targetVertex[i]];
            int ty = vy[targetVertex[i]];

            int x = (int)(startX + (tx - startX) * converge);
            int y = (int)(startY + (ty - startY) * converge);

            g.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }
}