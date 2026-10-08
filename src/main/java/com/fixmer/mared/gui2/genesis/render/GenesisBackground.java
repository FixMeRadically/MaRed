package com.fixmer.mared.gui2.genesis.render;

import com.fixmer.mared.gui2.genesis.GenesisCamera;
import com.fixmer.mared.gui2.genesis.GenesisWorld;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * v10:
 *  - beams РїРѕР»РЅРѕСЃС‚СЊСЋ СѓРґР°Р»РµРЅС‹ (РЅРµ Р±С‹Р»Рѕ СЃРјС‹СЃР»Р°, РјРµС€Р°Р»Рё РІРѕСЃРїСЂРёСЏС‚РёСЋ).
 *  - РЅРµР±Рѕ (nebulas/stars/dust) РЅРµ РјР°СЃС€С‚Р°Р±РёСЂСѓРµС‚СЃСЏ zoom'РѕРј: Сѓ РЅРµРіРѕ
 *    С„РёРєСЃРёСЂРѕРІР°РЅРЅР°СЏ РїРµСЂСЃРїРµРєС‚РёРІР° SKY_PERSPECTIVE.
 *  - С‚СѓРјР°РЅРЅРѕСЃС‚Рё: СЃР»РѕРё Р±РѕР»РµРµ РїСЂРѕР·СЂР°С‡РЅС‹Рµ (0.12-0.22 alpha РІРјРµСЃС‚Рѕ 0.32-0.55).
 */
public final class GenesisBackground {

    private static final int STAR_COUNT    = 80;
    private static final int DUST_COUNT    = 32;
    private static final int NEBULA_COUNT  = 6;
    private static final int NEBULA_LAYERS = 6;

    private static final float NEBULA_R_MIN = 2200f;
    private static final float NEBULA_R_MAX = 2800f;

    private static final float SKY_CAMERA_DIST = 1200f;
    private static final float SKY_BASE_DIST   = 3700f;

    private static final class Star {
        float x, y, z;
        int brightness, size;
        float phase;
    }

    private static final class Dust {
        float x, y, z;
        float phase;
        int alpha;
    }

    private static final class NebulaLayer {
        float dx, dy;
        float rx, ry;
        float alphaMul;
        float r, g, b;
    }

    private static final class Nebula {
        float x, y, z;
        float phase, drift;
        final NebulaLayer[] layers = new NebulaLayer[NEBULA_LAYERS];
    }

    private final Star[]   stars   = new Star[STAR_COUNT];
    private final Dust[]   dust    = new Dust[DUST_COUNT];
    private final Nebula[] nebulas = new Nebula[NEBULA_COUNT];

