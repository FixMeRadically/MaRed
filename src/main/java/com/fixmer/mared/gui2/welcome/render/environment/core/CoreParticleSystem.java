package com.fixmer.mared.gui2.welcome.render.environment.core;

import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentCamera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1.5.37.4.2 Cinematic Pass.
 *
 * 24 РіР»РёС„Р°: 8 SYMBOL / 8 KEYWORD / 8 VALUE.
 * РќРёРєР°РєРёС… РѕСЂР±РёС‚Р°Р»СЊРЅС‹С… СЃРІСЏР·РµР№ - С‚РѕР»СЊРєРѕ СЂРµРґРєРёРµ СЃРµРјР°РЅС‚РёС‡РµСЃРєРёРµ,
 * Рё С‚РѕР»СЊРєРѕ РєРѕРіРґР° РѕР±Рµ С‡Р°СЃС‚РёС†С‹ Р±Р»РёР·РєРѕ (РґРёСЃС‚Р°РЅС†РёСЏ < R*0.28).
 *
 * dataCollapse (0..1 РёР· CoreObjectLayer): СЃР¶РёРјР°РµС‚ СЂР°РґРёСѓСЃ РѕСЂР±РёС‚
 * Рє С†РµРЅС‚СЂСѓ. IDLE - 0, BUILDING - РїРѕС‡С‚Рё 1. Р§Р°СЃС‚РёС†С‹ "СЃС‚РµРєР°СЋС‚СЃСЏ"
 * РІ СЏРґСЂРѕ, РєРѕРіРґР° СЃРёСЃС‚РµРјР° СЃРѕР±РёСЂР°РµС‚ РѕР±СЉРµРєС‚.
 *
 * Р¦РІРµС‚Р° РЅРµ accent РєР°Рє РµСЃС‚СЊ, Р° СЃРјРµС€Р°РЅС‹ СЃ С…РѕР»РѕРґРЅС‹Рј data-tint.
 * РџР°Р»РёС‚СЂР° Р·Р°РґР°С‘С‚СЃСЏ РёР· CoreObjectLayer С‡РµСЂРµР· dataColor.
 */
public final class CoreParticleSystem {

    private static final int PER_ORBIT = 8;

    /** Р Р°РґРёСѓСЃ, РїРѕСЃР»Рµ РєРѕС‚РѕСЂРѕРіРѕ СЃС‡РёС‚Р°РµС‚СЃСЏ, С‡С‚Рѕ С‡Р°СЃС‚РёС†С‹ СЃРІСЏР·Р°РЅС‹. */
    private static final float SEMANTIC_DIST_RATIO = 0.28f;

    /** РњР°РєСЃРёРјСѓРј Р»РёРЅРёР№ РІ РєР°РґСЂРµ - С‡С‚РѕР±С‹ РЅРµ Р±С‹Р»Рѕ "СЃРµС‚РєРё". */
    private static final int MAX_SEMANTIC_LINES = 3;

    /** РњР°РєСЃРёРјР°Р»СЊРЅР°СЏ alpha СЃРµРјР°РЅС‚РёС‡РµСЃРєРѕР№ Р»РёРЅРёРё. */
    private static final int SEMANTIC_MAX_ALPHA = 90;

    private final List<CoreDataParticle> particles;
    private final List<int[]> semanticPairs;

    public CoreParticleSystem() {
        this.particles     = buildParticles();
        this.semanticPairs = buildSemanticPairs();
    }

    private static List<CoreDataParticle> buildParticles() {
        List<CoreDataParticle> out = new ArrayList<>(PER_ORBIT * 3);
        Random rng = new Random(0x5EEDC0DE);

        addOrbit(out, rng, 0, CoreDataParticle.Kind.SYMBOL);
        addOrbit(out, rng, 1, CoreDataParticle.Kind.KEYWORD);
        addOrbit(out, rng, 2, CoreDataParticle.Kind.VALUE);
        return out;
    }

    private static void addOrbit(List<CoreDataParticle> out, Random rng,
                                 int orbitIndex, CoreDataParticle.Kind kind) {
        for (int i = 0; i < PER_ORBIT; i++) {
            String glyph = switch (kind) {
                case SYMBOL  -> CoreGlyphRegistry.pickSymbol(rng);
                case KEYWORD -> CoreGlyphRegistry.pickKeyword(rng);
                case VALUE   -> CoreGlyphRegistry.pickValue(rng);
            };
            float baseAngle   = (float)(rng.nextDouble() * Math.PI * 2.0);
            float lifespanSec = 8.0f + rng.nextFloat() * 7.0f;
            float phaseOffset = rng.nextFloat() * lifespanSec;
            float alphaMul    = 0.75f + rng.nextFloat() * 0.25f;
            float twinkle     = rng.nextFloat() * (float)(Math.PI * 2.0);

            out.add(new CoreDataParticle(kind, glyph, orbitIndex,
                                         baseAngle, lifespanSec,
                                         phaseOffset, alphaMul, twinkle));
        }
    }

