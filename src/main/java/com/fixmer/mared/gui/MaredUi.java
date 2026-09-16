package com.fixmer.mared.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class MaredUi {

    private MaredUi() {}

    // ---------- базовые примитивы ----------

    public static void rect(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.renderOutline(x, y, w, h, color);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, border);
    }

    public static void panelGradient(GuiGraphics g, int x, int y, int w, int h,
                                     int bg, int topColor, int bottomColor) {
        g.fill(x, y, x + w, y + h, bg);
        outlineGradient(g, x, y, w, h, topColor, bottomColor);
    }

    // ---------- 3D-панели (свет сверху) ----------

    /**
     * Панель с лёгким вертикальным градиентом: верх светлее, низ темнее.
     * Даёт эффект «свет падает сверху».
     */
    public static void panelLit(GuiGraphics g, int x, int y, int w, int h, int baseColor) {
        int top = lighten(baseColor, 0.06f);
        int bottom = darken(baseColor, 0.10f);
        gradientV(g, x, y, x + w, y + h, top, bottom);
    }

    /** Панель с лёгким градиентом + однотонной рамкой. */
    public static void panelLitBordered(GuiGraphics g, int x, int y, int w, int h,
                                        int baseColor, int border) {
        panelLit(g, x, y, w, h, baseColor);
        outline(g, x, y, w, h, border);
    }

    // ---------- работа с цветом ----------

    /** Осветлить цвет. amount ∈ [0,1]. */
    public static int lighten(int color, float amount) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return (a << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    /** Затемнить цвет. amount ∈ [0,1]. */
    public static int darken(int color, float amount) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = (int) (r * (1 - amount));
        g = (int) (g * (1 - amount));
        b = (int) (b * (1 - amount));
        return (a << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    // ---------- градиенты ----------

    public static void outlineGradient(GuiGraphics g, int x, int y, int w, int h,
                                       int topColor, int bottomColor) {
        g.fill(x, y, x + w, y + 1, topColor);
        g.fill(x, y + h - 1, x + w, y + h, bottomColor);
        gradientV(g, x, y, x + 1, y + h, topColor, bottomColor);
        gradientV(g, x + w - 1, y, x + w, y + h, topColor, bottomColor);
    }

    public static void gradientV(GuiGraphics g, int x1, int y1, int x2, int y2,
                                 int topColor, int bottomColor) {
        int h = y2 - y1;
        if (h <= 0) return;
        for (int i = 0; i < h; i++) {
            int c = lerpColor(topColor, bottomColor, (float) i / Math.max(1, h - 1));
            g.fill(x1, y1 + i, x2, y1 + i + 1, c);
        }
    }

    public static int lerpColor(int a, int b, float t) {
        if (t < 0) t = 0; else if (t > 1) t = 1;
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    // ---------- текст ----------

    public static void text(GuiGraphics g, Font f, String s, int x, int y, int c) {
        g.drawString(f, s, x, y, c, true);
    }

    public static void centered(GuiGraphics g, Font f, String s, int cx, int y, int c) {
        g.drawString(f, s, cx - f.width(s) / 2, y, c, true);
    }

    public static boolean hovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------- wrapped ----------

    public static int wrapped(GuiGraphics g, Font f, String text, int x, int y, int maxW, int c) {
        if (text == null || text.isEmpty()) return y;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            while (f.width(word) > maxW) {
                if (line.length() > 0) {
                    g.drawString(f, line.toString(), x, y, c, true);
                    y += 10;
                    line = new StringBuilder();
                }
                int cut = 1;
                while (cut < word.length() && f.width(word.substring(0, cut + 1)) <= maxW) cut++;
                g.drawString(f, word.substring(0, cut), x, y, c, true);
                y += 10;
                word = word.substring(cut);
            }
            String test = line.length() == 0 ? word : line + " " + word;
            if (f.width(test) > maxW) {
                if (line.length() > 0) {
                    g.drawString(f, line.toString(), x, y, c, true);
                    y += 10;
                }
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) {
            g.drawString(f, line.toString(), x, y, c, true);
            y += 10;
        }
        return y;
    }

    public static int wrappedHeight(Font f, String text, int maxW) {
        if (text == null || text.isEmpty()) return 10;
        int lines = 0;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            while (f.width(word) > maxW) {
                if (line.length() > 0) { lines++; line = new StringBuilder(); }
                int cut = 1;
                while (cut < word.length() && f.width(word.substring(0, cut + 1)) <= maxW) cut++;
                lines++;
                word = word.substring(cut);
            }
            String test = line.length() == 0 ? word : line + " " + word;
            if (f.width(test) > maxW) {
                if (line.length() > 0) lines++;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) lines++;
        return Math.max(1, lines) * 10;
    }

    public static List<String> wrapLines(Font f, String text, int maxW) {
        List<String> result = new ArrayList<>();
        if (text == null) text = "";
        if (maxW <= 0) { result.add(text); return result; }
        if (text.isEmpty()) { result.add(""); return result; }
        if (f.width(text) <= maxW) { result.add(text); return result; }

        StringBuilder current = new StringBuilder();
        int i = 0;
        int n = text.length();

        while (i < n) {
            int wordEnd = i;
            while (wordEnd < n && text.charAt(wordEnd) != ' ') wordEnd++;
            String word = text.substring(i, wordEnd);
            String space = (wordEnd < n) ? " " : "";

            while (f.width(word) > maxW) {
                int cut = 1;
                while (cut < word.length()
                       && f.width(word.substring(0, cut + 1)) <= maxW) {
                    cut++;
                }
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                result.add(word.substring(0, cut));
                word = word.substring(cut);
            }

            String test = current.length() == 0 ? word : current + " " + word;
            if (f.width(test) > maxW) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                current.append(word);
            } else {
                current = new StringBuilder(test);
            }

            i = wordEnd + space.length();
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }
        if (result.isEmpty()) result.add("");
        return result;
    }

    // ---------- кнопки ----------

    public static void button(GuiGraphics g, Font f, int x, int y, int w, int h, String label,
                              int bg, int border, boolean hovered, int textColor) {
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, border);
        centered(g, f, label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    public static void buttonGradient(GuiGraphics g, Font f, int x, int y, int w, int h, String label,
                                      int bg, int topColor, int bottomColor, int textColor) {
        g.fill(x, y, x + w, y + h, bg);
        outlineGradient(g, x, y, w, h, topColor, bottomColor);
        centered(g, f, label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    // ---------- линии ----------

    public static void dashedLine(GuiGraphics g, int x1, int y, int x2, int color) {
        int dash = 4, gap = 3, x = x1;
        while (x < x2) {
            int e = Math.min(x + dash, x2);
            g.fill(x, y, e, y + 1, color);
            x += dash + gap;
        }
    }

    public static void dashedLineGradient(GuiGraphics g, int x1, int y, int x2,
                                          int leftColor, int rightColor) {
        int dash = 4, gap = 3, x = x1;
        int total = Math.max(1, x2 - x1);
        while (x < x2) {
            int e = Math.min(x + dash, x2);
            int c = lerpColor(leftColor, rightColor, (float) (x - x1) / total);
            g.fill(x, y, e, y + 1, c);
            x += dash + gap;
        }
    }

    public static void scissorOn(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    public static void scissorOff(GuiGraphics g) {
        g.disableScissor();
    }

    // ---------- drag ----------

    public enum DragKind { NONE, FILE_SCROLL, CMD_SCROLL, INFO_SCROLL, LOG_SCROLL }

    public static class DragState {
        public DragKind kind = DragKind.NONE;
        public double startY;
        public int startOffset, thumbH, trackH;

        public void start(DragKind k, double my, int offset, int thumbH, int trackH) {
            this.kind = k;
            this.startY = my;
            this.startOffset = offset;
            this.thumbH = thumbH;
            this.trackH = trackH;
        }

        public void clear() { kind = DragKind.NONE; }

        public boolean active() { return kind != DragKind.NONE; }
    }

    // ---------- скролл-область ----------

    public static class ScrollArea {
        public int x, y, w, h;
        public int offset;
        public int itemHeight, itemCount;
        public boolean inverted = false;

        public ScrollArea set(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            return this;
        }

        public ScrollArea items(int itemHeight, int itemCount) {
            this.itemHeight = itemHeight;
            this.itemCount = itemCount;
            clamp();
            return this;
        }

        public ScrollArea inverted(boolean inv) {
            this.inverted = inv;
            return this;
        }

        public int visibleItems() {
            if (itemHeight <= 0) return 0;
            return Math.max(1, Math.min(h, itemCount * itemHeight) / itemHeight);
        }

        public int trackH() { return visibleItems() * itemHeight; }

        public int maxScroll() { return Math.max(0, itemCount - visibleItems()); }

        public boolean scrollable() { return maxScroll() > 0; }

        public void clamp() { offset = Math.max(0, Math.min(maxScroll(), offset)); }

        public int contentW(int scrollbarWidth) { return w - scrollbarWidth - 2; }

        public int thumbH() {
            int th = trackH();
            if (itemCount == 0) return th;
            return Math.max(10, th * visibleItems() / itemCount);
        }

        public int thumbY() {
            int th = trackH();
            int max = maxScroll();
            if (max <= 0) return y;
            int travel = th - thumbH();
            int effOffset = inverted ? (max - offset) : offset;
            return y + travel * effOffset / max;
        }

        public void drawScrollbar(GuiGraphics g, int scrollbarColor, int width) {
            if (!scrollable()) return;
            int th = trackH();
            int trackX = x + w - width - 2;
            g.fill(trackX, y, trackX + width, y + th, 0xFF15151E);
            int thumbH = thumbH();
            int thumbY = thumbY();
            g.fill(trackX, thumbY, trackX + width, thumbY + thumbH, scrollbarColor);
        }

        public void drawScrollbarGradient(GuiGraphics g, int topColor, int bottomColor, int width) {
            if (!scrollable()) return;
            int th = trackH();
            int trackX = x + w - width - 2;
            g.fill(trackX, y, trackX + width, y + th, 0xFF15151E);
            int thumbH = thumbH();
            int thumbY = thumbY();
            gradientV(g, trackX, thumbY, trackX + width, thumbY + thumbH, topColor, bottomColor);
        }

        public boolean clickScrollbar(double mx, double my, int width, DragState drag, DragKind kind) {
            if (!scrollable()) return false;
            int th = trackH();
            int trackX = x + w - width - 2;
            if (!hovered(mx, my, trackX - 2, y, width + 4, th)) return false;
            int thumbH = thumbH();
            int thumbY = thumbY();
            if (my >= thumbY && my < thumbY + thumbH) {
                drag.start(kind, my, offset, thumbH, th);
            } else {
                int rel = (int) ((my - y) / Math.max(1, th) * maxScroll());
                int newOffset = inverted ? (maxScroll() - rel) : rel;
                offset = Math.max(0, Math.min(maxScroll(), newOffset));
            }
            return true;
        }

        public void dragScrollbar(double my, DragState drag) {
            int max = maxScroll();
            int travel = Math.max(1, drag.trackH - drag.thumbH);
            int delta = (int) Math.round((my - drag.startY) * max / travel);
            int newOffset = inverted ? drag.startOffset - delta : drag.startOffset + delta;
            offset = Math.max(0, Math.min(max, newOffset));
        }

        public void wheel(double deltaY, int step) {
            if (deltaY < 0) offset = Math.min(maxScroll(), offset + step);
            else if (deltaY > 0) offset = Math.max(0, offset - step);
        }

        public void wheelLog(double deltaY, int step) {
            if (deltaY < 0) offset = Math.max(0, offset - step);
            else if (deltaY > 0) offset = Math.min(maxScroll(), offset + step);
        }

        public int hitItem(double mx, double my, int scrollbarWidth) {
            if (my < y || my >= y + h) return -1;
            if (mx < x || mx >= x + w) return -1;
            if (scrollable() && mx >= x + w - scrollbarWidth - 2) return -1;
            int row = ((int) my - y) / itemHeight;
            int idx = row + offset;
            return (idx >= 0 && idx < itemCount) ? idx : -1;
        }
    }

    // ---------- pixel scroll ----------

    public static class PixelScroll {
        public int x, y, w, h;
        public int offset;
        public int contentHeight;

        public PixelScroll set(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            return this;
        }

        public PixelScroll content(int contentHeight) {
            this.contentHeight = contentHeight;
            clamp();
            return this;
        }

        public int maxScroll() { return Math.max(0, contentHeight - h); }
        public boolean scrollable() { return maxScroll() > 0; }
        public void clamp() { offset = Math.max(0, Math.min(maxScroll(), offset)); }

        public int thumbH() { return Math.max(10, h * h / Math.max(1, contentHeight)); }

        public int thumbY() {
            int max = maxScroll();
            if (max <= 0) return y;
            int travel = h - thumbH();
            return y + travel * offset / max;
        }

        public void drawScrollbarGradient(GuiGraphics g, int topColor, int bottomColor, int width) {
            if (!scrollable()) return;
            int trackX = x + w - width - 2;
            g.fill(trackX, y, trackX + width, y + h, 0xFF15151E);
            int thumbH = thumbH();
            int thumbY = thumbY();
            gradientV(g, trackX, thumbY, trackX + width, thumbY + thumbH, topColor, bottomColor);
        }

        public boolean clickScrollbar(double mx, double my, int width, DragState drag, DragKind kind) {
            if (!scrollable()) return false;
            int trackX = x + w - width - 2;
            if (!hovered(mx, my, trackX - 2, y, width + 4, h)) return false;
            int thumbH = thumbH();
            int thumbY = thumbY();
            if (my >= thumbY && my < thumbY + thumbH) {
                drag.start(kind, my, offset, thumbH, h);
            } else {
                int rel = (int) ((my - y) / Math.max(1, h) * maxScroll());
                offset = Math.max(0, Math.min(maxScroll(), rel));
            }
            return true;
        }

        public void dragScrollbar(double my, DragState drag) {
            int max = maxScroll();
            int travel = Math.max(1, drag.trackH - drag.thumbH);
            offset = Math.max(0, Math.min(max,
                (int) Math.round(drag.startOffset + (my - drag.startY) * max / travel)));
        }

        public void wheel(double deltaY, int step) {
            if (deltaY < 0) offset = Math.min(maxScroll(), offset + step);
            else if (deltaY > 0) offset = Math.max(0, offset - step);
        }
    }

    // ---------- список ----------

    public interface ItemRenderer {
        void render(GuiGraphics g, Font f, int idx, int x, int y, int w, int h,
                    boolean hovered, boolean selected);
    }

    public static void listGradient(GuiGraphics g, Font f, ScrollArea area,
                                    int selectedIdx, int scrollbarWidth,
                                    int topColor, int bottomColor,
                                    int selColor, int hoverColor, int normalColor,
                                    ItemRenderer renderer, int mouseX, int mouseY) {
        if (area.itemCount == 0) return;
        area.clamp();

        int itemW = area.contentW(scrollbarWidth);
        int visible = area.visibleItems();
        for (int i = 0; i < visible; i++) {
            int idx = i + area.offset;
            if (idx >= area.itemCount) break;
            int itemY = area.y + i * area.itemHeight;
            boolean selected = idx == selectedIdx;
            boolean hov = mouseX >= area.x && mouseX < area.x + itemW
                       && mouseY >= itemY && mouseY < itemY + area.itemHeight - 2;
            int bg = selected ? selColor : (hov ? hoverColor : normalColor);
            rect(g, area.x, itemY, area.x + itemW, itemY + area.itemHeight - 2, bg);
            if (selected) {
                gradientV(g, area.x, itemY, area.x + 2, itemY + area.itemHeight - 2,
                    topColor, bottomColor);
            }
            renderer.render(g, f, idx, area.x, itemY, itemW, area.itemHeight - 2, hov, selected);
        }
        area.drawScrollbarGradient(g, topColor, bottomColor, scrollbarWidth);
    }

    // ---------- Content ----------

    public static class Content {
        private final List<Row> rows = new ArrayList<>();

        public interface Row {
            int height(Font f, int maxW);
            void render(GuiGraphics g, Font f, int x, int y, int maxW);
        }

        public Content text(String s, int c) { rows.add(new TextRow(s, c)); return this; }
        public Content wrapped(String s, int c) { rows.add(new WrapRow(s, c)); return this; }
        public Content gap(int px) { rows.add(new GapRow(px)); return this; }

        public int height(Font f, int maxW) {
            int h = 0;
            for (Row r : rows) h += r.height(f, maxW);
            return h;
        }

        public void render(GuiGraphics g, Font f, int x, int y, int maxW) {
            int cy = y;
            for (Row r : rows) {
                r.render(g, f, x, cy, maxW);
                cy += r.height(f, maxW);
            }
        }

        private static class TextRow implements Row {
            final String s; final int c;
            TextRow(String s, int c) { this.s = s; this.c = c; }
            public int height(Font f, int maxW) { return 10; }
            public void render(GuiGraphics g, Font f, int x, int y, int maxW) {
                g.drawString(f, s, x, y, c, true);
            }
        }

        private static class WrapRow implements Row {
            final String s; final int c;
            WrapRow(String s, int c) { this.s = s; this.c = c; }
            public int height(Font f, int maxW) { return MaredUi.wrappedHeight(f, s, maxW); }
            public void render(GuiGraphics g, Font f, int x, int y, int maxW) {
                MaredUi.wrapped(g, f, s, x, y, maxW, c);
            }
        }

        private static class GapRow implements Row {
            final int px;
            GapRow(int px) { this.px = px; }
            public int height(Font f, int maxW) { return px; }
            public void render(GuiGraphics g, Font f, int x, int y, int maxW) {}
        }
    }
}