    public GenesisBackground() {
        Random rng = new Random(0xFACEB00C);

        for (int i = 0; i < STAR_COUNT; i++) {
            Star s = new Star();
            s.x = (rng.nextFloat() - 0.5f) * 4400f;
            s.y = (rng.nextFloat() - 0.5f) * 2600f;
            s.z = (rng.nextFloat() - 0.5f) * 4400f;
            s.brightness = 40 + rng.nextInt(150);
            s.size = rng.nextFloat() < 0.2f ? 2 : 1;
            s.phase = rng.nextFloat() * (float)(Math.PI * 2);
            stars[i] = s;
        }

        for (int i = 0; i < DUST_COUNT; i++) {
            Dust d = new Dust();
            float r = 500f + rng.nextFloat() * 900f;
            float theta = rng.nextFloat() * (float)(Math.PI * 2);
            float phi = (rng.nextFloat() - 0.5f) * 1.4f;
            d.x = r * (float)Math.cos(theta) * (float)Math.cos(phi);
            d.y = r * (float)Math.sin(phi);
            d.z = r * (float)Math.sin(theta) * (float)Math.cos(phi);
            d.phase = rng.nextFloat() * (float)(Math.PI * 2);
            d.alpha = 30 + rng.nextInt(40);
            dust[i] = d;
        }

        float[][] palettes = {
            { 0.42f, 0.55f, 1.00f,   0.60f, 0.30f, 0.90f,   0.85f, 0.88f, 1.00f },
            { 1.00f, 0.42f, 0.62f,   0.55f, 0.30f, 0.95f,   0.95f, 0.82f, 0.90f },
            { 0.55f, 0.30f, 1.00f,   0.30f, 0.55f, 0.95f,   0.88f, 0.85f, 1.00f },
            { 0.42f, 0.72f, 1.00f,   0.60f, 0.60f, 1.00f,   0.92f, 0.96f, 1.00f },
            { 0.62f, 0.42f, 1.00f,   0.40f, 0.65f, 0.90f,   0.90f, 0.88f, 1.00f },
            { 0.75f, 0.42f, 0.90f,   0.45f, 0.35f, 0.95f,   0.95f, 0.85f, 0.98f },
        };

        for (int i = 0; i < NEBULA_COUNT; i++) {
            Nebula n = new Nebula();
            float phi = (float)Math.acos(1.0 - 2.0 * (i + 0.5) / NEBULA_COUNT);
            float theta = (float)(i * Math.PI * (3.0 - Math.sqrt(5.0)));
            float r = NEBULA_R_MIN + rng.nextFloat() * (NEBULA_R_MAX - NEBULA_R_MIN);
            n.x = r * (float)(Math.sin(phi) * Math.cos(theta));
            n.y = r * (float)(Math.cos(phi));
            n.z = r * (float)(Math.sin(phi) * Math.sin(theta));
            n.phase = rng.nextFloat() * (float)(Math.PI * 2);
            n.drift = 0.025f + rng.nextFloat() * 0.035f;

            float[] pal = palettes[i % palettes.length];
            float pr = pal[0], pg = pal[1], pb = pal[2];
            float sr = pal[3], sg = pal[4], sb = pal[5];
            float hr = pal[6], hg = pal[7], hb = pal[8];

            for (int k = 0; k < NEBULA_LAYERS; k++) {
                NebulaLayer L = new NebulaLayer();
                float spread, sizeMul, aMul, cr, cg, cb;

                if (k < 2) {
                    spread = 700f; sizeMul = 1.6f; aMul = 0.10f;
                    cr = sr; cg = sg; cb = sb;
                } else if (k < 4) {
                    spread = 480f; sizeMul = 1.1f; aMul = 0.14f;
                    cr = pr; cg = pg; cb = pb;
                } else if (k == 4) {
                    spread = 280f; sizeMul = 0.75f; aMul = 0.12f;
                    cr = hr; cg = hg; cb = hb;
                } else {
                    spread = 160f; sizeMul = 0.45f; aMul = 0.16f;
                    cr = 1.0f; cg = 1.0f; cb = 1.0f;
                }

                L.dx = (rng.nextFloat() - 0.5f) * spread;
                L.dy = (rng.nextFloat() - 0.5f) * spread * 0.8f;
                L.rx = (480f + rng.nextFloat() * 260f) * sizeMul;
                L.ry = (300f + rng.nextFloat() * 200f) * sizeMul;
                L.alphaMul = aMul * (0.85f + rng.nextFloat() * 0.30f);
                L.r = cr; L.g = cg; L.b = cb;
                n.layers[k] = L;
            }
            nebulas[i] = n;
        }
    }

    public void renderSky(GuiGraphics g, int width, int height,
                          int accentRgb, float accentStrength) {
        if (width <= 0 || height <= 0) return;
        int top    = mixRgb(0xFF010104, accentRgb, accentStrength * 0.15f);
        int middle = mixRgb(0xFF050510, accentRgb, accentStrength * 0.30f);
        int bottom = mixRgb(0xFF000002, accentRgb, accentStrength * 0.08f);
        int halfH = height / 2;
        g.fillGradient(0, 0, width, halfH, top, middle);
        g.fillGradient(0, halfH, width, height, middle, bottom);
    }