    /**
     * РўРѕР»СЊРєРѕ РїР°СЂС‹ (keyword, value), РіРґРµ CoreDataRelations РїРѕРґС‚РІРµСЂР¶РґР°РµС‚
     * СЃРІСЏР·СЊ. РћСЂР±РёС‚Р°Р»СЊРЅС‹С… СЃРѕСЃРµРґРµР№ РќР• РІРєР»СЋС‡Р°РµРј - РёРЅР°С‡Рµ Р±СѓРґРµС‚ РІРёР·СѓР°Р»СЊРЅС‹Р№
     * С€СѓРј "РЅРµР№СЂРѕСЃРµС‚Рё".
     */
    private List<int[]> buildSemanticPairs() {
        List<int[]> out = new ArrayList<>(16);
        int n = particles.size();

        for (int i = 0; i < n; i++) {
            CoreDataParticle a = particles.get(i);
            if (a.kind != CoreDataParticle.Kind.KEYWORD) continue;

            for (int j = 0; j < n; j++) {
                CoreDataParticle b = particles.get(j);
                if (b.kind != CoreDataParticle.Kind.VALUE) continue;

                if (CoreDataRelations.related(a.glyph, b.glyph)) {
                    out.add(new int[]{i, j});
                }
            }
        }
        return out;
    }

    // ============================================================
    //  Р РµРЅРґРµСЂ
    // ============================================================

    /**
     * @param dataCollapse 0..1 - СЃР¶Р°С‚РёРµ РѕСЂР±РёС‚ Рє С†РµРЅС‚СЂСѓ
     * @param dataRgb      RGB (Р±РµР· alpha) С…РѕР»РѕРґРЅРѕРіРѕ С‚РѕРЅР° РґР°РЅРЅС‹С…
     */
    public void render(GuiGraphics g,
                       int cx, int cy,
                       float R,
                       float timeSec,
                       CoreState state,
                       int dataRgb,
                       float dataCollapse,
                       EnvironmentCamera camera,
                       int screenCx, int screenCy) {

        int n = particles.size();

        int[] xs = new int[n];
        int[] ys = new int[n];
        int[] alphas = new int[n];

        int glyphBase = CoreStateMachine.glyphAlpha(state);

        float radiusScale = 1f - 0.55f * CoreGeometry.smoothstep(dataCollapse);

        for (int i = 0; i < n; i++) {
            CoreDataParticle p = particles.get(i);
            CoreOrbit orbit = CoreOrbit.ORBITS[p.orbitIndex];

            float angle = p.angleAt(timeSec);
            float radius = R * orbit.radiusRatio() * radiusScale;
            float[] local = CoreGeometry.orbitPoint(radius,
                                                    orbit.squashY(), angle);

            int px = cx + (int)local[0];
            int py = cy + (int)local[1];
            int[] sh = camera.shear(px, py, screenCx, screenCy, 0.20f);
            xs[i] = sh[0];
            ys[i] = sh[1];
            alphas[i] = p.alphaAt(timeSec, glyphBase);
        }

        // --- РЎРµРјР°РЅС‚РёС‡РµСЃРєРёРµ СЃРІСЏР·Рё ---
        // РўРѕР»СЊРєРѕ РєРѕРіРґР° activity РІС‹СЃРѕРєР°СЏ, РѕР±Рµ С‡Р°СЃС‚РёС†С‹ Р¶РёРІС‹, СЂР°СЃСЃС‚РѕСЏРЅРёРµ
        // РјР°Р»РµРЅСЊРєРѕРµ, Рё Р»РёРјРёС‚ Р»РёРЅРёР№ РЅРµ РїСЂРµРІС‹С€РµРЅ.
        float activity = CoreStateMachine.streamActivity(state);
        int semanticAlpha = (int)(SEMANTIC_MAX_ALPHA * activity);

        if (semanticAlpha > 20) {
            float threshold = R * SEMANTIC_DIST_RATIO;
            float t2 = threshold * threshold;
            int drawn = 0;

            for (int[] pair : semanticPairs) {
                if (drawn >= MAX_SEMANTIC_LINES) break;

                int ia = pair[0], ib = pair[1];
                int dx = xs[ia] - xs[ib];
                int dy = ys[ia] - ys[ib];
                float d2 = (float)(dx * dx + dy * dy);
                if (d2 >= t2) continue;

                int minA = Math.min(alphas[ia], alphas[ib]);
                if (minA < 60) continue;

                float dNorm = (float)Math.sqrt(d2) / threshold;
                float falloff = 1f - dNorm;
                falloff *= falloff;

                int lineAlpha = (int)(falloff * semanticAlpha);
                lineAlpha = Math.min(lineAlpha, minA);
                if (lineAlpha <= 12) continue;

                int lineColor = (lineAlpha << 24) | (dataRgb & 0x00FFFFFF);
                drawLine(g, xs[ia], ys[ia], xs[ib], ys[ib], lineColor);
                drawn++;
            }
        }

        // --- Р“Р»РёС„С‹ ---
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < n; i++) {
            CoreDataParticle p = particles.get(i);
            int alpha = alphas[i];
            if (alpha <= 12) continue;

            int color = (alpha << 24) | (dataRgb & 0x00FFFFFF);
            int w = font.width(p.glyph);
            int gx = xs[i] - w / 2;
            int gy = ys[i] - 4;
            g.drawString(font, p.glyph, gx, gy, color, false);
        }
    }

    private static void drawLine(GuiGraphics g,
                                 int x1, int y1, int x2, int y2,
                                 int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            g.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 <  dx) { err += dx; y1 += sy; }
        }
    }
}