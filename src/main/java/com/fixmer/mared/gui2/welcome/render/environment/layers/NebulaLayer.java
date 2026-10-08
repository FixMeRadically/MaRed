package com.fixmer.mared.gui2.welcome.render.environment.layers;

import com.fixmer.mared.gui2.framework.render.MaredColor;
import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentCamera;
import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentLayer;
import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentPalette;
import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentTime;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 1.5.37.2 / fix13: РјСЏРіРєРёРµ РѕР±Р»Р°РєР° С‚СѓРјР°РЅРЅРѕСЃС‚Рё.
 *
 * РљР°Р¶РґРѕРµ РѕР±Р»Р°РєРѕ СЂРёСЃСѓРµС‚СЃСЏ РєР°Рє РЅР°Р±РѕСЂ РІР»РѕР¶РµРЅРЅС‹С… СЌР»Р»РёРїСЃРѕРІ - РѕС‚ РІРЅРµС€РЅРµРіРѕ
 * Рє РІРЅСѓС‚СЂРµРЅРЅРµРјСѓ, СЃ РѕРґРёРЅР°РєРѕРІРѕР№ РјР°Р»РѕР№ Р°Р»СЊС„РѕР№. РџСЂРѕР·СЂР°С‡РЅРѕСЃС‚СЊ СЃСѓРјРјРёСЂСѓРµС‚СЃСЏ
 * Р·Р° СЃС‡С‘С‚ alpha-blending:
 *
 *   С†РµРЅС‚СЂ      - РїРѕРєСЂС‹С‚ РІСЃРµРјРё СЌР»Р»РёРїСЃР°РјРё -> СЃСѓРјРјР°СЂРЅР°СЏ Р°Р»СЊС„Р° = MAX
 *   СЃСЂРµРґРЅСЏСЏ Р·РѕРЅР° - РїРѕРєСЂС‹С‚ РїРѕР»РѕРІРёРЅРѕР№       -> РїРѕР»РѕРІРёРЅР° MAX
 *   РєСЂР°Р№       - РїРѕРєСЂС‹С‚ С‚РѕР»СЊРєРѕ РІРЅРµС€РЅРёРј    -> MIN
 *
 * Р­С‚Рѕ РґР°С‘С‚ СЂР°РґРёР°Р»СЊРЅРѕРµ Р·Р°С‚СѓС…Р°РЅРёРµ РѕРґРЅРѕРІСЂРµРјРµРЅРЅРѕ РїРѕ X Рё Y, Р±РµР·
 * РІРµСЂС‚РёРєР°Р»СЊРЅС‹С… РїРѕР»РѕСЃ. РћРґРёРЅ "РїСЂРѕС…РѕРґ РїРѕ РєРѕР»СЊС†Р°Рј" = O(rings * radius)
 * fill() РІС‹Р·РѕРІРѕРІ, С‡С‚Рѕ РґРµС€РµРІР»Рµ, С‡РµРј РїРѕРїРёРєСЃРµР»СЊРЅС‹Р№ blur.
 *
 * Р¦РІРµС‚ - РЅРµ darken(accent), Р° lerp(skyMiddle, accent, 0.30):
 * РѕР±Р»Р°РєРѕ РґРѕР»Р¶РЅРѕ Р±С‹С‚СЊ РЎР’Р•РўР›Р•Р• С„РѕРЅР°, РёРЅР°С‡Рµ РІС‹РіР»СЏРґРёС‚ РєР°Рє РґС‹СЂР°.
 */
public final class NebulaLayer implements EnvironmentLayer {

    /** РЁР°Рі РїРѕ Y РІРЅСѓС‚СЂРё РѕРґРЅРѕРіРѕ СЌР»Р»РёРїСЃР°. */
    private static final int ROW_STEP = 2;

    /** РљРѕР»РёС‡РµСЃС‚РІРѕ РІР»РѕР¶РµРЅРЅС‹С… СЌР»Р»РёРїСЃРѕРІ РЅР° РѕРґРЅРѕ РѕР±Р»Р°РєРѕ. */
    private static final int RINGS = 16;

    /** РњР°РєСЃРёРјР°Р»СЊРЅР°СЏ СЃСѓРјРјР°СЂРЅР°СЏ Р°Р»СЊС„Р° РѕРґРЅРѕРіРѕ РѕР±Р»Р°РєР° (РІ С†РµРЅС‚СЂРµ). */
    private static final int TOTAL_ALPHA = 42;

    /** РќР°СЃРєРѕР»СЊРєРѕ РѕР±Р»Р°РєРѕ РїРѕРґРјРµС€РёРІР°РµС‚СЃСЏ Рє accent. */
    private static final float ACCENT_MIX = 0.30f;

    private static final class Cloud {
        final float baseX01;
        final float baseY01;
        final float radius01;
        final float depth;
        final float driftSpeed;
        final float phase;

        Cloud(float baseX01, float baseY01, float radius01,
              float depth, float driftSpeed, float phase) {
            this.baseX01 = baseX01;
            this.baseY01 = baseY01;
            this.radius01 = radius01;
            this.depth = depth;
            this.driftSpeed = driftSpeed;
            this.phase = phase;
        }
    }

