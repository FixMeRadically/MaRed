package com.fixmer.mared.gui2.spaces;

import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.navigation.MaredSpace;
import com.fixmer.mared.gui2.navigation.SpaceId;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Заглушка пространства.
 *
 * v1: все 6 пространств Content/World/... используют этот класс
 * до момента, пока не появятся настоящие редакторы. Рисует
 * большие инициалы + «Space X · under construction» на фоне
 * своего accent-цвета.
 *
 * Позже каждое пространство станет своей реализацией MaredSpace.
 */
public class StubSpace implements MaredSpace {

    private final SpaceId id;
    private final String title;
    private final int accent;

    public StubSpace(SpaceId id, String title, int accent) {
        this.id = id;
        this.title = title;
        this.accent = accent;
    }

    @Override public SpaceId id() { return id; }
    @Override public String titleKey() { return "mared.space." + id.id(); }
    @Override public int accentColor() { return accent; }

    @Override
    public void render(MaredRenderContext rctx) {
        GuiGraphics g = rctx.graphics();
        Font font = rctx.font();
        int w = rctx.width();
        int h = rctx.height();

        // Фон: вертикальный градиент от почти-чёрного к accent-fade.
        int top = 0xFF08080E;
        int mid = darken(accent, 0.85f);
        int bot = 0xFF030308;
        g.fillGradient(0, 0, w, h / 2, top, mid);
        g.fillGradient(0, h / 2, w, h, mid, bot);

        // Центральный «орб» — размытый accent-круг через кольца.
        int cx = w / 2;
        int cy = h / 2 - 30;
        int baseR = Math.min(w, h) / 6;
        drawSoftOrb(g, cx, cy, baseR, accent);

        // Заголовок.
        String titleUpper = title.toUpperCase(java.util.Locale.ROOT);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy + 60, 0);
        pose.scale(3f, 3f, 1f);
        int tw = font.width(titleUpper);
        g.drawString(font, titleUpper, -tw / 2, 0, 0xFFE8E8F0, false);
        pose.popPose();

        // Подзаголовок.
        String sub = "space under construction";
        int sw = font.width(sub);
        g.drawString(font, sub, cx - sw / 2, cy + 100,
            darken(accent, 0.15f) & 0x00FFFFFF | 0xCC000000, false);

        // Хинт.
        String hint = "Esc to return";
        int hw = font.width(hint);
        g.drawString(font, hint, cx - hw / 2, h - 40, 0xFF808090, false);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // Esc обрабатывает Shell (back).
        return false;
    }

    private static void drawSoftOrb(GuiGraphics g, int cx, int cy,
                                     int r, int accent) {
        int rings = 16;
        for (int i = 0; i < rings; i++) {
            float t = i / (float) rings;
            int rr = (int)(r * (1f - t));
            float a = (1f - t) * (1f - t) * 0.32f;
            int alpha = (int)(a * 255f);
            if (alpha <= 0) continue;
            int color = (alpha << 24) | (accent & 0x00FFFFFF);
            fillCircle(g, cx, cy, rr, color, 2);
        }
    }

    private static void fillCircle(GuiGraphics g, int cx, int cy,
                                    int r, int color, int step) {
        long r2 = (long) r * r;
        for (int dy = -r; dy <= r; dy += step) {
            long d2 = (long) dy * dy;
            if (d2 > r2) continue;
            int half = (int) Math.sqrt((double)(r2 - d2));
            g.fill(cx - half, cy + dy, cx + half, cy + dy + step, color);
        }
    }

    private static int darken(int color, float amount) {
        int a = (color >>> 24) & 0xFF;
        int r = (int)(((color >> 16) & 0xFF) * (1f - amount));
        int gg = (int)(((color >> 8) & 0xFF) * (1f - amount));
        int b = (int)((color & 0xFF) * (1f - amount));
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }
}