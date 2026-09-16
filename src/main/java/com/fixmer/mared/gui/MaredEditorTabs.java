package com.fixmer.mared.gui;

import com.fixmer.mared.script.MaredLang;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class MaredEditorTabs {

    public static final String[] TABS    = {"scripts", "commands", "npc", "events", "quests"};
    public static final String[] LETTERS = {"S", "C", "N", "E", "Q"};

    public static final int SCRIPTS_TOP  = 0xFFFF55FF;
    public static final int SCRIPTS_BOT  = 0xFFDD33DD;
    public static final int COMMANDS_TOP = 0xFFFFAA00;
    public static final int COMMANDS_BOT = 0xFFFF8800;
    public static final int NPC_TOP      = 0xFF55FF55;
    public static final int NPC_BOT      = 0xFF33DD33;
    public static final int EVENTS_TOP   = 0xFF55AAFF;
    public static final int EVENTS_BOT   = 0xFF3388DD;
    public static final int QUESTS_TOP   = 0xFFFF5555;
    public static final int QUESTS_BOT   = 0xFFDD3333;

    private static final int TEXT       = 0xFFFFFFFF;
    private static final int ITEM_HOVER = 0xFF3A3A4A;
    private static final int ITEM_NORMAL= 0xFF252530;

    private MaredEditorTabs() {}

    public static int hitTab(double mx, double my) {
        if (mx < 0 || my < 0) return -1;
        int tabW = MaredEditorLayout.TAB_W();
        int tabH = MaredEditorLayout.TAB_H();
        if (mx >= tabW) return -1;
        int idx = (int) (my / tabH);
        if (idx < 0 || idx >= TABS.length) return -1;
        return idx;
    }

    public static void draw(GuiGraphics g, Font font, String openTab, int mouseX, int mouseY) {
        int tabW = MaredEditorLayout.TAB_W();
        int tabH = MaredEditorLayout.TAB_H();
        int screenH = MaredEditorLayout.screenH();

        for (int i = 0; i < TABS.length; i++) {
            int y = i * tabH;
            // FIX: обрезка — не рисуем ниже экрана
            if (y >= screenH) break;
            int bottom = Math.min(y + tabH, screenH);

            boolean hovered = mouseX >= 0 && mouseX < tabW && mouseY >= y && mouseY < bottom;
            boolean active = TABS[i].equals(openTab);

            int[] colors = colorsFor(TABS[i]);
            int top = colors[0], bot = colors[1];

            if (active) {
                MaredUi.gradientV(g, 2, y + 2, tabW - 2, bottom - 2, top, bot);
                MaredUi.centered(g, font, LETTERS[i], tabW / 2, y + (tabH - 8) / 2 + 1, TEXT);
            } else {
                int bg = hovered ? ITEM_HOVER : ITEM_NORMAL;
                MaredUi.rect(g, 2, y + 2, tabW - 2, bottom - 2, bg);
                MaredUi.centered(g, font, LETTERS[i], tabW / 2, y + (tabH - 8) / 2 + 1, top);
            }
        }
    }

    public static int[] colorsFor(String tab) {
        return switch (tab) {
            case "scripts"  -> new int[]{SCRIPTS_TOP,  SCRIPTS_BOT};
            case "commands" -> new int[]{COMMANDS_TOP, COMMANDS_BOT};
            case "npc"      -> new int[]{NPC_TOP,      NPC_BOT};
            case "events"   -> new int[]{EVENTS_TOP,   EVENTS_BOT};
            default         -> new int[]{QUESTS_TOP,   QUESTS_BOT};
        };
    }

    public static String title(String key) {
        return switch (key) {
            case "scripts"  -> MaredLang.get("mared.ui.title.scripts");
            case "commands" -> MaredLang.get("mared.ui.title.commands");
            case "npc"      -> MaredLang.get("mared.ui.title.npc");
            case "events"   -> MaredLang.get("mared.ui.title.events");
            case "quests"   -> MaredLang.get("mared.ui.title.quests");
            default -> key;
        };
    }

    public static boolean isSupported(String tab) {
        return "scripts".equals(tab) || "commands".equals(tab);
    }
}