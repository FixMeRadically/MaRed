package com.fixmer.mared.gui.editor;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui.common.MaredUi;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class MaredEditorTabs {

    private MaredEditorTabs() {}

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

    public static int hitTab(double mx, double my) {
        if (mx < 0 || my < 0) return -1;
        int tabW = MaredEditorLayout.TAB_W();
        if (mx >= tabW) return -1;
        int idx = (int) (my / MaredEditorLayout.TAB_H());
        if (idx < 0 || idx >= TABS.length) return -1;
        return idx;
    }

    public static void draw(GuiGraphics g, Font font, String openTab, int mouseX, int mouseY) {
        int tabW = MaredEditorLayout.TAB_W();
        int tabH = MaredEditorLayout.TAB_H();
        int screenH = MaredEditorLayout.screenH();
        int textYOff = (tabH - 8) / 2 + 1;

        for (int i = 0; i < TABS.length; i++) {
            int y = i * tabH;
            if (y >= screenH) break;
            int bottom = Math.min(y + tabH, screenH);

            boolean hovered = mouseX >= 0 && mouseX < tabW && mouseY >= y && mouseY < bottom;
            boolean active = TABS[i].equals(openTab);

            int top = topColor(TABS[i]);
            int bot = bottomColor(TABS[i]);

            if (active) {
                MaredUi.gradientV(g, 2, y + 2, tabW - 2, bottom - 2, top, bot);
                MaredUi.centered(g, font, LETTERS[i], tabW / 2, y + textYOff, TEXT);
            } else {
                int bg = hovered ? ITEM_HOVER : ITEM_NORMAL;
                MaredUi.rect(g, 2, y + 2, tabW - 2, bottom - 2, bg);
                MaredUi.centered(g, font, LETTERS[i], tabW / 2, y + textYOff, top);
            }
        }
    }

    // ============================================================
    //  Цвета — без аллокации массивов
    // ============================================================

    public static int topColor(String tab) {
        return switch (tab) {
            case "scripts"  -> SCRIPTS_TOP;
            case "commands" -> COMMANDS_TOP;
            case "npc"      -> NPC_TOP;
            case "events"   -> EVENTS_TOP;
            default         -> QUESTS_TOP;
        };
    }

    public static int bottomColor(String tab) {
        return switch (tab) {
            case "scripts"  -> SCRIPTS_BOT;
            case "commands" -> COMMANDS_BOT;
            case "npc"      -> NPC_BOT;
            case "events"   -> EVENTS_BOT;
            default         -> QUESTS_BOT;
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
        return "commands".equals(tab);
    }
}