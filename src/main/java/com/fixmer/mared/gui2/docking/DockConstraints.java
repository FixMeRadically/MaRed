package com.fixmer.mared.gui2.docking;

/**
 * Ограничения размера для Dock-позиции.
 *
 * 0.3.0:
 *   Раньше DockLayout знал только глобальный RATIO_MIN/MAX (0.05..0.60).
 *   Теперь каждая позиция может иметь свои ограничения в пикселях —
 *   как в IDE (Explorer: min 180px, Inspector: min 250px, ...).
 *
 * minPx / maxPx применяются ПОСЛЕ ratio: сначала считается
 * screenWidth * ratio, потом клампится в minPx..maxPx.
 */
public final class DockConstraints {

    private final int minPx;
    private final int maxPx;
    private final boolean resizable;

    private DockConstraints(int minPx, int maxPx, boolean resizable) {
        if (minPx < 0) minPx = 0;
        if (maxPx < minPx) maxPx = minPx;
        this.minPx = minPx;
        this.maxPx = maxPx;
        this.resizable = resizable;
    }

    /** Обычная позиция с resize. */
    public static DockConstraints resizable(int minPx, int maxPx) {
        return new DockConstraints(minPx, maxPx, true);
    }

    /** Фиксированная позиция (например TOP: 26px). */
    public static DockConstraints fixed(int px) {
        return new DockConstraints(px, px, false);
    }

    public int minPx() { return minPx; }
    public int maxPx() { return maxPx; }
    public boolean resizable() { return resizable; }

    /** Применить к уже посчитанному размеру. */
    public int clamp(int size) {
        if (size < minPx) return minPx;
        if (size > maxPx) return maxPx;
        return size;
    }
}