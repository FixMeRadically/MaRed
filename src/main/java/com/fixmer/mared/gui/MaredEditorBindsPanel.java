package com.fixmer.mared.gui;

import java.util.List;

import com.fixmer.mared.script.MaredBindRegistry;
import com.fixmer.mared.script.MaredLang;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class MaredEditorBindsPanel {

    private static final int BG           = 0xFF101018;
    private static final int TEXT         = 0xFFFFFFFF;
    private static final int TEXT_DIM     = 0xFFAAAAAA;
    private static final int BTN_BG       = 0xFF2D2D2D;
    private static final int BTN_HOVER    = 0xFF3E3E42;
    private static final int ITEM_HOVER   = 0xFF2E2E3E;
    private static final int ITEM_NORMAL  = 0xFF1A1A22;
    private static final int DANGER       = 0xFFFF4444;
    private static final int UNLOCK_COLOR = 0xFF55FF55;

    private int scrollOffset = 0;

    public MaredEditorBindsPanel() {}

    public int scrollOffset() { return scrollOffset; }
    public void resetScroll() { scrollOffset = 0; }

    /** FIX: минус вместо крестика. */
    private static void drawMinus(GuiGraphics g, int cx, int cy, int arm, int color) {
        MaredUi.rect(g, cx - arm, cy, cx + arm + 1, cy + 1, color);
    }

    public void render(GuiGraphics g, Font font, MaredEditorLayout layout,
                       boolean logCollapsed, int mouseX, int mouseY) {
        if (logCollapsed) return;

        int pad = MaredEditorLayout.PAD();
        int x = layout.logWidth() + 1;
        int logTop = layout.logTop(logCollapsed);
        int screenW = layout.screenW();
        int screenH = layout.screenH();

        MaredUi.rect(g, x, logTop, screenW, screenH, BG);
        MaredUi.rect(g, x, logTop, x + 1, screenH, MaredEditorLayout.BORDER_TOP);

        int clearW = MaredEditorLayout.px(70);
        int clearX = screenW - pad - clearW;
        int clearY = logTop + 2;

        String header = MaredLang.get("mared.ui.active_binds")
                      + " (" + MaredBindRegistry.totalCount() + ")";
        int maxHeaderW = (clearX - 4) - (x + pad);
        if (maxHeaderW > 0 && font.width(header) > maxHeaderW) {
            header = font.plainSubstrByWidth(header, Math.max(0, maxHeaderW - 4)) + "...";
        }
        MaredUi.text(g, font, header, x + pad, logTop + 6, MaredEditorTabs.EVENTS_TOP);

        boolean clearHover = MaredUi.hovered(mouseX, mouseY, clearX, clearY, clearW, 12);
        MaredUi.button(g, font, clearX, clearY, clearW, 12, MaredLang.get("mared.ui.clear"),
            clearHover ? BTN_HOVER : BTN_BG, DANGER, clearHover, DANGER);

        int listY = logTop + MaredLogPanel.LOG_HEADER + 4;
        int listH = screenH - listY - pad;
        List<String> keys = MaredBindRegistry.keys();

        if (keys.isEmpty()) {
            scrollOffset = 0;
            MaredUi.text(g, font, MaredLang.get("mared.ui.no_binds"), x + pad, listY + 2, TEXT_DIM);
            return;
        }

        int itemH = MaredEditorLayout.ITEM_H();
        int delSz = MaredEditorLayout.BIND_DEL_SZ();
        int visible = Math.max(1, listH / itemH);
        int maxOffset = Math.max(0, keys.size() - visible);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));

        for (int i = 0; i < visible; i++) {
            int idx = i + scrollOffset;
            if (idx >= keys.size()) break;
            String key = keys.get(idx);
            List<MaredBindRegistry.Entry> entries = MaredBindRegistry.entries(key);
            boolean blocking = MaredBindRegistry.hasBlocking(key);
            int itemY = listY + i * itemH;

            boolean hov = MaredUi.hovered(mouseX, mouseY, x, itemY, screenW - x, itemH - 2);
            MaredUi.rect(g, x, itemY, screenW, itemY + itemH - 2, hov ? ITEM_HOVER : ITEM_NORMAL);

            String label = key + (blocking ? " (block)" : "") + "  [" + entries.size() + "]";
            int textY = itemY + (itemH - 2 - 8) / 2;
            MaredUi.text(g, font, label, x + pad, textY, TEXT);

            int delX = screenW - pad - delSz;
            int delY = itemY + (itemH - 2 - delSz) / 2;
            boolean delHover = MaredUi.hovered(mouseX, mouseY, delX, delY, delSz, delSz);
            MaredUi.rect(g, delX, delY, delX + delSz, delY + delSz,
                delHover ? 0xFF663333 : 0xFF3A2020);
            MaredUi.outline(g, delX, delY, delSz, delSz, DANGER);
            int cx = delX + delSz / 2;
            int cy = delY + delSz / 2;
            // FIX: минус вместо крестика
            drawMinus(g, cx, cy, delSz / 2 - 2, DANGER);

            if (blocking) {
                int unlockX = delX - delSz - 4;
                int unlockY = delY;
                boolean unlockHover = MaredUi.hovered(mouseX, mouseY, unlockX, unlockY, delSz, delSz);
                MaredUi.rect(g, unlockX, unlockY, unlockX + delSz, unlockY + delSz,
                    unlockHover ? 0xFF336633 : 0xFF203A20);
                MaredUi.outline(g, unlockX, unlockY, delSz, delSz, UNLOCK_COLOR);
                int ucx = unlockX + delSz / 2;
                int ucy = unlockY + delSz / 2;
                MaredUi.centered(g, font, "U", ucx, ucy - 4, UNLOCK_COLOR);
            }
        }

        if (keys.size() > visible) {
            int trackX = screenW - MaredEditorLayout.SCROLLBAR_W() - 2;
            MaredUi.rect(g, trackX, listY, trackX + MaredEditorLayout.SCROLLBAR_W(),
                listY + listH, 0xFF15151E);
            int thumbH = Math.max(10, listH * visible / keys.size());
            int thumbY = listY + (listH - thumbH) * scrollOffset / Math.max(1, maxOffset);
            MaredUi.rect(g, trackX, thumbY, trackX + MaredEditorLayout.SCROLLBAR_W(),
                thumbY + thumbH, MaredEditorTabs.EVENTS_TOP);
        }
    }

    public boolean mouseClicked(double mx, double my, MaredEditorLayout layout,
                                boolean logCollapsed) {
        if (logCollapsed) return false;

        int screenW = layout.screenW();
        int logTop = layout.logTop(logCollapsed);
        int pad = MaredEditorLayout.PAD();

        int clearW = MaredEditorLayout.px(70);
        int clearX = screenW - pad - clearW;
        int clearY = logTop + 2;
        if (MaredUi.hovered(mx, my, clearX, clearY, clearW, 12)) {
            MaredBindRegistry.clearAll();
            resetScroll();
            return true;
        }

        int listY = logTop + MaredLogPanel.LOG_HEADER + 4;
        int itemH = MaredEditorLayout.ITEM_H();
        int delSz = MaredEditorLayout.BIND_DEL_SZ();
        List<String> keys = MaredBindRegistry.keys();
        int visible = Math.max(1, (layout.screenH() - listY - pad) / itemH);

        for (int i = 0; i < visible; i++) {
            int idx = i + scrollOffset;
            if (idx >= keys.size()) break;
            String key = keys.get(idx);
            int itemY = listY + i * itemH;
            int delX = screenW - pad - delSz;
            int delY = itemY + (itemH - 2 - delSz) / 2;

            if (MaredBindRegistry.hasBlocking(key)) {
                int unlockX = delX - delSz - 4;
                int unlockY = delY;
                if (MaredUi.hovered(mx, my, unlockX, unlockY, delSz, delSz)) {
                    MaredBindRegistry.unblock(key);
                    return true;
                }
            }
            if (MaredUi.hovered(mx, my, delX, delY, delSz, delSz)) {
                MaredBindRegistry.clear(key);
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double deltaY, MaredEditorLayout layout,
                                 boolean logCollapsed) {
        if (logCollapsed) return false;
        if (mx < layout.logWidth() || my < layout.logTop(logCollapsed)) return false;

        List<String> keys = MaredBindRegistry.keys();
        int listY = layout.logTop(logCollapsed) + MaredLogPanel.LOG_HEADER + 4;
        int itemH = MaredEditorLayout.ITEM_H();
        int visible = Math.max(1, (layout.screenH() - listY - MaredEditorLayout.PAD()) / itemH);
        int maxOffset = Math.max(0, keys.size() - visible);

        if (deltaY < 0) scrollOffset = Math.min(maxOffset, scrollOffset + 1);
        else if (deltaY > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        return true;
    }
}