package com.fixmer.mared.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fixmer.mared.script.MaredScriptContext;
import com.fixmer.mared.script.MaredScriptExecutor;
import com.fixmer.mared.script.MaredScriptParser;
import com.fixmer.mared.script.MaredScriptRunner;
import com.fixmer.mared.script.MaredScriptStorage;
import com.fixmer.mared.script.commands.MaredScriptCommand;
import com.fixmer.mared.storage.MaredCommandStorage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class MaredEditorScreen extends Screen {

    // ---- Palette ----
    private static final int COLOR_BACKGROUND   = 0xFF0E0E14;
    private static final int COLOR_TAB_STRIP    = 0xFF181822;
    private static final int COLOR_TOOLBAR_BG   = 0xFF20202C;
    private static final int COLOR_PANEL        = 0xFF1E1E2A;
    private static final int COLOR_PANEL_STRIP  = 0xFF252530;
    private static final int COLOR_EDITOR_BG    = 0xFF141420;
    private static final int COLOR_LOG_BG       = 0xFF1A1A24;
    private static final int COLOR_INFO_BG      = 0xFF1A1A26;
    private static final int COLOR_TEXT         = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM     = 0xFFAAAAAA;
    private static final int COLOR_TEXT_WARN    = 0xFFFFAA00;
    private static final int COLOR_ITEM_HOVER   = 0xFF2E2E3E;
    private static final int COLOR_ITEM_NORMAL  = 0xFF252530;
    private static final int COLOR_SECTION_BG   = 0xFF2A2A38;
    private static final int COLOR_SCROLLBAR_BG = 0xFF15151E;
    private static final int COLOR_DIVIDER      = 0xFF333344;
    private static final int COLOR_BTN_BG       = 0xFF2D2D2D;
    private static final int COLOR_BTN_HOVER    = 0xFF3E3E42;
    private static final int COLOR_FILTER_BG    = 0xFF0A0A10;

    private static final int COLOR_SCRIPTS  = 0xFFFF55FF;
    private static final int COLOR_COMMANDS = 0xFFFFAA00;
    private static final int COLOR_NPC      = 0xFFFFAA00;
    private static final int COLOR_EVENTS   = 0xFF55AAFF;
    private static final int COLOR_QUESTS   = 0xFF55FF55;
    private static final int COLOR_DANGER   = 0xFFFF4444;

    // ---- Sizes ----
    private static final int TAB_WIDTH       = 30;
    private static final int TAB_HEIGHT      = 30;
    private static final int TOOLBAR_HEIGHT  = 22;
    private static final int PADDING         = 6;
    private static final int LOG_HEIGHT      = 110;
    private static final int LOG_HEIGHT_COLLAPSED = 20;
    private static final int STRIP_WIDTH     = 36;
    private static final int FULL_WIDTH      = 220;
    private static final int INFO_WIDTH      = 280;
    private static final int INFO_COLLAPSED_WIDTH = 24;
    private static final int BTN_SIZE        = 18;
    private static final int ITEM_HEIGHT     = 14;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int LOG_LINE_HEIGHT = 10;
    private static final int LOG_HEADER      = 16;
    private static final int GAP_EDITOR_LOG  = 6;
    private static final int EDITOR_HEADER   = 18;
    private static final int FILE_SECTION_HEIGHT = 140;
    private static final int FILTER_HEIGHT   = 14;

    private enum SidebarState { CLOSED, STRIP, FULL }
    private enum DragTarget { NONE, FILES, COMMANDS, INFO, LOG }

    private SidebarState sidebarState = SidebarState.CLOSED;
    private DragTarget dragTarget = DragTarget.NONE;
    private String openTab = "scripts";

    private final List<String> fileNames = new ArrayList<>();
    private final List<MaredCommandRegistry.CommandInfo> filteredCommands = new ArrayList<>();
    private final List<String> logLines = new ArrayList<>();
    private String selectedFile = null;
    private MaredCommandRegistry.CommandInfo selectedCommandInfo = null;

    private final Set<Integer> expandedArgs = new HashSet<>();

    private String commandFilter = "";
    private String argFilter = "";

    private EditBox commandFilterBox;
    private EditBox argFilterBox;

    private boolean infoPanelCollapsed = false;
    private boolean logCollapsed = false;

    private int fileScrollOffset = 0;
    private int commandScrollOffset = 0;
    private int infoScrollOffset = 0;
    private int logScrollOffset = 0;

    private double dragStartY = 0;
    private int dragStartScroll = 0;

    private String lastSavedText = "";

    private final List<AbstractWidget> toolButtons = new ArrayList<>();

    private MaredMultiLineEditBox editor;

    public MaredEditorScreen() {
        super(Component.literal("Mared Editor"));
    }

    @Override
    protected void init() {
        super.init();
        toolButtons.clear();
        reloadFiles();
        refreshCommands();

        int y = PADDING + 1;
        int rightEdge = this.width - PADDING;

        int closeW = 50;
        rightEdge -= closeW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, closeW, 18, Component.literal("Close"), 0xFFFF5555, this::onClose)));
        rightEdge -= 4;

        int runW = 40;
        rightEdge -= runW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, runW, 18, Component.literal("Run"), 0xFF55FF55, this::onRun)));
        rightEdge -= 4;

        int saveW = 44;
        rightEdge -= saveW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, saveW, 18, Component.literal("Save"), 0xFF55AAFF, this::onSave)));
        rightEdge -= 4;

        int delW = 50;
        rightEdge -= delW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, delW, 18, Component.literal("Delete"), COLOR_DANGER, this::onDelete)));
        rightEdge -= 4;

        int impW = 52;
        rightEdge -= impW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, impW, 18, Component.literal("Import"), 0xFFFFAA00, this::onImport)));
        rightEdge -= 4;

        int newW = 40;
        rightEdge -= newW;
        toolButtons.add(addRenderableWidget(new MaredCompactButton(
            rightEdge, y, newW, 18, Component.literal("New"), 0xFFFF55FF, this::onNew)));

        createEditor();
        createFilterBoxes();
        updateToolButtonsVisibility();
    }

    private void createFilterBoxes() {
        // Command filter box
        if (commandFilterBox != null) { removeWidget(commandFilterBox); commandFilterBox = null; }
        if (isCommands() && sidebarState == SidebarState.FULL) {
            int sx = TAB_WIDTH;
            int listX = sx + 6;
            int listW = FULL_WIDTH - 12 - SCROLLBAR_WIDTH;
            int ay = arrowY();
            int listY = ay + BTN_SIZE + 8;
            int filesH = Math.min(FILE_SECTION_HEIGHT, sidebarBottom() - listY - 6);
            int commandsY = listY + filesH + 4;
            int filterY = commandsY + 14;

            commandFilterBox = new EditBox(this.font, listX + 3, filterY + 1, listW - 6, FILTER_HEIGHT - 2, Component.literal("Filter"));
            commandFilterBox.setValue(commandFilter);
            commandFilterBox.setResponder(s -> {
                commandFilter = s;
                filteredCommands.clear();
                filteredCommands.addAll(MaredCommandRegistry.search(commandFilter));
                commandScrollOffset = 0;
            });
            commandFilterBox.setMaxLength(64);
            commandFilterBox.setBordered(false);
            addRenderableWidget(commandFilterBox);
        }

        // Arg filter box
        if (argFilterBox != null) { removeWidget(argFilterBox); argFilterBox = null; }
        if (!infoPanelCollapsed && isCommands() && selectedCommandInfo != null
            && !selectedCommandInfo.arguments.isEmpty()) {
            int infoX = infoPanelX();
            int y = infoArgFilterBoxY();

            argFilterBox = new EditBox(this.font, infoX + 5, y + 1, INFO_WIDTH - 10, FILTER_HEIGHT - 2, Component.literal("Filter"));
            argFilterBox.setValue(argFilter);
            argFilterBox.setResponder(s -> argFilter = s);
            argFilterBox.setMaxLength(64);
            argFilterBox.setBordered(false);
            addRenderableWidget(argFilterBox);
        }
    }

    private boolean isScripts() { return "scripts".equals(openTab); }
    private boolean isCommands() { return "commands".equals(openTab); }

    private int accentColor() {
        if (isCommands()) return COLOR_COMMANDS;
        if (isScripts()) return COLOR_SCRIPTS;
        return COLOR_TEXT_DIM;
    }

    private int itemSelColor(int accent) {
        int r = ((accent >> 16) & 0xFF) / 3;
        int g = ((accent >> 8) & 0xFF) / 3;
        int b = (accent & 0xFF) / 3;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void refreshCommands() {
        MaredCommandRegistry.load();
        filteredCommands.clear();
        filteredCommands.addAll(MaredCommandRegistry.search(commandFilter));
        commandScrollOffset = 0;
    }

    private void createEditor() {
        int sbW = sidebarWidth();
        int infoW = infoWidth();
        int frameX = TAB_WIDTH + sbW + PADDING;
        int frameY = TOOLBAR_HEIGHT + PADDING * 2;
        int frameW = this.width - frameX - PADDING - infoW;
        int frameH = editorBottom() - frameY - PADDING;

        int inputX = frameX + 2;
        int inputY = frameY + EDITOR_HEADER + 2;
        int inputW = frameW - 4;
        int inputH = frameH - EDITOR_HEADER - 4;

        editor = new MaredMultiLineEditBox(inputX, inputY, inputW, inputH, accentColor(), this::onEditorChanged);
        if (selectedFile != null) {
            if (isScripts()) {
                String val = MaredScriptStorage.readScript(selectedFile);
                editor.setValue(val);
                lastSavedText = val;
            } else if (isCommands()) {
                String val = MaredCommandStorage.readCommand(selectedFile);
                editor.setValue(val);
                lastSavedText = val;
            }
            editor.setEditable(true);
        } else {
            editor.setValue("");
            lastSavedText = "";
            editor.setEditable(false);
        }
        addRenderableWidget(editor);
    }

    private void recreateEditor() {
        if (editor != null) { removeWidget(editor); editor = null; }
        if (isScripts() || isCommands()) createEditor();
        createFilterBoxes();
    }

    private void onEditorChanged() { /* Empty. */ }

    private void updateToolButtonsVisibility() {
        boolean active = isScripts() || isCommands();
        for (int i = 0; i < toolButtons.size(); i++) {
            AbstractWidget w = toolButtons.get(i);
            boolean isClose = i == 0;
            w.visible = isClose || active;
            w.active = isClose || active;
        }
    }

    private void reloadFiles() {
        fileNames.clear();
        if (isScripts()) fileNames.addAll(MaredScriptStorage.listScripts());
        else if (isCommands()) fileNames.addAll(MaredCommandStorage.listCommands());
        if (selectedFile != null && !fileNames.contains(selectedFile)) selectedFile = null;
        fileScrollOffset = 0;
    }

    // ---- Actions ----

    private void onNew() {
        if (isScripts()) {
            Minecraft.getInstance().setScreen(new MaredNameDialog(this, "New script", name -> {
                if (MaredScriptStorage.createScript(name)) {
                    addLog("Created script: " + name);
                    selectedFile = name;
                    reloadFiles();
                    if (editor != null) {
                        String val = MaredScriptStorage.readScript(name);
                        editor.setValue(val); lastSavedText = val; editor.setEditable(true);
                    }
                } else addLog("Failed to create: " + name);
            }, COLOR_SCRIPTS, false));
        } else if (isCommands()) {
            Minecraft.getInstance().setScreen(new MaredNameDialog(this, "New command file", name -> {
                if (MaredCommandStorage.createCommand(name)) {
                    addLog("Created command file: " + name);
                    selectedFile = name;
                    reloadFiles();
                    if (editor != null) {
                        String val = MaredCommandStorage.readCommand(name);
                        editor.setValue(val); lastSavedText = val; editor.setEditable(true);
                    }
                } else addLog("Failed to create: " + name);
            }, COLOR_COMMANDS, true));
        }
    }

    private void onImport() { addLog("Import: not implemented yet"); }

    private void onDelete() {
        if (selectedFile == null) { addLog("No file selected for deletion"); return; }
        String name = selectedFile;
        String type = isScripts() ? "script" : "command file";
        Minecraft.getInstance().setScreen(new MaredConfirmDialog(this,
            "Delete " + type + "?", "\"" + name + "\" will be deleted.",
            () -> {
                boolean ok = isScripts() ? MaredScriptStorage.deleteScript(name) : MaredCommandStorage.deleteCommand(name);
                if (ok) {
                    addLog("Deleted: " + name);
                    selectedFile = null; reloadFiles();
                    if (editor != null) { editor.setValue(""); lastSavedText = ""; editor.setEditable(false); }
                } else addLog("Failed to delete: " + name);
            }));
    }

    private boolean saveCurrentFile() {
        if (selectedFile == null || editor == null) return false;
        String content = editor.getValue();
        if (content.equals(lastSavedText)) return false;
        boolean ok;
        if (isScripts()) ok = MaredScriptStorage.writeScript(selectedFile, content);
        else if (isCommands()) ok = MaredCommandStorage.writeCommand(selectedFile, content);
        else return false;
        if (ok) lastSavedText = content;
        return ok;
    }

    private void onSave() {
        if (selectedFile == null) { addLog("No file selected for saving"); return; }
        if (editor == null) return;
        String content = editor.getValue();
        boolean ok;
        if (isScripts()) ok = MaredScriptStorage.writeScript(selectedFile, content);
        else if (isCommands()) ok = MaredCommandStorage.writeCommand(selectedFile, content);
        else return;
        if (ok) { lastSavedText = content; addLog("Saved: " + selectedFile); }
        else addLog("Failed to save: " + selectedFile);
    }

    private void onRun() {
        if (selectedFile == null) { addLog("No file selected for running"); return; }
        if (editor == null) { addLog("Editor unavailable"); return; }
        if (!editor.getValue().equals(lastSavedText)) {
            if (saveCurrentFile()) addLog("[auto-save] Saved changes before running");
            else addLog("[auto-save] Failed to save");
        }
        if (isScripts()) runScript();
        else if (isCommands()) runCommandFile();
    }

    private void runScript() {
        String text = editor.getValue();
        if (text == null || text.trim().isEmpty()) { addLog("Script is empty"); return; }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) { addLog("Server unavailable (singleplayer only)"); return; }
        ServerPlayer initiator = null;
        if (mc.player != null) initiator = server.getPlayerList().getPlayer(mc.player.getUUID());
        List<MaredScriptCommand> commands;
        try { commands = MaredScriptParser.parse(text); }
        catch (MaredScriptParser.ParseException e) { addLog("[parse error] " + e.getMessage()); return; }
        if (commands.isEmpty()) { addLog("Script contains no commands"); return; }
        MaredScriptContext ctx = new MaredScriptContext(initiator, server, this::addLog);
        MaredScriptExecutor executor = new MaredScriptExecutor(ctx, commands);
        MaredScriptRunner.start(executor);
        addLog("[run script] " + selectedFile + " — " + commands.size() + " commands");
    }

    private void runCommandFile() {
        String text = editor.getValue();
        if (text == null || text.trim().isEmpty()) { addLog("Command file is empty"); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) { addLog("Player unavailable"); return; }
        String[] lines = text.split("\n");
        int count = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            String cmd = line.startsWith("/") ? line.substring(1) : line;
            try { mc.player.connection.sendCommand(cmd); addLog("[cmd] /" + cmd); count++; }
            catch (Exception e) { addLog("[cmd error] /" + cmd + " — " + e.getMessage()); }
        }
        addLog("[run commands] " + selectedFile + " — " + count + " commands sent");
    }

    private void addLog(String line) {
        logLines.add(line);
        if (logLines.size() > 500) logLines.remove(0);
        logScrollOffset = 0;
    }

    // ---- Geometry ----

    private int currentLogHeight() { return logCollapsed ? LOG_HEIGHT_COLLAPSED : LOG_HEIGHT; }
    private int logTop() { return this.height - currentLogHeight(); }
    private int sidebarBottom() { return logTop(); }
    private int editorBottom() { return logTop() - GAP_EDITOR_LOG; }

    private int infoPanelX() {
        return infoPanelCollapsed ? this.width - INFO_COLLAPSED_WIDTH : this.width - INFO_WIDTH;
    }

    private int infoWidth() {
        if (selectedCommandInfo == null || !isCommands()) return 0;
        return infoPanelCollapsed ? INFO_COLLAPSED_WIDTH : INFO_WIDTH;
    }

    /** Y-координата фильтра аргументов (для EditBox). */
    private int infoArgFilterBoxY() {
        if (selectedCommandInfo == null) return 0;
        int infoY = TOOLBAR_HEIGHT + PADDING * 2;
        int y = infoY + 22;
        y += 14 + 12 + 16 + 12;
        y = advanceWrappedHeight(selectedCommandInfo.description, INFO_WIDTH - 16, y);
        y += 6 + 12;
        y = advanceWrappedHeight(selectedCommandInfo.example, INFO_WIDTH - 16, y);
        y += 10;
        y += 12;
        return y;
    }

    /** Y-координата начала скроллируемой части панели информации. */
    private int infoBodyTop() {
        return infoArgFilterBoxY() + FILTER_HEIGHT + 4;
    }

    private int infoScrollTrackY() { return infoBodyTop(); }
    private int infoScrollTrackH() { return (editorBottom() - PADDING) - infoBodyTop(); }

    // ---- Mouse ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // ---- 1. Log toggle — САМЫМ ПЕРВЫМ, чтобы info-панель не перехватывала клик ----
        int logToggleX = this.width - 24;
        int logToggleY = this.height - 18;
        if (mx >= logToggleX && mx < logToggleX + 18 && my >= logToggleY && my < logToggleY + 16) {
            logCollapsed = !logCollapsed;
            recreateEditor();
            return true;
        }

        // ---- 2. Info panel ----
        if (selectedCommandInfo != null && isCommands()) {
            int infoX = infoPanelX();

            int toggleX = infoX + 4;
            int toggleY = TOOLBAR_HEIGHT + PADDING * 2 + 4;
            if (mx >= toggleX && mx < toggleX + 14 && my >= toggleY && my < toggleY + 14) {
                infoPanelCollapsed = !infoPanelCollapsed;
                infoScrollOffset = 0;
                recreateEditor();
                return true;
            }

            if (!infoPanelCollapsed && mx >= infoX) {
                int argBoxY = infoArgFilterBoxY();
                int argBoxBottom = argBoxY + FILTER_HEIGHT;
                boolean overFilterBox = my >= argBoxY - 1 && my < argBoxBottom + 1;

                if (!overFilterBox) {
                    int hitArg = hitTestArgument(mx, my);
                    if (hitArg >= 0) {
                        if (expandedArgs.contains(hitArg)) expandedArgs.remove(hitArg);
                        else expandedArgs.add(hitArg);
                        return true;
                    }
                }

                int trackX = infoX + INFO_WIDTH - SCROLLBAR_WIDTH - 2;
                int trackY = infoScrollTrackY();
                int trackH = infoScrollTrackH();
                if (!overFilterBox && mx >= trackX && mx < trackX + SCROLLBAR_WIDTH
                    && my >= trackY && my < trackY + trackH) {
                    dragTarget = DragTarget.INFO;
                    dragStartY = my;
                    dragStartScroll = infoScrollOffset;
                    return true;
                }

                if (!overFilterBox) return true;
            }
        }

        // ---- 3. Виджеты ----
        if (super.mouseClicked(mx, my, button)) return true;

        // ---- 4. Tabs ----
        if (mx < TAB_WIDTH) {
            int idx = (int) (my / TAB_HEIGHT);
            String[] tabs = {"scripts", "commands", "npc", "events", "quests"};
            if (idx >= 0 && idx < tabs.length) {
                String clicked = tabs[idx];
                boolean supported = "scripts".equals(clicked) || "commands".equals(clicked);
                if (clicked.equals(openTab) && sidebarWidth() > 0) sidebarState = SidebarState.CLOSED;
                else {
                    openTab = clicked;
                    sidebarState = supported ? SidebarState.STRIP : SidebarState.CLOSED;
                    selectedFile = null;
                    selectedCommandInfo = null;
                    expandedArgs.clear();
                }
                updateToolButtonsVisibility();
                recreateEditor();
                reloadFiles();
                refreshCommands();
                return true;
            }
        }

        // ---- 5. STRIP ----
        if ((isScripts() || isCommands()) && sidebarState == SidebarState.STRIP) {
            int ax = TAB_WIDTH + (STRIP_WIDTH - BTN_SIZE) / 2;
            int ay = arrowY();
            if (mx >= ax && mx < ax + BTN_SIZE && my >= ay && my < ay + BTN_SIZE) {
                sidebarState = SidebarState.FULL;
                recreateEditor();
                return true;
            }
        }

        // ---- 6. FULL ----
        if ((isScripts() || isCommands()) && sidebarState == SidebarState.FULL) {
            int sx = TAB_WIDTH;
            int ay = arrowY();
            int arrowX = sx + FULL_WIDTH - BTN_SIZE - 4;
            int plusX = arrowX - BTN_SIZE - 4;

            if (mx >= arrowX && mx < arrowX + BTN_SIZE && my >= ay && my < ay + BTN_SIZE) {
                sidebarState = SidebarState.STRIP;
                recreateEditor();
                return true;
            }
            if (mx >= plusX && mx < plusX + BTN_SIZE && my >= ay && my < ay + BTN_SIZE) {
                onNew();
                return true;
            }

            int listX = sx + 6;
            int listW = FULL_WIDTH - 12 - SCROLLBAR_WIDTH;
            int listY = ay + BTN_SIZE + 8;
            int filesH = Math.min(FILE_SECTION_HEIGHT, sidebarBottom() - listY - 6);
            int filesListY = listY + 12;
            int filesListH = filesH - 12;

            int filesScrollX = sx + FULL_WIDTH - SCROLLBAR_WIDTH - 2;
            if (fileNames.size() > filesListH / ITEM_HEIGHT) {
                int maxVisible = filesListH / ITEM_HEIGHT;
                int maxScroll = Math.max(0, fileNames.size() - maxVisible);
                int thumbH = Math.max(10, filesListH * maxVisible / fileNames.size());
                int thumbY = filesListY + (filesListH - thumbH) * fileScrollOffset / Math.max(1, maxScroll);
                if (mx >= filesScrollX && mx < filesScrollX + SCROLLBAR_WIDTH
                    && my >= filesListY && my < filesListY + filesListH) {
                    if (my >= thumbY && my < thumbY + thumbH) {
                        dragTarget = DragTarget.FILES;
                        dragStartY = my;
                        dragStartScroll = fileScrollOffset;
                    } else {
                        int rel = (int) ((my - filesListY) / Math.max(1, filesListH) * maxScroll);
                        fileScrollOffset = Math.max(0, Math.min(maxScroll, rel));
                    }
                    return true;
                }
            }

            if (mx >= listX && mx < listX + listW && my >= filesListY && my < filesListY + filesListH) {
                int idx = ((int) my - filesListY) / ITEM_HEIGHT + fileScrollOffset;
                if (idx >= 0 && idx < fileNames.size()) {
                    selectedFile = fileNames.get(idx);
                    addLog("Selected: " + selectedFile);
                    if (editor != null) {
                        String val = isScripts() ? MaredScriptStorage.readScript(selectedFile) : MaredCommandStorage.readCommand(selectedFile);
                        editor.setValue(val);
                        lastSavedText = val;
                        editor.setEditable(true);
                    }
                }
                return true;
            }

            if (isCommands()) {
                int commandsY = listY + filesH + 4;
                int filterY = commandsY + 14;
                int commandsListY = filterY + 16;
                int commandsH = sidebarBottom() - 6 - commandsListY;

                int cmdScrollX = sx + FULL_WIDTH - SCROLLBAR_WIDTH - 2;
                if (filteredCommands.size() > commandsH / ITEM_HEIGHT) {
                    int maxVisible = commandsH / ITEM_HEIGHT;
                    int maxScroll = Math.max(0, filteredCommands.size() - maxVisible);
                    int thumbH = Math.max(10, commandsH * maxVisible / filteredCommands.size());
                    int thumbY = commandsListY + (commandsH - thumbH) * commandScrollOffset / Math.max(1, maxScroll);
                    if (mx >= cmdScrollX && mx < cmdScrollX + SCROLLBAR_WIDTH
                        && my >= commandsListY && my < commandsListY + commandsH) {
                        if (my >= thumbY && my < thumbY + thumbH) {
                            dragTarget = DragTarget.COMMANDS;
                            dragStartY = my;
                            dragStartScroll = commandScrollOffset;
                        } else {
                            int rel = (int) ((my - commandsListY) / Math.max(1, commandsH) * maxScroll);
                            commandScrollOffset = Math.max(0, Math.min(maxScroll, rel));
                        }
                        return true;
                    }
                }

                if (mx >= listX && mx < listX + listW && my >= commandsListY && my < commandsListY + commandsH) {
                    int idx = ((int) my - commandsListY) / ITEM_HEIGHT + commandScrollOffset;
                    if (idx >= 0 && idx < filteredCommands.size()) {
                        selectedCommandInfo = filteredCommands.get(idx);
                        infoScrollOffset = 0;
                        infoPanelCollapsed = false;
                        expandedArgs.clear();
                        argFilter = "";
                        addLog("[info] " + selectedCommandInfo.name);
                        recreateEditor();
                    }
                    return true;
                }
            }
        }

        // ---- 7. Log scroll ----
        if (my >= logTop() && !logCollapsed) {
            if (logLines.size() > (LOG_HEIGHT - LOG_HEADER - PADDING) / LOG_LINE_HEIGHT) {
                int visibleLines = (LOG_HEIGHT - LOG_HEADER - PADDING) / LOG_LINE_HEIGHT;
                int trackX = this.width - SCROLLBAR_WIDTH - 24;
                int trackY = logTop() + LOG_HEADER;
                int trackH = LOG_HEIGHT - LOG_HEADER - PADDING;
                int maxScroll = Math.max(0, logLines.size() - visibleLines);
                int thumbH = Math.max(8, trackH * visibleLines / logLines.size());
                int thumbY = trackY + (trackH - thumbH) * (maxScroll - logScrollOffset) / Math.max(1, maxScroll);
                if (mx >= trackX && mx < trackX + SCROLLBAR_WIDTH && my >= trackY && my < trackY + trackH) {
                    if (my >= thumbY && my < thumbY + thumbH) {
                        dragTarget = DragTarget.LOG;
                        dragStartY = my;
                        dragStartScroll = logScrollOffset;
                    } else {
                        int rel = (int) ((my - trackY) / Math.max(1, trackH) * maxScroll);
                        logScrollOffset = Math.max(0, Math.min(maxScroll, maxScroll - rel));
                    }
                    return true;
                }
            }
            return true;
        }

        return false;
    }

    private int hitTestArgument(double mx, double my) {
        if (selectedCommandInfo == null || selectedCommandInfo.arguments.isEmpty()) return -1;
        int infoX = infoPanelX();
        int x = infoX + 8;
        int bodyTop = infoBodyTop();
        int contentY = bodyTop - infoScrollOffset;

        for (int i = 0; i < selectedCommandInfo.arguments.size(); i++) {
            MaredCommandRegistry.Argument arg = selectedCommandInfo.arguments.get(i);
            if (!argFilter.isEmpty()) {
                String q = argFilter.toLowerCase();
                if (!arg.value.toLowerCase().contains(q) && !arg.description.toLowerCase().contains(q)) continue;
            }
            int rowY = contentY;
            int rowH = 10;
            if (my >= rowY && my < rowY + rowH && mx >= x && mx < x + INFO_WIDTH - 16) return i;
            contentY += 10;
            contentY = advanceWrappedHeight(arg.description, INFO_WIDTH - 16 - 10, contentY);
            contentY += 4;
            if (expandedArgs.contains(i)) {
                contentY += 12;
                for (String ex : arg.examples) {
                    contentY = advanceWrappedHeight("• " + ex, INFO_WIDTH - 16 - 10, contentY);
                    contentY += 2;
                }
                contentY += 4;
            }
        }
        return -1;
    }

    private int advanceWrappedHeight(String text, int maxWidth, int y) {
        if (text == null || text.isEmpty()) return y;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(test) > maxWidth) { y += 10; line = new StringBuilder(word); }
            else line = new StringBuilder(test);
        }
        if (line.length() > 0) y += 10;
        return y;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragTarget == DragTarget.NONE) return super.mouseDragged(mx, my, button, dx, dy);
        double delta = my - dragStartY;

        if (dragTarget == DragTarget.FILES) {
            int filesH = Math.min(FILE_SECTION_HEIGHT, sidebarBottom() - arrowY() - BTN_SIZE - 8 - 6) - 12;
            int maxVisible = Math.max(1, filesH / ITEM_HEIGHT);
            int maxScroll = Math.max(0, fileNames.size() - maxVisible);
            double ratio = delta / Math.max(1, filesH);
            int newOffset = (int) Math.round(dragStartScroll + ratio * maxScroll);
            fileScrollOffset = Math.max(0, Math.min(maxScroll, newOffset));
            return true;
        }
        if (dragTarget == DragTarget.COMMANDS) {
            int listY = arrowY() + BTN_SIZE + 8;
            int filesH = Math.min(FILE_SECTION_HEIGHT, sidebarBottom() - listY - 6);
            int commandsY = listY + filesH + 4;
            int commandsListY = commandsY + 30;
            int commandsH = sidebarBottom() - 6 - commandsListY;
            int maxVisible = Math.max(1, commandsH / ITEM_HEIGHT);
            int maxScroll = Math.max(0, filteredCommands.size() - maxVisible);
            double ratio = delta / Math.max(1, commandsH);
            int newOffset = (int) Math.round(dragStartScroll + ratio * maxScroll);
            commandScrollOffset = Math.max(0, Math.min(maxScroll, newOffset));
            return true;
        }
        if (dragTarget == DragTarget.INFO) {
            int trackH = infoScrollTrackH();
            double ratio = delta / Math.max(1, trackH);
            int maxScroll = Math.max(0, infoContentHeight() - trackH);
            int newOffset = (int) Math.round(dragStartScroll + ratio * maxScroll);
            infoScrollOffset = Math.max(0, Math.min(maxScroll, newOffset));
            return true;
        }
        if (dragTarget == DragTarget.LOG) {
            int trackH = LOG_HEIGHT - LOG_HEADER - PADDING;
            int visibleLines = Math.max(1, trackH / LOG_LINE_HEIGHT);
            int maxScroll = Math.max(0, logLines.size() - visibleLines);
            double ratio = delta / Math.max(1, trackH);
            int newOffset = (int) Math.round(dragStartScroll - ratio * maxScroll);
            logScrollOffset = Math.max(0, Math.min(maxScroll, newOffset));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragTarget = DragTarget.NONE;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        if (selectedCommandInfo != null && isCommands() && !infoPanelCollapsed) {
            int infoX = infoPanelX();
            if (mx >= infoX) {
                int trackH = infoScrollTrackH();
                int maxScroll = Math.max(0, infoContentHeight() - trackH);
                if (deltaY < 0) infoScrollOffset = Math.min(maxScroll, infoScrollOffset + 5);
                else if (deltaY > 0) infoScrollOffset = Math.max(0, infoScrollOffset - 5);
                return true;
            }
        }
        if (my >= logTop() && !logCollapsed) {
            int visibleLines = Math.max(1, (LOG_HEIGHT - LOG_HEADER - PADDING) / LOG_LINE_HEIGHT);
            int maxScroll = Math.max(0, logLines.size() - visibleLines);
            if (deltaY < 0) logScrollOffset = Math.max(0, logScrollOffset - 1);
            else if (deltaY > 0) logScrollOffset = Math.min(maxScroll, logScrollOffset + 1);
            return true;
        }
        if ((isScripts() || isCommands()) && sidebarState == SidebarState.FULL) {
            int sx = TAB_WIDTH;
            int listX = sx + 6;
            int listW = FULL_WIDTH - 12 - SCROLLBAR_WIDTH;
            int listY = arrowY() + BTN_SIZE + 8;
            int filesH = Math.min(FILE_SECTION_HEIGHT, sidebarBottom() - listY - 6);
            int filesListY = listY + 12;
            int filesListH = filesH - 12;
            if (mx >= listX && mx < listX + listW && my >= filesListY && my < filesListY + filesListH) {
                int maxVisible = filesListH / ITEM_HEIGHT;
                int maxScroll = Math.max(0, fileNames.size() - maxVisible);
                if (deltaY < 0) fileScrollOffset = Math.min(maxScroll, fileScrollOffset + 1);
                else if (deltaY > 0) fileScrollOffset = Math.max(0, fileScrollOffset - 1);
                return true;
            }
            if (isCommands()) {
                int commandsY = listY + filesH + 4;
                int commandsListY = commandsY + 30;
                int commandsH = sidebarBottom() - 6 - commandsListY;
                if (mx >= listX && mx < listX + listW && my >= commandsListY && my < commandsListY + commandsH) {
                    int maxVisible = commandsH / ITEM_HEIGHT;
                    int maxScroll = Math.max(0, filteredCommands.size() - maxVisible);
                    if (deltaY < 0) commandScrollOffset = Math.min(maxScroll, commandScrollOffset + 1);
                    else if (deltaY > 0) commandScrollOffset = Math.max(0, commandScrollOffset - 1);
                    return true;
                }
            }
        }
        if (editor != null && editor.isMouseOver(mx, my)) {
            if (editor.mouseScrolled(mx, my, deltaX, deltaY)) return true;
        }
        return super.mouseScrolled(mx, my, deltaX, deltaY);
    }

    private int infoContentHeight() {
        if (selectedCommandInfo == null) return 0;
        int h = 0;
        for (int i = 0; i < selectedCommandInfo.arguments.size(); i++) {
            MaredCommandRegistry.Argument arg = selectedCommandInfo.arguments.get(i);
            if (!argFilter.isEmpty()) {
                String q = argFilter.toLowerCase();
                if (!arg.value.toLowerCase().contains(q) && !arg.description.toLowerCase().contains(q)) continue;
            }
            h += 10;
            h += wrappedHeight(arg.description, INFO_WIDTH - 16 - 10);
            h += 4;
            if (expandedArgs.contains(i)) {
                h += 12;
                for (String ex : arg.examples) { h += wrappedHeight("• " + ex, INFO_WIDTH - 16 - 10); h += 2; }
                h += 4;
            }
        }
        for (MaredCommandRegistry.NbtHint hint : selectedCommandInfo.nbtHints) {
            h += 10 + 10;
            h += wrappedHeight("Why: " + hint.why, INFO_WIDTH - 16 - 10);
            if (hint.example != null && !hint.example.isEmpty())
                h += wrappedHeight("Example: " + hint.example, INFO_WIDTH - 16 - 10);
            h += 6;
        }
        h += PADDING * 2;
        return h;
    }

    private int wrappedHeight(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return 10;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : words) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(test) > maxWidth) { lines++; line = new StringBuilder(word); }
            else line = new StringBuilder(test);
        }
        if (line.length() > 0) lines++;
        return Math.max(1, lines) * 10;
    }

    // ---- Render ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, COLOR_BACKGROUND);
        int sbW = sidebarWidth();
        int logTop = logTop();
        int accent = accentColor();

        drawLog(graphics, mouseX, mouseY, logTop);

        if (sbW > 0) {
            int bgColor = (sidebarState == SidebarState.STRIP) ? COLOR_PANEL_STRIP : COLOR_PANEL;
            graphics.fill(TAB_WIDTH, 0, TAB_WIDTH + sbW, sidebarBottom(), bgColor);
        }

        int toolbarX = TAB_WIDTH + sbW;
        graphics.fill(toolbarX, 0, this.width, TOOLBAR_HEIGHT + PADDING, COLOR_TOOLBAR_BG);

        graphics.fill(0, 0, TAB_WIDTH, this.height, COLOR_TAB_STRIP);
        drawTabs(graphics, mouseX, mouseY);

        if ((isScripts() || isCommands()) && sidebarState == SidebarState.STRIP)
            drawArrow(graphics, TAB_WIDTH + (STRIP_WIDTH - BTN_SIZE) / 2, arrowY(), "►", mouseX, mouseY, accent);
        if ((isScripts() || isCommands()) && sidebarState == SidebarState.FULL)
            drawFullSidebar(graphics, mouseX, mouseY, accent);

        if (isScripts() || isCommands()) {
            int infoW = infoWidth();
            int frameX = TAB_WIDTH + sbW + PADDING;
            int frameY = TOOLBAR_HEIGHT + PADDING * 2;
            int frameW = this.width - frameX - PADDING - infoW;
            int frameH = editorBottom() - frameY - PADDING;
            graphics.fill(frameX, frameY, frameX + frameW, frameY + frameH, COLOR_EDITOR_BG);
            graphics.renderOutline(frameX, frameY, frameW, frameH, accent);
            String type = isScripts() ? "Script" : "Commands";
            String title = (selectedFile == null) ? "Editor (no " + type.toLowerCase() + " selected)" : "Editor: " + selectedFile;
            graphics.drawString(this.font, title, frameX + 6, frameY + 5, COLOR_TEXT, true);
            drawDashedLine(graphics, frameX + 2, frameY + EDITOR_HEADER, frameX + frameW - 2, accent);
        } else {
            int frameX = TAB_WIDTH + sbW + PADDING;
            int frameY = TOOLBAR_HEIGHT + PADDING * 2;
            int frameW = this.width - frameX - PADDING;
            int frameH = editorBottom() - frameY - PADDING;
            graphics.fill(frameX, frameY, frameX + frameW, frameY + frameH, COLOR_EDITOR_BG);
            graphics.renderOutline(frameX, frameY, frameW, frameH, COLOR_TEXT_DIM);
            graphics.drawString(this.font, "Section \"" + tabTitle(openTab) + "\" is under development", frameX + 8, frameY + 8, COLOR_TEXT_DIM, true);
        }

        if (selectedCommandInfo != null && isCommands()) drawInfoPanel(graphics, mouseX, mouseY);

        int dividerY = logTop - 1;
        graphics.fill(0, dividerY, this.width, dividerY + 1, COLOR_EVENTS);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawInfoPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int infoX = infoPanelX();
        int infoY = TOOLBAR_HEIGHT + PADDING * 2;
        int infoH = editorBottom() - infoY - PADDING;

        graphics.fill(infoX, infoY, this.width, infoY + infoH, COLOR_INFO_BG);
        graphics.renderOutline(infoX, infoY, infoWidth(), infoH, COLOR_COMMANDS);

        int toggleX = infoX + 4;
        int toggleY = infoY + 4;
        boolean hover = mouseX >= toggleX && mouseX < toggleX + 14 && mouseY >= toggleY && mouseY < toggleY + 14;
        graphics.fill(toggleX, toggleY, toggleX + 14, toggleY + 14, hover ? COLOR_BTN_HOVER : COLOR_BTN_BG);
        graphics.renderOutline(toggleX, toggleY, 14, 14, COLOR_COMMANDS);
        String symbol = infoPanelCollapsed ? "◄" : "►";
        graphics.drawString(this.font, symbol, toggleX + 4, toggleY + 3, COLOR_COMMANDS, true);

        if (infoPanelCollapsed) {
            graphics.drawString(this.font, "INFO", infoX + 7, infoY + 24, COLOR_COMMANDS, true);
            return;
        }

        int x = infoX + 8;
        int maxW = INFO_WIDTH - 16;
        MaredCommandRegistry.CommandInfo info = selectedCommandInfo;

        int fixedY = infoY + 22;
        graphics.drawString(this.font, info.name, x, fixedY, COLOR_COMMANDS, true);
        fixedY += 14;
        graphics.drawString(this.font, "Category: " + info.category, x, fixedY, COLOR_TEXT_DIM, true);
        fixedY += 12;
        graphics.drawString(this.font, "OP level: " + info.opLevel, x, fixedY, COLOR_TEXT_DIM, true);
        fixedY += 16;
        graphics.drawString(this.font, "Description:", x, fixedY, COLOR_TEXT, true);
        fixedY += 12;
        fixedY = drawWrapped(graphics, info.description, x, fixedY, maxW, COLOR_TEXT_DIM);
        fixedY += 6;
        graphics.drawString(this.font, "Example:", x, fixedY, COLOR_TEXT, true);
        fixedY += 12;
        fixedY = drawWrapped(graphics, info.example, x, fixedY, maxW, COLOR_TEXT);
        fixedY += 10;
        graphics.drawString(this.font, "Filter arguments:", x, fixedY, COLOR_TEXT_WARN, true);
        fixedY += 12;

        // Фон под EditBox — только фон, рамку рисует сам EditBox (без bordered).
        graphics.fill(infoX + 3, fixedY, infoX + INFO_WIDTH - 3, fixedY + FILTER_HEIGHT, COLOR_FILTER_BG);
        graphics.renderOutline(infoX + 3, fixedY, INFO_WIDTH - 6, FILTER_HEIGHT, COLOR_COMMANDS);

        fixedY += FILTER_HEIGHT + 4;

        graphics.fill(infoX + 2, fixedY, infoX + INFO_WIDTH - 2, fixedY + 1, COLOR_DIVIDER);
        fixedY += 3;

        graphics.enableScissor(infoX + 1, fixedY, infoX + INFO_WIDTH - 1, infoY + infoH - 1);
        int contentY = fixedY - infoScrollOffset;

        if (!info.arguments.isEmpty()) {
            for (int i = 0; i < info.arguments.size(); i++) {
                MaredCommandRegistry.Argument arg = info.arguments.get(i);
                if (!argFilter.isEmpty()) {
                    String q = argFilter.toLowerCase();
                    if (!arg.value.toLowerCase().contains(q) && !arg.description.toLowerCase().contains(q)) continue;
                }
                boolean expanded = expandedArgs.contains(i);
                String marker = expanded ? "▾ " : "▸ ";
                graphics.drawString(this.font, marker + arg.value, x + 2, contentY, COLOR_TEXT, true);
                contentY += 10;
                contentY = drawWrapped(graphics, arg.description, x + 10, contentY, maxW - 10, COLOR_TEXT_DIM);
                if (expanded) {
                    contentY += 6;
                    graphics.drawString(this.font, "Examples:", x + 10, contentY, COLOR_TEXT_WARN, true);
                    contentY += 12;
                    for (String ex : arg.examples) {
                        contentY = drawWrapped(graphics, "• " + ex, x + 14, contentY, maxW - 14, COLOR_COMMANDS);
                        contentY += 2;
                    }
                    contentY += 4;
                }
                contentY += 4;
            }
            contentY += 4;
        }

        if (!info.nbtHints.isEmpty()) {
            graphics.drawString(this.font, "NBT / Components:", x, contentY, COLOR_TEXT_WARN, true);
            contentY += 14;
            for (MaredCommandRegistry.NbtHint hint : info.nbtHints) {
                graphics.drawString(this.font, hint.tag, x + 4, contentY, COLOR_COMMANDS, true);
                contentY += 10;
                graphics.drawString(this.font, "What: " + hint.what, x + 10, contentY, COLOR_TEXT_DIM, true);
                contentY += 10;
                contentY = drawWrapped(graphics, "Why: " + hint.why, x + 10, contentY, maxW - 10, COLOR_TEXT_DIM);
                if (hint.example != null && !hint.example.isEmpty()) {
                    contentY = drawWrapped(graphics, "Example: " + hint.example, x + 10, contentY, maxW - 10, COLOR_COMMANDS);
                }
                contentY += 6;
            }
        }

        graphics.disableScissor();

        int trackY = fixedY;
        int trackH = (infoY + infoH - 4) - trackY;
        int maxScroll = Math.max(0, infoContentHeight() - trackH);
        if (infoScrollOffset > maxScroll) infoScrollOffset = maxScroll;

        if (maxScroll > 0) {
            int trackX = infoX + INFO_WIDTH - SCROLLBAR_WIDTH - 2;
            graphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, COLOR_SCROLLBAR_BG);
            int thumbH = Math.max(10, trackH * trackH / Math.max(1, infoContentHeight()));
            int thumbY = trackY + (trackH - thumbH) * infoScrollOffset / Math.max(1, maxScroll);
            graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, COLOR_COMMANDS);
        }
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(test) > maxWidth) {
                if (line.length() > 0) { graphics.drawString(this.font, line.toString(), x, y, color, true); y += 10; line = new StringBuilder(word); }
                else { graphics.drawString(this.font, word, x, y, color, true); y += 10; line = new StringBuilder(); }
            } else line = new StringBuilder(test);
        }
        if (line.length() > 0) { graphics.drawString(this.font, line.toString(), x, y, color, true); y += 10; }
        return y;
    }

    private void drawDashedLine(GuiGraphics graphics, int x1, int y, int x2, int color) {
        int dash = 4, gap = 3;
        int x = x1;
        while (x < x2) { int end = Math.min(x + dash, x2); graphics.fill(x, y, end, y + 1, color); x += dash + gap; }
    }

    private void drawLog(GuiGraphics graphics, int mouseX, int mouseY, int logTop) {
        graphics.fill(0, logTop, this.width, this.height, COLOR_LOG_BG);
        graphics.drawString(this.font, "Log:", PADDING, logTop + 4, COLOR_EVENTS, true);

        if (!logCollapsed) {
            int visibleLines = Math.max(1, (LOG_HEIGHT - LOG_HEADER - PADDING) / LOG_LINE_HEIGHT);
            int maxScroll = Math.max(0, logLines.size() - visibleLines);
            if (logScrollOffset > maxScroll) logScrollOffset = maxScroll;
            int end = logLines.size() - logScrollOffset;
            int start = Math.max(0, end - visibleLines);
            for (int i = start; i < end && i < logLines.size(); i++)
                graphics.drawString(this.font, logLines.get(i), PADDING + 4, logTop + LOG_HEADER + (i - start) * LOG_LINE_HEIGHT, COLOR_TEXT, true);
            if (logLines.size() > visibleLines) {
                int trackX = this.width - SCROLLBAR_WIDTH - 24;
                int trackY = logTop + LOG_HEADER;
                int trackH = LOG_HEIGHT - LOG_HEADER - PADDING;
                graphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackH, COLOR_SCROLLBAR_BG);
                int thumbH = Math.max(8, trackH * visibleLines / logLines.size());
                int thumbY = trackY + (trackH - thumbH) * (maxScroll - logScrollOffset) / Math.max(1, maxScroll);
                graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, COLOR_EVENTS);
            }
        }

        int toggleX = this.width - 24;
        int toggleY = this.height - 18;
        boolean hover = mouseX >= toggleX && mouseX < toggleX + 18 && mouseY >= toggleY && mouseY < toggleY + 16;
        graphics.fill(toggleX, toggleY, toggleX + 18, toggleY + 16, hover ? COLOR_BTN_HOVER : COLOR_BTN_BG);
        graphics.renderOutline(toggleX, toggleY, 18, 16, COLOR_EVENTS);
        String arrow = logCollapsed ? "▲" : "▼";
        graphics.drawString(this.font, arrow, toggleX + 5, toggleY + 4, COLOR_EVENTS, true);
    }

    private int arrowY() { return PADDING + 1; }

    private int sidebarWidth() {
        if (!isScripts() && !isCommands()) return 0;
        switch (sidebarState) { case STRIP: return STRIP_WIDTH; case FULL: return FULL_WIDTH; default: return 0; }
    }

    private void drawArrow(GuiGraphics graphics, int ax, int ay, String symbol, int mouseX, int mouseY, int accent) {
        boolean hover = mouseX >= ax && mouseX < ax + BTN_SIZE && mouseY >= ay && mouseY < ay + BTN_SIZE;
        graphics.fill(ax, ay, ax + BTN_SIZE, ay + BTN_SIZE, hover ? COLOR_BTN_HOVER : COLOR_BTN_BG);
        graphics.renderOutline(ax, ay, BTN_SIZE, BTN_SIZE, accent);
        graphics.drawString(this.font, symbol, ax + 5, ay + 5, accent, true);
    }

    private void drawFullSidebar(GuiGraphics graphics, int mouseX, int mouseY, int accent) {
        int sx = TAB_WIDTH, sw = FULL_WIDTH, ay = arrowY();
        graphics.drawString(this.font, tabTitle(openTab), sx + 8, ay + 5, COLOR_TEXT, true);
        int arrowX = sx + sw - BTN_SIZE - 4;
        int plusX = arrowX - BTN_SIZE - 4;
        boolean plusHover = mouseX >= plusX && mouseX < plusX + BTN_SIZE && mouseY >= ay && mouseY < ay + BTN_SIZE;
        graphics.fill(plusX, ay, plusX + BTN_SIZE, ay + BTN_SIZE, plusHover ? COLOR_BTN_HOVER : COLOR_BTN_BG);
        graphics.renderOutline(plusX, ay, BTN_SIZE, BTN_SIZE, accent);
        graphics.drawString(this.font, "+", plusX + 6, ay + 5, accent, true);
        boolean arrowHover = mouseX >= arrowX && mouseX < arrowX + BTN_SIZE && mouseY >= ay && mouseY < ay + BTN_SIZE;
        graphics.fill(arrowX, ay, arrowX + BTN_SIZE, ay + BTN_SIZE, arrowHover ? COLOR_BTN_HOVER : COLOR_BTN_BG);
        graphics.renderOutline(arrowX, ay, BTN_SIZE, BTN_SIZE, accent);
        graphics.drawString(this.font, "◄", arrowX + 5, ay + 5, accent, true);

        int listX = sx + 6, listW = sw - 12 - SCROLLBAR_WIDTH;
        int listY = ay + BTN_SIZE + 8;
        int filesH = Math.min(FILE_SECTION_HEIGHT, sidebarBottom() - listY - 6);
        graphics.drawString(this.font, "Files", listX, listY, COLOR_TEXT_DIM, true);
        int filesListY = listY + 12;
        int filesListH = filesH - 12;

        if (fileNames.isEmpty()) {
            graphics.drawString(this.font, "No files. Click +", listX + 4, filesListY + 4, COLOR_TEXT_DIM, true);
        } else {
            int maxVisible = filesListH / ITEM_HEIGHT;
            int maxScroll = Math.max(0, fileNames.size() - maxVisible);
            if (fileScrollOffset > maxScroll) fileScrollOffset = maxScroll;
            for (int i = 0; i < maxVisible; i++) {
                int realIdx = i + fileScrollOffset;
                if (realIdx >= fileNames.size()) break;
                String name = fileNames.get(realIdx);
                int itemY = filesListY + i * ITEM_HEIGHT;
                boolean hovered = mouseX >= listX && mouseX < listX + listW && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT - 2;
                boolean selected = name.equals(selectedFile);
                if (selected) {
                    graphics.fill(listX, itemY, listX + listW, itemY + ITEM_HEIGHT - 2, itemSelColor(accent));
                    graphics.fill(listX, itemY, listX + 2, itemY + ITEM_HEIGHT - 2, accent);
                    graphics.drawString(this.font, name, listX + 4, itemY + 2, COLOR_TEXT, true);
                } else {
                    int bg = hovered ? COLOR_ITEM_HOVER : COLOR_ITEM_NORMAL;
                    graphics.fill(listX, itemY, listX + listW, itemY + ITEM_HEIGHT - 2, bg);
                    graphics.drawString(this.font, name, listX + 4, itemY + 2, COLOR_TEXT, true);
                }
            }
            if (fileNames.size() > maxVisible) {
                int trackX = sx + sw - SCROLLBAR_WIDTH - 2;
                graphics.fill(trackX, filesListY, trackX + SCROLLBAR_WIDTH, filesListY + filesListH, COLOR_SCROLLBAR_BG);
                int thumbH = Math.max(10, filesListH * maxVisible / fileNames.size());
                int thumbY = filesListY + (filesListH - thumbH) * fileScrollOffset / Math.max(1, maxScroll);
                graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, accent);
            }
        }

        if (isCommands()) {
            int commandsY = listY + filesH + 4;
            graphics.fill(sx + 4, commandsY - 2, sx + sw - 4, commandsY, COLOR_SECTION_BG);
            graphics.drawString(this.font, "Available Commands", listX, commandsY + 2, COLOR_TEXT_DIM, true);
            int filterY = commandsY + 14;

            graphics.fill(listX, filterY, listX + listW, filterY + FILTER_HEIGHT, COLOR_FILTER_BG);
            graphics.renderOutline(listX, filterY, listW, FILTER_HEIGHT, accent);

            int commandsListY = filterY + 16;
            int commandsH = sidebarBottom() - 6 - commandsListY;
            if (commandsH < 20) return;
            int maxVisible = commandsH / ITEM_HEIGHT;
            int maxScroll = Math.max(0, filteredCommands.size() - maxVisible);
            if (commandScrollOffset > maxScroll) commandScrollOffset = maxScroll;
            for (int i = 0; i < maxVisible; i++) {
                int realIdx = i + commandScrollOffset;
                if (realIdx >= filteredCommands.size()) break;
                MaredCommandRegistry.CommandInfo info = filteredCommands.get(realIdx);
                int itemY = commandsListY + i * ITEM_HEIGHT;
                boolean hovered = mouseX >= listX && mouseX < listX + listW && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT - 2;
                boolean selected = info == selectedCommandInfo;
                if (selected) {
                    graphics.fill(listX, itemY, listX + listW, itemY + ITEM_HEIGHT - 2, itemSelColor(accent));
                    graphics.fill(listX, itemY, listX + 2, itemY + ITEM_HEIGHT - 2, accent);
                    graphics.drawString(this.font, info.name, listX + 4, itemY + 2, COLOR_TEXT, true);
                } else {
                    int bg = hovered ? COLOR_ITEM_HOVER : COLOR_ITEM_NORMAL;
                    graphics.fill(listX, itemY, listX + listW, itemY + ITEM_HEIGHT - 2, bg);
                    graphics.drawString(this.font, info.name, listX + 4, itemY + 2, COLOR_TEXT, true);
                }
            }
            if (filteredCommands.size() > maxVisible) {
                int trackX = sx + sw - SCROLLBAR_WIDTH - 2;
                graphics.fill(trackX, commandsListY, trackX + SCROLLBAR_WIDTH, commandsListY + commandsH, COLOR_SCROLLBAR_BG);
                int thumbH = Math.max(10, commandsH * maxVisible / filteredCommands.size());
                int thumbY = commandsListY + (commandsH - thumbH) * commandScrollOffset / Math.max(1, maxScroll);
                graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, accent);
            }
        }
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        String[] tabs = {"scripts", "commands", "npc", "events", "quests"};
        int[] colors = {COLOR_SCRIPTS, COLOR_COMMANDS, COLOR_NPC, COLOR_EVENTS, COLOR_QUESTS};
        String[] letters = {"S", "C", "N", "E", "Q"};
        for (int i = 0; i < tabs.length; i++) {
            int y = i * TAB_HEIGHT;
            boolean hovered = mouseX < TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT;
            boolean active = tabs[i].equals(openTab);
            int bg = active ? colors[i] : (hovered ? 0xFF3A3A4A : COLOR_ITEM_NORMAL);
            graphics.fill(2, y + 2, TAB_WIDTH - 2, y + TAB_HEIGHT - 2, bg);
            int textColor = active ? 0xFF000000 : colors[i];
            int tw = this.font.width(letters[i]);
            graphics.drawString(this.font, letters[i], (TAB_WIDTH - tw) / 2, y + (TAB_HEIGHT - 8) / 2 + 1, textColor, false);
        }
    }

    private String tabTitle(String key) {
        switch (key) {
            case "scripts": return "Scripts";
            case "commands": return "Commands";
            case "npc": return "NPC";
            case "events": return "Events";
            case "quests": return "Quests";
            default: return key;
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { /* Empty. */ }
}