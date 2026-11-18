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
        if(text==null)text="";
        var result=new ArrayList<String>();
        for(String paragraph:text.replace("\r\n","\n").replace('\r','\n').split("\n",-1)) {
            if(paragraph.isEmpty()||maxW<=0){result.add(paragraph);continue;}
            int at=0;
            while(at<paragraph.length()){
                String rest=paragraph.substring(at);
                String part=f.plainSubstrByWidth(rest,maxW);
                int end=part.length();
                if(end>0&&end<rest.length()&&Character.isHighSurrogate(rest.charAt(end-1))&&Character.isLowSurrogate(rest.charAt(end)))end--;
                if(end==0)end=Character.charCount(rest.codePointAt(0));
                if(end<rest.length()){
                    int space=rest.lastIndexOf(' ',end-1);
                    if(space>0)end=space+1;
                }
                result.add(rest.substring(0,end));at+=end;
            }
        }
        return List.copyOf(result);
    }

    public static List<String> wrapLinesCached(Font f, String text, int maxW) {
        if (text == null) text = "";
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