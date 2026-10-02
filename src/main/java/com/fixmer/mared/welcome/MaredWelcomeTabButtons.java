package com.fixmer.mared.welcome;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.framework.render.MaredTabStyles;
import com.fixmer.mared.gui2.framework.render.MaredUi;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 0.3.0 (Stage B7): MaredTabStyles / MaredUi — из gui2.framework.render.
 */
public final class MaredWelcomeTabButtons {

    private static final class TabButton {
        final String tab;
        final String letter;
        final String name;
        final String desc;
        int x, y, w, h;

        TabButton(String tab, String letter, String name, String desc) {
            this.tab = tab; this.letter = letter;
            this.name = name; this.desc = desc;
        }
    }

    private final TabButton[] buttons;

    public MaredWelcomeTabButtons() {
        buttons = new TabButton[]{
            new TabButton("scripts",  "S", "Scripts",  "Пиши скрипты"),
            new TabButton("commands", "C", "Commands", "Справочник\nи файлы\nкоманд"),
            new TabButton("npc",      "N", "NPC",      "Управление\nNPC"),
            new TabButton("events",   "E", "Events",   "События"),
            new TabButton("quests",   "Q", "Quests",   "Квесты"),
        };
    }

    public void layout(int centerX, int centerY) {
        int btnW = MaredUi.px(120);
        int btnH = MaredUi.px(90);
        int gapX = MaredUi.px(16);
        int gapY = MaredUi.px(16);

        int row1Count = 3;
        int row1W = row1Count * btnW + (row1Count - 1) * gapX;
        int row1X = centerX - row1W / 2;
        int row1Y = centerY - btnH - gapY / 2;

        for (int i = 0; i < row1Count; i++) {
            TabButton b = buttons[i];
            b.x = row1X + i * (btnW + gapX);
            b.y = row1Y;
            b.w = btnW;
            b.h = btnH;
        }

        int row2Count = 2;
        int row2W = row2Count * btnW + (row2Count - 1) * gapX;
        int row2X = centerX - row2W / 2;
        int row2Y = row1Y + btnH + gapY;

        for (int i = 0; i < row2Count; i++) {
            TabButton b = buttons[row1Count + i];
            b.x = row2X + i * (btnW + gapX);
            b.y = row2Y;
            b.w = btnW;
            b.h = btnH;
        }
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        for (TabButton b : buttons) {
            boolean hover = MaredUi.hovered(mouseX, mouseY,
                b.x, b.y, b.w, b.h);
            drawCard(g, font, b, hover);
        }
    }

    private void drawCard(GuiGraphics g, Font font, TabButton b,
                          boolean hover) {
        int accentTop = MaredTabStyles.topColor(b.tab);
        int accentBot = MaredTabStyles.bottomColor(b.tab);

        MaredUi.shadowAll(g, b.x, b.y, b.w, b.h, hover ? 4 : 2, 0x80000000);

        int bg = hover ? MaredUi.lighten(MaredUi.theme().bgPanelRaised, 0.05f)
                       : MaredUi.theme().bgPanelRaised;
        MaredUi.roundedRect(g, b.x, b.y, b.w, b.h,
            MaredUi.radiusLarge(), bg);

        MaredUi.roundedRect(g, b.x, b.y, b.w, MaredUi.px(4),
            MaredUi.radiusLarge(), accentTop);
        MaredUi.rect(g, b.x, b.y + MaredUi.px(2),
            b.x + b.w, b.y + MaredUi.px(4), accentTop);

        MaredUi.roundedOutline(g, b.x, b.y, b.w, b.h,
            MaredUi.radiusLarge(),
            hover ? accentTop : MaredUi.theme().border);

        if (MaredSettings.isPatternsEnabled()) {
            MaredTabStyles.drawBackgroundPattern(g, b.x + 1,
                b.y + MaredUi.px(5),
                b.w - 2, b.h - MaredUi.px(6), b.tab, 20);
        }

        int letterY = b.y + MaredUi.px(14);
        MaredUi.centered(g, font, b.letter, b.x + b.w / 2, letterY, accentTop);

        int nameY = b.y + MaredUi.px(46);
        MaredUi.centered(g, font, b.name, b.x + b.w / 2, nameY,
            MaredUi.theme().text);

        int descY = b.y + MaredUi.px(60);
        String[] lines = b.desc.split("\n");
        for (String line : lines) {
            MaredUi.centered(g, font, line, b.x + b.w / 2, descY,
                MaredUi.theme().textDim);
            descY += 10;
        }
    }

    public String mouseClicked(double mx, double my) {
        for (TabButton b : buttons) {
            if (MaredUi.hovered(mx, my, b.x, b.y, b.w, b.h)) return b.tab;
        }
        return null;
    }

    public boolean contains(double mx, double my) {
        for (TabButton b : buttons) {
            if (MaredUi.hovered(mx, my, b.x, b.y, b.w, b.h)) return true;
        }
        return false;
    }
}