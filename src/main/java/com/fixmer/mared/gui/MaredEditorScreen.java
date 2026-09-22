package com.fixmer.mared.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.script.MaredEventRegistry;
import com.fixmer.mared.script.MaredLang;
import com.fixmer.mared.script.MaredPersistentLoader;
import com.fixmer.mared.script.MaredPersistentStorage;
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

    public static final int BG            = 0xFF0A0A10;
    public static final int PANEL_BASE    = 0xFF14141C;
    public static final int PANEL_RAISED  = 0xFF1A1A24;
    public static final int PANEL_SUNKEN  = 0xFF101018;
    public static final int EDITOR_BG     = 0xFF0E0E16;
    public static final int TAB_STRIP_BG  = 0xFF0F0F14;
    public static final int INFO_BG       = 0xFF1A1A24;

    private static final int TEXT         = 0xFFFFFFFF;
    private static final int TEXT_DIM     = 0xFFAAAAAA;

    private final MaredLogPanel          logPanel     = new MaredLogPanel();
    private final MaredEditorToolbar     toolbar      = new MaredEditorToolbar();
    private final MaredEditorSidebar     sidebar      = new MaredEditorSidebar();
    private final MaredEditorInfoPanel   infoPanel    = new MaredEditorInfoPanel();
    private final MaredEditorBindsPanel  bindsPanel   = new MaredEditorBindsPanel();

    private String openTab = "scripts";
    private MaredEditorLayout.SidebarState sidebarState = MaredEditorLayout.SidebarState.CLOSED;
    private boolean logCollapsed = false;
    private String lastSavedText = "";

    private final MaredUi.DragState drag = new MaredUi.DragState();
    private MaredMultiLineEditBox editor;

    private static List<MaredCommandRegistry.CommandInfo> MRED_COMMANDS = null;

    // Позиция мыши (для Ctrl+C/Ctrl+A приоритетов)
    private double lastMouseX = -1;
    private double lastMouseY = -1;

    public MaredEditorScreen() { super(Component.literal("Mared Editor")); }

    @Override
    protected void init() {
        super.init();

        MaredEditorLayout.update(this.width, this.height);
        MaredSettings.load();
        MaredLang.reload();
        // FIX 0.2.4: перезагружаем справочник команд после смены языка —
        // иначе описания остаются на старом языке (они кэшируются в CommandInfo).
        MaredCommandRegistry.reload();
        ensureMredCommands();

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

    private void updateSidebarFilterBox() {
        EditBox current = sidebar.commandFilterBox();
        if (current != null) {
            removeWidget(current);
            sidebar.setCommandFilterBox(null);
        }

        EditBox box = sidebar.ensureFilterBox(this.font, MaredEditorLayout.INSTANCE,
            openTab, sidebarState, logCollapsed);
        if (box != null && !children().contains(box)) {
            addRenderableWidget(box);
        }
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
        if (box != null && !children().contains(box)) {
            addRenderableWidget(box);
        }
    }

    /** FIX 0.2.4: синхронизация searchBox в логе с widgets этого экрана. */
    private void updateLogSearchBox() {
        EditBox current = logPanel.searchBox();
        if (current != null) {
            removeWidget(current);
        }

        int logTop = MaredEditorLayout.logTop(logCollapsed);
        int logW = MaredEditorLayout.logWidth();
        EditBox box = logPanel.ensureSearchBox(this.font, 0, logTop, logW);
        if (box != null && !children().contains(box)) {
            addRenderableWidget(box);
            // Если поиск только что открылся — фокусируемся
            box.setFocused(true);
        }
    }

    private void rebuildEditor() {
        if (editor != null) { removeWidget(editor); editor = null; }
        if (MaredEditorTabs.isSupported(openTab)) {
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

    private int sidebarWidth() {
        return MaredEditorLayout.sidebarWidth(sidebarState,
            MaredEditorTabs.isSupported(openTab));
    }

    private int infoWidth() {
        if (!"commands".equals(openTab) || sidebar.selectedCommandInfo() == null) return 0;
        return MaredEditorLayout.infoWidth(infoPanel.isCollapsed(), true);
    }

    private int accentTop() {
        int[] c = MaredEditorTabs.colorsFor(openTab);
        return c[0];
    }

    private int accentBottom() {
        int[] c = MaredEditorTabs.colorsFor(openTab);
        return c[1];
    }

    private boolean isMaredInfo() {
        MaredCommandRegistry.CommandInfo ci = sidebar.selectedCommandInfo();
        return ci != null && "Mared".equals(ci.category);
    }

    private static int selColor(int a) {
        return 0xFF000000 | ((a >> 16 & 0xFF) / 3 << 16)
                         | ((a >> 8  & 0xFF) / 3 << 8)
                         | ((a       & 0xFF) / 3);
    }

    private void onNew() {
        if ("scripts".equals(openTab)) {
            Minecraft.getInstance().setScreen(new MaredNameDialog(this, "New script",
                (name, persistent) -> {
                    if (MaredScriptStorage.createScript(name)) {
                        addLog(MaredLang.format("mared.log.info.created_script", name));
                        sidebar.setSelectedFile(name);
                        sidebar.reloadFiles(openTab);
                        rebuildEditor();
                    } else addLog(MaredLang.format("mared.log.error.failed_create", name));
                },
                MaredEditorTabs.SCRIPTS_TOP, false));
        } else if ("commands".equals(openTab)) {
            Minecraft.getInstance().setScreen(new MaredNameDialog(this, "New command file",
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
                MaredEditorTabs.COMMANDS_TOP, true));
        }
    }

    private void onImport() { addLog(MaredLang.get("mared.log.warn.import")); }

    private void onDelete(String fileName) {
        if (fileName == null) { addLog(MaredLang.get("mared.log.warn.no_file")); return; }
        final boolean commandsTab = "commands".equals(openTab);
        String type = "scripts".equals(openTab) ? "script" : "command file";
        final boolean wasPersistent = commandsTab && MaredPersistentStorage.isPersistent(fileName);
        Minecraft.getInstance().setScreen(new MaredConfirmDialog(this,
            "Delete " + type + "?", "\"" + fileName + "\" will be deleted.",
            () -> {
                boolean ok = "scripts".equals(openTab)
                    ? MaredScriptStorage.deleteScript(fileName)
                    : MaredCommandStorage.deleteCommand(fileName);
                if (ok) {
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
                } else addLog(MaredLang.format("mared.log.error.failed_delete", fileName));
            }));
    }

    private void onDelete() { onDelete(sidebar.selectedFile()); }

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

    private void runScript() {
        String text = editor.getValue();
        if (text == null || text.trim().isEmpty()) {
            addLog(MaredLang.get("mared.log.warn.script_empty")); return;
        }
        boolean isPersistent = text.startsWith("#persistent");
        if (isPersistent) {
            int nl = text.indexOf('\n');
            text = (nl >= 0) ? text.substring(nl + 1) : "";
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) { addLog(MaredLang.get("mared.log.error.server_unavailable")); return; }
        ServerPlayer initiator = mc.player != null
            ? server.getPlayerList().getPlayer(mc.player.getUUID()) : null;

        List<MaredScriptCommand> commands;
        try { commands = MaredScriptParser.parse(text); }
        catch (MaredScriptParser.ParseException e) {
            addLog(MaredLang.format("mared.log.mared.parse_error", e.getMessage())); return;
        } catch (RuntimeException e) {
            addLog("[mared parse] " + e.getClass().getSimpleName() + ": " + e.getMessage()); return;
        }
        if (commands.isEmpty()) { addLog(MaredLang.get("mared.log.warn.script_empty")); return; }
        addLog(MaredLang.format("mared.log.run.script", sidebar.selectedFile(), commands.size()));

        MaredScriptContext ctx = new MaredScriptContext(initiator, server, this::addLog);
        ctx.setPersistent(isPersistent);
        ctx.refreshPlayerData();
        MaredScriptRunner.start(new MaredScriptExecutor(ctx, commands));
    }

    private void runCommandFile() {
        String text = editor.getValue();
        if (text == null || text.trim().isEmpty()) {
            addLog(MaredLang.get("mared.log.warn.file_empty")); return;
        }
        boolean fileIsPersistent = text.startsWith("#persistent");
        if (fileIsPersistent) {
            int nl = text.indexOf('\n');
            text = (nl >= 0) ? text.substring(nl + 1) : "";
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) {
            addLog(MaredLang.get("mared.log.error.player_unavailable")); return;
        }
        addLog(MaredLang.format("mared.log.run.file", sidebar.selectedFile()));

        List<String> blockBuffer = new ArrayList<>();
        int braceDepth = 0, cmdCount = 0, blockCount = 0, blockStartLine = 0;
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
                    runMaredBlock(blockBuffer, blockStartLine, fileIsPersistent);
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

    private static int countChar(String s, char c) {
        int n = 0; boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') inString = !inString;
            if (!inString && ch == c) n++;
        }
        return n;
    }

    private void runMaredBlock(List<String> blockLines, int startLine, boolean parentPersistent) {
        String text = String.join("\n", blockLines);
        boolean isPersistent = parentPersistent || text.startsWith("#persistent");
        if (text.startsWith("#persistent")) {
            int nl = text.indexOf('\n');
            text = (nl >= 0) ? text.substring(nl + 1) : "";
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) { addLog(MaredLang.get("mared.log.error.server_unavailable")); return; }
        ServerPlayer initiator = mc.player != null
            ? server.getPlayerList().getPlayer(mc.player.getUUID()) : null;

        List<MaredScriptCommand> commands;
        try { commands = MaredScriptParser.parse(text); }
        catch (MaredScriptParser.ParseException e) {
            addLog(MaredLang.format("mared.log.mared.parse_error", e.getMessage())); return;
        } catch (RuntimeException e) {
            addLog("[mared parse] " + e.getClass().getSimpleName() + ": " + e.getMessage()); return;
        }
        if (commands.isEmpty()) {
            addLog(MaredLang.format("mared.log.mared.empty", startLine)); return;
        }
        addLog(MaredLang.format("mared.log.mared.block_ready", startLine, commands.size()));

        MaredScriptContext ctx = new MaredScriptContext(initiator, server, this::addLog);
        ctx.setPersistent(isPersistent);
        ctx.refreshPlayerData();
        MaredScriptRunner.start(new MaredScriptExecutor(ctx, commands));
    }

    private void addLog(String line) { logPanel.add(line); }

    // ============================================================
    //  Input
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

        int tabIdx = MaredEditorTabs.hitTab(mx, my);
        if (tabIdx >= 0) {
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

        if (MaredEditorTabs.isSupported(openTab)
            && (sidebarState == MaredEditorLayout.SidebarState.STRIP
             || sidebarState == MaredEditorLayout.SidebarState.FULL)) {

            MaredEditorLayout.SidebarState prevState = sidebarState;

            boolean clickedSidebar = sidebar.handleClick(
                MaredEditorLayout.INSTANCE, openTab, mx, my, sidebarState, drag,
                this::onNew, this::onDelete, logCollapsed);

            if (clickedSidebar) {
                boolean needRebuild = false;

                if (sidebar.shouldToggleSidebar) {
                    sidebar.shouldToggleSidebar = false;
                    if (prevState == MaredEditorLayout.SidebarState.STRIP) {
                        sidebarState = MaredEditorLayout.SidebarState.FULL;
                    } else if (prevState == MaredEditorLayout.SidebarState.FULL) {
                        sidebarState = MaredEditorLayout.SidebarState.STRIP;
                    }
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
        }

        if (super.mouseClicked(mx, my, button)) return true;

        if ("commands".equals(openTab) && sidebar.selectedCommandInfo() != null
            && my >= MaredEditorLayout.TOOLBAR_H() + MaredEditorLayout.PAD()) {
            int editorBottom = MaredEditorLayout.editorBottom(logCollapsed);
            if (infoPanel.mouseClicked(MaredEditorLayout.INSTANCE, this.font,
                sidebar.selectedCommandInfo(), mx, my, editorBottom, drag)) {
                rebuildEditor();
                updateInfoFilterBox();
                return true;
            }
        }

        int logTop = MaredEditorLayout.logTop(logCollapsed);
        int logW = MaredEditorLayout.logWidth();

        if (!logCollapsed && my >= logTop && mx >= logW) {
            if (bindsPanel.mouseClicked(mx, my, MaredEditorLayout.INSTANCE, logCollapsed)) return true;
        }

        if (my >= logTop) {
            // FIX 0.2.4: при клике на кнопку поиска — открыть/закрыть searchBox
            boolean wasSearchOpen = logPanel.isSearchOpen();
            if (logPanel.mouseClicked(mx, my, button, 0, logTop, logW,
                MaredEditorLayout.logHeight(logCollapsed), this.font)) {
                if (wasSearchOpen != logPanel.isSearchOpen()) {
                    updateLogSearchBox();
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & 2) != 0;
        boolean shift = (modifiers & 1) != 0;
        boolean editorFocused = editor != null && editor.isFocused();
        boolean editorHasSelection = editor != null && editor.hasSelection();
        EditBox searchBox = logPanel.searchBox();
        boolean searchFocused = searchBox != null && searchBox.isFocused();

        // Если фокус на поиске — Esc закрывает, обычные клавиши отдаём EditBox
        if (searchFocused && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            logPanel.closeSearch();
            updateLogSearchBox();
            return true;
        }
        if (searchFocused && !ctrl) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // Мышь над логом?
        int logTop = MaredEditorLayout.logTop(logCollapsed);
        int logW = MaredEditorLayout.logWidth();
        boolean mouseOverLog = !logCollapsed
            && lastMouseX >= 0 && lastMouseY >= 0
            && lastMouseX < logW
            && lastMouseY >= logTop
            && lastMouseY < MaredEditorLayout.screenH();

        // FIX 0.2.4: Ctrl+F — открыть/закрыть поиск по логу
        if (ctrl && keyCode == GLFW.GLFW_KEY_F) {
            if (!logPanel.isSearchOpen()) {
                openLogSearch();
            } else if (searchFocused) {
                logPanel.closeSearch();
                updateLogSearchBox();
            } else if (searchBox != null) {
                searchBox.setFocused(true);
            }
            return true;
        }

        // Ctrl+C — в редактор только если фокус + выделение + мышь НЕ над логом
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            if (editorFocused && editorHasSelection && !mouseOverLog) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            logPanel.copySelectedOrAll();
            return true;
        }

        // Ctrl+A — в лог, если мышь над логом; иначе в редактор
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            if (editorFocused && !mouseOverLog) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            logPanel.selectAll();
            return true;
        }

        // Ctrl+S / Ctrl+Shift+S — экспорт лога / сохранение
        if (ctrl && keyCode == GLFW.GLFW_KEY_S && !editorFocused) {
            if (shift) {
                onSave();
                return true;
            }
            logPanel.exportLog();
            return true;
        }

        // Delete — удаление выбранного файла
        if (keyCode == GLFW.GLFW_KEY_DELETE
            && sidebar.selectedFile() != null && !editorFocused) {
            onDelete();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Открывает поиск в логе и фокусирует поле. */
    private void openLogSearch() {
        logPanel.setCollapsed(false);
        logPanel.setSearchOpen(true);
        updateLogSearchBox();
        EditBox box = logPanel.searchBox();
        if (box != null) box.setFocused(true);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        this.lastMouseX = mx;
        this.lastMouseY = my;

        int logTop = MaredEditorLayout.logTop(logCollapsed);
        int logW = MaredEditorLayout.logWidth();

        logPanel.tickAutoScroll();

        if (my >= logTop) {
            if (logPanel.mouseDragged(mx, my, button, dx, dy, 0, logTop, logW,
                MaredEditorLayout.logHeight(logCollapsed), this.font)) return true;
        }
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

        if (!logCollapsed
            && bindsPanel.mouseScrolled(mx, my, dy, MaredEditorLayout.INSTANCE, logCollapsed)) return true;

        if (logPanel.mouseScrolled(mx, my, dy, 0, logTop, logW,
            MaredEditorLayout.logHeight(logCollapsed))) return true;

        if ("commands".equals(openTab) && sidebar.selectedCommandInfo() != null
            && !infoPanel.isCollapsed()
            && mx >= MaredEditorLayout.infoPanelX(infoPanel.isCollapsed())) {
            infoPanel.scroll().wheel(dy, 5); return true;
        }

        if (MaredEditorTabs.isSupported(openTab)
            && sidebarState == MaredEditorLayout.SidebarState.FULL) {
            if (sidebar.handleScroll(MaredEditorLayout.INSTANCE, openTab, mx, my, dy, sidebarState, logCollapsed))
                return true;
        }

        if (editor != null && editor.isMouseOver(mx, my)
            && editor.mouseScrolled(mx, my, dx, dy)) return true;

        return super.mouseScrolled(mx, my, dx, dy);
    }

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

        // Автоскролл ДО рендера
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

        if (MaredEditorTabs.isSupported(openTab)
            && sidebarState == MaredEditorLayout.SidebarState.STRIP) {
            sidebar.drawStrip(g, this.font, MaredEditorLayout.INSTANCE,
                mouseX, mouseY, accentTop(), accentBottom());
        }
        if (MaredEditorTabs.isSupported(openTab)
            && sidebarState == MaredEditorLayout.SidebarState.FULL) {
            sidebar.drawFull(g, this.font, MaredEditorLayout.INSTANCE,
                openTab, mouseX, mouseY, accentTop(), accentBottom(), selColor(accentTop()), logCollapsed);
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

        // Автоскролл ПОСЛЕ рендера
        logPanel.tickAutoScroll();

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

        if (editable) {
            MaredUi.rect(g, frameX, frameY, frameX + frameW, frameY + frameH, EDITOR_BG);
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
        } else {
            MaredUi.rect(g, frameX, frameY, frameX + frameW, frameY + frameH, EDITOR_BG);
            MaredUi.text(g, this.font,
                MaredLang.format("mared.ui.under_development", MaredEditorTabs.title(openTab)),
                frameX + 8, frameY + 8, TEXT_DIM);
        }
    }

    private static void ensureMredCommands() {
        if (MRED_COMMANDS != null) return;
        MRED_COMMANDS = new ArrayList<>();

        MRED_COMMANDS.add(mk("say", "Отправить сообщение в чат",
            "say \"Hello, $self\"",
            args("text", "текст сообщения",
                "say \"Hello!\"", "say $greeting")));
        MRED_COMMANDS.add(mk("wait", "Пауза в тиках/секундах",
            "wait 3 seconds",
            args("time", "время ожидания", "wait 3 seconds", "wait 100 ticks")));
        MRED_COMMANDS.add(mk("set", "Присвоить переменную",
            "set count = 5",
            args("name = value", "имя и значение",
                "set count = 5", "set name = \"Steve\""),
            args("global.name", "глобальная переменная",
                "set global.hp = 20", "set global.count = $global.count + 1")));
        MRED_COMMANDS.add(mk("array", "Объявить массив",
            "array items = [1, 2, 3]",
            args("name = [values]", "массив значений",
                "array items = [1, 2, 3]", "array names = [\"a\", \"b\"]")));
        MRED_COMMANDS.add(mk("give", "Выдать предмет",
            "give @s diamond 5",
            args("target", "получатель", "give @s diamond 5"),
            args("item", "предмет", "give @s minecraft:diamond 5"),
            args("count", "количество", "give @s diamond 64")));
        MRED_COMMANDS.add(mk("bind", "Привязать клавишу",
            "bind R { say \"Hi\" }",
            args("key", "клавиша", "bind R { say \"Hi\" }"),
            args("add", "добавить действие", "bind R add { say \"second\" }"),
            args("clear", "очистить бинд", "bind Q clear"),
            args("block", "заблокировать ваниль", "bind W block { say \"locked\" }"),
            args("hold", "удержание", "bind W hold { say \"held\" }"),
            args("release", "отпускание", "bind W release { say \"released\" }")));
        MRED_COMMANDS.add(mk("on", "Обработчик события",
            "on right_click { say \"clicked\" }",
            args("event", "тип события", "on right_click { ... }"),
            args("add", "добавить", "on right_click add { ... }"),
            args("replace", "заменить", "on right_click replace { ... }")));
        MRED_COMMANDS.add(mk("block", "Заблокировать клавишу", "block W",
            args("key", "клавиша", "block W", "block Space")));
        MRED_COMMANDS.add(mk("unblock", "Разблокировать клавишу", "unblock W",
            args("key", "клавиша", "unblock W", "unblock Space")));
        MRED_COMMANDS.add(mk("toggle", "Переключить блокировку", "toggle W",
            args("key", "клавиша", "toggle W", "toggle MouseMove")));
        MRED_COMMANDS.add(mk("mc", "Ванильная команда", "mc time set day",
            args("command", "команда", "mc time set day", "mc weather clear")));
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
        MRED_COMMANDS.add(mk("func", "Объявить функцию",
            "func greet($name) { say \"Hi, $name\" }",
            args("name(params)", "имя и параметры", "func greet($name) { ... }")));
        MRED_COMMANDS.add(mk("call", "Вызвать функцию", "call greet(\"Steve\")",
            args("name(args)", "имя и аргументы", "call greet(\"Steve\")"),
            args("return", "присвоить результат", "set x = call add(2, 3)")));
        MRED_COMMANDS.add(mk("return", "Вернуть значение", "return $a + $b",
            args("expression", "выражение", "return 5", "return call f($n - 1)")));
        MRED_COMMANDS.add(mk("exit", "Остановить скрипт", "exit",
            args("", "", "exit")));
        MRED_COMMANDS.add(mk("print", "Вывести в лог", "print \"Hello\"",
            args("text", "текст", "print \"value: $x\"")));
        MRED_COMMANDS.add(mk("debug", "Вывести значение", "debug $x",
            args("expression", "выражение", "debug $x", "debug $x + 1")));
        MRED_COMMANDS.add(mk("assert", "Проверка", "assert $x > 10 \"must be big\"",
            args("condition [message]", "условие и сообщение", "assert $x > 10 \"x too small\"")));
        MRED_COMMANDS.add(mk("{}", "Блок команд", "{ say \"Hello\" }",
            args("open", "открыть", "{"),
            args("close", "закрыть", "}")));
        MRED_COMMANDS.add(mk("$self", "Имя игрока", "say \"Hello, $self\"",
            args("", "", "say \"Hello, $self\"")));
        MRED_COMMANDS.add(mk("$world", "Мир", "say \"World: $world\"",
            args("", "", "say \"World: $world\"")));
        MRED_COMMANDS.add(mk("global", "Глобальная переменная",
            "set global.hp = 20",
            args("set/get", "использование",
                "set global.hp = 20", "say \"HP: $global.hp\"")));
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

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Пусто — не даём MC рисовать blur.
    }
}