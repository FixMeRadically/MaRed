package com.fixmer.mared.gui2.framework.render.legacy;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.render.MaredColor;

/**
 * Фасад над TextUtils.
 *
 * 0.3.0 (Phase B): перенос legacy-интерфейса MaredText.
 * 0.3.0 (Phase E1): два fix'а:
 *   - Locale.ROOT при toLowerCase (турецкая локаль ломала I/ı);
 *   - highlightSubstring подсвечивает ВСЕ совпадения, а не только первое.
 *
 * @deprecated after 0.4.0 — использовать TextUtils напрямую.
 * Фасад удаляется вместе с MaredDraw/MaredWidgets.
 */
@Deprecated
public final class MaredText {

    private MaredText() {}

    public static void text(GuiGraphics g, Font f, String s, int x, int y, int c) {
        TextUtils.text(g, f, s, x, y, c);
    }

    public static void centered(GuiGraphics g, Font f, String s, int cx, int y, int c) {
        TextUtils.centered(g, f, s, cx, y, c);
    }

    public static boolean hovered(double mx, double my, int x, int y, int w, int h) {
        return Render.hovered(mx, my, x, y, w, h);
    }

    public static String ellipsize(Font f, String s, int maxW) {
        return TextUtils.ellipsize(f, s, maxW);
    }

    /**
     * Подсветить все вхождения query в text (не только первое).
     * Регистронезависимо, без учёта локали.
     */
    public static void highlightSubstring(GuiGraphics g, Font f, String text,
                                          String query, int x, int y,
                                          int baseColor, int highlightColor) {
        if (query == null || query.isEmpty()) {
            text(g, f, text, x, y, baseColor);
            return;
        }
        if (text == null || text.isEmpty()) return;

        String lower = text.toLowerCase(Locale.ROOT);
        String qLower = query.toLowerCase(Locale.ROOT);
        int qLen = query.length();

        int cursor = 0;
        int drawX = x;
        int n = text.length();
        boolean foundAny = false;

        while (cursor < n) {
            int idx = lower.indexOf(qLower, cursor);
            if (idx < 0) break;
            foundAny = true;

            if (idx > cursor) {
                String before = text.substring(cursor, idx);
                text(g, f, before, drawX, y, baseColor);
                drawX += f.width(before);
            }

            String match = text.substring(idx, idx + qLen);
            int mw = f.width(match);
            g.fill(drawX, y - 1, drawX + mw, y + 9,
                MaredColor.withAlpha(highlightColor, 100));
            text(g, f, match, drawX, y, highlightColor);
            drawX += mw;

            cursor = idx + qLen;
        }

        if (!foundAny) {
            text(g, f, text, x, y, baseColor);
            return;
        }
        if (cursor < n) {
            String tail = text.substring(cursor);
            text(g, f, tail, drawX, y, baseColor);
        }
    }

    public static List<String> wrapLines(Font f, String text, int maxW) {
        return TextUtils.wrapLines(f, text, maxW);
    }

    public static int wrapped(GuiGraphics g, Font f, String text, int x, int y,
                              int maxW, int c) {
        return TextUtils.wrapped(g, f, text, x, y, maxW, c);
    }

    public static int wrappedHeight(Font f, String text, int maxW) {
        return TextUtils.wrappedHeight(f, text, maxW);
    }

    public static void clearWrapCaches() {
        TextUtils.clearCaches();
    }
}