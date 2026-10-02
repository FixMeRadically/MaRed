package com.fixmer.mared.gui2.framework.render;

/**
 * 0.3.0 (Phase B): вынесен из MaredUi в top-level.
 */
public final class DragState {

    public DragKind kind = DragKind.NONE;
    public double startY;
    public int startOffset;
    public int thumbH;
    public int trackH;

    public void start(DragKind k, double my, int offset, int thumbH, int trackH) {
        this.kind        = k;
        this.startY      = my;
        this.startOffset = offset;
        this.thumbH      = thumbH;
        this.trackH      = trackH;
    }

    public void clear()      { kind = DragKind.NONE; }
    public boolean active()  { return kind != DragKind.NONE; }
}