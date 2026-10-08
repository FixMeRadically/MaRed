package com.fixmer.mared.gui2.genesis.render;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.genesis.GenesisCamera;
import com.fixmer.mared.gui2.genesis.GenesisController;
import com.fixmer.mared.gui2.genesis.GenesisWorld;
import com.fixmer.mared.gui2.genesis.node.GenesisNode;
import com.fixmer.mared.gui2.genesis.node.GenesisNodeData;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * v11:
 *  - РґРІР° РЅРµР·Р°РІРёСЃРёРјС‹С… РїСЂРѕС…РѕРґР°: renderVisual (GLOW batch) Рё renderGlyphs
 *    (GuiGraphics text). РњРµР¶РґСѓ РЅРёРјРё вЂ” endBatch(GLOW).
 *  - beams СѓРґР°Р»РµРЅС‹ (v10).
 *  - zoom РЅРµ РґРІРёРіР°РµС‚ РїРѕР·РёС†РёРё (v10).
 */
public final class GenesisRenderer {

    private static final int PANEL_W = 480;
    private static final int PANEL_H = 140;
    private static final int PANEL_BOTTOM_MARGIN = 60;
    private static final int PANEL_SLIDE_DISTANCE = PANEL_H + 40;

    private static final int ORBIT_RGB = 0x9AA3C0;
    private static final int ORBIT_SEGMENTS = 90;

    private final GenesisNodeRenderer nodeRenderer = new GenesisNodeRenderer();
    private final GenesisCoreRenderer coreRenderer = new GenesisCoreRenderer();
    private final GenesisBackground background   = new GenesisBackground();

    private final List<GenesisNode> sortedScratch = new ArrayList<>(8);
    private final float[] dimFactors = new float[16];

    public void render(GuiGraphics g,
                       GenesisCamera camera,
                       GenesisController controller,
                       List<GenesisNode> nodes,
                       int width, int height,
                       float orbitTimeSec,
                       float age,
                       int mouseX, int mouseY,
                       float fps) {

        boolean reduced = GenesisWorld.isReducedMotion();
        int accent = controller.accentTint();
        float strength = controller.accentStrength();

        background.renderSky(g, width, height, accent, strength);
        g.flush();

        VertexConsumer vc = g.bufferSource().getBuffer(GenesisRenderTypes.GLOW);
        Matrix4f m = g.pose().last().pose();

        background.renderVbo(vc, m, camera, width, height,
                             orbitTimeSec, accent, strength);

        if (!reduced) {
            for (int i = 0; i < nodes.size(); i++) {
                GenesisNode n = nodes.get(i);
                if (n.data().type() == GenesisNodeData.NodeType.CORE) continue;
                drawDottedOrbit(vc, m, camera, n, width, height,
                                orbitTimeSec, age);
            }
        }

        GenesisNode selected = controller.selected();
        if (selected != null
            && selected.data().type() != GenesisNodeData.NodeType.CORE) {
            drawFocusTether(vc, m, camera, selected, width, height);
        }

        // ---- Pass 1: visual (vertex-Р±Р°С‚С‡) ----
        int nCount = nodes.size();
        boolean dimming = (selected != null
            && (controller.state() == GenesisController.State.FOCUSED
                || controller.state() == GenesisController.State.FLYING
                || controller.state() == GenesisController.State.ENTERING));

        sortedScratch.clear();
        sortedScratch.addAll(nodes);

        int dimCount = Math.min(nCount, dimFactors.length);

        for (int i = 0; i < nCount; i++) {
            GenesisNode n = sortedScratch.get(i);
            float[] s = camera.worldToScreen(
                n.worldX(), n.worldY(), n.worldZ(), width, height);

            nodeRenderer.cachedScreenX = s[0];
            nodeRenderer.cachedScreenY = s[1];
            nodeRenderer.cachedDepth = s[2];
            nodeRenderer.cachedPerspScale = camera.perspectiveScale(s[2]);
            nodeRenderer.cachedDepthAlpha = camera.depthAlpha(s[2]) / 255f;

            float dim = 1f;
            if (dimming && n != selected) {
                float z = s[2];
                if (z < 0f) {
                    dim = 1f + z / 400f;
                    if (dim < 0.35f) dim = 0.35f;
                }
            }
            nodeRenderer.dimFactor = dim;
            if (i < dimCount) dimFactors[i] = dim;

            if (n.data().type() == GenesisNodeData.NodeType.CORE) {
                coreRenderer.render(vc, m, camera, width, height,
                    orbitTimeSec,
                    (int)n.data().radius(),
                    n.data().defaultColor());
            } else {
                nodeRenderer.renderVisual(vc, m, n, camera, width, height,
                                          orbitTimeSec);
            }
        }

        if (!reduced) {
            drawHoverRipple(vc, m, camera, nodes, width, height, orbitTimeSec);
        }

        // Р—Р°РєСЂС‹РІР°РµРј GLOW-Р±Р°С‚С‡ вЂ” С‚РµРїРµСЂСЊ РјРѕР¶РЅРѕ Р±РµР·РѕРїР°СЃРЅРѕ СЂРёСЃРѕРІР°С‚СЊ С‚РµРєСЃС‚.
        g.bufferSource().endBatch(GenesisRenderTypes.GLOW);

        // ---- Pass 2: РіР»РёС„С‹ (GuiGraphics text) ----
        if (!reduced) {
            for (int i = 0; i < nCount; i++) {
                GenesisNode n = sortedScratch.get(i);
                if (n.data().type() == GenesisNodeData.NodeType.CORE) continue;
                float dim = (i < dimCount) ? dimFactors[i] : 1f;
                nodeRenderer.renderGlyphs(g, n, camera, width, height,
                                          orbitTimeSec, dim);
            }
        }

        background.renderVignette(g, width, height);

        drawTitle(g, width, height, age);
        drawHoverTooltip(g, camera, controller, width, height, mouseX, mouseY);
        drawFps(g, width, height, fps);

        float panelSlide = controller.panelSlide();
        if (panelSlide > 0f) {
            int veilAlpha = (int)(panelSlide * 80);
            g.fill(0, 0, width, height, (veilAlpha << 24));
        }
        if (panelSlide > 0f && controller.selected() != null) {
            drawInfoPanel(g, controller.selected(), panelSlide, width, height);
        }

        float enter = controller.enterProgress();
        if (enter > 0f) {
            int a = (int)(enter * 255);
            g.fill(0, 0, width, height, (a << 24));
        }
    }

