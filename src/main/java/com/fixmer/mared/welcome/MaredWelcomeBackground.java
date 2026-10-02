package com.fixmer.mared.welcome;

import java.util.Random;

import com.fixmer.mared.gui2.framework.render.MaredAnimation;
import com.fixmer.mared.gui2.framework.render.MaredUi;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Stage B7): MaredAnimation / MaredUi — из gui2.framework.render.
 */
public final class MaredWelcomeBackground {

    private static final String[] GLYPHS = {
        "say", "set", "if", "for", "while", "func", "call",
        "{", "}", "$", "=", "+", "->", ">", "repeat", "return",
        "on", "every", "bind", "move", "jump", "wait"
    };

    private static final int PARTICLE_COUNT = 40;

    private static final class Particle {
        float x01;
        float y01;
        float speed;
        float size;
        float alpha;
        String glyph;
        int layer;
    }

    private final Particle[] particles = new Particle[PARTICLE_COUNT];
    private final Random rng = new Random(0x5EEDC0DE);
    private long lastTickMs = 0;

    public MaredWelcomeBackground() {
        initParticles();
    }

    private void initParticles() {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            Particle p = new Particle();
            p.x01   = rng.nextFloat();
            p.y01   = rng.nextFloat();
            p.speed = 6f + rng.nextFloat() * 14f;
            p.size  = 0.85f + rng.nextFloat() * 0.6f;
            p.alpha = 0.06f + rng.nextFloat() * 0.10f;
            p.glyph = GLYPHS[rng.nextInt(GLYPHS.length)];
            p.layer = rng.nextInt(3);
            particles[i] = p;
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (lastTickMs == 0) { lastTickMs = now; return; }
        float dt = Math.min(0.1f, (now - lastTickMs) / 1000f);
        lastTickMs = now;

        for (Particle p : particles) {
            p.y01 -= p.speed * dt / 1080f;
            if (p.y01 < -0.05f) {
                p.y01 = 1.05f;
                p.x01 = rng.nextFloat();
                p.glyph = GLYPHS[rng.nextInt(GLYPHS.length)];
            }
        }
    }

    public void render(GuiGraphics g, Font font, int w, int h,
                       int mouseX, int mouseY, float partialTick) {
        tick();

        float t = MaredAnimation.nowSec();

        int top = 0xFF0A0512;
        int mid = 0xFF1A0A28;
        int bot = 0xFF0A0512;
        MaredUi.gradientV(g, 0, 0, w, h / 2, top, mid);
        MaredUi.gradientV(g, 0, h / 2, w, h, mid, bot);

        for (Particle p : particles) {
            int cx = (int) (p.x01 * w);
            int cy = (int) (p.y01 * h);

            float parallaxX = (mouseX - w / 2f) / w * (0.3f + p.layer * 0.15f);
            float parallaxY = (mouseY - h / 2f) / h * (0.3f + p.layer * 0.15f);
            cx += (int) (parallaxX * 40);
            cy += (int) (parallaxY * 40);

            int alpha = (int) (p.alpha * 255);
            int color = switch (p.layer) {
                case 0 -> MaredUi.withAlpha(0xFF55AAFF, alpha);
                case 1 -> MaredUi.withAlpha(0xFFFF55FF, alpha);
                default -> MaredUi.withAlpha(0xFFFFAA00, alpha);
            };

            MaredUi.textNoShadow(g, font, p.glyph, cx, cy, color);
        }

        int cx = w / 2;
        int cy = h / 2;
        for (int i = 0; i < 4; i++) {
            float phase = (t * 0.15f + i * 0.25f) % 1f;
            float radius = phase * Math.max(w, h) * 0.7f;
            int alpha = (int) ((1f - phase) * 30);
            if (alpha <= 0) continue;
            int color = MaredUi.withAlpha(0xFFFF55FF, alpha);
            drawRing(g, cx, cy, (int) radius, 2, color);
        }

        int gridColor = 0x10FFFFFF;
        int cell = MaredUi.px(32);
        for (int x = 0; x < w; x += cell) {
            g.fill(x, 0, x + 1, h, gridColor);
        }
        for (int y = 0; y < h; y += cell) {
            g.fill(0, y, w, y + 1, gridColor);
        }

        drawVignette(g, w, h);
    }

    private void drawRing(GuiGraphics g, int cx, int cy, int radius,
                          int thickness, int color) {
        if (radius <= 0) return;
        for (int dx = -radius; dx <= radius; dx++) {
            int x = cx + dx;
            if (x < 0 || x >= g.guiWidth()) continue;
            int dy = (int) Math.sqrt(radius * radius - dx * dx);
            int y1 = cy - dy;
            int y2 = cy + dy;
            if (y1 >= 0 && y1 + thickness <= g.guiHeight()) {
                g.fill(x, y1, x + 1, y1 + thickness, color);
            }
            if (y2 >= 0 && y2 + thickness <= g.guiHeight()) {
                g.fill(x, y2, x + 1, y2 + thickness, color);
            }
        }
    }

    private void drawVignette(GuiGraphics g, int w, int h) {
        int size = MaredUi.px(80);
        for (int i = 0; i < size; i++) {
            int alpha = (int) ((1f - i / (float) size) * 60);
            int color = (alpha << 24);
            g.fill(0, i, w, i + 1, color);
            g.fill(0, h - i - 1, w, h - i, color);
            g.fill(i, 0, i + 1, h, color);
            g.fill(w - i - 1, 0, w - i, h, color);
        }
    }
}