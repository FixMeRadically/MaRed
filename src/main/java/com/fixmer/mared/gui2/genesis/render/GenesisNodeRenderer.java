package com.fixmer.mared.gui2.genesis.render;

import com.fixmer.mared.gui2.genesis.GenesisCamera;
import com.fixmer.mared.gui2.genesis.node.GenesisNode;
import com.fixmer.mared.gui2.genesis.node.GenesisNodeData;
import com.fixmer.mared.gui2.genesis.node.GenesisSubParticle;
import com.fixmer.mared.gui2.genesis.node.Shape3D;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

import java.util.List;

/**
 * v11:
 *  - РЎС‚СЂРѕРіРѕ СЂР°Р·РґРµР»РµРЅРѕ РЅР° РґРІР° РїСЂРѕС…РѕРґР°:
 *      renderVisual  вЂ” РІСЃС‘, С‡С‚Рѕ РёРґС‘С‚ С‡РµСЂРµР· VertexConsumer GLOW (shape,
 *                      shadow, ring, dots, cobweb, blik).
 *      renderGlyphs  вЂ” РІСЃС‘, С‡С‚Рѕ РёРґС‘С‚ С‡РµСЂРµР· GuiGraphics.drawString.
 *    РЎРјРµС€РµРЅРёРµ РІ РѕРґРЅРѕРј РїСЂРѕС…РѕРґРµ РІС‹Р·С‹РІР°Р»Рѕ "Not building!", РїРѕС‚РѕРјСѓ С‡С‚Рѕ
 *    drawString Р·Р°РІРµСЂС€Р°РµС‚ Р°РєС‚РёРІРЅС‹Р№ batch GLOW Рё РїРµСЂРµРєР»СЋС‡Р°РµС‚СЃСЏ РЅР°
 *    RenderType.text(...).
 *  - zoom РЅРµ РїСЂРёРјРµРЅСЏРµС‚СЃСЏ Рє РїРѕР·РёС†РёСЏРј (GenesisCamera.worldToScreen).
 *  - zoom РїСЂРёРјРµРЅСЏРµС‚СЃСЏ Рє СЂР°Р·РјРµСЂСѓ (scale = camera.zoom() * perspScale).
 *  - СЃСѓР±-С‡Р°СЃС‚РёС†С‹ СЃС‡РёС‚Р°СЋС‚СЃСЏ РІ screen-space РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅРѕ С†РµРЅС‚СЂР° РѕР±СЉРµРєС‚Р°.
 *  - 3D-С„РѕСЂРјР° СЂРёСЃСѓРµС‚СЃСЏ Р”Рћ СЃСѓР±-С‡Р°СЃС‚РёС†, СЌС„С„РµРєС‚С‹ РїРѕРІРµСЂС….
 *  - С‚РѕРЅРєР°СЏ Р·Р°Р»РёРІРєР° РіСЂР°РЅРµР№.
 */
public final class GenesisNodeRenderer {

    private static final float COBWEB_DIST_PX = 90f;
    private static final float GLYPH_ZOOM_MIN = 1.30f;
    private static final float SHAPE_SPIN_Y   = 0.20f;
    private static final int   SHADOW_ALPHA   = 90;

    private static final float FILL_ALPHA_MIN = 0.10f;
    private static final float FILL_ALPHA_MAX = 0.26f;

    private static final int MAX_SHAPE_VERTS = 16;
    private static final int MAX_SUB         = 16;

    private final float[] pxCache = new float[MAX_SHAPE_VERTS];
    private final float[] pyCache = new float[MAX_SHAPE_VERTS];
    private final float[] spxCache = new float[MAX_SUB];
    private final float[] spyCache = new float[MAX_SUB];
    private final float[] spACache = new float[MAX_SUB];

    // РЈСЃС‚Р°РЅР°РІР»РёРІР°РµС‚СЃСЏ GenesisRenderer РїРµСЂРµРґ РІС‹Р·РѕРІРѕРј renderVisual.
    public float cachedScreenX;
    public float cachedScreenY;
    public float cachedDepth;
    public float cachedPerspScale;
    public float cachedDepthAlpha;
    public float dimFactor = 1f;

    // ============================================================
    //  РџСЂРѕС…РѕРґ 1: РІРёР·СѓР°Р» (VertexConsumer)
    // ============================================================

