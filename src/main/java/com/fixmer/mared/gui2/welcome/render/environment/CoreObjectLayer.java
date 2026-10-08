package com.fixmer.mared.gui2.welcome.render.environment;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.framework.render.MaredColor;
import com.fixmer.mared.gui2.welcome.render.environment.core.CoreAnimation;
import com.fixmer.mared.gui2.welcome.render.environment.core.CoreGeometry;
import com.fixmer.mared.gui2.welcome.render.environment.core.CoreGlyphFormation;
import com.fixmer.mared.gui2.welcome.render.environment.core.CoreMemory;
import com.fixmer.mared.gui2.welcome.render.environment.core.CoreOrbit;
import com.fixmer.mared.gui2.welcome.render.environment.core.CoreParticleSystem;
import com.fixmer.mared.gui2.welcome.render.environment.core.CorePrototype;
import com.fixmer.mared.gui2.welcome.render.environment.core.CorePrototypeAssembly;
import com.fixmer.mared.gui2.welcome.render.environment.core.CoreState;
import com.fixmer.mared.gui2.welcome.render.environment.genesis.GenesisContext;
import com.fixmer.mared.gui2.welcome.render.environment.genesis.GenesisSequence;
import com.fixmer.mared.gui2.welcome.render.environment.genesis.GenesisStage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 1.5.37.3 + 1.5.37.4 + 1.5.37.4.1 + 1.5.37.4.2 + 1.5.38.0.
 *
 * Genesis Engine Skeleton: С‚Р°Р№РјР»Р°Р№РЅ РїРµСЂРµРµС…Р°Р» РІ GenesisSequence.
 * CoreObjectLayer С‚РµРїРµСЂСЊ РєР°Р¶РґС‹Р№ РєР°РґСЂ Р·Р°РїСЂР°С€РёРІР°РµС‚ GenesisContext Рё
 * РєРѕРЅРІРµСЂС‚РёСЂСѓРµС‚ РµРіРѕ РІ СЃС‚Р°СЂС‹Р№ CoreState РґР»СЏ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёС… СЂРµРЅРґРµСЂРµСЂРѕРІ.
 * Р›РѕРіРёРєР° РІРёР·СѓР°Р»СЊРЅС‹С… СЃР»РѕС‘РІ РќР• РјРµРЅСЏР»Р°СЃСЊ.
 *
 * РњР°РїРїРёРЅРі GenesisStage -> CoreState:
 *   BOOT          -> IDLE
 *   AWAKENING     -> IDLE
 *   PARSING       -> PARSING
 *   SEED          -> SCANNING
 *   CONSTRUCTION  -> BUILDING
 *   STABILIZATION -> STABILIZING
 *   LIVING        -> READY
 *
 * Fast mode РІРєР»СЋС‡Р°РµС‚СЃСЏ, РµСЃР»Рё CoreMemory.hasMemory() == true
 * (РїРѕР»СЊР·РѕРІР°С‚РµР»СЊ СѓР¶Рµ РѕС‚РєСЂС‹РІР°Р» Welcome СЂР°РЅСЊС€Рµ РІ СЌС‚РѕР№ СЃРµСЃСЃРёРё).
 */
public final class CoreObjectLayer implements EnvironmentLayer {

    private static final float R_RATIO        = 0.13f;
    private static final float SHEAR_K        = 0.15f;
    private static final float PARALLAX_DEPTH = 0.30f;

    private static final int COLD_WHITE = 0xFFFFFFFF;
    private static final int COLD_CYAN  = 0xFF7DDCFF;
    private static final int COLD_GRAY  = 0xFF4A6070;

    private final CoreParticleSystem    particles = new CoreParticleSystem();
    private final CorePrototype         prototype = new CorePrototype();
    private final CoreGlyphFormation    formation = new CoreGlyphFormation();
    private final CorePrototypeAssembly assembly  = new CorePrototypeAssembly();

    /** РћС‚СЃР»РµР¶РёРІР°РЅРёРµ РїРµСЂРµС…РѕРґРѕРІ РґР»СЏ debug-Р»РѕРіР°. */
    private GenesisStage lastLoggedStage = null;