    /**
     * Р Р°РґРёСѓСЃС‹ СѓРјРµРЅСЊС€РµРЅС‹ РїСЂРёРјРµСЂРЅРѕ РІ 2 СЂР°Р·Р° РїСЂРѕС‚РёРІ РїРµСЂРІРѕР№ РІРµСЂСЃРёРё
     * (Р±С‹Р»Рё 0.42 / 0.38 / 0.45 -> СЃС‚Р°Р»Рё 0.20 / 0.18 / 0.22).
     * РџРѕР·РёС†РёРё СЃР»РµРіРєР° СЂР°Р·РІРµРґРµРЅС‹ РѕС‚ С†РµРЅС‚СЂР°, С‡С‚РѕР±С‹ РѕР±Р»Р°РєР° РЅРµ СЃР»РёРІР°Р»РёСЃСЊ.
     */
    private final Cloud[] clouds = new Cloud[]{
        new Cloud(0.22f, 0.32f, 0.20f, 0.15f, 0.11f, 0.00f),
        new Cloud(0.78f, 0.26f, 0.18f, 0.20f, 0.09f, 1.30f),
        new Cloud(0.50f, 0.70f, 0.22f, 0.35f, 0.13f, 2.10f),
        new Cloud(0.16f, 0.76f, 0.16f, 0.55f, 0.17f, 3.40f),
        new Cloud(0.84f, 0.68f, 0.17f, 0.70f, 0.19f, 4.20f),
    };

    @Override
    public void render(GuiGraphics g,
                       int width, int height,
                       EnvironmentTime time,
                       EnvironmentPalette palette,
                       EnvironmentCamera camera) {
        if (width <= 0 || height <= 0) return;

        int minWH = Math.min(width, height);
        int rgb = MaredColor.lerpColor(
            palette.skyMiddle, palette.accent, ACCENT_MIX) & 0x00FFFFFF;

        for (Cloud c : clouds) {
            renderCloud(g, c, width, height, minWH, time, rgb, camera);
        }
    }

    private void renderCloud(GuiGraphics g, Cloud c,
                             int width, int height, int minWH,
                             EnvironmentTime time,
                             int rgb,
                             EnvironmentCamera camera) {

        float driftX = time.sin(c.driftSpeed) * 0.04f;
        float driftY = time.cos(c.driftSpeed * 0.8f) * 0.03f;

        int cx = (int) ((c.baseX01 + driftX) * width)
                + camera.offsetPxX(width, c.depth);

        int cy = (int) ((c.baseY01 + driftY) * height)
                + camera.offsetPxY(height, c.depth);

        int radius = (int) (minWH * c.radius01);
        if (radius <= 0) return;

        // РџР»РѕСЃРєРёРµ РѕР±Р»Р°РєР°: squashY РґРµР»Р°РµС‚ СЌР»Р»РёРїСЃ, Р° РЅРµ РєСЂСѓРі.
        float squashY = 0.75f;
        int radiusY = (int)(radius * squashY);

        drawSoftEllipse(g, cx, cy, radius, radiusY, rgb, RINGS, TOTAL_ALPHA);
    }

    /**
     * Р’Р»РѕР¶РµРЅРЅС‹Рµ СЌР»Р»РёРїСЃС‹, РєР°Р¶РґС‹Р№ СЃ alpha = totalAlpha / rings.
     * РџСЂРѕР·СЂР°С‡РЅРѕСЃС‚СЊ СЃРєР»Р°РґС‹РІР°РµС‚СЃСЏ РїСЂРё Р±Р»РµРЅРґРёРЅРіРµ - РїРѕР»СѓС‡Р°РµС‚СЃСЏ
     * СЂР°РґРёР°Р»СЊРЅРѕРµ Р·Р°С‚СѓС…Р°РЅРёРµ.
     */
    private static void drawSoftEllipse(GuiGraphics g,
                                        int cx, int cy,
                                        int rx, int ry,
                                        int rgb,
                                        int rings, int totalAlpha) {
        if (rx <= 0 || ry <= 0 || rings <= 0) return;

        int perRing = totalAlpha / rings;
        if (perRing <= 0) return;

        int color = (perRing << 24) | (rgb & 0x00FFFFFF);

        for (int k = 0; k < rings; k++) {
            float frac = 1f - k / (float) rings;
            int crx = (int) (rx * frac);
            int cry = (int) (ry * frac);
            if (crx <= 0 || cry <= 0) continue;

            long ry2 = (long) cry * cry;

            for (int dy = -cry; dy <= cry; dy += ROW_STEP) {
                long d2 = (long) dy * dy;
                if (d2 > ry2) continue;

                double ratio = 1.0 - (double) d2 / (double) ry2;
                if (ratio < 0.0) continue;

                int half = (int) (crx * Math.sqrt(ratio));
                if (half <= 0) continue;

                g.fill(cx - half, cy + dy,
                       cx + half, cy + dy + ROW_STEP, color);
            }
        }
    }
}