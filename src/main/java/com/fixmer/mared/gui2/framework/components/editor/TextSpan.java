package com.fixmer.mared.gui2.framework.components.editor;

/**
 * Непрерывный участок текста с одним цветом.
 *
 * 0.3.2:
 *   Раньше EditorView рисовал каждый символ через отдельный
 *   g.drawString() — на видимых 30 строках по 100 символов это 3000
 *   draw calls на кадр. Теперь строка разбивается на spans, каждый
 *   span — один draw call.
 *
 * Координаты — в logical columns (не визуальные).
 * start включительно, end исключительно.
 */
public record TextSpan(int start, int end, int color) {

    public TextSpan {
        if (end < start) {
            int t = start; start = end; end = t;
        }
    }

    public boolean isEmpty() {
        return end <= start;
    }

    public int length() {
        return end - start;
    }

    /** Пересекается ли с диапазоном [lo, hi). */
    public boolean overlaps(int lo, int hi) {
        return start < hi && end > lo;
    }

    /** Обрезка по [lo, hi). Возвращает новый span или null. */
    public TextSpan clamp(int lo, int hi) {
        int s = Math.max(start, lo);
        int e = Math.min(end, hi);
        if (e <= s) return null;
        return new TextSpan(s, e, color);
    }
}