    @Override
    public void render(GuiGraphics g,
                       int width, int height,
                       EnvironmentTime time,
                       EnvironmentPalette palette,
                       EnvironmentCamera camera) {

        if (width <= 0 || height <= 0) return;

        float t = time.seconds();
        float minWH = Math.min(width, height);
        float R = minWH * R_RATIO * CoreAnimation.breathingScale(t);

        int baseCx = width  / 2 + camera.offsetPxX(width,  PARALLAX_DEPTH);
        int baseCy = height / 2 + camera.offsetPxY(height, PARALLAX_DEPTH);
        int[] sh = camera.shear(baseCx, baseCy, width / 2, height / 2, SHEAR_K);
        int cx = sh[0];
        int cy = sh[1];

        int accent = palette.accent;

        int structuralRgb = mixRgb(COLD_WHITE, accent, 0.35f);
        int energyRgb     = mixRgb(COLD_CYAN,  accent, 0.40f);
        int dataRgb       = mixRgb(COLD_GRAY,  accent, 0.25f);

        int screenCx = width / 2;
        int screenCy = height / 2;

        // --- Genesis Engine ---
        boolean fastMode = CoreMemory.hasMemory();
        GenesisContext gctx = GenesisSequence.contextAt(
            t, fastMode, CoreMemory.lastModule());

        // Р›РѕРіРёСЂСѓРµРј СЃРјРµРЅСѓ СЃС‚Р°РґРёРё РѕРґРёРЅ СЂР°Р· - РґР»СЏ РѕС‚Р»Р°РґРєРё С‚Р°Р№РјР»Р°Р№РЅР°
        if (gctx.stage() != lastLoggedStage) {
            if (lastLoggedStage != null) {
                Mared.LOGGER.debug("[genesis] {} -> {} (t={}s, fast={})",
                    lastLoggedStage, gctx.stage(),
                    String.format("%.2f", t), fastMode);
            }
            lastLoggedStage = gctx.stage();
        }

        CoreState state = mapToCoreState(gctx.stage());
        float stateProgress = gctx.stageProgress();

        float dataCollapse = collapseAt(gctx.stage(), stateProgress);

        // --- 1. Shadow ---
        drawCoreShadow(g, cx, cy, R, t, accent);

        // --- 2. Shells ---
        drawShells(g, cx, cy, R, t, structuralRgb, camera, screenCx, screenCy);

        // --- 3. Orbits ---
        drawOrbits(g, cx, cy, R, t, structuralRgb, camera, screenCx, screenCy);

        // --- 4. Energy veins ---
        drawEnergyVeins(g, cx, cy, R, t, energyRgb, camera,
                        screenCx, screenCy);

        // --- 5. Inner energy ---
        drawInnerEnergy(g, cx, cy, R, t, energyRgb);

        // --- 6. Specular ---
        drawSpecular(g, cx, cy, R, t);

        // --- 7. Data System ---
        particles.render(g, cx, cy, R, t, state, dataRgb, dataCollapse,
                         camera, screenCx, screenCy);

        // --- 8. Glyph Formation ---
        Font font = Minecraft.getInstance().font;
        formation.render(g, font, cx, cy, R, t, state, structuralRgb);

        // --- 9. Assembly ---
        assembly.render(g, cx, cy, R, t, state, stateProgress, structuralRgb);

        // --- 10. Prototype ---
        prototype.render(g, cx, cy, R, t, state, stateProgress,
                         accent, structuralRgb, energyRgb);

        // --- 11. Flash ---
        drawFlash(g, cx, cy, R, t, energyRgb);
    }

    // ============================================================
    //  РњР°РїРїРёРЅРі Genesis -> Core (РЅР° РІСЂРµРјСЏ РјРёРіСЂР°С†РёРё)
    // ============================================================

    private static CoreState mapToCoreState(GenesisStage stage) {
        return switch (stage) {
            case BOOT          -> CoreState.IDLE;
            case AWAKENING     -> CoreState.IDLE;
            case PARSING       -> CoreState.PARSING;
            case SEED          -> CoreState.SCANNING;
            case CONSTRUCTION  -> CoreState.BUILDING;
            case STABILIZATION -> CoreState.STABILIZING;
            case LIVING        -> CoreState.READY;
        };
    }

