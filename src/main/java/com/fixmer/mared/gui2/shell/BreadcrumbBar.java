package com.fixmer.mared.gui2.shell;

import java.util.List;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.navigation.MaredSpace;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Левая часть TopBar'а — путь навигации.
 *
 * Рисует:
 *   MaRed > Genesis > Content > Zombie
 *
 * с акцентной подсветкой текущего сегмента и тонкими стрелками
 * между ними. При мыши на сегменте — underline (в будущем — клик
 * на сегмент = back до этого уровня).
 */
public final class BreadcrumbBar {

    private BreadcrumbBar() {}

    private static final int SEP_GAP = 8;
    private static final String SEPARATOR = ">";   // ASCII-safe, не U+203A

    private static final int TEXT_DIM  = 0xFF808090;
    private static final int TEXT_MAIN = 0xFFE8E8F0;
    private static final int SEP_COLOR = 0xFF555566;

    public static void render(GuiGraphics g, Font font,
                              List<MaredSpace> stack,
                              int x, int y,
                              int mouseX, int mouseY) {

        if (stack == null || stack.isEmpty()) return;

        String rootLabel = MaredLang.get("mared.shell.root");
        if (rootLabel.equals("mared.shell.root")) rootLabel = "MaRed";

        // v14: тёмная подложка, чтобы breadcrumb читался поверх
        // Studio TopBar и любых других панелей под ним.
        int cx = x;
        int cy = y;
        int width = estimateWidth(font, rootLabel, stack);
        g.fill(x - 6, y - 3, x + width + 6, y + 12, 0xB0000000);

        // Root
        cx = drawSegment(g, font, rootLabel, cx, cy,
            TEXT_DIM, 0xFFFF55FF, mouseX, mouseY);

        for (int i = 0; i < stack.size(); i++) {
            MaredSpace space = stack.get(i);
            boolean isCurrent = (i == stack.size() - 1);

            cx = drawSeparator(g, font, cx, cy);

            String title = MaredLang.get(space.titleKey());
            if (title.equals(space.titleKey())) {
                title = fallbackTitle(space);
            }

            int color = isCurrent ? TEXT_MAIN : TEXT_DIM;
            cx = drawSegment(g, font, title, cx, cy,
                color, space.accentColor(), mouseX, mouseY);
        }
    }

    private static int estimateWidth(Font font, String root,
                                      List<MaredSpace> stack) {
        int w = font.width(root);
        for (MaredSpace space : stack) {
            String title = MaredLang.get(space.titleKey());
            if (title.equals(space.titleKey())) title = fallbackTitle(space);
            w += SEP_GAP * 2 + font.width(SEPARATOR) + font.width(title);
        }
        return w;
    }

    private static int drawSegment(GuiGraphics g, Font font, String text,
                                   int x, int y,
                                   int color, int accent,
                                   int mouseX, int mouseY) {
        int w = font.width(text);
        boolean hover = mouseX >= x && mouseX < x + w
                     && mouseY >= y && mouseY < y + 8;
        g.drawString(font, text, x, y, color, false);
        if (hover) {
            g.fill(x, y + 9, x + w, y + 10, accent);
        }
        return x + w;
    }

    private static int drawSeparator(GuiGraphics g, Font font, int x, int y) {
        int sx = x + SEP_GAP / 2;
        g.drawString(font, SEPARATOR, sx, y, SEP_COLOR, false);
        return sx + font.width(SEPARATOR) + SEP_GAP / 2;
    }

    private static String fallbackTitle(MaredSpace space) {
        return switch (space.id()) {
            case GENESIS   -> "Genesis";
            case CONTENT   -> "Content";
            case WORLD     -> "World";
            case LOGIC     -> "Logic";
            case RESOURCES -> "Resources";
            case TOOLS     -> "Tools";
            case SCENARIOS -> "Scenarios";
            case STUDIO    -> "Studio";
        };
    }
}