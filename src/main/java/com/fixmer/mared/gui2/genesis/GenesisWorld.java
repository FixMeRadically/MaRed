package com.fixmer.mared.gui2.genesis;

import com.fixmer.mared.gui2.genesis.node.GenesisNode;
import com.fixmer.mared.gui2.genesis.node.GenesisNodeData;
import com.fixmer.mared.gui2.genesis.node.NodeRegistry;
import com.fixmer.mared.gui2.genesis.render.GenesisRenderer;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GenesisWorld {

    private static final float INTRO_DURATION = 1.8f;
    private static final float MAX_DT = 0.1f;

    private final GenesisCamera camera = new GenesisCamera();
    private final GenesisController controller = new GenesisController();
    private final GenesisRenderer renderer = new GenesisRenderer();
    private final List<GenesisNode> nodes;

    // Scratch Р Т‘Р В»РЎРЏ pickNode - Р В±Р ВµР В· Р В°Р В»Р В»Р С•Р С”Р В°РЎвЂ Р С‘Р в„– Р Р† Р С”Р В°Р Т‘РЎР‚Р Вµ
    private final List<GenesisNode> pickScratch = new ArrayList<>(8);

    private float orbitTimeSec = 0f;
    private float age = 0f;
    private long lastFrameNanos = 0L;

    private int frames = 0;
    private long fpsResetNanos = 0L;
    private float currentFps = 0f;

    public GenesisWorld() {
        List<GenesisNodeData> raw = NodeRegistry.buildAll();
        List<GenesisNode> list = new ArrayList<>(raw.size());
        for (GenesisNodeData d : raw) list.add(new GenesisNode(d));
        this.nodes = Collections.unmodifiableList(list);
    }

    public void tick() { /* no-op */ }

    private void frameUpdate() {
        long now = System.nanoTime();

        if (fpsResetNanos == 0L) {
            fpsResetNanos = now;
            lastFrameNanos = now;
            return;
        }
        frames++;
        long elapsed = now - fpsResetNanos;
        if (elapsed > 500_000_000L) {
            currentFps = frames * 1_000_000_000f / elapsed;
            frames = 0;
            fpsResetNanos = now;
        }

        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;

        if (dt <= 0f) return;
        if (dt > MAX_DT) dt = MAX_DT;

        controller.tick(this, dt);

        orbitTimeSec += dt;
        age += dt;

        float reveal = introReveal();
        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).tick(orbitTimeSec, dt, reveal);
        }

        camera.tick(dt);
    }

    public float introReveal() {
        float t = age / INTRO_DURATION;
        if (t >= 1f) return 1f;
        if (t <= 0f) return 0f;
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    public float age() { return age; }
    public float currentFps() { return currentFps; }

    public static boolean isReducedMotion() {
        try {
            return com.fixmer.mared.MaredSettings.isReducedMotion();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void render(GuiGraphics g, int width, int height,
                       int mouseX, int mouseY) {
        frameUpdate();

        renderer.render(g, camera, controller, nodes, width, height,
                        orbitTimeSec, age, mouseX, mouseY,
                        currentFps);
    }

    public void onMouseMoved(double mx, double my, int w, int h) {
        controller.onMouseMoved(this, mx, my, w, h);
    }
    public void onMousePressed(double mx, double my, int w, int h) {
        controller.onMousePressed(this, mx, my, w, h);
    }
    public void onMouseDragged(double mx, double my, int w, int h) {
        controller.onMouseDragged(this, mx, my, w, h);
    }
    public void onMouseReleased(double mx, double my, int w, int h) {
        controller.onMouseReleased(this, mx, my, w, h);
    }
    public void onMouseScrolled(double scrollY) {
        controller.onMouseScrolled(this, scrollY);
    }
    public void onEscape() { controller.onEscape(this); }

    public GenesisNode pickNode(double mx, double my, int w, int h) {
        pickScratch.clear();
        pickScratch.addAll(nodes);
        pickScratch.sort((a, b) -> {
            float za = camera.toCameraSpace(a.worldX(), a.worldY(), a.worldZ())[2];
            float zb = camera.toCameraSpace(b.worldX(), b.worldY(), b.worldZ())[2];
            return Float.compare(zb, za);
        });
        for (int i = pickScratch.size() - 1; i >= 0; i--) {
            GenesisNode n = pickScratch.get(i);
            GenesisNodeData d = n.data();
            // v13: CORE не интерактивен.
            if (d.type() == GenesisNodeData.NodeType.CORE) continue;
            float[] s = camera.worldToScreen(
                n.worldX(), n.worldY(), n.worldZ(), w, h);
            // v13: zoom применяется только к выбранному узлу.
            float zoomMul = n.isFocused() ? camera.zoom() : 1f;
            float scale = zoomMul
                        * camera.perspectiveScale(s[2])
                        * n.currentScale();
            float r = d.radius() * scale + 8f;
            float dx = (float)mx - s[0];
            float dy = (float)my - s[1];
            if (dx * dx + dy * dy <= r * r) return n;
        }
        return null;
    }

    public GenesisCamera camera() { return camera; }
    public GenesisController controller() { return controller; }
    public List<GenesisNode> nodes() { return nodes; }
    public float orbitTimeSec() { return orbitTimeSec; }
}