    private static int mixRgb(int base, int accent, float tintAmount) {
        float t = tintAmount;
        if (t < 0f) t = 0f; else if (t > 1f) t = 1f;

        int br = (base >> 16) & 0xFF;
        int bg = (base >>  8) & 0xFF;
        int bb =  base        & 0xFF;

        int ar = (accent >> 16) & 0xFF;
        int ag = (accent >>  8) & 0xFF;
        int ab =  accent        & 0xFF;

        int rr = (int)(br + (ar - br) * t);
        int rg = (int)(bg + (ag - bg) * t);
        int rb = (int)(bb + (ab - bb) * t);

        return (rr << 16) | (rg << 8) | rb;
    }

    // ============================================================
    //  dataCollapse РїРѕ СЃС‚Р°РґРёСЏРј Genesis
    // ============================================================

    private static float collapseAt(GenesisStage stage, float progress) {
        float p = CoreGeometry.smoothstep(progress);
        return switch (stage) {
            case BOOT          -> 0f;
            case AWAKENING     -> 0f;
            case PARSING       -> 0.05f + 0.20f * p;
            case SEED          -> 0.25f + 0.20f * p;
            case CONSTRUCTION  -> 0.45f + 0.50f * p;
            case STABILIZATION -> 0.95f - 0.30f * p;
            case LIVING        -> 0.65f - 0.20f * p;
        };
    }

    // ============================================================
    //  1. Shadow
    // ============================================================

    private void drawCoreShadow(GuiGraphics g, int cx, int cy,
                                float R, float t, int accent) {
        int alpha = CoreAnimation.coreShadowAlpha(t);
        if (alpha <= 0) return;

        int radius = (int)(R * 1.15f);
        int rgb = MaredColor.darken(accent, 0.65f) & 0x00FFFFFF;
        CoreGeometry.drawSoftEllipse(g, cx, cy, radius, radius,
                                     rgb, 12, alpha);
    }

    // ============================================================
    //  2. Shells
    // ============================================================

    private void drawShells(GuiGraphics g, int cx, int cy,
                            float R, float t, int structuralRgb,
                            EnvironmentCamera camera,
                            int screenCx, int screenCy) {

        int[] outerXs = new int[7];
        int[] outerYs = new int[7];
        buildShellPoints(outerXs, outerYs, cx, cy, R, t, R * 0.015f,
                         camera, screenCx, screenCy);

        int outerOutline = (160 << 24) | structuralRgb;
        CoreGeometry.drawPolygonOutline(g, outerXs, outerYs, outerOutline, 1);

        int outerDots = (200 << 24) | structuralRgb;
        for (int i = 0; i < 7; i++) {
            CoreGeometry.drawDiamond(g, outerXs[i], outerYs[i], 1, outerDots);
        }

        int midRgb = (int)(structuralRgb * 0.85f) & 0x00FFFFFF;
        int[] midXs = new int[7];
        int[] midYs = new int[7];
        buildShellPoints(midXs, midYs, cx, cy, R * 0.88f, t + 1.3f,
                         R * 0.020f, camera, screenCx, screenCy);
        int midOutline = (110 << 24) | midRgb;
        CoreGeometry.drawPolygonOutline(g, midXs, midYs, midOutline, 1);

        int depthRgb = (int)(structuralRgb * 0.65f) & 0x00FFFFFF;
        int[] depthXs = new int[7];
        int[] depthYs = new int[7];
        buildShellPoints(depthXs, depthYs, cx, cy, R * 0.72f, t + 3.7f,
                         R * 0.025f, camera, screenCx, screenCy);
        int depthOutline = (70 << 24) | depthRgb;
        CoreGeometry.drawPolygonOutline(g, depthXs, depthYs, depthOutline, 1);

        int glassFill = (8 << 24) | (structuralRgb & 0x00FFFFFF);
        CoreGeometry.fillPolygon(g, outerXs, outerYs, glassFill);
    }

    private void buildShellPoints(int[] xs, int[] ys,
                                  int cx, int cy, float R, float t,
                                  float wobbleAmp,
                                  EnvironmentCamera camera,
                                  int screenCx, int screenCy) {
        int n = CoreGeometry.POLYGON_ANGLES.length;
        for (int i = 0; i < n; i++) {
            float[] local = CoreGeometry.polygonVertex(i, R, t, wobbleAmp);
            int px = cx + (int)local[0];
            int py = cy + (int)local[1];
            int[] s = camera.shear(px, py, screenCx, screenCy, SHEAR_K);
            xs[i] = s[0];
            ys[i] = s[1];
        }
    }

