package com.fixmer.mared.gui.editor.sidebar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.registry.MaredCommandRegistry;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.commands.storage.MaredPersistentStorage;
import com.fixmer.mared.commands.storage.MaredScriptStorage;
import com.fixmer.mared.gui.common.MaredUi;
import com.fixmer.mared.gui.editor.MaredEditorLayout;
import com.fixmer.mared.gui.editor.MaredEditorTabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class MaredEditorSidebar {

    private static final int TEXT             = MaredUi.TEXT;
    private static final int TEXT_DIM         = MaredUi.TEXT_DIM;
    private static final int ITEM_HOVER       = 0xFF2E2E3E;
    private static final int ITEM_NORMAL      = 0xFF1A1A22;
    private static final int BTN_BG           = 0xFF2D2D2D;
    private static final int BTN_HOVER        = 0xFF3E3E42;
    private static final int PERSISTENT_COLOR = 0xFF55FF88;
    private static final int MRED_COLOR       = 0xFFFF3333;
    private static final int FILTER_BG        = MaredUi.SUNKEN_BG;

    private static final int TOGGLE_W = 40;
    private static final int TOGGLE_H = 14;

    // ---- Данные ----
    private final List<String> fileNames = new ArrayList<>(16);
    private final List<MaredCommandRegistry.CommandInfo> filteredCommands = new ArrayList<>(64);
    private String selectedFile = null;
    private MaredCommandRegistry.CommandInfo selectedCommandInfo = null;
    private boolean showMaredCommands = false;
    private String commandFilter = "";

    // ---- Скролл ----
    private final MaredUi.ScrollArea fileScroll = new MaredUi.ScrollArea();
    private final MaredUi.ScrollArea cmdScroll  = new MaredUi.ScrollArea();

    private EditBox commandFilterBox;

    // ---- Флаги для экрана ----
    public boolean shouldToggleSidebar = false;
    public boolean shouldRebuildEditor = false;
    public boolean shouldRefreshCommands = false;

    public MaredEditorSidebar() {}

    // ============================================================
    //  Геометрия — обычный immutable-класс
    // ============================================================

    public static final class Geo {
        public final int tabW, pad, sidebarW;
        public final int listX, listW;
        public final int listY, filesH, filesListY, filesListH, itemH;
        public final int arrowX, plusX;
        public final int toggleX, toggleY, toggleW, toggleH;
        public final int commandsY, filterY, commandsListY, commandsH;
        public final int scrollbarW;

        Geo(int tabW, int pad, int sidebarW,
            int listX, int listW,
            int listY, int filesH, int filesListY, int filesListH, int itemH,
            int arrowX, int plusX,
            int toggleX, int toggleY, int toggleW, int toggleH,
            int commandsY, int filterY, int commandsListY, int commandsH,
            int scrollbarW) {
            this.tabW = tabW; this.pad = pad; this.sidebarW = sidebarW;
            this.listX = listX; this.listW = listW;
            this.listY = listY; this.filesH = filesH;
            this.filesListY = filesListY; this.filesListH = filesListH;
            this.itemH = itemH;
            this.arrowX = arrowX; this.plusX = plusX;
            this.toggleX = toggleX; this.toggleY = toggleY;
            this.toggleW = toggleW; this.toggleH = toggleH;
            this.commandsY = commandsY; this.filterY = filterY;
            this.commandsListY = commandsListY; this.commandsH = commandsH;
            this.scrollbarW = scrollbarW;
        }
    }

    public static Geo computeGeometry(MaredEditorLayout layout,
                                       boolean logCollapsed,
                                       String openTab) {
        int tabW = MaredEditorLayout.TAB_W();
        int pad = MaredEditorLayout.PAD();
        int sidebarW = MaredEditorLayout.SIDEBAR_W();
        int itemH = MaredEditorLayout.ITEM_H();

        int listX = tabW + pad;
        int listW = sidebarW - pad * 2 - MaredEditorLayout.SCROLLBAR_W();
        int listY = MaredEditorLayout.arrowY() + MaredEditorLayout.BTN_SZ() + MaredEditorLayout.px(8);

        int filesH = Math.min(MaredEditorLayout.FILE_SECTION_H(),
            layout.sidebarBottom(logCollapsed) - listY - pad);
        int filesListY = listY + MaredEditorLayout.px(12);
        int filesListH = (Math.max(0, filesH - MaredEditorLayout.px(12)) / itemH) * itemH;

        int btnSz = MaredEditorLayout.BTN_SZ();
        int ay = MaredEditorLayout.arrowY();
        int arrowX = tabW + sidebarW - btnSz - pad;
        int plusX  = arrowX - btnSz - MaredEditorLayout.px(4);

        int toggleW = MaredEditorLayout.px(TOGGLE_W);
        int toggleH = MaredEditorLayout.px(TOGGLE_H);

        int commandsY = listY + filesH + MaredEditorLayout.px(4);
        int toggleX = listX + listW - toggleW - MaredEditorLayout.px(4);
        int toggleY = commandsY + 1;

        int filterY = commandsY + MaredEditorLayout.px(14);
        int commandsListY = filterY + MaredEditorLayout.FILTER_H() + 2;
        int commandsH = ((layout.sidebarBottom(logCollapsed) - pad - commandsListY) / itemH) * itemH;

        return new Geo(tabW, pad, sidebarW,
            listX, listW,
            listY, filesH, filesListY, filesListH, itemH,
            arrowX, plusX,
            toggleX, toggleY, toggleW, toggleH,
            commandsY, filterY, commandsListY, commandsH,
            MaredEditorLayout.SCROLLBAR_W());
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
            String q = commandFilter.toLowerCase();
            int n = mredCommands.size();
            for (int i = 0; i < n; i++) {
                MaredCommandRegistry.CommandInfo c = mredCommands.get(i);
                if (q.isEmpty() || c.name.toLowerCase().startsWith(q)) filteredCommands.add(c);
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

        Geo g = computeGeometry(layout, logCollapsed, openTab);

        if (commandFilterBox == null) {
            commandFilterBox = new EditBox(font,
                g.listX + 4, g.filterY + 2,
                g.listW - 8, MaredEditorLayout.FILTER_H() - 4,
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
            commandFilterBox.setX(g.listX + 4);
            commandFilterBox.setY(g.filterY + 2);
            commandFilterBox.setWidth(g.listW - 8);
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
        Geo geo = computeGeometry(layout, logCollapsed, openTab);
        int pad = geo.pad;
        int ay = MaredEditorLayout.arrowY();
        int btnSz = MaredEditorLayout.BTN_SZ();

        MaredUi.text(g, font, MaredEditorTabs.title(openTab),
            geo.listX + 2, ay + (btnSz - 8) / 2, TEXT);

        drawButton(g, font, geo.plusX, ay, btnSz, "+", mouseX, mouseY, accentTop);
        drawButton(g, font, geo.arrowX, ay, btnSz, "◄", mouseX, mouseY, accentBottom);

        drawFilesList(g, font, geo, openTab, mouseX, mouseY, accentTop, accentBottom, selColor);

        if ("commands".equals(openTab)) {
            drawCommandsList(g, font, geo, openTab, mouseX, mouseY,
                accentTop, accentBottom, selColor);
        }
    }

    private void drawButton(GuiGraphics g, Font font, int x, int y, int sz,
                            String label, int mouseX, int mouseY, int accent) {
        boolean hover = MaredUi.hovered(mouseX, mouseY, x, y, sz, sz);
        MaredUi.button3D(g, font, x, y, sz, sz, label,
            hover ? BTN_HOVER : BTN_BG, accent, TEXT, hover);
    }

    private void drawFilesList(GuiGraphics g, Font font, Geo geo, String openTab,
                               int mouseX, int mouseY,
                               int accentTop, int accentBottom, int selColor) {
        MaredUi.text(g, font, MaredLang.get("mared.ui.files"), geo.listX, geo.listY, TEXT_DIM);

        fileScroll.set(geo.listX, geo.filesListY, geo.listW, geo.filesListH)
                  .items(geo.itemH, fileNames.size());

        if (fileNames.isEmpty()) {
            MaredUi.text(g, font, MaredLang.get("mared.ui.no_files"),
                geo.listX + 4, geo.filesListY + 4, TEXT_DIM);
            return;
        }

        final int delSz = MaredEditorLayout.FILE_DEL_SZ();
        final int stripeW = MaredEditorLayout.PERSISTENT_STRIPE();
        final int pad = geo.pad;
        final boolean commandsTab = "commands".equals(openTab);
        int selIdx = fileNames.indexOf(selectedFile);

        MaredUi.listGradient(g, font, fileScroll, selIdx, geo.scrollbarW,
            accentTop, accentBottom, selColor, ITEM_HOVER, ITEM_NORMAL,
            (gr, f, idx, ix, iy, iw, ih, hov, sel) -> {
                String fname = fileNames.get(idx);
                if (commandsTab && MaredPersistentStorage.isPersistent(fname)) {
                    MaredUi.rect(gr, ix, iy, ix + stripeW, iy + ih, PERSISTENT_COLOR);
                }
                int textY = iy + (ih - 8) / 2;
                int maxTextW = iw - pad * 2 - delSz - 8;
                MaredUi.text(gr, f, MaredUi.ellipsize(f, fname, maxTextW),
                    ix + pad, textY, TEXT);

                int delX = ix + iw - delSz - 4;
                int delY = iy + (ih - delSz) / 2;
                boolean dHov = MaredUi.hovered(mouseX, mouseY, delX, delY, delSz, delSz);
                MaredUi.drawDelButton(gr, delX, delY, delSz, dHov);
            }, mouseX, mouseY);
    }

    private void drawCommandsList(GuiGraphics g, Font font, Geo geo, String openTab,
                                   int mouseX, int mouseY,
                                   int accentTop, int accentBottom, int selColor) {
        int pad = geo.pad;

        MaredUi.rect(g, geo.tabW + pad - 2, geo.commandsY - 2,
            geo.tabW + geo.sidebarW - pad + 2, geo.commandsY, 0xFF2A2A38);

        int maxHeaderW = geo.toggleX - geo.listX - 4;
        String header = MaredUi.ellipsize(font,
            MaredLang.get("mared.ui.available_commands"), maxHeaderW);
        MaredUi.text(g, font, header, geo.listX, geo.commandsY + 2, TEXT_DIM);

        String toggleLabel = showMaredCommands ? "MR" : "MC";
        boolean toggleHover = MaredUi.hovered(mouseX, mouseY,
            geo.toggleX, geo.toggleY, geo.toggleW, geo.toggleH);
        int bottom = showMaredCommands ? MRED_COLOR : accentBottom;
        MaredUi.button3D(g, font, geo.toggleX, geo.toggleY,
            geo.toggleW, geo.toggleH, toggleLabel,
            toggleHover ? BTN_HOVER : BTN_BG, bottom, TEXT, toggleHover);

        MaredUi.rect(g, geo.listX, geo.filterY,
            geo.listX + geo.listW, geo.filterY + MaredEditorLayout.FILTER_H(), FILTER_BG);
        MaredUi.outlineGradient(g, geo.listX, geo.filterY,
            geo.listW, MaredEditorLayout.FILTER_H(), accentTop, bottom);

        if (geo.commandsH < geo.itemH) return;

        cmdScroll.set(geo.listX, geo.commandsListY, geo.listW, geo.commandsH)
                 .items(geo.itemH, filteredCommands.size());

        int selCol = showMaredCommands ? selColor(MRED_COLOR) : selColor;
        int selIdx = filteredCommands.indexOf(selectedCommandInfo);

        MaredUi.listGradient(g, font, cmdScroll, selIdx, geo.scrollbarW,
            accentTop, bottom, selCol, ITEM_HOVER, ITEM_NORMAL,
            (gr, f, idx, ix, iy, iw, ih, hov, sel) -> {
                int textY = iy + (ih - 8) / 2;
                String cname = filteredCommands.get(idx).name;
                int maxTextW = iw - pad * 2;
                MaredUi.text(gr, f, MaredUi.ellipsize(f, cname, maxTextW),
                    ix + pad, textY, TEXT);
            }, mouseX, mouseY);
    }

    private static int selColor(int a) {
        return 0xFF000000 | ((a >> 16 & 0xFF) / 3 << 16)
                         | ((a >> 8  & 0xFF) / 3 << 8)
                         | ((a       & 0xFF) / 3);
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

        Geo geo = computeGeometry(layout, logCollapsed, openTab);

        if (MaredUi.hovered(mx, my, geo.arrowX, ay, btnSz, btnSz)) {
            shouldToggleSidebar = true;
            shouldRebuildEditor = true;
            return true;
        }
        if (MaredUi.hovered(mx, my, geo.plusX, ay, btnSz, btnSz)) {
            onNew.run();
            return true;
        }

        if (my < geo.listY - MaredEditorLayout.px(4)) return false;
        if (mx < tabW || mx >= tabW + geo.sidebarW) return false;

        // --- Файлы ---
        if (handleFileClick(mx, my, geo, drag, onDelete)) return true;

        // --- Команды ---
        if ("commands".equals(openTab)) {
            return handleCommandClick(mx, my, geo, drag);
        }
        return false;
    }

    private boolean handleFileClick(double mx, double my, Geo geo,
                                    MaredUi.DragState drag,
                                    Consumer<String> onDelete) {
        int itemW = geo.listW - geo.scrollbarW - 2;

        fileScroll.set(geo.listX, geo.filesListY, geo.listW, geo.filesListH)
                  .items(geo.itemH, fileNames.size());

        if (fileScroll.clickScrollbar(mx, my, geo.scrollbarW, drag,
            MaredUi.DragKind.FILE_SCROLL)) return true;

        int visible = fileScroll.visibleItems();
        int delSz = MaredEditorLayout.FILE_DEL_SZ();
        int x = geo.listX;
        for (int i = 0; i < visible; i++) {
            int idx = i + fileScroll.offset;
            if (idx >= fileNames.size()) break;
            int itemY = geo.filesListY + i * geo.itemH;
            int delX = x + itemW - delSz - 4;
            int delY = itemY + (geo.itemH - 2 - delSz) / 2;
            if (MaredUi.hovered(mx, my, delX, delY, delSz, delSz)) {
                onDelete.accept(fileNames.get(idx));
                return true;
            }
        }
        int fileIdx = fileScroll.hitItem(mx, my, geo.scrollbarW);
        if (fileIdx >= 0) {
            selectedFile = fileNames.get(fileIdx);
            shouldRebuildEditor = true;
            return true;
        }
        return false;
    }

    private boolean handleCommandClick(double mx, double my, Geo geo,
                                       MaredUi.DragState drag) {
        if (MaredUi.hovered(mx, my, geo.toggleX, geo.toggleY, geo.toggleW, geo.toggleH)) {
            showMaredCommands = !showMaredCommands;
            commandFilter = "";
            if (commandFilterBox != null) commandFilterBox.setValue("");
            shouldRefreshCommands = true;
            return true;
        }

        if (commandFilterBox != null && commandFilterBox.isMouseOver(mx, my)) return false;

        cmdScroll.set(geo.listX, geo.commandsListY, geo.listW, geo.commandsH)
                 .items(geo.itemH, filteredCommands.size());
        if (cmdScroll.clickScrollbar(mx, my, geo.scrollbarW, drag,
            MaredUi.DragKind.CMD_SCROLL)) return true;

        int cmdIdx = cmdScroll.hitItem(mx, my, geo.scrollbarW);
        if (cmdIdx >= 0) {
            selectedCommandInfo = filteredCommands.get(cmdIdx);
            shouldRebuildEditor = true;
            return true;
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

        Geo geo = computeGeometry(layout, logCollapsed, openTab);

        if (MaredUi.hovered(mx, my, geo.listX, geo.filesListY, geo.listW, geo.filesListH)) {
            fileScroll.wheel(deltaY, 1);
            return true;
        }
        if ("commands".equals(openTab)
            && MaredUi.hovered(mx, my, geo.listX, geo.commandsListY, geo.listW, geo.commandsH)) {
            cmdScroll.wheel(deltaY, 1);
            return true;
        }
        return false;
    }
}