    private void drawFps(GuiGraphics g, int width, int height, float fps) {
        if (fps <= 0f) return;
        Font font = Minecraft.getInstance().font;
        String text = ((int)fps) + " fps";
        int tw = font.width(text);
        int x = width - tw - 10;
        int y = height - 14;
        int color = fps > 300 ? 0xFF55FF88
                  : fps > 150 ? 0xFFFFDD55
                  : 0xFFFF5555;
        g.drawString(font, text, x, y, color, false);
    }

    private static void drawDottedOrbit(VertexConsumer vc, Matrix4f m,
                                        GenesisCamera camera,
                                        GenesisNode node,
                                        int sw, int sh,
                                        float timeSec, float age) {
        GenesisNodeData d = node.data();
        int R = (int)d.orbitRadius();
        float reveal = Math.min(1f, age / 1.8f);
        if (reveal < 0.05f) return;

        int step = ORBIT_SEGMENTS / 18;
        if (step < 1) step = 1;

        float fr = ((ORBIT_RGB >> 16) & 0xFF) / 255f;
        float fg = ((ORBIT_RGB >>  8) & 0xFF) / 255f;
        float fb = ( ORBIT_RGB        & 0xFF) / 255f;

        float cX = (float)Math.cos(d.orbitTiltX());
        float sX = (float)Math.sin(d.orbitTiltX());
        float cZ = (float)Math.cos(d.orbitTiltZ());
        float sZ = (float)Math.sin(d.orbitTiltZ());

        for (int i = 0; i < ORBIT_SEGMENTS; i += step) {
            float angle = i * 2f * (float)Math.PI / ORBIT_SEGMENTS;
            float x = (float)(Math.cos(angle) * R);
            float z = (float)(Math.sin(angle) * R);
            float y = 0f;

            float y2 = y * cX - z * sX;
            float z2 = y * sX + z * cX;
            y = y2; z = z2;

            float x2 = x * cZ - y * sZ;
            float y3 = x * sZ + y * cZ;
            x = x2; y = y3;

            float[] p = camera.worldToScreen(x, y, z, sw, sh);
            float depthFade = camera.depthAlpha(p[2]) / 255f;
            float twinkle = 0.65f + 0.35f
                * (float)Math.sin(timeSec * 1.2f + angle * 2.3f);
            float a = 70f * twinkle * depthFade * reveal / 255f;
            if (a < 6f / 255f) continue;

            GenesisDraw.quad(vc, m, p[0], p[1], 2f, 2f, fr, fg, fb, a);
        }
    }

