package com.fixmer.mared.gui2.development;

/** Layout in scaled GUI pixels. The sidebar collapses to icons in small windows. */
public record DevelopmentLayout129(int width, int height, boolean narrow, int contentX,
                                   int contentY, int contentWidth, int contentBottom) {
    public static DevelopmentLayout129 of(int width, int height) {
        boolean narrow = width < 480 || height < 240;
        int x = 12;
        return new DevelopmentLayout129(width, height, narrow, x, 44,
            Math.max(1, width - x - 12), Math.max(44, height - 38));
    }
    public int navX(int index) { return 8; }
    public int navY(int index) { return narrow ? 34 + index * 22 : 46 + index * 30; }
    public int navWidth() { return narrow ? 34 : 134; }
    public int navHeight() { return narrow ? 20 : 24; }
    public int viewportHeight() { return contentBottom - contentY; }
    public int clampScroll(int requested, int bodyHeight) {
        return Math.max(0, Math.min(requested, Math.max(0, bodyHeight - viewportHeight())));
    }
}
