package com.fixmer.mared.gui2.framework.render;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Phase B): вынесен из MaredUi в top-level.
 * Поэлементный скролл (фикс. высота элемента).
 */
public final class ScrollArea {

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
        this.itemCount  = itemCount;
        clamp();
        return this;
    }

    public ScrollArea inverted(boolean inv) { this.inverted = inv; return this; }

    public int visibleItems() {
        if (itemHeight <= 0) return 0;
        return Math.max(1, Math.min(h, itemCount * itemHeight) / itemHeight);
    }

    public int trackH()      { return visibleItems() * itemHeight; }
    public int maxScroll()   { return Math.max(0, itemCount - visibleItems()); }
    public boolean scrollable() { return maxScroll() > 0; }
    public void clamp()      { offset = Math.max(0, Math.min(maxScroll(), offset)); }
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

    public void drawScrollbar(GuiGraphics g, int color, int width) {
        if (!scrollable()) return;
        int th = trackH();
        int trackX = x + w - width - 2;
        UiControls.scrollbarTrack(g, trackX, y, width, th);
        UiControls.scrollbarThumb(g, trackX, thumbY(), width, thumbH(), color, color);
    }

    public void drawScrollbarGradient(GuiGraphics g, int topColor,
                                      int bottomColor, int width) {
        if (!scrollable()) return;
        int th = trackH();
        int trackX = x + w - width - 2;
        UiControls.scrollbarTrack(g, trackX, y, width, th);
        UiControls.scrollbarThumb(g, trackX, thumbY(), width, thumbH(), topColor, bottomColor);
    }

    public boolean clickScrollbar(double mx, double my, int width,
                                  DragState drag, DragKind kind) {
        if (!scrollable()) return false;
        int th = trackH();
        int trackX = x + w - width - 2;
        if (!Render.hovered(mx, my, trackX - 2, y, width + 4, th)) return false;
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