    private static void drawFocusTether(VertexConsumer vc, Matrix4f m,
                                        GenesisCamera camera,
                                        GenesisNode node,
                                        int sw, int sh) {
        float[] coreP = camera.worldToScreen(0f, 0f, 0f, sw, sh);
        float[] nodeP = camera.worldToScreen(
            node.worldX(), node.worldY(), node.worldZ(), sw, sh);

        int rgb = node.data().defaultColor() & 0x00FFFFFF;
        float fr = ((rgb >> 16) & 0xFF) / 255f;
        float fg = ((rgb >>  8) & 0xFF) / 255f;
        float fb = ( rgb        & 0xFF) / 255f;

        int segments = 40;
        for (int i = 3; i <= segments; i += 2) {
            float t = i / (float) segments;
            float x = coreP[0] + (nodeP[0] - coreP[0]) * t;
            float y = coreP[1] + (nodeP[1] - coreP[1]) * t;
            float falloff = 1f - t;
            falloff *= falloff;
            float alpha = falloff * 150f / 255f;
            if (alpha < 8f / 255f) continue;
            GenesisDraw.quad(vc, m, x, y, 2f, 2f, fr, fg, fb, alpha);
        }
        GenesisDraw.quad(vc, m, nodeP[0] - 1f, nodeP[1] - 1f, 4f, 4f,
            1f, 1f, 1f, 200f / 255f);
    }

    private static void drawHoverRipple(VertexConsumer vc, Matrix4f m,
                                        GenesisCamera camera,
                                        List<GenesisNode> nodes,
                                        int sw, int sh,
                                        float timeSec) {
        for (int idx = 0; idx < nodes.size(); idx++) {
            GenesisNode n = nodes.get(idx);
            if (n.data().type() == GenesisNodeData.NodeType.CORE) continue;
            if (n.hover() < 0.15f && n.focus() < 0.15f) continue;

            float[] s = camera.worldToScreen(
                n.worldX(), n.worldY(), n.worldZ(), sw, sh);
            float persp = camera.perspectiveScale(s[2]);
            float depthAlpha = camera.depthAlpha(s[2]) / 255f;
            float zoomMul = n.isFocused() ? camera.zoom() : 1f;
            float scale = zoomMul * persp * n.currentScale();
            float r = n.data().radius() * scale;
            if (r < 4f) continue;

            int rgb = n.data().defaultColor() & 0x00FFFFFF;
            float fr = ((rgb >> 16) & 0xFF) / 255f;
            float fg = ((rgb >>  8) & 0xFF) / 255f;
            float fb = ( rgb        & 0xFF) / 255f;

            float intensity = Math.max(n.hover(), n.focus()) * depthAlpha;

            for (int k = 0; k < 2; k++) {
                float phase = (timeSec * 0.55f + k * 0.5f) % 1f;
                float radius = r * (1.2f + phase * 1.5f);
                float alpha = (1f - phase) * (1f - phase) * 130f * intensity / 255f;
                if (alpha < 6f / 255f) continue;
                GenesisDraw.ring(vc, m, s[0], s[1],
                    radius - 1f, radius + 1f,
                    fr, fg, fb, alpha);
            }
        }
    }

    private void drawTitle(GuiGraphics g, int width, int height, float age) {
        Font font = Minecraft.getInstance().font;
        float fadeIn = Math.min(1f, Math.max(0f, (age - 0.6f) / 0.8f));
        if (fadeIn <= 0.02f) return;

        int baseAlpha = (int)(fadeIn * 220);
        String title = MaredLang.get("mared.genesis.title");
        String hint  = MaredLang.get("mared.genesis.hint");

        int tx = 32, ty = 26;
        g.drawString(font, title, tx + 2, ty + 2, (baseAlpha / 3) << 24, false);
        g.drawString(font, title, tx, ty,
            (baseAlpha << 24) | 0x00F0F0FF, false);

        int a2 = (int)(baseAlpha * 0.65f);
        g.drawString(font, hint, tx + 1, ty + 19, (a2 / 2) << 24, false);
        g.drawString(font, hint, tx, ty + 18,
            (a2 << 24) | 0x00A0A8C0, false);

        g.fill(tx - 12, ty + 2, tx - 10, ty + 8,
               (baseAlpha << 24) | 0x00C0C8E0);
    }

