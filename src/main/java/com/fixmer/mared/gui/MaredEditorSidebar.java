package com.fixmer.mared.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredPersistentStorage;
import com.fixmer.mared.script.MaredScriptStorage;
import com.fixmer.mared.storage.MaredCommandStorage;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class MaredEditorSidebar {

    private static final int TEXT        = MaredUi.TEXT;
    private static final int TEXT_DIM    = MaredUi.TEXT_DIM;
    private static final int ITEM_HOVER  = 0xFF2E2E3E;
    private static final int ITEM_NORMAL = 0xFF1A1A22;
    private static final int BTN_BG      = 0xFF2D2D2D;
    private static final int BTN_HOVER   = 0xFF3E3E42;
    private static final int PERSISTENT_COLOR = 0xFF55FF88;
    private static final int MRED_COLOR  = 0xFFFF3333;
    private static final int FILTER_BG   = MaredUi.SUNKEN_BG;

    private static final int TOGGLE_W_FIXED = 40;
    private static final int TOGGLE_H_FIXED = 14;

    private final List<String> fileNames = new ArrayList<>();
    private final List<MaredCommandRegistry.CommandInfo> filteredCommands = new ArrayList<>();
    private String selectedFile = null;
    private MaredCommandRegistry.CommandInfo selectedCommandInfo = null;
    private boolean showMaredCommands = false;
    private String commandFilter = "";

    private final MaredUi.ScrollArea fileScroll = new MaredUi.ScrollArea();
    private final MaredUi.ScrollArea cmdScroll  = new MaredUi.ScrollArea();

    private EditBox commandFilterBox;

    public boolean shouldToggleSidebar = false;
    public boolean shouldRebuildEditor = false;
    public boolean shouldRefreshCommands = false;
    public boolean shouldReloadFiles = false;

    public MaredEditorSidebar() {}

    // ============================================================
    //  Геометрия
    // ============================================================

    public record SidebarGeometry(
        int tabW, int pad, int sidebarW,
        int listX, int listW,
        int listY, int filesH, int filesListY, int filesListH, int itemH,
        int arrowX, int plusX,
        int toggleX, int toggleY, int toggleW, int toggleH,
        int commandsY, int filterY, int commandsListY, int commandsH,
        int scrollbarW
    ) {}

    public static SidebarGeometry computeGeometry(MaredEditorLayout layout,
                                                   boolean logCollapsed,
                                                   String openTab) {
        int tabW = MaredEditorLayout.TAB_W();
        int pad = MaredEditorLayout.PAD();
        int sidebarW = MaredEditorLayout.SIDEBAR_W();
        int itemH = MaredEditorLayout.ITEM_H();

        int listX = tabW + pad;
        int listW = sidebarW - pad * 2 - MaredEditorLayout.SCROLLBAR_W();
        int listY = MaredEditorLayout.arrowY() + MaredEditorLayout.BTN_SZ()
                  + MaredEditorLayout.px(8);

        int filesH = Math.min(MaredEditorLayout.FILE_SECTION_H(),
            layout.sidebarBottom(logCollapsed) - listY - pad);
        int filesListY = listY + MaredEditorLayout.px(12);
        int filesListH = (Math.max(0, filesH - MaredEditorLayout.px(12)) / itemH) * itemH;

        int btnSz = MaredEditorLayout.BTN_SZ();
        int ay = MaredEditorLayout.arrowY();
        int arrowX = tabW + sidebarW - btnSz - pad;
        int plusX  = arrowX - btnSz - MaredEditorLayout.px(4);

        int toggleW = MaredEditorLayout.px(TOGGLE_W_FIXED);
        int toggleH = MaredEditorLayout.px(TOGGLE_H_FIXED);

        int commandsY = listY + filesH + MaredEditorLayout.px(4);
        int toggleX = listX + listW - toggleW - MaredEditorLayout.px(4);
        int toggleY = commandsY + 1;

        int filterY = commandsY + MaredEditorLayout.px(14);
        int commandsListY = filterY + MaredEditorLayout.FILTER_H() + 2;
        int commandsH = ((layout.sidebarBottom(logCollapsed) - pad - commandsListY) / itemH) * itemH;

        return new SidebarGeometry(
            tabW, pad, sidebarW,
            listX, listW,
            listY, filesH, filesListY, filesListH, itemH,
            arrowX, plusX,
            toggleX, toggleY, toggleW, toggleH,
            commandsY, filterY, commandsListY, commandsH,
            MaredEditorLayout.SCROLLBAR_W()
        );
    }

    // ============================================================
    //  Public API
    // ============================================================

    public List<String> fileNames() { return fileNames; }
    public String selectedFile() { return selectedFile; }
    public void setSelectedFile(String f) { this.selectedFile = f; }
    public MaredCommandRegistry.CommandInfo selectedCommandInfo() { return selectedCommandInfo; }
    public void setSelectedCommandInfo(MaredCommandRegistry.CommandInfo c) { this.selectedCommandInfo = c; }
    public boolean isShowMaredCommands() { return showMaredCommands; }
    public String commandFilter() { return commandFilter; }
    public void setCommandFilter(String s) { commandFilter = s; }
    public MaredUi.ScrollArea fileScroll() { return fileScroll; }
    public MaredUi.ScrollArea cmdScroll() { return cmdScroll; }
    public List<MaredCommandRegistry.CommandInfo> filteredCommands() { return filteredCommands; }
    public EditBox commandFilterBox() { return commandFilterBox; }
    public void setCommandFilterBox(EditBox box) { this.commandFilterBox = box; }

    public void reloadFiles(String openTab) {
        fileNames.clear();
        if ("scripts".equals(openTab)) fileNames.addAll(MaredScriptStorage.listScripts());
        else if ("commands".equals(openTab)) fileNames.addAll(MaredCommandStorage.listCommands());
        if (selectedFile != null && !fileNames.contains(selectedFile)) selectedFile = null;
        fileScroll.offset = 0;
    }

    public void refreshCommands(String openTab, List<MaredCommandRegistry.CommandInfo> mredCommands) {
        MaredCommandRegistry.load();
        filteredCommands.clear();
        if (showMaredCommands && mredCommands != null) {
            for (MaredCommandRegistry.CommandInfo c : mredCommands) {
                if (commandFilter.isEmpty()
                    || c.name.toLowerCase().startsWith(commandFilter.toLowerCase())) {
                    filteredCommands.add(c);
                }
            }
        } else {
            filteredCommands.addAll(MaredCommandRegistry.search(commandFilter));
        }
        cmdScroll.offset = 0;
    }

    public EditBox ensureFilterBox(Font font, MaredEditorLayout layout,
                                   String openTab,
                                   MaredEditorLayout.SidebarState state,
                                   boolean logCollapsed) {
        if (!"commands".equals(openTab) || state != MaredEditorLayout.SidebarState.FULL) {
            commandFilterBox = null;
            return null;
        }

        SidebarGeometry geo = computeGeometry(layout, logCollapsed, openTab);

        if (commandFilterBox == null) {
            commandFilterBox = new EditBox(font,
                geo.listX() + 4, geo.filterY() + 2,
                geo.listW() - 8, MaredEditorLayout.FILTER_H() - 4,
                Component.literal(MaredLang.get("mared.ui.filter")));
            commandFilterBox.setBordered(false);
            commandFilterBox.setMaxLength(64);
            commandFilterBox.setValue(commandFilter);
            commandFilterBox.setTextColor(0xFFFFFFFF);
            commandFilterBox.setTextColorUneditable(0xFFAAAAAA);
            commandFilterBox.setResponder(s -> {
                commandFilter = s;
                shouldRefreshCommands = true;
            });
        } else {
            commandFilterBox.setX(geo.listX() + 4);
            commandFilterBox.setY(geo.filterY() + 2);
            commandFilterBox.setWidth(geo.listW() - 8);
        }
        return commandFilterBox;
    }

    // ============================================================
    //  Draw: strip
    // ============================================================

    public void drawStrip(GuiGraphics g, Font font, MaredEditorLayout layout,
                          int mouseX, int mouseY, int accentTop, int accentBottom) {
        int tabW = MaredEditorLayout.TAB_W();
        int stripW = MaredEditorLayout.STRIP_W();
        int btnSz = MaredEditorLayout.BTN_SZ();
        int ax = tabW + (stripW - btnSz) / 2;
        int ay = MaredEditorLayout.arrowY();

        boolean hover = MaredUi.hovered(mouseX, mouseY, ax, ay, btnSz, btnSz);
        MaredUi.button3D(g, font, ax, ay, btnSz, btnSz, "►",
            hover ? BTN_HOVER : BTN_BG, accentTop, TEXT, hover);
    }

    // ============================================================
    //  Draw: full
    // ============================================================

    public void drawFull(GuiGraphics g, Font font, MaredEditorLayout layout,
                         String openTab, int mouseX, int mouseY,
                         int accentTop, int accentBottom, int selColor,
                         boolean logCollapsed) {
        SidebarGeometry geo = computeGeometry(layout, logCollapsed, openTab);
        int pad = geo.pad();

        MaredUi.text(g, font, MaredEditorTabs.title(openTab),
            geo.listX() + 2, MaredEditorLayout.arrowY() + (MaredEditorLayout.BTN_SZ() - 8) / 2, TEXT);

        // "+"
        boolean plusHover = MaredUi.hovered(mouseX, mouseY,
            geo.plusX(), MaredEditorLayout.arrowY(),
            MaredEditorLayout.BTN_SZ(), MaredEditorLayout.BTN_SZ());
        MaredUi.button3D(g, font, geo.plusX(), MaredEditorLayout.arrowY(),
            MaredEditorLayout.BTN_SZ(), MaredEditorLayout.BTN_SZ(), "+",
            plusHover ? BTN_HOVER : BTN_BG, accentTop, TEXT, plusHover);

        // "◄"
        boolean arrowHover = MaredUi.hovered(mouseX, mouseY,
            geo.arrowX(), MaredEditorLayout.arrowY(),
            MaredEditorLayout.BTN_SZ(), MaredEditorLayout.BTN_SZ());
        MaredUi.button3D(g, font, geo.arrowX(), MaredEditorLayout.arrowY(),
            MaredEditorLayout.BTN_SZ(), MaredEditorLayout.BTN_SZ(), "◄",
            arrowHover ? BTN_HOVER : BTN_BG, accentBottom, TEXT, arrowHover);

        // Список файлов
        MaredUi.text(g, font, MaredLang.get("mared.ui.files"), geo.listX(), geo.listY(), TEXT_DIM);
        fileScroll.set(geo.listX(), geo.filesListY(), geo.listW(), geo.filesListH())
                  .items(geo.itemH(), fileNames.size());

        if (fileNames.isEmpty()) {
            MaredUi.text(g, font, MaredLang.get("mared.ui.no_files"),
                geo.listX() + MaredEditorLayout.px(4),
                geo.filesListY() + MaredEditorLayout.px(4), TEXT_DIM);
        } else {
            final int scrollbarW = geo.scrollbarW();
            final int delSz = MaredEditorLayout.FILE_DEL_SZ();
            final int stripeW = MaredEditorLayout.PERSISTENT_STRIPE();
            final boolean commandsTab = "commands".equals(openTab);

            MaredUi.listGradient(g, font, fileScroll,
                fileNames.indexOf(selectedFile), scrollbarW,
                accentTop, accentBottom, selColor, ITEM_HOVER, ITEM_NORMAL,
                (gr, f, idx, ix, iy, iw, ih, hov, sel) -> {
                    String fname = fileNames.get(idx);
                    if (commandsTab && MaredPersistentStorage.isPersistent(fname)) {
                        MaredUi.rect(gr, ix, iy, ix + stripeW, iy + ih, PERSISTENT_COLOR);
                    }
                    int textY = iy + (ih - 8) / 2;
                    int textX = ix + pad;

                    // FIX 0.2.6: обрезка длинных имён
                    int maxTextW = iw - pad * 2 - delSz - MaredEditorLayout.px(8);
                    String shown = fname;
                    if (f.width(shown) > maxTextW) {
                        int ellipsisW = f.width("...");
                        shown = f.plainSubstrByWidth(fname, Math.max(0, maxTextW - ellipsisW)) + "...";
                    }
                    MaredUi.text(gr, f, shown, textX, textY, TEXT);

                    int delX = ix + iw - delSz - MaredEditorLayout.px(4);
                    int delY = iy + (ih - delSz) / 2;
                    boolean dHov = MaredUi.hovered(mouseX, mouseY, delX, delY, delSz, delSz);
                    MaredUi.drawDelButton(gr, delX, delY, delSz, dHov);
                }, mouseX, mouseY);
        }

        if ("commands".equals(openTab)) {
            drawCommandsList(g, font, layout, openTab, mouseX, mouseY,
                geo, accentTop, accentBottom, selColor);
        }
    }

    private void drawCommandsList(GuiGraphics g, Font font, MaredEditorLayout layout,
                                  String openTab, int mouseX, int mouseY,
                                  SidebarGeometry geo,
                                  int accentTop, int accentBottom, int selColor) {
        int pad = geo.pad();

        MaredUi.rect(g, geo.tabW() + pad - 2, geo.commandsY() - 2,
            geo.tabW() + geo.sidebarW() - pad + 2, geo.commandsY(), 0xFF2A2A38);

        int maxHeaderW = geo.toggleX() - geo.listX() - 4;
        String header = MaredLang.get("mared.ui.available_commands");
        if (maxHeaderW > 0 && font.width(header) > maxHeaderW) {
            header = font.plainSubstrByWidth(header, Math.max(0, maxHeaderW - 4)) + "...";
        }
        MaredUi.text(g, font, header, geo.listX(), geo.commandsY() + 2, TEXT_DIM);

        String toggleLabel = showMaredCommands ? "MR" : "MC";
        boolean toggleHover = MaredUi.hovered(mouseX, mouseY,
            geo.toggleX(), geo.toggleY(), geo.toggleW(), geo.toggleH());
        int bottom = showMaredCommands ? MRED_COLOR : accentBottom;
        MaredUi.button3D(g, font, geo.toggleX(), geo.toggleY(),
            geo.toggleW(), geo.toggleH(), toggleLabel,
            toggleHover ? BTN_HOVER : BTN_BG, bottom, TEXT, toggleHover);

        MaredUi.rect(g, geo.listX(), geo.filterY(),
            geo.listX() + geo.listW(), geo.filterY() + MaredEditorLayout.FILTER_H(), FILTER_BG);
        MaredUi.outlineGradient(g, geo.listX(), geo.filterY(),
            geo.listW(), MaredEditorLayout.FILTER_H(), accentTop, bottom);

        if (geo.commandsH() < geo.itemH()) return;

        cmdScroll.set(geo.listX(), geo.commandsListY(), geo.listW(), geo.commandsH())
                 .items(geo.itemH(), filteredCommands.size());

        int selCol = selColor;
        if (showMaredCommands) {
            selCol = 0xFF000000 | ((MRED_COLOR >> 16 & 0xFF) / 3 << 16)
                                 | ((MRED_COLOR >> 8  & 0xFF) / 3 << 8)
                                 | ((MRED_COLOR       & 0xFF) / 3);
        }
        MaredUi.listGradient(g, font, cmdScroll,
            filteredCommands.indexOf(selectedCommandInfo), geo.scrollbarW(),
            accentTop, bottom, selCol, ITEM_HOVER, ITEM_NORMAL,
            (gr, f, idx, ix, iy, iw, ih, hov, sel) -> {
                int textY = iy + (ih - 8) / 2;
                String cname = filteredCommands.get(idx).name;
                // FIX 0.2.6: обрезка длинных имён команд
                int maxTextW = iw - pad * 2;
                String shown = cname;
                if (f.width(shown) > maxTextW) {
                    int ellipsisW = f.width("...");
                    shown = f.plainSubstrByWidth(cname, Math.max(0, maxTextW - ellipsisW)) + "...";
                }
                MaredUi.text(gr, f, shown, ix + pad, textY, TEXT);
            },
            mouseX, mouseY);
    }

    // ============================================================
    //  Click
    // ============================================================

    public boolean handleClick(MaredEditorLayout layout, String openTab,
                               double mx, double my,
                               MaredEditorLayout.SidebarState state,
                               MaredUi.DragState drag,
                               Runnable onNew,
                               Consumer<String> onDelete,
                               boolean logCollapsed) {
        int tabW = MaredEditorLayout.TAB_W();
        int btnSz = MaredEditorLayout.BTN_SZ();
        int ay = MaredEditorLayout.arrowY();

        if (state == MaredEditorLayout.SidebarState.STRIP) {
            int stripW = MaredEditorLayout.STRIP_W();
            int ax = tabW + (stripW - btnSz) / 2;
            if (MaredUi.hovered(mx, my, ax, ay, btnSz, btnSz)) {
                shouldToggleSidebar = true;
                shouldRebuildEditor = true;
                return true;
            }
            return false;
        }

        if (state != MaredEditorLayout.SidebarState.FULL) return false;

        SidebarGeometry geo = computeGeometry(layout, logCollapsed, openTab);

        if (MaredUi.hovered(mx, my, geo.arrowX(), ay, btnSz, btnSz)) {
            shouldToggleSidebar = true;
            shouldRebuildEditor = true;
            return true;
        }
        if (MaredUi.hovered(mx, my, geo.plusX(), ay, btnSz, btnSz)) {
            onNew.run();
            return true;
        }

        if (my < geo.listY() - MaredEditorLayout.px(4)) return false;
        if (mx < tabW || mx >= tabW + geo.sidebarW()) return false;

        int itemW = geo.listW() - geo.scrollbarW() - 2;

        fileScroll.set(geo.listX(), geo.filesListY(), geo.listW(), geo.filesListH())
                  .items(geo.itemH(), fileNames.size());

        if (fileScroll.clickScrollbar(mx, my, geo.scrollbarW(), drag,
            MaredUi.DragKind.FILE_SCROLL)) return true;

        int visibleFiles = fileScroll.visibleItems();
        for (int i = 0; i < visibleFiles; i++) {
            int idx = i + fileScroll.offset;
            if (idx >= fileNames.size()) break;
            int itemY = geo.filesListY() + i * geo.itemH();
            int delX = geo.listX() + itemW - MaredEditorLayout.FILE_DEL_SZ() - MaredEditorLayout.px(4);
            int delY = itemY + (geo.itemH() - 2 - MaredEditorLayout.FILE_DEL_SZ()) / 2;
            if (MaredUi.hovered(mx, my, delX, delY,
                MaredEditorLayout.FILE_DEL_SZ(), MaredEditorLayout.FILE_DEL_SZ())) {
                onDelete.accept(fileNames.get(idx));
                return true;
            }
        }
        int fileIdx = fileScroll.hitItem(mx, my, geo.scrollbarW());
        if (fileIdx >= 0) {
            selectedFile = fileNames.get(fileIdx);
            shouldRebuildEditor = true;
            return true;
        }

        if ("commands".equals(openTab)) {
            if (MaredUi.hovered(mx, my, geo.toggleX(), geo.toggleY(),
                geo.toggleW(), geo.toggleH())) {
                showMaredCommands = !showMaredCommands;
                commandFilter = "";
                if (commandFilterBox != null) commandFilterBox.setValue("");
                shouldRefreshCommands = true;
                return true;
            }

            if (commandFilterBox != null && commandFilterBox.isMouseOver(mx, my)) {
                return false;
            }

            cmdScroll.set(geo.listX(), geo.commandsListY(), geo.listW(), geo.commandsH())
                     .items(geo.itemH(), filteredCommands.size());
            if (cmdScroll.clickScrollbar(mx, my, geo.scrollbarW(), drag,
                MaredUi.DragKind.CMD_SCROLL)) return true;

            int cmdIdx = cmdScroll.hitItem(mx, my, geo.scrollbarW());
            if (cmdIdx >= 0) {
                selectedCommandInfo = filteredCommands.get(cmdIdx);
                shouldRebuildEditor = true;
                return true;
            }
        }
        return false;
    }

    // ============================================================
    //  Scroll
    // ============================================================

    public boolean handleScroll(MaredEditorLayout layout, String openTab,
                                double mx, double my, double deltaY,
                                MaredEditorLayout.SidebarState state,
                                boolean logCollapsed) {
        if (state != MaredEditorLayout.SidebarState.FULL) return false;

        SidebarGeometry geo = computeGeometry(layout, logCollapsed, openTab);

        if (MaredUi.hovered(mx, my, geo.listX(), geo.filesListY(),
            geo.listW(), geo.filesListH())) {
            fileScroll.wheel(deltaY, 1);
            return true;
        }
        if ("commands".equals(openTab)) {
            if (MaredUi.hovered(mx, my, geo.listX(), geo.commandsListY(),
                geo.listW(), geo.commandsH())) {
                cmdScroll.wheel(deltaY, 1);
                return true;
            }
        }
        return false;
    }
}