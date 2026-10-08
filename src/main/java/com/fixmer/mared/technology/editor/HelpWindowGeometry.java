package com.fixmer.mared.technology.editor;

import com.fixmer.genesis.technology.editor.EditorLayout.Rect;

/** Relative geometry survives GUI scale changes; windows always remain inside the viewport. */
public final class HelpWindowGeometry {
    private HelpWindowGeometry() {}
    public static Rect calculate(int width, int height, double x, double y, double w, double h) {
        width = Math.max(0, width); height = Math.max(0, height);
        int margin = Math.min(8, Math.min(width, height) / 2);
        int availableW = Math.max(0, width - margin * 2);
        int availableH = Math.max(0, height - margin * 2);
        int windowW = Math.min(availableW, Math.max(Math.min(320, availableW), (int)(finite(w,.70) * width)));
        int windowH = Math.min(availableH, Math.max(Math.min(170, availableH), (int)(finite(h,.80) * height)));
        int left = Math.max(margin, Math.min(width - margin - windowW, (int)(finite(x,.18) * width)));
        int top = Math.max(margin, Math.min(height - margin - windowH, (int)(finite(y,.10) * height)));
        return new Rect(left, top, windowW, windowH);
    }
    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
    }
}
