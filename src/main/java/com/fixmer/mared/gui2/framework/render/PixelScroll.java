package com.fixmer.mared.gui2.framework.render;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Phase B): вынесен из MaredUi в top-level.
 * Пиксельный скролл (переменная высота контента).
 */
public final class PixelScroll {

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

    public int maxScroll()   { return Math.max(0, contentHeight - h); }
    public boolean scrollable() { return maxScroll() > 0; }
    public void clamp()      { offset = Math.max(0, Math.min(maxScroll(), offset)); }

    public int thumbH() {
        return Math.max(10, h * h / Math.max(1, contentHeight));
    }

    public int thumbY() {
        int max = maxScroll();
        if (max <= 0) return y;
        return y + (h - thumbH()) * offset / max;
    }

    public void drawScrollbarGradient(GuiGraphics g, int topColor,
                                      int bottomColor, int width) {
        if (!scrollable()) return;
        int trackX = x + w - width - 2;
        UiControls.scrollbarTrack(g, trackX, y, width, h);
        UiControls.scrollbarThumb(g, trackX, thumbY(), width, thumbH(), topColor, bottomColor);
    }

    public boolean clickScrollbar(double mx, double my, int width,
                                  DragState drag, DragKind kind) {
        if (!scrollable()) return false;
        int trackX = x + w - width - 2;
        if (!Render.hovered(mx, my, trackX - 2, y, width + 4, h)) return false;
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