    public void renderVisual(VertexConsumer vc, Matrix4f m,
                             GenesisNode node,
                             GenesisCamera camera,
                             int screenW, int screenH,
                             float orbitTimeSec) {

        GenesisNodeData d = node.data();

        float cx = cachedScreenX;
        float cy = cachedScreenY;
        float perspScale = cachedPerspScale;
        float alphaMul = cachedDepthAlpha * dimFactor;
        if (alphaMul < 0.04f) return;

        float pulse = node.currentPulse(orbitTimeSec);
        // v13: zoom применяется только к focused-узлу.
        float zoomMul = node.isFocused() ? camera.zoom() : 1f;
        float scale = zoomMul * perspScale * node.currentScale() * pulse;
        float r = d.radius() * scale;
        if (r <= 0.5f) return;

        int rgb = animateColor(
            d.defaultColor() & 0x00FFFFFF,
            orbitTimeSec,
            node.pulsePhase());

        float fr = ((rgb >> 16) & 0xFF) / 255f;
        float fg = ((rgb >>  8) & 0xFF) / 255f;
        float fb = ( rgb        & 0xFF) / 255f;

        // --- 1. Shadow ---
        if (alphaMul > 0.15f) {
            float len = (float)Math.sqrt(
                node.worldX() * node.worldX()
              + node.worldY() * node.worldY()
              + node.worldZ() * node.worldZ());
            if (len > 1f) {
                float nx = node.worldX() / len;
                float ny = node.worldY() / len;
                float nz = node.worldZ() / len;
                float[] sp = camera.worldToScreen(
                    node.worldX() + nx * r * 0.4f,
                    node.worldY() + ny * r * 0.4f,
                    node.worldZ() + nz * r * 0.4f,
                    screenW, screenH);
                float shR = r * 1.05f;
                float shA = SHADOW_ALPHA * alphaMul / 255f;
                if (shR > 4f && shA > 0.024f) {
                    GenesisDraw.glowCircle(vc, m, sp[0], sp[1], shR,
                        0f, 0f, 0f, shA);
                }
            }
        }

        // --- 2. 3D-С„РѕСЂРјР° (Р·Р°Р»РёРІРєР° + СЂС‘Р±СЂР°) ---
        Shape3D shape = d.shape();
        if (shape != null && !shape.isEmpty()) {
            drawSolidShape(vc, m, camera, screenW, screenH,
                node.worldX(), node.worldY(), node.worldZ(),
                r, shape, orbitTimeSec,
                node.spinYaw(), node.spinPitch(),
                fr, fg, fb, alphaMul);
        }

        // --- 3. Focus ring ---
        if (node.focus() > 0.05f) {
            float ringR = r * (1.9f + node.focus() * 0.7f);
            float ringAlpha = node.focus() * 170f * alphaMul / 255f;
            if (ringAlpha > 0.016f) {
                GenesisDraw.ring(vc, m, cx, cy, ringR - 1f, ringR + 1f,
                    fr, fg, fb, ringAlpha);
            }
        }

        // --- 4. Sub-particles РІ screen-space ---
        List<GenesisSubParticle> sps = node.subParticles();
        int spN = Math.min(sps.size(), MAX_SUB);
        float spBaseAlpha = (70f + node.morph() * 130f) / 255f;
        if (spBaseAlpha > 1f) spBaseAlpha = 1f;

        for (int i = 0; i < spN; i++) {
            GenesisSubParticle sp = sps.get(i);
            float[] off = sp.offsetAt(r * 1.35f, orbitTimeSec);
            spxCache[i] = cx + off[0];
            spyCache[i] = cy - off[1];
            spACache[i] = sp.alphaAt() * spBaseAlpha * alphaMul;
        }

        // --- 5. Cobweb ---
        for (int i = 0; i < spN; i++) {
            if (spACache[i] < 30f / 255f) continue;
            for (int j = i + 1; j < spN; j++) {
                if (spACache[j] < 30f / 255f) continue;
                float dx = spxCache[i] - spxCache[j];
                float dy = spyCache[i] - spyCache[j];
                float d2 = dx * dx + dy * dy;
                if (d2 > COBWEB_DIST_PX * COBWEB_DIST_PX) continue;
                float dn = (float)Math.sqrt(d2) / COBWEB_DIST_PX;
                float t = 1f - dn;
                float a = t * t * 80f * alphaMul / 255f;
                if (a > Math.min(spACache[i], spACache[j])) {
                    a = Math.min(spACache[i], spACache[j]);
                }
                if (a < 10f / 255f) continue;
                GenesisDraw.line(vc, m,
                    spxCache[i], spyCache[i], spxCache[j], spyCache[j],
                    1f, fr, fg, fb, a);
            }
        }

        // --- 6. РўРѕС‡РєРё СЃСѓР±-С‡Р°СЃС‚РёС† (С‚РѕР»СЊРєРѕ РїРѕРєР° РЅРµ РїРѕРєР°Р·С‹РІР°РµРј РіР»РёС„С‹) ---
        boolean showGlyphs = camera.zoom() > GLYPH_ZOOM_MIN;
        if (!showGlyphs) {
            for (int i = 0; i < spN; i++) {
                float a = spACache[i];
                if (a < 20f / 255f) continue;
                GenesisSubParticle sp = sps.get(i);
                float sz = sp.size < 1.6f ? 2f : 3f;
                GenesisDraw.quad(vc, m,
                    spxCache[i] - sz * 0.5f, spyCache[i] - sz * 0.5f, sz, sz,
                    fr, fg, fb, a);
            }
        }

        // --- 7. Р‘Р»РёРє ---
        float hx = cx - r * 0.28f;
        float hy = cy - r * 0.32f;
        float hR = r * 0.42f;
        GenesisDraw.glowCircle(vc, m, hx, hy, hR,
            1f, 1f, 1f, 0.20f * alphaMul);

        float hx2 = cx - r * 0.35f;
        float hy2 = cy - r * 0.38f;
        float hR2 = r * 0.14f;
        GenesisDraw.glowCircle(vc, m, hx2, hy2, hR2,
            1f, 1f, 1f, 0.50f * alphaMul);
    }

