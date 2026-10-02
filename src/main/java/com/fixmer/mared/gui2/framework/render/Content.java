package com.fixmer.mared.gui2.framework.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Phase B): вынесен из MaredUi в top-level.
 * Построитель прокручиваемого контента.
 */
public final class Content {

    public interface Row {
        int height(Font f, int maxW);
        void render(GuiGraphics g, Font f, int x, int y, int maxW);
    }

    private final List<Row> rows = new ArrayList<>(16);

    public Content text(String s, int c)     { rows.add(new TextRow(s, c)); return this; }
    public Content wrapped(String s, int c)  { rows.add(new WrapRow(s, c)); return this; }
    public Content gap(int px)               { rows.add(new GapRow(px));    return this; }

    public int height(Font f, int maxW) {
        int h = 0;
        int n = rows.size();
        for (int i = 0; i < n; i++) h += rows.get(i).height(f, maxW);
        return h;
    }

    public void render(GuiGraphics g, Font f, int x, int y, int maxW) {
        int cy = y;
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            r.render(g, f, x, cy, maxW);
            cy += r.height(f, maxW);
        }
    }

    private static final class TextRow implements Row {
        final String s; final int c;
        TextRow(String s, int c) { this.s = s; this.c = c; }
        public int height(Font f, int maxW) { return 10; }
        public void render(GuiGraphics g, Font f, int x, int y, int maxW) {
            TextUtils.text(g, f, s, x, y, c);
        }
    }

    private static final class WrapRow implements Row {
        final String s; final int c;
        WrapRow(String s, int c) { this.s = s; this.c = c; }
        public int height(Font f, int maxW) { return TextUtils.wrappedHeight(f, s, maxW); }
        public void render(GuiGraphics g, Font f, int x, int y, int maxW) {
            TextUtils.wrapped(g, f, s, x, y, maxW, c);
        }
    }

    private static final class GapRow implements Row {
        final int px;
        GapRow(int px) { this.px = px; }
        public int height(Font f, int maxW) { return px; }
        public void render(GuiGraphics g, Font f, int x, int y, int maxW) {}
    }
}