    private void drawHoverTooltip(GuiGraphics g,
                                  GenesisCamera camera,
                                  GenesisController controller,
                                  int sw, int sh,
                                  int mouseX, int mouseY) {
        GenesisNode hovered = controller.hovered();
        if (hovered == null) return;
        float best = hovered.hover();
        if (best < 0.4f) return;

        Font font = Minecraft.getInstance().font;
        String name = MaredLang.get(categoryKey(hovered.data().type()));
        int tw = font.width(name);
        int padX = 8, padY = 4;
        int boxW = tw + padX * 2;
        int boxH = 8 + padY * 2;

        float[] ns = camera.worldToScreen(
            hovered.worldX(), hovered.worldY(), hovered.worldZ(), sw, sh);
        float tooltipZoom = hovered.isFocused() ? camera.zoom() : 1f;
        float nScale = tooltipZoom
                     * camera.perspectiveScale(ns[2])
                     * hovered.currentScale();
        int nodeR = (int)(hovered.data().radius() * nScale);
        int anchorX = (int)ns[0];
        int anchorY = (int)ns[1];

        int bx1 = anchorX - boxW / 2;
        int by1 = anchorY + nodeR + 8;
        if (by1 + boxH > sh - 4) by1 = anchorY - nodeR - 8 - boxH;
        if (bx1 < 4) bx1 = 4;
        if (bx1 + boxW > sw - 4) bx1 = sw - 4 - boxW;
        if (by1 < 4) by1 = 4;

        int bx2 = bx1 + boxW;
        int by2 = by1 + boxH;

        int a = (int)(best * 230);
        int rgb = hovered.data().defaultColor() & 0x00FFFFFF;

        g.fill(bx1, by1, bx2, by2, (int)(a * 0.55f) << 24);
        g.fill(bx1, by1, bx2, by1 + 1, (a << 24) | rgb);
        g.fill(bx1, by2 - 1, bx2, by2, (a << 24) | rgb);
        g.fill(bx1, by1, bx1 + 1, by2, (a << 24) | rgb);
        g.fill(bx2 - 1, by1, bx2, by2, (a << 24) | rgb);

        g.drawString(font, name, bx1 + padX, by1 + padY,
                     (a << 24) | 0x00E8E8F5, false);
    }

    private void drawInfoPanel(GuiGraphics g,
                               GenesisNode node,
                               float slide,
                               int width, int height) {
        GenesisNodeData d = node.data();
        int accent = d.defaultColor() & 0x00FFFFFF;

        int x = (width - PANEL_W) / 2;
        int yTarget = height - PANEL_H - PANEL_BOTTOM_MARGIN;
        int y = yTarget + (int)((1f - slide) * PANEL_SLIDE_DISTANCE);

        g.fill(x + 6, y + 6, x + PANEL_W + 6, y + PANEL_H + 6,
               (int)(slide * 120) << 24);

        int bgAlpha = (int)(slide * 235);
        g.fill(x, y, x + PANEL_W, y + PANEL_H, (bgAlpha << 24) | 0x0E0E18);
        g.fill(x, y, x + PANEL_W, y + 2, ((int)(slide * 255) << 24) | accent);

        drawOutline(g, x, y, PANEL_W, PANEL_H,
                    ((int)(slide * 200) << 24) | accent);

        Font font = Minecraft.getInstance().font;

        String title = MaredLang.get(categoryKey(d.type()));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 28, y + 24, 0);
        pose.scale(2f, 2f, 1f);
        g.drawString(font, title, 0, 0,
                     ((int)(slide * 255) << 24) | 0xFFFFFFFF, false);
        pose.popPose();

        String desc = MaredLang.get(descKey(d.type()));
        List<String> lines = wrap(font, desc, PANEL_W - 56);
        int lineY = y + 60;
        int textAlpha = (int)(slide * 220);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + 28, lineY,
                         (textAlpha << 24) | 0x00D0D0E0, false);
            lineY += 12;
        }

        String hint = MaredLang.get("mared.genesis.panel_hint");
        int hintW = font.width(hint);
        int hintAlpha = (int)(slide * 180);
        g.drawString(font, hint,
                     x + (PANEL_W - hintW) / 2,
                     y + PANEL_H - 22,
                     (hintAlpha << 24) | 0x009090A0, false);
    }

    private static void drawOutline(GuiGraphics g,
                                    int x, int y, int w, int h,
                                    int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static String categoryKey(GenesisNodeData.NodeType type) {
        return switch (type) {
            case CORE      -> "mared.genesis.core";
            case CONTENT   -> "mared.genesis.content";
            case WORLD     -> "mared.genesis.world";
            case LOGIC     -> "mared.genesis.logic";
            case RESOURCES -> "mared.genesis.resources";
            case TOOLS     -> "mared.genesis.tools";
            case SCENARIOS -> "mared.genesis.scenarios";
        };
    }

    private static String descKey(GenesisNodeData.NodeType type) {
        return switch (type) {
            case CORE      -> "mared.genesis.desc.core";
            case CONTENT   -> "mared.genesis.desc.content";
            case WORLD     -> "mared.genesis.desc.world";
            case LOGIC     -> "mared.genesis.desc.logic";
            case RESOURCES -> "mared.genesis.desc.resources";
            case TOOLS     -> "mared.genesis.desc.tools";
            case SCENARIOS -> "mared.genesis.desc.scenarios";
        };
    }

    private static List<String> wrap(Font font, String text, int maxW) {
        List<String> out = new ArrayList<>(4);
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(next) > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            } else {
                cur.setLength(0);
                cur.append(next);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}