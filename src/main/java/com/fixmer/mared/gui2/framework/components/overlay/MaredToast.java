package com.fixmer.mared.gui2.framework.components.overlay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Всплывающие уведомления в углу.
 *
 * 0.3.0: перенос из gui.common.MaredToast. Тема — через MaredThemeRegistry.
 */
public final class MaredToast {

    private MaredToast() {}

    public enum Kind { SUCCESS, ERROR, INFO, WARN }

    private static final class Entry {
        final String text;
        final Kind kind;
        final long createdAt;
        final long durationMs;

        Entry(String text, Kind kind, long durationMs) {
            this.text = text;
            this.kind = kind;
            this.createdAt = System.currentTimeMillis();
            this.durationMs = durationMs;
        }

        float progress() {
            long elapsed = System.currentTimeMillis() - createdAt;
            if (elapsed >= durationMs) return 1f;
            return (float) elapsed / durationMs;
        }

        boolean isDead() {
            return System.currentTimeMillis() - createdAt >= durationMs + 250;
        }
    }

    private static final List<Entry> stack = new ArrayList<>(8);
    private static final int MAX_STACK = 5;
    private static final long DEFAULT_MS = 2500;

    private static final long SLIDE_IN_MS  = 180;
    private static final long SLIDE_OUT_MS = 180;
    private static final int WIDTH  = 180;
    private static final int HEIGHT = 22;
    private static final int GAP    = 4;

    public static void success(String text) { push(text, Kind.SUCCESS, DEFAULT_MS); }
    public static void error(String text)   { push(text, Kind.ERROR,   DEFAULT_MS + 1000); }
    public static void info(String text)    { push(text, Kind.INFO,    DEFAULT_MS); }
    public static void warn(String text)    { push(text, Kind.WARN,    DEFAULT_MS + 500); }

    public static void push(String text, Kind kind, long durationMs) {
        if (text == null || text.isEmpty()) return;
        stack.add(new Entry(text, kind, Math.max(800, durationMs)));
        while (stack.size() > MAX_STACK) stack.remove(0);
    }

    public static void clear() { stack.clear(); }
    public static int size() { return stack.size(); }

    public static void render(GuiGraphics g, Font font, int screenW, int screenH) {
        if (stack.isEmpty() || font == null) return;

        Iterator<Entry> it = stack.iterator();
        while (it.hasNext()) {
            if (it.next().isDead()) it.remove();
        }
        if (stack.isEmpty()) return;

        MaredTheme t = MaredThemeRegistry.active();
        if (t == null) return;

        int baseX = screenW - WIDTH - 8;
        int baseY = screenH - 8 - HEIGHT;

        for (int i = stack.size() - 1; i >= 0; i--) {
            Entry e = stack.get(i);
            int idx = stack.size() - 1 - i;
            int y = baseY - idx * (HEIGHT + GAP);

            float p = e.progress();
            float alpha;
            int offsetX = 0;

            long ageMs = System.currentTimeMillis() - e.createdAt;
            long remainMs = e.durationMs - ageMs;

            if (ageMs < SLIDE_IN_MS) {
                float k = (float) ageMs / SLIDE_IN_MS;
                alpha = k;
                offsetX = (int) ((1f - k) * (WIDTH + 8));
            } else if (remainMs < SLIDE_OUT_MS) {
                float k = Math.max(0f, (float) remainMs / SLIDE_OUT_MS);
                alpha = k;
                offsetX = (int) ((1f - k) * (WIDTH + 8));
            } else {
                alpha = 1f;
            }

            int x = baseX + offsetX;

            int accent = switch (e.kind) {
                case SUCCESS -> t.success;
                case ERROR   -> t.danger;
                case WARN    -> t.warn;
                case INFO    -> t.accent;
            };

            int bg = applyAlpha(t.bgPanelRaised, alpha);
            int fg = applyAlpha(t.text, alpha);
            int ac = applyAlpha(accent, alpha);
            int sh = applyAlpha(0xFF000000, alpha * 0.6f);

            Render.rect(g, x + 2, y + 2, x + WIDTH + 2, y + HEIGHT + 2, sh);
            Render.rect(g, x, y, x + WIDTH, y + HEIGHT, bg);
            Render.rect(g, x, y, x + 3, y + HEIGHT, ac);
            Render.outline(g, x, y, WIDTH, HEIGHT, ac);

            String label = TextUtils.ellipsize(font, e.text, WIDTH - 16);
            Render.text(g, font, label, x + 8, y + (HEIGHT - 8) / 2, fg);

            int barW = (int) ((1f - p) * (WIDTH - 3));
            if (barW > 0) {
                Render.rect(g, x + 3, y + HEIGHT - 2, x + 3 + barW, y + HEIGHT - 1, ac);
            }
        }
    }

    private static int applyAlpha(int color, float a) {
        if (a >= 1f) return color;
        if (a <= 0f) return 0;
        int alpha = (color >>> 24) & 0xFF;
        int newAlpha = (int) (alpha * a);
        return (newAlpha << 24) | (color & 0x00FFFFFF);
    }
}