    // ============================================================
    //  РџСЂРѕС…РѕРґ 2: РіР»РёС„С‹ (GuiGraphics)
    // ============================================================

    /**
     * Р РёСЃСѓРµС‚ С‚РµРєСЃС‚ СЃСѓР±-С‡Р°СЃС‚РёС† С‡РµСЂРµР· GuiGraphics.drawString. Р’С‹Р·С‹РІР°С‚СЊ
     * РџРћРЎР›Р• g.bufferSource().endBatch(GLOW). РџРѕР·РёС†РёРё СЃСѓР±-С‡Р°СЃС‚РёС†
     * РїРµСЂРµСЃС‡РёС‚С‹РІР°СЋС‚СЃСЏ Р·Р°РЅРѕРІРѕ вЂ” СЌС‚Рѕ РґРµС€С‘РІР°СЏ O(N) РѕРїРµСЂР°С†РёСЏ, Р·Р°С‚Рѕ РЅРµ
     * РЅСѓР¶РЅРѕ С‚Р°СЃРєР°С‚СЊ СЃРѕСЃС‚РѕСЏРЅРёРµ РјРµР¶РґСѓ РїСЂРѕС…РѕРґР°РјРё.
     *
     * @param dimFactor РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ С‚РµРј Р¶Рµ, С‡С‚Рѕ Р±С‹Р» РїСЂРё renderVisual
     *                  РґР»СЏ СЌС‚РѕРіРѕ СѓР·Р»Р°, РёРЅР°С‡Рµ alpha РіР»РёС„РѕРІ РЅРµ СЃРѕРІРїР°РґС‘С‚.
     */
    public void renderGlyphs(GuiGraphics g,
                             GenesisNode node,
                             GenesisCamera camera,
                             int screenW, int screenH,
                             float orbitTimeSec,
                             float dimFactor) {

        if (camera.zoom() <= GLYPH_ZOOM_MIN) return;

        GenesisNodeData d = node.data();

        float[] s = camera.worldToScreen(
            node.worldX(), node.worldY(), node.worldZ(), screenW, screenH);
        float cx = s[0];
        float cy = s[1];
        float alphaMul = camera.depthAlpha(s[2]) / 255f * dimFactor;
        if (alphaMul < 0.04f) return;

        float pulse = node.currentPulse(orbitTimeSec);
        // v13: zoom применяется только к focused-узлу.
        float zoomMul = node.isFocused() ? camera.zoom() : 1f;
        float scale = zoomMul * camera.perspectiveScale(s[2])
                    * node.currentScale() * pulse;
        float r = d.radius() * scale;
        if (r <= 0.5f) return;

        int rgb = d.defaultColor() & 0x00FFFFFF;

        List<GenesisSubParticle> sps = node.subParticles();
        int spN = Math.min(sps.size(), MAX_SUB);
        float spBaseAlpha = (70f + node.morph() * 130f) / 255f;
        if (spBaseAlpha > 1f) spBaseAlpha = 1f;

        Font font = Minecraft.getInstance().font;

        for (int i = 0; i < spN; i++) {
            GenesisSubParticle sp = sps.get(i);
            float a = sp.alphaAt() * spBaseAlpha * alphaMul;
            if (a < 20f / 255f) continue;

            float[] off = sp.offsetAt(r * 1.35f, orbitTimeSec);
            float x = cx + off[0];
            float y = cy - off[1];

            int w = font.width(sp.glyph);
            int alphaByte = (int)(a * 0.75f * 255f);
            if (alphaByte <= 0) continue;

            g.drawString(font, sp.glyph,
                (int)x - w / 2, (int)y - 4,
                (alphaByte << 24) | rgb,
                false);
        }
    }