    public void renderVbo(VertexConsumer vc, Matrix4f m,
                          GenesisCamera camera,
                          int width, int height,
                          float timeSec,
                          int accentRgb,
                          float accentStrength) {
        if (width <= 0 || height <= 0) return;

        int glowRgb = (accentStrength > 0.01f)
            ? mixRgb(0x303050, accentRgb, accentStrength * 0.5f)
            : 0x202030;
        float gr = ((glowRgb >> 16) & 0xFF) / 255f;
        float gg = ((glowRgb >>  8) & 0xFF) / 255f;
        float gb = ( glowRgb        & 0xFF) / 255f;
        float maxGlowR = Math.min(width, height) * 0.32f;
        GenesisDraw.glowCircle(vc, m, width / 2f, height / 2f,
            maxGlowR, gr, gg, gb, 5f / 255f);

        boolean reduced = GenesisWorld.isReducedMotion();
        if (reduced) return;

        drawNebulas(vc, m, camera, width, height, timeSec);

        for (int i = 0; i < STAR_COUNT; i++) {
            Star s = stars[i];
            float[] p = camera.worldToScreen(s.x, s.y, s.z, width, height);
            if (p[0] < 0 || p[0] >= width || p[1] < 0 || p[1] >= height) continue;
            float tw = 0.55f + 0.45f * (float)Math.sin(timeSec * 0.8f + s.phase);
            float a = s.brightness * tw / 255f;
            if (a < 8f / 255f) continue;
            float sz = s.size;
            GenesisDraw.quad(vc, m, p[0], p[1], sz, sz, 1f, 1f, 1f, a);
        }

        for (int i = 0; i < DUST_COUNT; i++) {
            Dust d = dust[i];
            float angle = timeSec * 0.03f + d.phase;
            float x = d.x * (float)Math.cos(angle) - d.z * (float)Math.sin(angle);
            float z = d.x * (float)Math.sin(angle) + d.z * (float)Math.cos(angle);
            float[] p = camera.worldToScreen(x, d.y, z, width, height);
            if (p[0] < 0 || p[0] >= width || p[1] < 0 || p[1] >= height) continue;
            float tw = 0.6f + 0.4f * (float)Math.sin(timeSec * 1.3f + d.phase);
            float a = d.alpha * tw / 255f;
            if (a < 6f / 255f) continue;
            GenesisDraw.quad(vc, m, p[0], p[1], 1f, 1f, 0.85f, 0.8f, 1f, a);
        }
    }

    public void renderVignette(GuiGraphics g, int width, int height) {
        int bandSize = Math.min(width, height) / 4;
        if (bandSize <= 0) return;
        int step = Math.max(1, bandSize / 8);
        for (int i = 0; i < 8; i++) {
            int offset = i * step;
            float t = i / 8f;
            float falloff = (1f - t) * (1f - t);
            int alpha = (int)(falloff * 42);
            if (alpha < 2) continue;
            int c = alpha << 24;
            g.fill(0, offset, width, offset + step, c);
            g.fill(0, height - offset - step, width, height - offset, c);
            g.fill(offset, 0, offset + step, height, c);
            g.fill(width - offset - step, 0, width - offset, height, c);
        }
    }

    private void drawNebulas(VertexConsumer vc, Matrix4f m,
                             GenesisCamera camera, int w, int h,
                             float timeSec) {
        int cx0 = w / 2;
        int cy0 = h / 2;

        for (int i = 0; i < NEBULA_COUNT; i++) {
            Nebula n = nebulas[i];
            float dx = (float)Math.sin(timeSec * n.drift + n.phase) * 40f;
            float dy = (float)Math.cos(timeSec * n.drift * 0.8f + n.phase) * 30f;

            float[] camSpace = camera.toCameraSpace(
                n.x + dx, n.y + dy, n.z);

            float depth = camSpace[2];
            float persp = SKY_CAMERA_DIST / (depth + SKY_BASE_DIST);
            if (persp < 0.10f) persp = 0.10f;
            if (persp > 1.20f) persp = 1.20f;

            float cx = cx0 + camSpace[0] * persp;
            float cy = cy0 - camSpace[1] * persp;

            float proxyR = 1600f * persp;
            if (cx + proxyR < -200f || cx - proxyR > w + 200f) continue;
            if (cy + proxyR < -200f || cy - proxyR > h + 200f) continue;

            for (int k = 0; k < NEBULA_LAYERS; k++) {
                NebulaLayer L = n.layers[k];
                float lx = cx + L.dx * persp;
                float ly = cy + L.dy * persp;
                float rx = L.rx * persp;
                float ry = L.ry * persp;
                if (rx < 12f || ry < 8f) continue;

                float a = L.alphaMul;
                if (a < 5f / 255f) continue;

                GenesisDraw.mistEllipse(vc, m, lx, ly, rx, ry,
                    L.r, L.g, L.b, a);
            }
        }
    }

    private static int mixRgb(int baseArgb, int accentRgb, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        int br = (baseArgb >> 16) & 0xFF;
        int bg = (baseArgb >> 8)  & 0xFF;
        int bb =  baseArgb        & 0xFF;
        int ar = (accentRgb >> 16) & 0xFF;
        int ag = (accentRgb >> 8)  & 0xFF;
        int ab =  accentRgb        & 0xFF;
        int rr = (int)(br + (ar - br) * t);
        int rg = (int)(bg + (ag - bg) * t);
        int rb = (int)(bb + (ab - bb) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }
}