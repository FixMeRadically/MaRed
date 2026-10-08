package com.fixmer.mared.gui2.genesis.render;

import com.fixmer.mared.gui2.genesis.GenesisCamera;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * v10: zoom РїСЂРёРјРµРЅСЏРµС‚СЃСЏ Рє СЂР°РґРёСѓСЃСѓ СЏРґСЂР° СЏРІРЅРѕ (perspectiveScale С‚РµРїРµСЂСЊ
 * Р±РµР· zoom вЂ” СЃРј. GenesisCamera).
 */
public final class GenesisCoreRenderer {

    private static final int RING_SEGMENTS = 48;

    public void render(VertexConsumer vc, Matrix4f m,
                       GenesisCamera camera,
                       int screenW, int screenH,
                       float timeSec,
                       int baseRadius,
                       int accent) {

        float[] sc = camera.worldToScreen(0f, 0f, 0f, screenW, screenH);
        float cx = sc[0], cy = sc[1], depth = sc[2];

        float persp = camera.perspectiveScale(depth);
        float alphaMul = camera.depthAlpha(depth) / 255f;
        // v10: zoom Рє СЂР°Р·РјРµСЂСѓ.
        float r = baseRadius * persp * camera.zoom();
        if (r <= 4f) return;

        int rgb = accent & 0x00FFFFFF;
        float fr = ((rgb >> 16) & 0xFF) / 255f;
        float fg = ((rgb >>  8) & 0xFF) / 255f;
        float fb = ( rgb        & 0xFF) / 255f;

        float pulse = (float)Math.sin(timeSec * 1.8f) * 0.5f + 0.5f;

        GenesisDraw.glowCircle(vc, m, cx, cy, r * 3.2f,
            fr, fg, fb, 24f / 255f * alphaMul);

        float pulseHaloR = r * (2.4f + 0.4f * pulse);
        GenesisDraw.glowCircle(vc, m, cx, cy, pulseHaloR,
            fr, fg, fb, (20f + 20f * pulse) / 255f * alphaMul);

        drawRing(vc, m, camera, screenW, screenH, r * 1.05f,
            timeSec * 0.40f, 0f, 0f, 140f * alphaMul / 255f, fr, fg, fb);
        drawRing(vc, m, camera, screenW, screenH, r * 0.88f,
            0f, timeSec * 0.55f, 0f, 110f * alphaMul / 255f, fr, fg, fb);
        drawRing(vc, m, camera, screenW, screenH, r * 1.18f,
            0f, 0f, timeSec * 0.45f, 95f * alphaMul / 255f, fr, fg, fb);
        drawRing(vc, m, camera, screenW, screenH, r * 1.35f,
            timeSec * 0.35f, timeSec * 0.22f, 0f,
            75f * alphaMul / 255f, fr, fg, fb);

        drawOctahedron(vc, m, camera, screenW, screenH, r * 0.65f,
            timeSec, 190f * alphaMul / 255f, fr, fg, fb);

        float pulseR = r * (0.20f + 0.08f * pulse);
        float pulseAlpha = (190f + 65f * pulse) * alphaMul / 255f;

        GenesisDraw.pixelSoftCircle(vc, m, cx, cy, pulseR * 2f,
            1f, 1f, 1f, pulseAlpha * 0.5f);
        GenesisDraw.pixelCircle(vc, m, cx, cy, pulseR,
            1f, 1f, 1f, pulseAlpha);
        GenesisDraw.quad(vc, m, cx - 2f, cy - 2f, 5f, 5f,
            1f, 1f, 1f, 1f);
    }

    private void drawRing(VertexConsumer vc, Matrix4f m,
                          GenesisCamera camera,
                          int sw, int sh, float r,
                          float rx, float ry, float rz,
                          float alpha, float fr, float fg, float fb) {
        if (alpha <= 0.008f) return;

        float cxr = (float)Math.cos(rx), sxr = (float)Math.sin(rx);
        float cyr = (float)Math.cos(ry), syr = (float)Math.sin(ry);
        float czr = (float)Math.cos(rz), szr = (float)Math.sin(rz);

        float prevX = 0f, prevY = 0f;
        boolean first = true;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            float a = i * 2f * (float)Math.PI / RING_SEGMENTS;
            float wx = (float)Math.cos(a) * r;
            float wy = 0f;
            float wz = (float)Math.sin(a) * r;

            float y1 = wy * cxr - wz * sxr;
            float z1 = wy * sxr + wz * cxr;
            float x2 = wx * cyr - z1 * syr;
            float z2 = wx * syr + z1 * cyr;
            float x3 = x2 * czr - y1 * szr;
            float y3 = x2 * szr + y1 * czr;

            float[] s = camera.worldToScreen(x3, y3, z2, sw, sh);
            float sx = s[0], sy = s[1];
            if (!first) {
                GenesisDraw.line(vc, m, prevX, prevY, sx, sy,
                    1f, fr, fg, fb, alpha);
            }
            prevX = sx; prevY = sy; first = false;
        }
    }

    private void drawOctahedron(VertexConsumer vc, Matrix4f m,
                                 GenesisCamera camera,
                                 int sw, int sh, float r, float timeSec,
                                 float alpha, float fr, float fg, float fb) {
        if (alpha <= 0.008f) return;

        float rotY = timeSec * 0.4f;
        float c = (float)Math.cos(rotY);
        float s = (float)Math.sin(rotY);

        float[][] V = {
            { 0f,  1f,  0f}, { 1f,  0f,  0f}, { 0f,  0f,  1f},
            {-1f,  0f,  0f}, { 0f,  0f, -1f}, { 0f, -1f,  0f},
        };
        int[][] E = {
            {0,1},{0,2},{0,3},{0,4},
            {1,2},{2,3},{3,4},{4,1},
            {5,1},{5,2},{5,3},{5,4},
        };

        int n = V.length;
        float[] xs = new float[n];
        float[] ys = new float[n];

        for (int i = 0; i < n; i++) {
            float x = V[i][0];
            float y = V[i][1];
            float z = V[i][2];
            float xr = x * c - z * s;
            float zr = x * s + z * c;
            float[] p = camera.worldToScreen(xr * r, y * r, zr * r, sw, sh);
            xs[i] = p[0];
            ys[i] = p[1];
        }

        for (int[] e : E) {
            GenesisDraw.line(vc, m, xs[e[0]], ys[e[0]], xs[e[1]], ys[e[1]],
                1f, fr, fg, fb, alpha);
        }
        for (int i = 0; i < n; i++) {
            GenesisDraw.quad(vc, m, xs[i] - 1f, ys[i] - 1f, 3f, 3f,
                1f, 1f, 1f, alpha);
        }
    }
}