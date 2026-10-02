package com.fixmer.mared.gui2.framework.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Текстовые утилиты gui2.
 *
 * 0.3.0: перенос из gui.common.MaredUi.wrapped / wrappedHeight.
 * 0.3.0 (Phase F5'): cache key теперь включает identityHashCode(Font) —
 *   раньше при смене шрифта/масштаба кэш давал грязные данные.
 *   Лимиты увеличены (2048 / 1024) — на scroll-панелях с разными
 *   размерами текста старое значение 512/256 вытеснялось мгновенно.
 */
public final class TextUtils {

    private TextUtils() {}

    private static final int HEIGHT_CACHE_MAX = 2048;
    private static final int LINES_CACHE_MAX  = 1024;

    private static final Map<String, Integer> HEIGHT_CACHE =
        Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Integer> e) {
                return size() > HEIGHT_CACHE_MAX;
            }
        });

    private static final Map<String, List<String>> LINES_CACHE =
        Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, List<String>> e) {
                return size() > LINES_CACHE_MAX;
            }
        });

    // ============================================================
    //  Базовый текст
    // ============================================================

    public static void text(GuiGraphics g, Font f, String s, int x, int y, int c) {
        g.drawString(f, s, x, y, c, true);
    }

    public static void textNoShadow(GuiGraphics g, Font f, String s, int x, int y, int c) {
        g.drawString(f, s, x, y, c, false);
    }

    public static void centered(GuiGraphics g, Font f, String s, int cx, int y, int c) {
        g.drawString(f, s, cx - f.width(s) / 2, y, c, true);
    }

    public static String ellipsize(Font f, String s, int maxW) {
        if (s == null) return "";
        if (f.width(s) <= maxW) return s;
        int ell = f.width("...");
        if (maxW <= ell) return "";
        return f.plainSubstrByWidth(s, maxW - ell) + "...";
    }

    // ============================================================
    //  Wrapped
    // ============================================================

    /**
     * 0.3.0 (Phase F5'): cache key = text + maxW + identityHashCode(font).
     * Раньше ключ был text + maxW — при переключении шрифта (например,
     * при смене scale в MC) возвращались неправильные размеры.
     */
    private static String cacheKey(Font f, String text, int maxW) {
        return System.identityHashCode(f) + "\u0000" + maxW + "\u0000" + text;
    }

    public static List<String> wrapLines(Font f, String text, int maxW) {
        if (text == null) text = "";
        if (maxW <= 0) return Collections.singletonList(text);
        if (text.isEmpty()) return Collections.singletonList("");
        if (f.width(text) <= maxW) return Collections.singletonList(text);

        List<String> result = new ArrayList<>(4);
        int n = text.length();
        int i = 0;
        StringBuilder cur = new StringBuilder(64);
        int curW = 0;

        while (i < n) {
            int wordStart = i;
            while (i < n && text.charAt(i) == ' ') i++;
            int wordEnd = i;
            while (wordEnd < n && text.charAt(wordEnd) != ' ') wordEnd++;
            if (wordStart == wordEnd) break;

            String word = text.substring(wordStart, wordEnd);
            int wordW = f.width(word);

            if (wordW > maxW) {
                if (cur.length() > 0) {
                    result.add(cur.toString());
                    cur.setLength(0);
                    curW = 0;
                }
                int wStart = 0;
                int wn = word.length();
                while (wStart < wn) {
                    int wEnd = wStart + 1;
                    int lastFit = wStart;
                    while (wEnd <= wn) {
                        int ww = f.width(word.substring(wStart, wEnd));
                        if (ww > maxW) break;
                        lastFit = wEnd;
                        wEnd++;
                    }
                    if (lastFit == wStart) lastFit = wStart + 1;
                    result.add(word.substring(wStart, lastFit));
                    wStart = lastFit;
                }
                i = wordEnd;
                continue;
            }

            if (cur.length() == 0) {
                cur.append(word);
                curW = wordW;
            } else {
                int totalW = curW + f.width(" ") + wordW;
                if (totalW <= maxW) {
                    cur.append(' ').append(word);
                    curW = totalW;
                } else {
                    result.add(cur.toString());
                    cur.setLength(0);
                    cur.append(word);
                    curW = wordW;
                }
            }
            i = wordEnd;
        }

        if (cur.length() > 0) result.add(cur.toString());
        if (result.isEmpty()) result.add("");
        return result;
    }

    public static List<String> wrapLinesCached(Font f, String text, int maxW) {
        if (text == null) text = "";
        if (maxW <= 0) return Collections.singletonList(text);
        if (text.isEmpty()) return Collections.singletonList("");

        String key = cacheKey(f, text, maxW);
        List<String> cached = LINES_CACHE.get(key);
        if (cached != null) return cached;

        List<String> lines = wrapLines(f, text, maxW);
        LINES_CACHE.put(key, lines);
        return lines;
    }

    public static int wrapped(GuiGraphics g, Font f, String text, int x, int y,
                              int maxW, int color) {
        if (text == null || text.isEmpty()) return y;
        List<String> lines = wrapLines(f, text, maxW);
        int n = lines.size();
        for (int i = 0; i < n; i++) {
            g.drawString(f, lines.get(i), x, y, color, true);
            y += 10;
        }
        return y;
    }

    public static int wrappedHeight(Font f, String text, int maxW) {
        if (text == null || text.isEmpty()) return 10;
        if (maxW <= 0) return 10;

        String key = cacheKey(f, text, maxW);
        Integer cached = HEIGHT_CACHE.get(key);
        if (cached != null) return cached;

        int h = wrapLines(f, text, maxW).size() * 10;
        HEIGHT_CACHE.put(key, h);
        return h;
    }

    public static void clearCaches() {
        HEIGHT_CACHE.clear();
        LINES_CACHE.clear();
    }
}