package com.fixmer.mared.gui2.framework.render;

import com.fixmer.mared.gui2.framework.render.patterns.DiagonalPattern;
import com.fixmer.mared.gui2.framework.render.patterns.GridPattern;
import com.fixmer.mared.gui2.framework.render.patterns.NoisePattern;
import com.fixmer.mared.gui2.framework.render.patterns.WavePattern;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Стили табов: цвета, паттерны, torn edges.
 *
 * 0.3.0 (Phase B3b1): перенос legacy gui.common.MaredTabStyles.
 * 0.3.0 (Phase E2b): делегирует к patterns/* вместо MaredPatterns.
 */
public final class MaredTabStyles {

    private MaredTabStyles() {}

    public enum Pattern { NONE, NOISE, GRID, DIAGONAL, DOTS, WAVES, ZIGZAG, DIAMONDS, HEX }

    public static int topColor(String tab) {
        MaredTheme t = MaredThemeRegistry.active();
        if (t.monotoneTabs) return t.accent;
        return switch (tab) {
            case "scripts"  -> t.tabScripts;
            case "commands" -> t.tabCommands;
            case "npc"      -> t.tabNpc;
            case "events"   -> t.tabEvents;
            case "quests"   -> t.tabQuests;
            default         -> t.accent;
        };
    }

    public static int bottomColor(String tab) {
        return MaredColor.darken(topColor(tab), 0.25f);
    }

    public static int dimColor(String tab) {
        return MaredColor.darken(topColor(tab), 0.55f);
    }

    public static Pattern pattern(String tab) {
        MaredTheme t = MaredThemeRegistry.active();
        if (t.monotoneTabs) return Pattern.NOISE;
        return switch (tab) {
            case "scripts"  -> Pattern.ZIGZAG;
            case "commands" -> Pattern.GRID;
            case "npc"      -> Pattern.DOTS;
            case "events"   -> Pattern.WAVES;
            case "quests"   -> Pattern.DIAMONDS;
            default         -> Pattern.NOISE;
        };
    }

    public static void drawBackgroundPattern(GuiGraphics g, int x, int y,
                                             int w, int h, String tab,
                                             int baseAlpha) {
        Pattern p = pattern(tab);
        int accent = topColor(tab);
        switch (p) {
            case GRID -> GridPattern.grid(g, x, y, w, h,
                Math.max(8, MaredScale.px(24)),
                MaredColor.withAlpha(accent, baseAlpha / 2));
            case DIAGONAL -> DiagonalPattern.diagonal(g, x, y, w, h,
                Math.max(6, MaredScale.px(20)),
                MaredColor.withAlpha(accent, baseAlpha / 3));
            case DOTS -> GridPattern.dots(g, x, y, w, h,
                Math.max(8, MaredScale.px(22)), 1,
                MaredColor.withAlpha(accent, baseAlpha / 2));
            case WAVES -> WavePattern.waves(g, x, y, w, h,
                Math.max(2, MaredScale.px(4)),
                Math.max(20, MaredScale.px(60)),
                MaredColor.withAlpha(accent, baseAlpha / 4));
            case ZIGZAG -> WavePattern.zigzag(g, x, y, w, h,
                Math.max(4, MaredScale.px(10)),
                Math.max(2, MaredScale.px(4)),
                MaredColor.withAlpha(accent, baseAlpha / 3));
            case DIAMONDS -> GridPattern.diamonds(g, x, y, w, h,
                Math.max(12, MaredScale.px(36)),
                Math.max(3, MaredScale.px(6)),
                MaredColor.withAlpha(accent, baseAlpha / 3));
            case HEX -> GridPattern.hexGrid(g, x, y, w, h,
                Math.max(4, MaredScale.px(10)),
                MaredColor.withAlpha(accent, baseAlpha / 4));
            case NOISE -> NoisePattern.noise(g, x, y, w, h, 0, 0.3f,
                baseAlpha / 2);
            case NONE -> {}
        }
    }

    public static void drawTornEdges(GuiGraphics g, int x, int y, int w, int h,
                                     String tab) {
        int accent = topColor(tab);
        int color = MaredColor.withAlpha(accent, 40);
        int size = MaredScale.px(16);
        long seed = System.identityHashCode(tab);
        NoisePattern.tornCorner(g, x, y, w, h, 0,
            size, (int) seed, color);
        NoisePattern.tornCorner(g, x, y, w, h, 3,
            size, (int) (seed ^ 0xAAAA), color);
    }
}