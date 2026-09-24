package com.fixmer.mared.gui.editor;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.engine.MaredScriptParser;
import com.fixmer.mared.commands.engine.MaredScriptRunner;
import com.fixmer.mared.commands.events.MaredEventRegistry;
import com.fixmer.mared.commands.events.MaredPersistentLoader;
import com.fixmer.mared.commands.registry.MaredCommandRegistry;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.commands.storage.MaredPersistentStorage;
import com.fixmer.mared.commands.storage.MaredScriptStorage;
import com.fixmer.mared.gui.common.MaredConfirmDialog;
import com.fixmer.mared.gui.common.MaredLogPanel;
import com.fixmer.mared.gui.common.MaredMultiLineEditBox;
import com.fixmer.mared.gui.common.MaredNameDialog;
import com.fixmer.mared.gui.common.MaredSettingsScreen;
import com.fixmer.mared.gui.common.MaredUi;
import com.fixmer.mared.gui.editor.panels.MaredEditorBindsPanel;
import com.fixmer.mared.gui.editor.sidebar.MaredEditorInfoPanel;
import com.fixmer.mared.gui.editor.sidebar.MaredEditorSidebar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class MaredEditorScreen extends Screen {

    public static final int BG           = 0xFF0A0A10;
    public static final int PANEL_BASE   = 0xFF14141C;
    public static final int PANEL_RAISED = 0xFF1A1A24;
    public static final int EDITOR_BG    = 0xFF0E0E16;
    public static final int TAB_STRIP_BG = 0xFF0F0F14;

    private static final int TEXT     = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;

    private final MaredLogPanel         logPanel   = new MaredLogPanel();
    private final MaredEditorToolbar    toolbar    = new MaredEditorToolbar();
    private final MaredEditorSidebar    sidebar    = new MaredEditorSidebar();
    private final MaredEditorInfoPanel  infoPanel  = new MaredEditorInfoPanel();
    private final MaredEditorBindsPanel bindsPanel = new MaredEditorBindsPanel();

    private String openTab = "scripts";
    private MaredEditorLayout.SidebarState sidebarState = MaredEditorLayout.SidebarState.CLOSED;
    private boolean logCollapsed = false;
    private String lastSavedText = "";

    private final MaredUi.DragState drag = new MaredUi.DragState();
    private MaredMultiLineEditBox editor;

    private static List<MaredCommandRegistry.CommandInfo> MRED_COMMANDS = null;

    private double lastMouseX = -1;
    private double lastMouseY = -1;

    private EditBox lastLogSearchBox = null;

    public MaredEditorScreen() { super(Component.literal("Mared Editor")); }

    // ============================================================
    //  Lifecycle
    // ============================================================

    @Override
    protected void init() {
        super.init();

        MaredEditorLayout.update(this.width, this.height);
        MaredSettings.load();
        MaredLang.reload();
        MaredCommandRegistry.reload();
        ensureMredCommands();

        lastLogSearchBox = null;

        sidebar.reloadFiles(openTab);
        sidebar.refreshCommands(openTab, MRED_COMMANDS);

        buildToolButtons();
        rebuildEditor();
        updateSidebarFilterBox();
        updateInfoFilterBox();
        updateLogSearchBox();

        logPanel.setOnToggle(() -> {
            logCollapsed = !logCollapsed;
            rebuildEditor();
            updateInfoFilterBox();
            updateSidebarFilterBox();
            updateLogSearchBox();
        });
    }

    private void buildToolButtons() {
        for (AbstractWidget w : toolbar.buttons()) removeWidget(w);
        toolbar.build(new ToolbarContextImpl(), this.font);
        for (AbstractWidget w : toolbar.buttons()) addRenderableWidget(w);
    }

    private final class ToolbarContextImpl implements MaredEditorToolbar.ScreenContext {
        @Override public int screenW() { return MaredEditorLayout.screenW(); }
        @Override public String openTab() { return openTab; }
        @Override public void onClose() { MaredEditorScreen.this.onClose(); }
        @Override public void onRun() { MaredEditorScreen.this.onRun(); }
        @Override public void onSave() { MaredEditorScreen.this.onSave(); }
        @Override public void onDelete() { MaredEditorScreen.this.onDelete(); }
        @Override public void onImport() { MaredEditorScreen.this.onImport(); }
        @Override public void onSettings() { MaredEditorScreen.this.onSettings(); }
        @Override public void onReloadPersistent() { MaredEditorScreen.this.onReloadPersistent(); }
        @Override public void onNew() { MaredEditorScreen.this.onNew(); }
    }

    // ============================================================
    //  EditBox-виджеты
    // ============================================================

    private void updateSidebarFilterBox() {
        EditBox current = sidebar.commandFilterBox();
        if (current != null) {
            removeWidget(current);
            sidebar.setCommandFilterBox(null);
        }
        EditBox box = sidebar.ensureFilterBox(this.font, MaredEditorLayout.INSTANCE,
            openTab, sidebarState, logCollapsed);
        if (box != null && !children().contains(box)) addRenderableWidget(box);
    }

    private void updateInfoFilterBox() {
        EditBox current = infoPanel.argFilterBox();
        if (current != null) {
            removeWidget(current);
            infoPanel.setArgFilterBox(null);
        }
        MaredCommandRegistry.CommandInfo info = sidebar.selectedCommandInfo();
        if (info == null || infoPanel.isCollapsed()) return;

        EditBox box = infoPanel.ensureFilterBox(this.font, MaredEditorLayout.INSTANCE, info);
        if (box != null && !children().contains(box)) addRenderableWidget(box);
    }

    private void updateLogSearchBox() {
        if (lastLogSearchBox != null) {
            removeWidget(lastLogSearchBox);
            lastLogSearchBox = null;
        }
        if (!logPanel.isSearchOpen()) return;

        int logTop = MaredEditorLayout.logTop(logCollapsed);
        int logW = MaredEditorLayout.logWidth();
        EditBox box = logPanel.ensureSearchBox(this.font, 0, logTop, logW);
        if (box != null) {
            addRenderableWidget(box);
            box.setFocused(true);
            lastLogSearchBox = box;
        }
    }

    // ============================================================
    //  Editor rebuild
    // ============================================================

    private void rebuildEditor() {
        if (editor != null) { removeWidget(editor); editor = null; }
        if (!MaredEditorTabs.isSupported(openTab)) return;

        int pad = MaredEditorLayout.PAD();
        int frameX = MaredEditorLayout.TAB_W() + sidebarWidth() + pad;
        int frameY = MaredEditorLayout.TOOLBAR_H() + pad * 2;
        int infoW = infoWidth();
        int frameW = MaredEditorLayout.screenW() - frameX - pad - infoW;
        int frameH = MaredEditorLayout.editorBottom(logCollapsed) - frameY - pad;
        int editorHdr = MaredEditorLayout.EDITOR_HDR();

        int editorW = Math.max(80, frameW - 4);
        int editorH = Math.max(40, frameH - editorHdr - 4);

        editor = new MaredMultiLineEditBox(
            frameX + 2, frameY + editorHdr + 2,
            editorW, editorH,
            accentTop(), this::onEditorChanged);
        loadIntoEditor();
        addRenderableWidget(editor);
    }

    private void onEditorChanged() {}

    private void loadIntoEditor() {
        if (editor == null) return;
        String sel = sidebar.selectedFile();
        if (sel == null) {
            editor.setValue("");
            lastSavedText = "";
            editor.setEditable(false);
            return;
        }
        String val = "scripts".equals(openTab)
            ? MaredScriptStorage.readScript(sel)
            : MaredCommandStorage.readCommand(sel);
        editor.setValue(val);
        lastSavedText = val;
        editor.setEditable(true);
        editor.setFocused(true);
    }

    // ============================================================
    //  Геометрия
    // ============================================================

    private int sidebarWidth() {
        return MaredEditorLayout.sidebarWidth(sidebarState,
            MaredEditorTabs.isSupported(openTab));
    }

    private int infoWidth() {
        if (!"commands".equals(openTab) || sidebar.selectedCommandInfo() == null) return 0;
        return MaredEditorLayout.infoWidth(infoPanel.isCollapsed(), true);
    }

    private int accentTop()    { return MaredEditorTabs.topColor(openTab); }
    private int accentBottom() { return MaredEditorTabs.bottomColor(openTab); }

    private boolean isMaredInfo() {
        MaredCommandRegistry.CommandInfo ci = sidebar.selectedCommandInfo();
        return ci != null && "Mared".equals(ci.category);
    }

    private static int selColor(int a) {
        return 0xFF000000 | ((a >> 16 & 0xFF) / 3 << 16)
                         | ((a >> 8  & 0xFF) / 3 << 8)
                         | ((a       & 0xFF) / 3);
    }

    // ============================================================
    //  Действия
    // ============================================================

    private void onNew() {
        if ("scripts".equals(openTab)) {
            Minecraft.getInstance().setScreen(new MaredNameDialog(this,
                MaredLang.get("mared.dialog.new_script"),
                (name, persistent) -> {
                    if (MaredScriptStorage.createScript(name)) {
                        addLog(MaredLang.format("mared.log.info.created_script", name));
                        sidebar.setSelectedFile(name);
                        sidebar.reloadFiles(openTab);
                        rebuildEditor();
                    } else addLog(MaredLang.format("mared.log.error.failed_create", name));
                },
                MaredEditorTabs.SCRIPTS_TOP, false,
                name -> MaredScriptStorage.listScripts().contains(name)
                    ? MaredLang.format("mared.dialog.error.exists", name)
                    : null
            ));
        } else if ("commands".equals(openTab)) {
            Minecraft.getInstance().setScreen(new MaredNameDialog(this,
                MaredLang.get("mared.dialog.new_command_file"),
                (name, persistent) -> {
                    if (MaredCommandStorage.createCommand(name)) {
                        if (persistent) {
                            String content = MaredCommandStorage.readCommand(name);
                            if (content == null) content = "";
                            if (!content.startsWith("#persistent")) {
                                content = "#persistent\n" + content;
                                MaredCommandStorage.writeCommand(name, content);
                            }
                            MaredPersistentStorage.add(name);
                            addLog("[mared] created persistent: " + name);
                        } else {
                            addLog(MaredLang.format("mared.log.info.created_command", name));
                        }
                        sidebar.setSelectedFile(name);
                        sidebar.reloadFiles(openTab);
                        rebuildEditor();
                    } else addLog(MaredLang.format("mared.log.error.failed_create", name));
                },
                MaredEditorTabs.COMMANDS_TOP, true,
                name -> MaredCommandStorage.listCommands().contains(name)
                    ? MaredLang.format("mared.dialog.error.exists", name)
                    : null
            ));
        }
    }

    private void onImport() { addLog(MaredLang.get("mared.log.warn.import")); }

    private void onDelete() { onDelete(sidebar.selectedFile()); }

    private void onDelete(String fileName) {
        if (fileName == null) {
            addLog(MaredLang.get("mared.log.warn.no_file"));
            return;
        }
        final boolean commandsTab = "commands".equals(openTab);
        final boolean wasPersistent = commandsTab && MaredPersistentStorage.isPersistent(fileName);

        String type = commandsTab
            ? MaredLang.get("mared.dialog.type_command")
            : MaredLang.get("mared.dialog.type_script");

        final String title;
        final String message;
        final boolean warning;

        if (wasPersistent) {
            title = MaredLang.format("mared.dialog.delete_persistent_title", fileName);
            message = MaredLang.get("mared.dialog.delete_persistent_message");
            warning = true;
        } else {
            title = MaredLang.format("mared.dialog.delete_title", type, fileName);
            message = MaredLang.format("mared.dialog.delete_message", fileName);
            warning = false;
        }

        Minecraft.getInstance().setScreen(new MaredConfirmDialog(this, title, message,
            () -> {
                boolean ok = commandsTab
                    ? MaredCommandStorage.deleteCommand(fileName)
                    : MaredScriptStorage.deleteScript(fileName);
                if (!ok) {
                    addLog(MaredLang.format("mared.log.error.failed_delete", fileName));
                    return;
                }
                MaredPersistentStorage.remove(fileName);
                if (wasPersistent) {
                    MaredEventRegistry.clearAllPersistent();
                    MaredPersistentLoader.reset();
                    MaredPersistentLoader.loadAll();
                    addLog("[mared] removed persistent handlers for: " + fileName);
                }
                addLog(MaredLang.format("mared.log.info.deleted", fileName));
                if (fileName.equals(sidebar.selectedFile())) sidebar.setSelectedFile(null);
                sidebar.reloadFiles(openTab);
                rebuildEditor();
            },
            warning
        ));
    }

    private void onSettings() {
        Minecraft.getInstance().setScreen(new MaredSettingsScreen(this));
    }

    private void onReloadPersistent() {
        MaredEventRegistry.clearAll();
        MaredEventRegistry.clearAllPersistent();
        MaredPersistentStorage.invalidateCache();
        MaredPersistentLoader.reset();
        MaredPersistentLoader.loadAll();
        addLog("[mared] persistent scripts reloaded");
    }

    private boolean saveCurrentFile() {
        if (sidebar.selectedFile() == null || editor == null) return false;
        String content = editor.getValue();
        if (content.equals(lastSavedText)) return false;
        boolean ok = "scripts".equals(openTab)
            ? MaredScriptStorage.writeScript(sidebar.selectedFile(), content)
            : MaredCommandStorage.writeCommand(sidebar.selectedFile(), content);
        if (ok) lastSavedText = content;
        return ok;
    }

    private void onSave() {
        if (sidebar.selectedFile() == null || editor == null) return;
        String content = editor.getValue();
        boolean ok = "scripts".equals(openTab)
            ? MaredScriptStorage.writeScript(sidebar.selectedFile(), content)
            : MaredCommandStorage.writeCommand(sidebar.selectedFile(), content);
        if (ok) {
            lastSavedText = content;
            addLog(MaredLang.format("mared.log.info.saved", sidebar.selectedFile()));
        } else {
            addLog(MaredLang.format("mared.log.error.failed_save", sidebar.selectedFile()));
        }
    }

    private void onRun() {
        if (sidebar.selectedFile() == null || editor == null) return;
        if (!editor.getValue().equals(lastSavedText)) {
            if (saveCurrentFile()) addLog(MaredLang.get("mared.log.auto_save.saved"));
            else addLog(MaredLang.get("mared.log.auto_save.failed"));
        }
        if ("scripts".equals(openTab)) runScript();
        else if ("commands".equals(openTab)) runCommandFile();
    }

    // ============================================================
    //  Run
    // ============================================================

    private void runScript() {
        String text = editor.getValue();
        if (text == null || text.trim().isEmpty()) {
            addLog(MaredLang.get("mared.log.warn.script_empty"));
            return;
        }
        boolean isPersistent = text.startsWith("#persistent");
        if (isPersistent) {
            int nl = text.indexOf('\n');
            text = (nl >= 0) ? text.substring(nl + 1) : "";
        }
        runMaredText(text, isPersistent, "script");
    }

    private void runCommandFile() {
        String text = editor.getValue();
        if (text == null || text.trim().isEmpty()) {
            addLog(MaredLang.get("mared.log.warn.file_empty"));
            return;
        }
        boolean fileIsPersistent = text.startsWith("#persistent");
        if (fileIsPersistent) {
            int nl = text.indexOf('\n');
            text = (nl >= 0) ? text.substring(nl + 1) : "";
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) {
            addLog(MaredLang.get("mared.log.error.player_unavailable"));
            return;
        }
        addLog(MaredLang.format("mared.log.run.file", sidebar.selectedFile()));

        int cmdCount = 0;
        int blockCount = 0;
        List<String> blockBuffer = new ArrayList<>();
        int braceDepth = 0;
        int blockStartLine = 0;

        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trimmed = raw.trim();
            int ln = i + 1;

            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                if (braceDepth > 0) blockBuffer.add(raw);
                continue;
            }

            int opens = countChar(trimmed, '{');
            int closes = countChar(trimmed, '}');

            if (braceDepth == 0 && opens > 0) {
                blockStartLine = ln;
                addLog(MaredLang.format("mared.log.mared.block_start", ln));
            }

            if (braceDepth > 0 || opens > 0) {
                blockBuffer.add(raw);
                braceDepth += opens - closes;
                if (braceDepth == 0 && !blockBuffer.isEmpty()) {
                    runMaredText(String.join("\n", blockBuffer), fileIsPersistent,
                        "line " + blockStartLine);
                    blockCount++;
                    blockBuffer.clear();
                }
                continue;
            }

            String cmd = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
            try {
                mc.player.connection.sendCommand(cmd);
                addLog(MaredLang.format("mared.log.cmd.sent", cmd));
                cmdCount++;
            } catch (Exception e) {
                addLog(MaredLang.format("mared.log.cmd.error", cmd, e.getMessage()));
            }
        }
        if (braceDepth > 0) addLog(MaredLang.get("mared.log.warn.block_not_closed"));
        addLog(MaredLang.format("mared.log.run.done", cmdCount, blockCount));
    }

    private void runMaredText(String text, boolean isPersistent, String label) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();

        ServerPlayer initiator = null;
        if (server != null && mc.player != null) {
            initiator = server.getPlayerList().getPlayer(mc.player.getUUID());
        }

        List<MaredScriptCommand> commands;
        try {
            commands = MaredScriptParser.parse(text);
        } catch (MaredScriptParser.ParseException e) {
            addLog(MaredLang.format("mared.log.mared.parse_error", e.getMessage()));
            return;
        } catch (RuntimeException e) {
            addLog("[mared parse] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        if (commands.isEmpty()) {
            addLog("[mared] " + label + ": empty");
            return;
        }
        addLog("[mared] " + label + ": " + commands.size() + " commands ready");

        MaredScriptContext ctx = new MaredScriptContext(initiator, server, this::addLog);
        ctx.setPersistent(isPersistent);
        ctx.forceRefreshPlayerData();
        MaredScriptRunner.start(new MaredScriptExecutor(ctx, commands));
    }

    private static int countChar(String s, char c) {
        int n = 0;
        boolean inString = false;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < len) { i++; continue; }
            if (ch == '"') inString = !inString;
            if (!inString && ch == c) n++;
        }
        return n;
    }

    private void addLog(String line) { logPanel.add(line); }

    // ============================================================
    //  Mouse
    // ============================================================

    @Override
    public void mouseMoved(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
        super.mouseMoved(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        this.lastMouseX = mx;
        this.lastMouseY = my;

        if (handleTabClick(mx, my)) return true;
        if (handleSidebarClick(mx, my)) return true;

        if (super.mouseClicked(mx, my, button)) return true;

        if (handleInfoPanelClick(mx, my)) return true;
        if (handleBindsPanelClick(mx, my)) return true;
        if (handleLogPanelClick(mx, my, button)) return true;

        return false;
    }

    private boolean handleTabClick(double mx, double my) {
        int tabIdx = MaredEditorTabs.hitTab(mx, my);
        if (tabIdx < 0) return false;

        String clicked = MaredEditorTabs.TABS[tabIdx];
        boolean supported = MaredEditorTabs.isSupported(clicked);

        if (clicked.equals(openTab) && sidebarWidth() > 0) {
            sidebarState = MaredEditorLayout.SidebarState.CLOSED;
        } else {
            openTab = clicked;
            sidebarState = supported
                ? MaredEditorLayout.SidebarState.STRIP
                : MaredEditorLayout.SidebarState.CLOSED;
            sidebar.setSelectedFile(null);
            sidebar.setSelectedCommandInfo(null);
            infoPanel.clearExpanded();
        }
        buildToolButtons();
        rebuildEditor();
        updateSidebarFilterBox();
        updateInfoFilterBox();
        updateLogSearchBox();
        sidebar.reloadFiles(openTab);
        sidebar.refreshCommands(openTab, MRED_COMMANDS);
        return true;
    }

    private boolean handleSidebarClick(double mx, double my) {
        if (!MaredEditorTabs.isSupported(openTab)) return false;

        MaredEditorLayout.SidebarState state = sidebarState;
        if (state != MaredEditorLayout.SidebarState.STRIP
            && state != MaredEditorLayout.SidebarState.FULL) return false;

        MaredEditorLayout.SidebarState prev = state;

        boolean clicked = sidebar.handleClick(
            MaredEditorLayout.INSTANCE, openTab, mx, my, state, drag,
            this::onNew, this::onDelete, logCollapsed);

        if (!clicked) return false;

        boolean needRebuild = false;

        if (sidebar.shouldToggleSidebar) {
            sidebar.shouldToggleSidebar = false;
            if (prev == MaredEditorLayout.SidebarState.STRIP)
                sidebarState = MaredEditorLayout.SidebarState.FULL;
            else if (prev == MaredEditorLayout.SidebarState.FULL)
                sidebarState = MaredEditorLayout.SidebarState.STRIP;
            needRebuild = true;
        }
        if (sidebar.shouldRebuildEditor) {
            sidebar.shouldRebuildEditor = false;
            needRebuild = true;
        }
        if (sidebar.shouldRefreshCommands) {
            sidebar.shouldRefreshCommands = false;
            sidebar.refreshCommands(openTab, MRED_COMMANDS);
        }
        if (needRebuild) {
            rebuildEditor();
            updateInfoFilterBox();
        }
        updateSidebarFilterBox();
        return true;
    }

    private boolean handleInfoPanelClick(double mx, double my) {
        if (!"commands".equals(openTab) || sidebar.selectedCommandInfo() == null) return false;
        if (my < MaredEditorLayout.TOOLBAR_H() + MaredEditorLayout.PAD()) return false;

        int editorBottom = MaredEditorLayout.editorBottom(logCollapsed);
        if (!infoPanel.mouseClicked(MaredEditorLayout.INSTANCE, this.font,
            sidebar.selectedCommandInfo(), mx, my, editorBottom, drag)) return false;

        rebuildEditor();
        updateInfoFilterBox();
        return true;
    }

    private boolean handleBindsPanelClick(double mx, double my) {
        if (logCollapsed) return false;
        int logTop = MaredEditorLayout.logTop(false);
        int logW = MaredEditorLayout.logWidth();
        if (my < logTop || mx < logW) return false;
        return bindsPanel.mouseClicked(mx, my, MaredEditorLayout.INSTANCE, false);
    }

    private boolean handleLogPanelClick(double mx, double my, int button) {
        int logTop = MaredEditorLayout.logTop(logCollapsed);
        if (my < logTop) return false;

        int logW = MaredEditorLayout.logWidth();
        boolean wasSearchOpen = logPanel.isSearchOpen();
        boolean handled = logPanel.mouseClicked(mx, my, button, 0, logTop, logW,
            MaredEditorLayout.logHeight(logCollapsed), this.font);

        if (wasSearchOpen != logPanel.isSearchOpen()) updateLogSearchBox();
        return handled;
    }

    // ============================================================
    //  Key
    // ============================================================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & 2) != 0;
        boolean shift = (modifiers & 1) != 0;
        boolean editorFocused = editor != null && editor.isFocused();
        boolean editorHasSelection = editor != null && editor.hasSelection();
        EditBox searchBox = logPanel.searchBox();
        boolean searchFocused = searchBox != null && searchBox.isFocused();

        if (searchFocused && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            logPanel.closeSearch();
            updateLogSearchBox();
            return true;
        }
        if (searchFocused && !ctrl) return super.keyPressed(keyCode, scanCode, modifiers);

        int logTop = MaredEditorLayout.logTop(logCollapsed);
        int logW = MaredEditorLayout.logWidth();
        boolean mouseOverLog = !logCollapsed
            && lastMouseX >= 0 && lastMouseY >= 0
            && lastMouseX < logW
            && lastMouseY >= logTop
            && lastMouseY < MaredEditorLayout.screenH();

        if (ctrl && keyCode == GLFW.GLFW_KEY_F) {
            if (!logPanel.isSearchOpen()) openLogSearch();
            else if (searchFocused) { logPanel.closeSearch(); updateLogSearchBox(); }
            else if (searchBox != null) searchBox.setFocused(true);
            return true;
        }

        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            if (editorFocused && editorHasSelection && !mouseOverLog)
                return super.keyPressed(keyCode, scanCode, modifiers);
            logPanel.copySelectedOrAll();
            return true;
        }

        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            if (editorFocused && !mouseOverLog)
                return super.keyPressed(keyCode, scanCode, modifiers);
            logPanel.selectAll();
            return true;
        }

        if (ctrl && keyCode == GLFW.GLFW_KEY_S && !editorFocused) {
            if (shift) onSave();
            else logPanel.exportLog();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE
            && sidebar.selectedFile() != null && !editorFocused) {
            onDelete();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openLogSearch() {
        logPanel.setCollapsed(false);
        logPanel.setSearchOpen(true);
        updateLogSearchBox();
        EditBox box = logPanel.searchBox();
        if (box != null) box.setFocused(true);
    }

    // ============================================================
    //  Drag / Scroll / Release
    // ============================================================

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        this.lastMouseX = mx;
        this.lastMouseY = my;

        int logTop = MaredEditorLayout.logTop(logCollapsed);
        int logW = MaredEditorLayout.logWidth();

        logPanel.tickAutoScroll();

        if (my >= logTop && logPanel.mouseDragged(mx, my, button, dx, dy, 0, logTop, logW,
            MaredEditorLayout.logHeight(logCollapsed), this.font)) return true;

        if (!drag.active()) return super.mouseDragged(mx, my, button, dx, dy);

        switch (drag.kind) {
            case FILE_SCROLL -> sidebar.fileScroll().dragScrollbar(my, drag);
            case CMD_SCROLL  -> sidebar.cmdScroll().dragScrollbar(my, drag);
            case INFO_SCROLL -> infoPanel.scroll().dragScrollbar(my, drag);
            default -> {}
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        logPanel.mouseReleased(mx, my, button);
        drag.clear();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int logTop = MaredEditorLayout.logTop(logCollapsed);
        int logW = MaredEditorLayout.logWidth();

        if (!logCollapsed && bindsPanel.mouseScrolled(mx, my, dy,
            MaredEditorLayout.INSTANCE, false)) return true;

        if (logPanel.mouseScrolled(mx, my, dy, 0, logTop, logW,
            MaredEditorLayout.logHeight(logCollapsed))) return true;

        if ("commands".equals(openTab) && sidebar.selectedCommandInfo() != null
            && !infoPanel.isCollapsed()
            && mx >= MaredEditorLayout.infoPanelX(infoPanel.isCollapsed())) {
            infoPanel.scroll().wheel(dy, 5);
            return true;
        }

        if (MaredEditorTabs.isSupported(openTab)
            && sidebarState == MaredEditorLayout.SidebarState.FULL) {
            if (sidebar.handleScroll(MaredEditorLayout.INSTANCE, openTab, mx, my, dy,
                sidebarState, logCollapsed)) return true;
        }

        if (editor != null && editor.isMouseOver(mx, my)
            && editor.mouseScrolled(mx, my, dx, dy)) return true;

        return super.mouseScrolled(mx, my, dx, dy);
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MaredEditorLayout.update(this.width, this.height);
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        int screenW = MaredEditorLayout.screenW();
        int screenH = MaredEditorLayout.screenH();
        int logTop = MaredEditorLayout.logTop(logCollapsed);
        int logW = MaredEditorLayout.logWidth();
        int sbW = sidebarWidth();
        int tabW = MaredEditorLayout.TAB_W();
        int toolbarH = MaredEditorLayout.TOOLBAR_H() + MaredEditorLayout.PAD();

        logPanel.tickAutoScroll();

        MaredUi.rect(g, 0, 0, screenW, screenH, BG);

        if (sbW > 0) {
            int bg = (sidebarState == MaredEditorLayout.SidebarState.STRIP)
                ? PANEL_BASE : PANEL_RAISED;
            MaredUi.panelLit(g, tabW, 0, sbW, logTop, bg);
        }

        MaredUi.panelLit(g, tabW + sbW, 0, screenW - tabW - sbW, toolbarH, PANEL_RAISED);

        MaredUi.rect(g, 0, 0, tabW, screenH, TAB_STRIP_BG);
        MaredEditorTabs.draw(g, this.font, openTab, mouseX, mouseY);

        if (MaredEditorTabs.isSupported(openTab)) {
            if (sidebarState == MaredEditorLayout.SidebarState.STRIP) {
                sidebar.drawStrip(g, this.font, MaredEditorLayout.INSTANCE,
                    mouseX, mouseY, accentTop(), accentBottom());
            } else if (sidebarState == MaredEditorLayout.SidebarState.FULL) {
                sidebar.drawFull(g, this.font, MaredEditorLayout.INSTANCE,
                    openTab, mouseX, mouseY, accentTop(), accentBottom(),
                    selColor(accentTop()), logCollapsed);
            }
        }

        drawEditorFrame(g, sbW);

        if ("commands".equals(openTab) && sidebar.selectedCommandInfo() != null) {
            infoPanel.render(g, this.font, MaredEditorLayout.INSTANCE,
                sidebar.selectedCommandInfo(), accentTop(), accentBottom(),
                isMaredInfo(), mouseX, mouseY,
                MaredEditorLayout.editorBottom(logCollapsed), drag);
        }

        logPanel.setCollapsed(logCollapsed);
        logPanel.render(g, this.font, 0, logTop, logW,
            MaredEditorLayout.logHeight(logCollapsed), mouseX, mouseY);

        if (!logCollapsed) {
            bindsPanel.render(g, this.font, MaredEditorLayout.INSTANCE,
                false, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawEditorFrame(GuiGraphics g, int sbW) {
        boolean editable = MaredEditorTabs.isSupported(openTab);
        int pad = MaredEditorLayout.PAD();
        int frameX = MaredEditorLayout.TAB_W() + sbW + pad;
        int frameY = MaredEditorLayout.TOOLBAR_H() + pad * 2;
        int frameW = MaredEditorLayout.screenW() - frameX - pad
                   - (editable ? infoWidth() : 0);
        int frameH = MaredEditorLayout.editorBottom(logCollapsed) - frameY - pad;
        int editorHdr = MaredEditorLayout.EDITOR_HDR();

        MaredUi.rect(g, frameX, frameY, frameX + frameW, frameY + frameH, EDITOR_BG);

        if (!editable) {
            MaredUi.text(g, this.font,
                MaredLang.format("mared.ui.under_development", MaredEditorTabs.title(openTab)),
                frameX + 8, frameY + 8, TEXT_DIM);
            return;
        }

        MaredUi.rect(g, frameX, frameY, frameX + frameW, frameY + 1, accentTop());
        MaredUi.rect(g, frameX, frameY + frameH - 1, frameX + frameW, frameY + frameH, accentBottom());

        String type = "scripts".equals(openTab) ? "script" : "commands";
        String sel = sidebar.selectedFile();
        String title = (sel == null)
            ? MaredLang.format("mared.ui.editor_no_selected", type)
            : MaredLang.format("mared.ui.editor_file", sel);
        MaredUi.text(g, this.font, title, frameX + 6, frameY + 5, TEXT);
        MaredUi.dashedLineGradient(g, frameX + 2, frameY + editorHdr,
            frameX + frameW - 2, accentTop(), accentBottom());
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    // ============================================================
    //  MRED_COMMANDS — встроенный справочник
    // ============================================================

    private static void ensureMredCommands() {
        if (MRED_COMMANDS != null) return;
        MRED_COMMANDS = new ArrayList<>();

        // ---- базовые ----
        MRED_COMMANDS.add(mk("say", "Отправить сообщение в чат", "say \"Hello, $self\"",
            args("text", "текст сообщения", "say \"Hello!\"", "say $greeting")));
        MRED_COMMANDS.add(mk("wait", "Пауза в тиках/секундах", "wait 3 seconds",
            args("time", "время ожидания", "wait 3 seconds", "wait 100 ticks")));
        MRED_COMMANDS.add(mk("set", "Присвоить переменную", "set count = 5",
            args("name = value", "имя и значение", "set count = 5", "set name = \"Steve\""),
            args("global.name", "глобальная переменная", "set global.hp = 20", "set global.count = $global.count + 1")));
        MRED_COMMANDS.add(mk("array", "Объявить массив", "array items = [1, 2, 3]",
            args("name = [values]", "массив значений", "array items = [1, 2, 3]", "array names = [\"a\", \"b\"]")));
        MRED_COMMANDS.add(mk("give", "Выдать предмет", "give @s diamond 5",
            args("target", "получатель", "give @s diamond 5"),
            args("item", "предмет", "give @s minecraft:diamond 5"),
            args("count", "количество", "give @s diamond 64")));
        MRED_COMMANDS.add(mk("mc", "Ванильная команда", "mc time set day",
            args("command", "команда", "mc time set day", "mc weather clear")));
        MRED_COMMANDS.add(mk("print", "Вывести в лог", "print \"Hello\"",
            args("text", "текст", "print \"value: $x\"")));
        MRED_COMMANDS.add(mk("debug", "Вывести значение", "debug $x",
            args("expression", "выражение", "debug $x", "debug $x + 1")));
        MRED_COMMANDS.add(mk("log", "Записать в лог", "log \"message\"",
            args("text", "текст", "log \"checkpoint\"")));
        MRED_COMMANDS.add(mk("assert", "Проверка", "assert $x > 10 \"must be big\"",
            args("condition [message]", "условие и сообщение", "assert $x > 10 \"x too small\"")));

        // ---- условия и циклы ----
        MRED_COMMANDS.add(mk("if", "Условие", "if $count > 5 { say \"Many\" }",
            args("condition", "условие", "if $count > 5 { ... }"),
            args("elif / else", "доп. ветки", "elif $count == 5 { ... } else { ... }")));
        MRED_COMMANDS.add(mk("repeat", "Повторить N раз", "repeat 3 { say \"Tick\" }",
            args("N", "количество", "repeat 5 { ... }")));
        MRED_COMMANDS.add(mk("for", "Цикл", "for $i = 1 to 5 { say $i }",
            args("var = from to to", "диапазон", "for $i = 1 to 5 { ... }"),
            args("var in array", "по массиву", "for $item in $items { ... }")));
        MRED_COMMANDS.add(mk("while", "Пока условие", "while $n < 3 { set n = $n + 1 }",
            args("condition", "условие", "while $n < 3 { ... }")));
        MRED_COMMANDS.add(mk("break", "Выйти из цикла", "break",
            args("", "", "repeat 10 { break }")));
        MRED_COMMANDS.add(mk("continue", "Пропустить итерацию", "continue",
            args("", "", "if $i == 3 { continue }")));

        // ---- функции ----
        MRED_COMMANDS.add(mk("func", "Объявить функцию", "func greet($name) { say \"Hi, $name\" }",
            args("name(params)", "имя и параметры", "func greet($name) { ... }")));
        MRED_COMMANDS.add(mk("call", "Вызвать функцию", "call greet(\"Steve\")",
            args("name(args)", "имя и аргументы", "call greet(\"Steve\")"),
            args("return", "присвоить результат", "set x = call add(2, 3)")));
        MRED_COMMANDS.add(mk("return", "Вернуть значение", "return $a + $b",
            args("expression", "выражение", "return 5", "return call f($n - 1)")));
        MRED_COMMANDS.add(mk("exit", "Остановить скрипт", "exit",
            args("", "", "exit")));

        // ---- бинды ----
        MRED_COMMANDS.add(mk("bind", "Привязать клавишу", "bind R { say \"Hi\" }",
            args("key", "клавиша", "bind R { say \"Hi\" }"),
            args("add", "добавить действие", "bind R add { say \"second\" }"),
            args("clear", "очистить бинд", "bind Q clear"),
            args("block", "заблокировать ваниль", "bind W block { say \"locked\" }"),
            args("hold", "удержание", "bind W hold { say \"held\" }"),
            args("release", "отпускание", "bind W release { say \"released\" }")));
        MRED_COMMANDS.add(mk("block", "Заблокировать клавишу", "block W",
            args("key", "клавиша", "block W", "block Space")));
        MRED_COMMANDS.add(mk("unblock", "Разблокировать клавишу", "unblock W",
            args("key", "клавиша", "unblock W", "unblock Space")));
        MRED_COMMANDS.add(mk("toggle", "Переключить блокировку", "toggle W",
            args("key", "клавиша", "toggle W", "toggle MouseMove")));

        // ---- события ----
        MRED_COMMANDS.add(mk("on", "Обработчик события", "on right_click { say \"clicked\" }",
            args("event", "тип события", "on right_click { ... }"),
            args("add", "добавить", "on right_click add { ... }"),
            args("replace", "заменить", "on right_click replace { ... }")));
        MRED_COMMANDS.add(mk("off", "Снять слушателей", "off all",
            args("target", "имя | every | after | all", "off tick_client", "off all")));
        MRED_COMMANDS.add(mk("every", "Каждые N тиков", "every 20 ticks { say \"1 sec\" }",
            args("N", "период в тиках", "every 20 ticks { ... }")));
        MRED_COMMANDS.add(mk("after", "Через N тиков (один раз)", "after 100 ticks { say \"5 sec\" }",
            args("N", "задержка", "after 100 ticks { ... }")));
        MRED_COMMANDS.add(mk("wait_until", "Ждать условие", "wait_until $hp < 5",
            args("condition", "условие", "wait_until $hp < 5")));
        MRED_COMMANDS.add(mk("once", "Один раз за сессию", "once { say \"init\" }",
            args("block", "тело", "once { ... }")));
        MRED_COMMANDS.add(mk("first_join", "Первый вход (синоним on first_join)",
            "first_join { say \"Welcome!\" }",
            args("block", "тело", "first_join { ... }")));

        // ---- action API ----
        MRED_COMMANDS.add(mk("look_at", "Повернуть камеру на точку", "look_at 100 64 200",
            args("x/y/z", "координаты цели", "look_at $x $y ($z + 5)")));
        MRED_COMMANDS.add(mk("look", "Установить углы камеры", "look 90 0",
            args("yaw", "поворот", "look 90 0"),
            args("pitch", "наклон", "look 0 -45")));
        MRED_COMMANDS.add(mk("move", "Зажать/отпустить клавишу движения", "move forward on",
            args("direction", "forward | back | left | right | sneak | sprint", "move forward on"),
            args("mode", "on | off | toggle", "move forward off")));
        MRED_COMMANDS.add(mk("stop", "Сбросить все действия", "stop"));
        MRED_COMMANDS.add(mk("jump", "Прыжок (действие)", "jump"));
        MRED_COMMANDS.add(mk("attack", "Атака (действие)", "attack"));
        MRED_COMMANDS.add(mk("use", "Использовать предмет (ПКМ)", "use"));
        MRED_COMMANDS.add(mk("drop", "Выбросить предмет", "drop"));
        MRED_COMMANDS.add(mk("swap_hands", "Сменить руки", "swap_hands"));
        MRED_COMMANDS.add(mk("select_slot", "Выбрать слот хотбара", "select_slot 3",
            args("n", "номер слота 0-8", "select_slot 0")));

        // ---- события клиента ----
        MRED_COMMANDS.add(mk("on player_move", "Игрок сдвинулся", "on player_move { say \"$dx $dy $dz\" }",
            args("dx/dy/dz", "смещение", "1 -1 0"),
            args("from_x/y/z", "откуда", "10 64 20"),
            args("x/y/z", "куда", "11 63 20")));
        MRED_COMMANDS.add(mk("on health_change", "HP изменился", "on health_change { say \"hp $old_hp → $new_hp\" }",
            args("old_hp/new_hp/delta", "старое, новое, разница", "20 15 -5")));
        MRED_COMMANDS.add(mk("on hunger_change", "Голод изменился", "on hunger_change { say \"food=$new_food\" }",
            args("old_food/new_food/food_delta", "старое, новое, разница", "20 18 -2")));
        MRED_COMMANDS.add(mk("on xp_change", "Опыт изменился", "on xp_change { say \"+$xp_delta xp\" }",
            args("old_xp/new_xp/xp_delta", "старое, новое, разница", "0 5 5")));
        MRED_COMMANDS.add(mk("on item_drop", "Игрок выбросил предмет", "on item_drop { say \"dropped $dropped\" }",
            args("old_count/new_count/dropped", "было, стало, выброшено", "64 63 1")));
        MRED_COMMANDS.add(mk("on gamemode_change", "Смена режима игры", "on gamemode_change { say \"$old_gamemode → $new_gamemode\" }",
            args("old_gamemode/new_gamemode", "было, стало", "survival creative")));

        // ---- события сервера (только singleplayer) ----
        MRED_COMMANDS.add(mk("on block_break", "Сломан блок", "on block_break { say \"$block_id\" }",
            args("block_id", "ID блока", "minecraft:stone"),
            args("block_x/y/z", "координаты", "10 64 20")));
        MRED_COMMANDS.add(mk("on block_place", "Поставлен блок", "on block_place { say \"$block_id\" }",
            args("block_id", "ID блока", "minecraft:oak_planks")));
        MRED_COMMANDS.add(mk("on block_interact", "Клик по блоку", "on block_interact { say \"clicked $block_id\" }",
            args("block_id", "ID блока", "minecraft:chest")));
        MRED_COMMANDS.add(mk("on entity_kill", "Убийство моба", "on entity_kill { say \"killed $entity_id\" }",
            args("entity_id", "ID сущности", "minecraft:zombie")));
        MRED_COMMANDS.add(mk("on entity_hurt", "Получен урон", "on entity_hurt { say \"-$damage HP\" }",
            args("damage/hp/attacker", "урон, HP, атакующий", "5 15 Zombie")));
        MRED_COMMANDS.add(mk("on player_death", "Смерть игрока", "on player_death { say \"died at $x $y $z\" }",
            args("death_cause/killer", "причина, убийца", "zombie Zombie")));
        MRED_COMMANDS.add(mk("on respawn", "Возрождение", "on respawn { say \"respawned\" }"));
        MRED_COMMANDS.add(mk("on item_pickup", "Подобран предмет", "on item_pickup { say \"picked $item_id\" }",
            args("item_id/count", "ID и количество", "minecraft:diamond 1")));
        MRED_COMMANDS.add(mk("on item_crafted", "Скрафчен предмет", "on item_crafted { say \"crafted $item_id\" }",
            args("item_id/count", "ID и количество", "minecraft:stick 4")));
        MRED_COMMANDS.add(mk("on dimension_change", "Смена измерения", "on dimension_change { say \"$from_dimension → $to_dimension\" }",
            args("from_dimension/to_dimension", "откуда, куда", "overworld the_nether")));
        MRED_COMMANDS.add(mk("on hotbar_switch", "Смена слота хотбара", "on hotbar_switch { say \"slot $to_slot\" }",
            args("from_slot/to_slot", "было, стало", "0 1")));
        MRED_COMMANDS.add(mk("on sneak_start", "Начало подкрадывания", "on sneak_start { say \"sneaking\" }"));
        MRED_COMMANDS.add(mk("on sneak_end", "Конец подкрадывания", "on sneak_end { say \"standing\" }"));
        MRED_COMMANDS.add(mk("on sprint_start", "Начало спринта", "on sprint_start { say \"running\" }"));
        MRED_COMMANDS.add(mk("on sprint_end", "Конец спринта", "on sprint_end { say \"stopped\" }"));
        MRED_COMMANDS.add(mk("on jump", "Прыжок", "on jump { say \"jumped\" }",
            args("x/y/z", "координаты", "10 64 20")));
        MRED_COMMANDS.add(mk("on use_item", "Использование предмета", "on use_item { say \"used $item_id\" }",
            args("item_id", "ID предмета", "minecraft:potion")));
        MRED_COMMANDS.add(mk("on attack", "Атака сущности", "on attack { say \"hit $target_id\" }",
            args("target_id/target_name", "ID и имя цели", "minecraft:zombie Zombie")));
        MRED_COMMANDS.add(mk("on first_join", "Первый вход игрока", "on first_join { say \"Welcome!\" }"));

        // ---- переменные ----
        MRED_COMMANDS.add(mk("$self", "Имя игрока", "say \"Hello, $self\""));
        MRED_COMMANDS.add(mk("$world", "Мир", "say \"World: $world\""));
        MRED_COMMANDS.add(mk("global", "Глобальная переменная", "set global.hp = 20",
            args("set/get", "использование", "set global.hp = 20", "say \"HP: $global.hp\"")));
    }

    private static MaredCommandRegistry.Argument args(String value, String desc, String... examples) {
        return new MaredCommandRegistry.Argument(value, desc, List.of(examples));
    }

    private static MaredCommandRegistry.CommandInfo mk(String name, String desc, String example,
                                                        MaredCommandRegistry.Argument... arguments) {
        return new MaredCommandRegistry.CommandInfo(
            name, "Mared", 0, desc, example,
            new ArrayList<>(List.of(arguments)), new ArrayList<>());
    }
}