    // ============================================================
    //  Р’РЅСѓС‚СЂРµРЅРЅРµРµ
    // ============================================================

    private static int animateColor(int baseRgb, float timeSec, float phase) {
        int r = (baseRgb >> 16) & 0xFF;
        int g = (baseRgb >> 8) & 0xFF;
        int b = baseRgb & 0xFF;

        float t1 = (float)Math.sin(timeSec * 0.42f + phase);
        float t2 = (float)Math.sin(timeSec * 0.31f + phase * 1.7f + 1.1f);
        float t3 = (float)Math.sin(timeSec * 0.55f + phase * 0.6f + 2.3f);

        float rMul = 1f + t1 * 0.10f;
        float gMul = 1f + t2 * 0.08f;
        float bMul = 1f - t1 * 0.06f + t3 * 0.05f;

        r = clamp((int)(r * rMul), 0, 255);
        g = clamp((int)(g * gMul), 0, 255);
        b = clamp((int)(b * bMul), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void drawSolidShape(VertexConsumer vc, Matrix4f m,
                                 GenesisCamera camera,
                                 int sw, int sh,
                                 float wx, float wy, float wz,
                                 float r, Shape3D shape,
                                 float timeSec,
                                 float spinYaw, float spinPitch,
                                 float fr, float fg, float fb,
                                 float alphaMul) {

        float[][] verts = shape.verts;
        int n = Math.min(verts.length, MAX_SHAPE_VERTS);

        float rotY = timeSec * SHAPE_SPIN_Y + spinYaw;
        float rotX = spinPitch;

        float cosY = (float)Math.cos(rotY);
        float sinY = (float)Math.sin(rotY);
        float cosX = (float)Math.cos(rotX);
        float sinX = (float)Math.sin(rotX);

        for (int i = 0; i < n; i++) {
            float vx = verts[i][0];
            float vy = verts[i][1];
            float vz = verts[i][2];

            float xr = vx * cosY - vz * sinY;
            float zr = vx * sinY + vz * cosY;
            float yr = vy * cosX - zr * sinX;
            float zr2 = vy * sinX + zr * cosX;

            float[] p = camera.worldToScreen(
                wx + xr * r, wy + yr * r, wz + zr2 * r, sw, sh);
            pxCache[i] = p[0];
            pyCache[i] = p[1];
        }

        float fillAlpha = FILL_ALPHA_MIN
            + (FILL_ALPHA_MAX - FILL_ALPHA_MIN) * alphaMul;
        fillAlpha = clamp01(fillAlpha);

        for (int[] f : shape.faces) {
            if (!isFrontFacing(pxCache, pyCache, f)) continue;
            fillFaceFan(vc, m, pxCache, pyCache, f, fr, fg, fb, fillAlpha);
        }

        float outlineAlpha = clamp01(0.35f + 0.55f * alphaMul);
        float lr = fr + (1f - fr) * 0.45f;
        float lg = fg + (1f - fg) * 0.45f;
        float lb = fb + (1f - fb) * 0.45f;

        for (int[] e : shape.edges) {
            GenesisDraw.line(vc, m,
                pxCache[e[0]], pyCache[e[0]],
                pxCache[e[1]], pyCache[e[1]],
                1f, lr, lg, lb, outlineAlpha);
        }

        float[] sc = camera.worldToScreen(wx, wy, wz, sw, sh);
        float coreSize = Math.max(2f, r / 5f);
        float coreAlpha = clamp01(0.55f + 0.35f * alphaMul);
        GenesisDraw.quad(vc, m,
            sc[0] - coreSize * 0.5f, sc[1] - coreSize * 0.5f,
            coreSize, coreSize,
            1f, 1f, 1f, coreAlpha);
    }

    private static void fillFaceFan(VertexConsumer vc, Matrix4f m,
                                     float[] px, float[] py, int[] face,
                                     float fr, float fg, float fb, float alpha) {
        int n = face.length;
        if (n < 3) return;
        int v0 = face[0];
        for (int i = 1; i < n - 1; i++) {
            int v1 = face[i];
            int v2 = face[i + 1];
            vc.addVertex(m, px[v0], py[v0], 0).setColor(fr, fg, fb, alpha);
            vc.addVertex(m, px[v1], py[v1], 0).setColor(fr, fg, fb, alpha);
            vc.addVertex(m, px[v2], py[v2], 0).setColor(fr, fg, fb, alpha);
        }
    }

    private static boolean isFrontFacing(float[] px, float[] py, int[] face) {
        if (face.length < 3) return true;
        float cross = 0f;
        for (int i = 0; i < face.length; i++) {
            int a = face[i];
            int b = face[(i + 1) % face.length];
            cross += px[a] * py[b] - px[b] * py[a];
        }
        return cross > 0;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}