    // ============================================================
    //  3. Orbits
    // ============================================================

    private void drawOrbits(GuiGraphics g, int cx, int cy,
                            float R, float t, int structuralRgb,
                            EnvironmentCamera camera,
                            int screenCx, int screenCy) {

        int[] sc = camera.shear(cx, cy, screenCx, screenCy, SHEAR_K);
        int scx = sc[0];
        int scy = sc[1];

        for (int oi = 0; oi < CoreOrbit.ORBITS.length; oi++) {
            CoreOrbit orbit = CoreOrbit.ORBITS[oi];
            int rx = (int)(R * orbit.radiusRatio());
            int ry = (int)(rx * orbit.squashY());

            int alpha = 30 + oi * 8;
            int color = (alpha << 24) | (structuralRgb & 0x00FFFFFF);

            CoreGeometry.drawEllipseOutline(g, scx, scy, rx, ry, color);
        }
    }

    // ============================================================
    //  4. Energy veins
    // ============================================================

    private void drawEnergyVeins(GuiGraphics g, int cx, int cy,
                                 float R, float t, int energyRgb,
                                 EnvironmentCamera camera,
                                 int screenCx, int screenCy) {

        int alpha = CoreAnimation.energyVeinAlpha(t) + 10;
        if (alpha <= 0) return;

        int color = (alpha << 24) | (energyRgb & 0x00FFFFFF);

        int n = CoreGeometry.POLYGON_ANGLES.length;
        float offset = (float)Math.PI / n;

        for (int i = 0; i < n; i++) {
            float angle = CoreGeometry.POLYGON_ANGLES[i] + offset;
            float innerR = R * 0.18f;
            float outerR = R * 0.60f;

            int x1 = cx + (int)(Math.cos(angle) * innerR);
            int y1 = cy + (int)(Math.sin(angle) * innerR);
            int x2 = cx + (int)(Math.cos(angle) * outerR);
            int y2 = cy + (int)(Math.sin(angle) * outerR);

            int[] s1 = camera.shear(x1, y1, screenCx, screenCy, SHEAR_K);
            int[] s2 = camera.shear(x2, y2, screenCx, screenCy, SHEAR_K);

            CoreGeometry.drawLine(g, s1[0], s1[1], s2[0], s2[1], color, 1);
        }
    }

    // ============================================================
    //  5. Inner energy
    // ============================================================

    private void drawInnerEnergy(GuiGraphics g, int cx, int cy,
                                 float R, float t, int energyRgb) {

        int coronaAlpha = 55 + (int)(CoreAnimation.flashPulse(t) * 40);
        int coronaRadius = (int)(R * 0.26f);
        CoreGeometry.drawSoftEllipse(g, cx, cy,
                                     coronaRadius, coronaRadius,
                                     energyRgb, 10, coronaAlpha);

        float baseR = R * 0.18f;
        float radius = CoreAnimation.innerEnergyRadius(baseR, t);
        int alpha = CoreAnimation.innerEnergyAlpha(t);
        CoreGeometry.fillDisc(g, cx, cy, (int)radius, energyRgb, alpha, 1);

        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
    }

    // ============================================================
    //  6. Specular
    // ============================================================

    private void drawSpecular(GuiGraphics g, int cx, int cy,
                              float R, float t) {
        int sx = cx - (int)(R * 0.35f);
        int sy = cy - (int)(R * 0.40f);

        int rx = (int)(R * 0.14f);
        int ry = (int)(R * 0.07f);

        float pulse = 0.85f + 0.15f * (float)Math.sin(t * 1.1f);
        int alpha = (int)(100 * pulse);

        CoreGeometry.drawSoftEllipse(g, sx, sy, rx, ry, 0xFFFFFF, 5, alpha);
    }

    // ============================================================
    //  11. Flash
    // ============================================================

    private void drawFlash(GuiGraphics g, int cx, int cy,
                           float R, float t, int energyRgb) {
        float flash = CoreAnimation.flashPulse(t);
        if (flash <= 0.02f) return;

        int alpha = (int)(flash * 100);
        int radius = (int)(R * 0.22f);

        CoreGeometry.drawSoftEllipse(g, cx, cy, radius, radius,
                                     energyRgb, 8, alpha);
    }
}
