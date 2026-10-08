package com.fixmer.mared.gui2.welcome.render.environment.layers;

import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentCamera;
import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentLayer;
import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentPalette;
import com.fixmer.mared.gui2.welcome.render.environment.EnvironmentTime;

import net.minecraft.client.gui.GuiGraphics;

public final class GlowFieldLayer implements EnvironmentLayer {

    private static final float DRIFT_X_SPEED = 0.35f;
    private static final float DRIFT_Y_SPEED = 0.27f;
    private static final float DRIFT_X_RATIO = 0.035f;
    private static final float DRIFT_Y_RATIO = 0.025f;

    private static final float RADIUS_RATIO = 0.60f;
    private static final float RADIUS_PULSE = 0.08f;
    private static final float PULSE_SPEED  = 0.50f;

    private static final int   MAX_INTENSITY = 0x50;
    private static final float PARALLAX_DEPTH = 0.05f;

    @Override
    public void render(GuiGraphics g,
                       int width,
                       int height,
                       EnvironmentTime time,
                       EnvironmentPalette palette,
                       EnvironmentCamera camera) {
        if (width <= 0 || height <= 0) return;

        int cx = width / 2
                + (int) (time.sin(DRIFT_X_SPEED) * width * DRIFT_X_RATIO)
                + camera.offsetPxX(width, PARALLAX_DEPTH);

        int cy = height / 2
                + (int) (time.cos(DRIFT_Y_SPEED) * height * DRIFT_Y_RATIO)
                + camera.offsetPxY(height, PARALLAX_DEPTH);

        int baseRadius = (int) (Math.min(width, height) * RADIUS_RATIO);
        if (baseRadius <= 0) return;

        float pulse = time.pulse(PULSE_SPEED);
        int radius = (int) (baseRadius * (1f + pulse * RADIUS_PULSE));
        if (radius <= 0) return;

        int rgb = palette.glow & 0x00FFFFFF;
        long r2 = (long) radius * radius;

        int yStart = Math.max(0, cy - radius);
        int yEnd   = Math.min(height, cy + radius);

        for (int y = yStart; y < yEnd; y++) {
            int dy = y - cy;
            long d2 = (long) dy * dy;
            if (d2 > r2) continue;

            int half = (int) Math.sqrt((double) (r2 - d2));
            int x1 = Math.max(0, cx - half);
            int x2 = Math.min(width, cx + half);
            if (x1 >= x2) continue;

            float dNorm = (float) Math.abs(dy) / radius;
            float falloff = 1f - dNorm;
            falloff *= falloff;

            int alpha = (int) (falloff * MAX_INTENSITY);
            if (alpha <= 0) continue;

            g.fill(x1, y, x2, y + 1, (alpha << 24) | rgb);
        }
    }
}