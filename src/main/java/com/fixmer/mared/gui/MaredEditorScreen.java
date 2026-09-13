package com.fixmer.mared.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.script.MaredBindRegistry;
import com.fixmer.mared.script.MaredLang;
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
    private static final int BG          = 0xFF0E0E14;
    private static final int TAB_STRIP   = 0xFF181822;
    private static final int TOOLBAR_BG  = 0xFF20202C;
    private static final int PANEL       = 0xFF1E1E2A;
    private static final int PANEL_STRIP = 0xFF252530;
    private static final int EDITOR_BG   = 0xFF141420;
    private static final int LOG_BG      = 0xFF1A1A24;
    private static final int INFO_BG     = 0xFF1A1A26;
    private static final int TEXT        = 0xFFFFFFFF;
    private static final int TEXT_DIM    = 0xFFAAAAAA;
    private static final int TEXT_WARN   = 0xFFFFAA00;
    private static final int ITEM_HOVER  = 0xFF2E2E3E;
    private static final int ITEM_NORMAL = 0xFF252530;
    private static final int SECTION_BG  = 0xFF2A2A38;
    private static final int DIVIDER     = 0xFF333344;
    private static final int BTN_BG      = 0xFF2D2D2D;
    private static final int BTN_HOVER   = 0xFF3E3E42;
    private static final int FILTER_BG   = 0xFF0A0A10;
    private static final int DANGER      = 0xFFFF4444;

    private static final int SCRIPTS_TOP  = 0xFFFF55FF;
    private static final int SCRIPTS_BOT  = 0xFFDD33DD;
    private static final int COMMANDS_TOP = 0xFFFFAA00;
    private static final int COMMANDS_BOT = 0xFFFF8800;
    private static final int NPC_TOP      = 0xFF55FF55;
    private static final int NPC_BOT      = 0xFF33DD33;
    private static final int EVENTS_TOP   = 0xFF55AAFF;
    private static final int EVENTS_BOT   = 0xFF3388DD;
    private static final int QUESTS_TOP   = 0xFFFF5555;
    private static final int QUESTS_BOT   = 0xFFDD3333;

    private static final int MRED_COLOR = 0xFFFF3333;

    private static final int TAB_W = 30, TAB_H = 30, TOOLBAR_H = 22, PAD = 6;
    private static final int LOG_H = 110, LOG_H_COLLAPSED = 20, LOG_HEADER = 16, LOG_LINE = 10;
    private static final int STRIP_W = 36, FULL_W = 220;
    private static final int INFO_W = 280, INFO_COLLAPSED_W = 24;
    private static final int BTN_SZ = 18, ITEM_H = 14, SCROLLBAR_W = 6;
    private static final int GAP_EDITOR_LOG = 6, EDITOR_HDR = 18;
    private static final int FILE_SECTION_H = 140, FILTER_H = 14, FILE_DEL_SZ = 12;
    private static final int TOGGLE_W = 22, TOGGLE_H = 12;
    private static final int ACTIVE_BINDS_W = 220;
    private static final int BIND_DEL_SZ = 10;

    private enum SidebarState { CLOSED, STRIP, FULL }

    private SidebarState sidebarState = SidebarState.CLOSED;
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
    private boolean showMaredCommands = false;

    private final MaredUi.ScrollArea fileScroll = new MaredUi.ScrollArea();
    private final MaredUi.ScrollArea cmdScroll  = new MaredUi.ScrollArea();
    private final MaredUi.PixelScroll infoScroll = new MaredUi.PixelScroll();
    private final MaredUi.ScrollArea logScroll  = new MaredUi.ScrollArea();
    private final MaredUi.DragState drag = new MaredUi.DragState();

    private int activeBindsScrollOffset = 0;

    private String lastSavedText = "";

    private final List<AbstractWidget> toolButtons = new ArrayList<>();
    private MaredMultiLineEditBox editor;

    /** Ленивая инициализация — заполняется при первом обращении. */
    private static List<MaredCommandRegistry.CommandInfo> MRED_COMMANDS = null;

    /** Заполнить список Mared-команд с локализованными описаниями. */
    private static void ensureMredCommands() {
        if (MRED_COMMANDS != null) return;
        MRED_COMMANDS = new ArrayList<>();

        MRED_COMMANDS.add(mk("say",
            MaredLang.get("mared.help.say.desc"),
            "say \"Hello, $self\"",
            args("text", MaredLang.get("mared.help.say.arg.text"), "say \"Hello!\"", "say $greeting")));

        MRED_COMMANDS.add(mk("wait",
            MaredLang.get("mared.help.wait.desc"),
            "wait 3 seconds",
            args("time", MaredLang.get("mared.help.wait.arg.time"), "wait 3 seconds", "wait 100 ticks")));

        MRED_COMMANDS.add(mk("set",
            MaredLang.get("mared.help.set.desc"),
            "set count = 5",
            args("name = value", MaredLang.get("mared.help.set.arg.name"), "set count = 5", "set name = \"Steve\"")));

        MRED_COMMANDS.add(mk("array",
            MaredLang.get("mared.help.array.desc"),
            "array items = [1, 2, 3]",
            args("name = [values]", MaredLang.get("mared.help.array.arg.name"),
                "array items = [1, 2, 3]", "array names = [\"a\", \"b\"]")));

        MRED_COMMANDS.add(mk("give",
            MaredLang.get("mared.help.give.desc"),
            "give @s diamond 5",
            args("target", MaredLang.get("mared.help.give.arg.target"), "give @s diamond 5"),
            args("item", MaredLang.get("mared.help.give.arg.item"), "give @s minecraft:diamond 5"),
            args("count", MaredLang.get("mared.help.give.arg.count"), "give @s diamond 64")));

        MRED_COMMANDS.add(mk("bind",
            MaredLang.get("mared.help.bind.desc"),
            "bind R { say \"Hi\" }",
            args("key", MaredLang.get("mared.help.bind.arg.key"), "bind R { say \"Hi\" }"),
            args("add", MaredLang.get("mared.help.bind.arg.add"), "bind R add { say \"second\" }"),
            args("replace", MaredLang.get("mared.help.bind.arg.replace"), "bind R replace { say \"new\" }"),
            args("clear", MaredLang.get("mared.help.bind.arg.clear"), "bind Q clear"),
            args("block", MaredLang.get("mared.help.bind.arg.block"), "bind W block { say \"locked\" }")));

        MRED_COMMANDS.add(mk("if",
            MaredLang.get("mared.help.if.desc"),
            "if $count > 5 { say \"Many\" }",
            args("condition", MaredLang.get("mared.help.if.arg.condition"), "if $count > 5 { say \"Many\" }"),
            args("block", MaredLang.get("mared.help.if.arg.block"), "if $name == \"Steve\" { say \"Hi\" }"),
            args("elif / else", MaredLang.get("mared.help.if.arg.elif"), "elif $count == 5 { say \"Equal\" } else { say \"Less\" }")));

        MRED_COMMANDS.add(mk("repeat",
            MaredLang.get("mared.help.repeat.desc"),
            "repeat 3 { say \"Tick\" }",
            args("N", MaredLang.get("mared.help.repeat.arg.n"), "repeat 5 { say \"Tick\" }"),
            args("block", MaredLang.get("mared.help.repeat.arg.block"), "repeat 3 { wait 1 second }")));

        MRED_COMMANDS.add(mk("for",
            MaredLang.get("mared.help.for.desc"),
            "for $i = 1 to 5 { say $i }",
            args("var = from to to", MaredLang.get("mared.help.for.arg.range"), "for $i = 1 to 5 { say $i }")));

        MRED_COMMANDS.add(mk("while",
            MaredLang.get("mared.help.while.desc"),
            "while $n < 3 { set n = $n + 1 }",
            args("condition", MaredLang.get("mared.help.while.arg.condition"), "while $n < 3 { set n = $n + 1 }")));

        MRED_COMMANDS.add(mk("break",
            MaredLang.get("mared.help.break.desc"),
            "break",
            args("", MaredLang.get("mared.help.break.arg.empty"), "repeat 10 { break }")));

        MRED_COMMANDS.add(mk("continue",
            MaredLang.get("mared.help.continue.desc"),
            "continue",
            args("", MaredLang.get("mared.help.continue.arg.empty"), "if $i == 3 { continue }")));

        MRED_COMMANDS.add(mk("func",
            MaredLang.get("mared.help.func.desc"),
            "func greet($name) { say \"Hi, $name\" }",
            args("name(params)", MaredLang.get("mared.help.func.arg.name"), "func greet($name) { say \"Hi\" }")));

        MRED_COMMANDS.add(mk("call",
            MaredLang.get("mared.help.call.desc"),
            "call greet(\"Steve\")",
            args("name(args)", MaredLang.get("mared.help.call.arg.name"), "call greet(\"Steve\")")));

        MRED_COMMANDS.add(mk("debug",
            MaredLang.get("mared.help.debug.desc"),
            "debug $x",
            args("expression", MaredLang.get("mared.help.debug.arg.expr"), "debug $x")));

        MRED_COMMANDS.add(mk("assert",
            MaredLang.get("mared.help.assert.desc"),
            "assert $x > 10 \"must be big\"",
            args("condition [message]", MaredLang.get("mared.help.assert.arg.condition"), "assert $x > 10 \"x too small\"")));

        MRED_COMMANDS.add(mk("{}",
            MaredLang.get("mared.help.block.desc"),
            "{ say \"Hello\" }",
            args("open", MaredLang.get("mared.help.block.arg.open"), "{"),
            args("close", MaredLang.get("mared.help.block.arg.close"), "}"),
            args("inside", MaredLang.get("mared.help.block.arg.inside"), "say \"Hi\"")));

        MRED_COMMANDS.add(mk("$self",
            MaredLang.get("mared.help.self.desc"),
            "say \"Hello, $self\"",
            args("", MaredLang.get("mared.help.self.arg.empty"), "say \"Hello, $self\"")));

        MRED_COMMANDS.add(mk("$world",
            MaredLang.get("mared.help.world.desc"),
            "say \"World: $world\"",
            args("", MaredLang.get("mared.help.world.arg.empty"), "say \"World: $world\"")));

        MRED_COMMANDS.add(mk("help",
            MaredLang.get("mared.help.help.desc"),
            "help",
            args("hybrid mode", MaredLang.get("mared.help.help.arg.hybrid"), "/give @s diamond 5", "{ say \"Hi\" }"),
            args("variables", MaredLang.get("mared.help.help.arg.variables"), "set count = 5"),
            args("special vars", MaredLang.get("mared.help.help.arg.special"), "say \"Hi, $self\""),
            args("conditions", MaredLang.get("mared.help.help.arg.conditions"), "if $count > 5 { say \"5+\" }"),
            args("loops", MaredLang.get("mared.help.help.arg.loops"), "repeat 3 { say \"Tick\" }"),
            args("binds", MaredLang.get("mared.help.help.arg.binds"), "bind R { say \"Hi\" }"),
            args("expressions", MaredLang.get("mared.help.help.arg.expr"), "${2 + 3}"),
            args("functions", MaredLang.get("mared.help.help.arg.funcs"), "func hi($n) { say $n }"),
            args("arrays", MaredLang.get("mared.help.help.arg.arrays"), "$items.push(4)"),
            args("strings", MaredLang.get("mared.help.help.arg.strings"), "$name.upper()"),
            args("hotkeys", MaredLang.get("mared.help.help.arg.hotkeys"), "Ctrl+S")));
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

    public MaredEditorScreen() { super(Component.literal("Mared Editor")); }

    @Override
    protected void init() {
        super.init();
        MaredSettings.load();
        MaredLang.reload();
        ensureMredCommands();
        toolButtons.clear();
        reloadFiles();
        refreshCommands();
        buildToolButtons();
        createEditor();
        createFilterBoxes();
        updateToolButtonsVisibility();
    }

    private void buildToolButtons() {
        int y = PAD + 1;
        int right = this.width - PAD;

        String[][] defs = {
            {MaredLang.get("mared.ui.close"),    "50", "FF5555"},
            {MaredLang.get("mared.ui.run"),      "40", "55FF55"},
            {MaredLang.get("mared.ui.save"),     "60", "55AAFF"},
            {MaredLang.get("mared.ui.delete"),   "60", "FF4444"},
            {MaredLang.get("mared.ui.import"),   "60", "FFAA00"},
            {MaredLang.get("mared.ui.settings"), "70", "AAAAFF"},
            {MaredLang.get("mared.ui.new"),      "50", "FF55FF"}
        };
        Runnable[] actions = {
            this::onClose, this::onRun, this::onSave, this::onDelete,
            this::onImport, this::onSettings, this::onNew
        };

        for (int i = 0; i < defs.length; i++) {
            int w = Integer.parseInt(defs[i][1]);
            int color = 0xFF000000 | Integer.parseInt(defs[i][2], 16);
            right -= w;
            Runnable action = actions[i];
            toolButtons.add(addRenderableWidget(new MaredCompactButton(
                right, y, w, 18, Component.literal(defs[i][0]), color, action)));
            right -= 4;
        }
    }

    private void updateToolButtonsVisibility() {
        boolean active = isScripts() || isCommands();
        boolean commands = isCommands();
        for (int i = 0; i < toolButtons.size(); i++) {
            AbstractWidget w = toolButtons.get(i);
            boolean alwaysVisible = (i == 0);
            boolean onlyCommands  = (i == 5);
            if (onlyCommands) {
                w.visible = w.active = commands;
            } else {
                w.visible = w.active = alwaysVisible || active;
            }
        }
    }

    private void createFilterBoxes() {
        if (commandFilterBox != null) { removeWidget(commandFilterBox); commandFilterBox = null; }
        if (isCommands() && sidebarState == SidebarState.FULL) {
            int listX = TAB_W + 6;
            int listW = FULL_W - 12 - SCROLLBAR_W;
            int ay = arrowY();
            int listY = ay + BTN_SZ + 8;
            int filesH = Math.min(FILE_SECTION_H, sidebarBottom() - listY - 6);
            int filterY = listY + filesH + 4 + 14;
            commandFilterBox = new EditBox(this.font, listX + 4, filterY + 2, listW - 8, FILTER_H - 2,
                Component.literal(MaredLang.get("mared.ui.filter")));
            commandFilterBox.setValue(commandFilter);
            commandFilterBox.setResponder(s -> { commandFilter = s; refreshCommands(); });
            commandFilterBox.setMaxLength(64);
            commandFilterBox.setBordered(false);
            addRenderableWidget(commandFilterBox);
        }

        if (argFilterBox != null) { removeWidget(argFilterBox); argFilterBox = null; }
        if (!infoPanelCollapsed && isCommands() && selectedCommandInfo != null
            && !selectedCommandInfo.arguments.isEmpty()) {
            int infoX = infoPanelX();
            int y = infoArgFilterBoxY();
            argFilterBox = new EditBox(this.font, infoX + 5, y + 2, INFO_W - 10, FILTER_H - 2,
                Component.literal(MaredLang.get("mared.ui.filter")));
            argFilterBox.setValue(argFilter);
            argFilterBox.setResponder(s -> argFilter = s);
            argFilterBox.setMaxLength(64);
            argFilterBox.setBordered(false);
            addRenderableWidget(argFilterBox);
        }
    }

    private boolean isScripts() { return "scripts".equals(openTab); }
    private boolean isCommands() { return "commands".equals(openTab); }
    private int selColor(int a) { return 0xFF000000 | ((a >> 16 & 0xFF) / 3 << 16) | ((a >> 8 & 0xFF) / 3 << 8) | ((a & 0xFF) / 3); }
    private boolean isMaredInfo() { return selectedCommandInfo != null && "Mared".equals(selectedCommandInfo.category); }

    private int accentTop() {
        if (isCommands()) return COMMANDS_TOP;
        if (isScripts()) return SCRIPTS_TOP;
        return COMMANDS_TOP;
    }

    private int accentBottom() {
        if (isCommands()) return COMMANDS_BOT;
        if (isScripts()) return SCRIPTS_BOT;
        return COMMANDS_BOT;
    }

    private void refreshCommands() {
        ensureMredCommands();
        MaredCommandRegistry.load();
        filteredCommands.clear();
        if (showMaredCommands) {
            for (MaredCommandRegistry.CommandInfo c : MRED_COMMANDS) {
                if (commandFilter.isEmpty() || c.name.toLowerCase().startsWith(commandFilter.toLowerCase())) {
                    filteredCommands.add(c);
                }
            }
        } else {
            filteredCommands.addAll(MaredCommandRegistry.search(commandFilter));
        }
        cmdScroll.offset = 0;
    }

    private void reloadFiles() {
        fileNames.clear();
        if (isScripts()) fileNames.addAll(MaredScriptStorage.listScripts());
        else if (isCommands()) fileNames.addAll(MaredCommandStorage.listCommands());
        if (selectedFile != null && !fileNames.contains(selectedFile)) selectedFile = null;
        fileScroll.offset = 0;
    }

    private void createEditor() {
        int frameX = TAB_W + sidebarWidth() + PAD;
        int frameY = TOOLBAR_H + PAD * 2;
        int frameW = this.width - frameX - PAD - infoWidth();
        int frameH = editorBottom() - frameY - PAD;
        editor = new MaredMultiLineEditBox(
            frameX + 2, frameY + EDITOR_HDR + 2,
            frameW - 4, frameH - EDITOR_HDR - 4,
            accentTop(), this::onEditorChanged);
        loadIntoEditor();
        addRenderableWidget(editor);
    }

    private void recreateEditor() {
        if (editor != null) { removeWidget(editor); editor = null; }
        if (isScripts() || isCommands()) createEditor();
        createFilterBoxes();
    }

    private void onEditorChanged() {}

    private void loadIntoEditor() {
        if (editor == null) return;
        if (selectedFile == null) {
            editor.setValue("");
            lastSavedText = "";
            editor.setEditable(false);
            return;
        }
        String val = isScripts() ? MaredScriptStorage.readScript(selectedFile)
                                 : MaredCommandStorage.readCommand(selectedFile);
        editor.setValue(val);
        lastSavedText = val;
        editor.setEditable(true);
        editor.setFocused(true);
    }

    private void onNew() {
        if (isScripts()) {
            Minecraft.getInstance().setScreen(new MaredNameDialog(this, "New script", name -> {
                if (MaredScriptStorage.createScript(name)) {
                    addLog(MaredLang.format("mared.log.info.created_script", name));
                    selectedFile = name;
                    reloadFiles();
                    loadIntoEditor();
                } else addLog(MaredLang.format("mared.log.error.failed_create", name));
            }, SCRIPTS_TOP, false));
        } else if (isCommands()) {
            Minecraft.getInstance().setScreen(new MaredNameDialog(this, "New command file", name -> {
                if (MaredCommandStorage.createCommand(name)) {
                    addLog(MaredLang.format("mared.log.info.created_command", name));
                    selectedFile = name;
                    reloadFiles();
                    loadIntoEditor();
                } else addLog(MaredLang.format("mared.log.error.failed_create", name));
            }, COMMANDS_TOP, true));
        }
    }

    private void onImport() { addLog(MaredLang.get("mared.log.warn.import")); }
    private void onDelete() { onDelete(selectedFile); }

    private void onSettings() {
        Minecraft.getInstance().setScreen(new MaredSettingsScreen(this));
    }

    private void onDelete(String fileName) {
        if (fileName == null) { addLog(MaredLang.get("mared.log.warn.no_file")); return; }
        String type = isScripts() ? "script" : "command file";
        Minecraft.getInstance().setScreen(new MaredConfirmDialog(this,
            "Delete " + type + "?", "\"" + fileName + "\" will be deleted.",
            () -> {
                boolean ok = isScripts()
                    ? MaredScriptStorage.deleteScript(fileName)
                    : MaredCommandStorage.deleteCommand(fileName);
                if (ok) {
                    addLog(MaredLang.format("mared.log.info.deleted", fileName));
                    if (fileName.equals(selectedFile)) selectedFile = null;
                    reloadFiles();
                    loadIntoEditor();
                } else addLog(MaredLang.format("mared.log.error.failed_delete", fileName));
            }));
    }

    private boolean saveCurrentFile() {
        if (selectedFile == null || editor == null) return false;
        String content = editor.getValue();
        if (content.equals(lastSavedText)) return false;
        boolean ok = isScripts()
            ? MaredScriptStorage.writeScript(selectedFile, content)
            : MaredCommandStorage.writeCommand(selectedFile, content);
        if (ok) lastSavedText = content;
        return ok;
    }

    private void onSave() {
        if (selectedFile == null || editor == null) return;
        String content = editor.getValue();
        boolean ok = isScripts()
            ? MaredScriptStorage.writeScript(selectedFile, content)
            : MaredCommandStorage.writeCommand(selectedFile, content);
        if (ok) { lastSavedText = content; addLog(MaredLang.format("mared.log.info.saved", selectedFile)); }
        else addLog(MaredLang.format("mared.log.error.failed_save", selectedFile));
    }

    private void onRun() {
        if (selectedFile == null || editor == null) return;
        if (!editor.getValue().equals(lastSavedText)) {
            if (saveCurrentFile()) addLog(MaredLang.get("mared.log.auto_save.saved"));
            else addLog(MaredLang.get("mared.log.auto_save.failed"));
        }
        if (isScripts()) runScript();
        else if (isCommands()) runCommandFile();
    }

    private void runScript() {
        String text = editor.getValue();
        if (text == null || text.trim().isEmpty()) { addLog(MaredLang.get("mared.log.warn.script_empty")); return; }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) { addLog(MaredLang.get("mared.log.error.server_unavailable")); return; }
        ServerPlayer initiator = mc.player != null
            ? server.getPlayerList().getPlayer(mc.player.getUUID())
            : null;
        List<MaredScriptCommand> commands;
        try { commands = MaredScriptParser.parse(text); }
        catch (MaredScriptParser.ParseException e) {
            addLog(MaredLang.format("mared.log.mared.parse_error", e.getMessage()));
            return;
        }
        if (commands.isEmpty()) { addLog(MaredLang.get("mared.log.warn.script_empty")); return; }
        addLog(MaredLang.format("mared.log.run.script", selectedFile, commands.size()));
        MaredScriptContext ctx = new MaredScriptContext(initiator, server, this::addLog);
        MaredScriptRunner.start(new MaredScriptExecutor(ctx, commands));
    }

    private void runCommandFile() {
        String text = editor.getValue();
        if (text == null || text.trim().isEmpty()) { addLog(MaredLang.get("mared.log.warn.file_empty")); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) {
            addLog(MaredLang.get("mared.log.error.player_unavailable"));
            return;
        }

        addLog(MaredLang.format("mared.log.run.file", selectedFile));

        List<String> blockBuffer = new ArrayList<>();
        int braceDepth = 0;
        int cmdCount = 0, blockCount = 0;
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

                if (braceDepth == 0) {
                    if (!blockBuffer.isEmpty()) {
                        runMaredBlock(blockBuffer, blockStartLine);
                        blockCount++;
                        blockBuffer.clear();
                    }
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
        int n = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') inString = !inString;
            if (!inString && ch == c) n++;
        }
        return n;
    }

    private void runMaredBlock(List<String> blockLines, int startLine) {
        String text = String.join("\n", blockLines);
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) { addLog(MaredLang.get("mared.log.error.server_unavailable")); return; }
        ServerPlayer initiator = mc.player != null
            ? server.getPlayerList().getPlayer(mc.player.getUUID())
            : null;

        List<MaredScriptCommand> commands;
        try { commands = MaredScriptParser.parse(text); }
        catch (MaredScriptParser.ParseException e) {
            addLog(MaredLang.format("mared.log.mared.parse_error", e.getMessage()));
            return;
        }
        if (commands.isEmpty()) {
            addLog(MaredLang.format("mared.log.mared.empty", startLine));
            return;
        }

        addLog(MaredLang.format("mared.log.mared.block_ready", startLine, commands.size()));
        MaredScriptContext ctx = new MaredScriptContext(initiator, server, this::addLog);
        MaredScriptRunner.start(new MaredScriptExecutor(ctx, commands));
    }

    private void addLog(String line) {
        logLines.add(line);
        if (logLines.size() > 500) logLines.remove(0);
        logScroll.offset = 0;
    }

    private int logHeight() { return logCollapsed ? LOG_H_COLLAPSED : LOG_H; }
    private int logTop() { return this.height - logHeight(); }
    private int sidebarBottom() { return logTop(); }
    private int editorBottom() { return logTop() - GAP_EDITOR_LOG; }
    private int infoPanelX() { return infoPanelCollapsed ? this.width - INFO_COLLAPSED_W : this.width - INFO_W; }
    private int infoWidth() {
        if (selectedCommandInfo == null || !isCommands()) return 0;
        return infoPanelCollapsed ? INFO_COLLAPSED_W : INFO_W;
    }
    private int infoArgFilterBoxY() {
        if (selectedCommandInfo == null) return 0;
        int y = TOOLBAR_H + PAD * 2 + 22 + 14 + 12 + 16 + 12;
        y += MaredUi.wrappedHeight(this.font, selectedCommandInfo.description, INFO_W - 16) + 6 + 12;
        y += MaredUi.wrappedHeight(this.font, selectedCommandInfo.example, INFO_W - 16) + 10 + 12;
        return y;
    }
    private int infoBodyTop() { return infoArgFilterBoxY() + FILTER_H + 4; }
    private int logToggleX() { return this.width - 24; }
    private int logToggleY() { return this.height - 18; }
    private int arrowY() { return PAD + 1; }
    private int sidebarWidth() {
        if (!isScripts() && !isCommands()) return 0;
        return switch (sidebarState) {
            case STRIP -> STRIP_W;
            case FULL -> FULL_W;
            default -> 0;
        };
    }
    private int itemW(int listW) { return listW - SCROLLBAR_W - 2; }
    private int fitHeight(int h, int itemHeight) { return (h / itemHeight) * itemHeight; }

    private MaredUi.Content buildInfoContent() {
        MaredUi.Content c = new MaredUi.Content();
        MaredCommandRegistry.CommandInfo info = selectedCommandInfo;
        if (info == null) return c;

        int argColor = isMaredInfo() ? MRED_COLOR : accentTop();

        for (int i = 0; i < info.arguments.size(); i++) {
            MaredCommandRegistry.Argument arg = info.arguments.get(i);
            if (!argFilter.isEmpty()) {
                String q = argFilter.toLowerCase();
                if (!arg.value.toLowerCase().contains(q) && !arg.description.toLowerCase().contains(q)) continue;
            }
            boolean expanded = expandedArgs.contains(i);
            c.text((expanded ? "▾ " : "▸ ") + arg.value, TEXT);
            c.wrapped(arg.description, TEXT_DIM);
            if (expanded) {
                c.gap(6);
                c.text(MaredLang.get("mared.ui.examples"), TEXT_WARN);
                c.gap(2);
                for (String ex : arg.examples) {
                    c.wrapped("• " + ex, argColor);
                    c.gap(2);
                }
                c.gap(4);
            }
            c.gap(4);
        }
        if (!info.arguments.isEmpty()) c.gap(4);

        if (!info.nbtHints.isEmpty()) {
            c.text(MaredLang.get("mared.ui.nbt_components"), TEXT_WARN);
            c.gap(4);
            for (MaredCommandRegistry.NbtHint hint : info.nbtHints) {
                c.text(hint.tag, argColor);
                c.text(MaredLang.get("mared.ui.what") + " " + hint.what, TEXT_DIM);
                c.wrapped(MaredLang.get("mared.ui.why") + " " + hint.why, TEXT_DIM);
                if (hint.example != null && !hint.example.isEmpty()) {
                    c.wrapped(MaredLang.get("mared.ui.example") + " " + hint.example, argColor);
                }
                c.gap(6);
            }
        }
        return c;
    }

    private int hitArg(double mx, double my) {
        if (selectedCommandInfo == null) return -1;
        if (my < infoBodyTop() || my > editorBottom() - PAD) return -1;
        if (mx < infoPanelX() || mx > infoPanelX() + INFO_W) return -1;

        int innerW = INFO_W - 16 - 10;
        int y = infoBodyTop() - infoScroll.offset;
        for (int i = 0; i < selectedCommandInfo.arguments.size(); i++) {
            MaredCommandRegistry.Argument arg = selectedCommandInfo.arguments.get(i);
            if (!argFilter.isEmpty()) {
                String q = argFilter.toLowerCase();
                if (!arg.value.toLowerCase().contains(q) && !arg.description.toLowerCase().contains(q)) continue;
            }
            if (my >= y && my < y + 10) return i;
            y += 10 + MaredUi.wrappedHeight(this.font, arg.description, innerW);
            if (expandedArgs.contains(i)) {
                y += 6 + 12;
                for (String ex : arg.examples) y += MaredUi.wrappedHeight(this.font, "• " + ex, innerW) + 2;
                y += 4;
            }
            y += 4;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!logCollapsed) {
            setupLogScrollArea();
            if (logScroll.clickScrollbar(mx, my, SCROLLBAR_W, drag, MaredUi.DragKind.LOG_SCROLL))
                return true;
        }
        if (MaredUi.hovered(mx, my, logToggleX(), logToggleY(), 18, 16)) {
            logCollapsed = !logCollapsed;
            recreateEditor();
            return true;
        }

        if (!logCollapsed && mx >= this.width - ACTIVE_BINDS_W && my >= logTop()) {
            if (handleActiveBindsClick(mx, my)) return true;
        }

        if (my >= logTop()) return true;

        if (selectedCommandInfo != null && isCommands() && my >= TOOLBAR_H + PAD) {
            if (handleInfoClick(mx, my)) return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (handleTabClick(mx, my)) return true;
        if (handleStripClick(mx, my)) return true;
        if ((isScripts() || isCommands()) && sidebarState == SidebarState.FULL && handleSidebarClick(mx, my))
            return true;

        return false;
    }

    private boolean handleActiveBindsClick(double mx, double my) {
        int logTop = logTop();

        int clearW = 70;
        int clearX = this.width - PAD - clearW;
        int clearY = logTop + 2;
        if (MaredUi.hovered(mx, my, clearX, clearY, clearW, 12)) {
            MaredBindRegistry.clearAll();
            activeBindsScrollOffset = 0;
            addLog(MaredLang.get("mared.log.bind.all_cleared"));
            return true;
        }

        int listY = logTop + LOG_HEADER + 4;
        List<String> keys = MaredBindRegistry.keys();
        int visible = Math.max(1, (this.height - listY - PAD) / ITEM_H);
        for (int i = 0; i < visible; i++) {
            int idx = i + activeBindsScrollOffset;
            if (idx >= keys.size()) break;
            String key = keys.get(idx);
            int itemY = listY + i * ITEM_H;
            int delX = this.width - PAD - BIND_DEL_SZ;
            int delY = itemY + (ITEM_H - 2 - BIND_DEL_SZ) / 2;
            if (MaredUi.hovered(mx, my, delX, delY, BIND_DEL_SZ, BIND_DEL_SZ)) {
                MaredBindRegistry.clear(key);
                addLog(MaredLang.format("mared.log.bind.removed", key));
                return true;
            }
        }
        return true;
    }

    private boolean handleInfoClick(double mx, double my) {
        int infoX = infoPanelX();
        int toggleX = infoX + 4;
        int toggleY = TOOLBAR_H + PAD * 2 + 4;
        if (MaredUi.hovered(mx, my, toggleX, toggleY, 14, 14)) {
            infoPanelCollapsed = !infoPanelCollapsed;
            infoScroll.offset = 0;
            recreateEditor();
            return true;
        }
        if (infoPanelCollapsed || mx < infoX) return false;

        int argBoxY = infoArgFilterBoxY();
        boolean overFilter = my >= argBoxY - 1 && my < argBoxY + FILTER_H + 1;
        if (!overFilter) {
            int hit = hitArg(mx, my);
            if (hit >= 0) {
                if (expandedArgs.contains(hit)) expandedArgs.remove(hit);
                else expandedArgs.add(hit);
                return true;
            }
        }

        int bodyTop = infoBodyTop();
        int bodyH = (editorBottom() - PAD) - bodyTop;
        infoScroll.set(infoX, bodyTop, INFO_W, bodyH);
        infoScroll.content(buildInfoContent().height(this.font, INFO_W - 16));
        if (!overFilter
            && infoScroll.clickScrollbar(mx, my, SCROLLBAR_W, drag, MaredUi.DragKind.INFO_SCROLL))
            return true;
        return !overFilter;
    }

    private boolean handleTabClick(double mx, double my) {
        if (mx >= TAB_W) return false;
        int idx = (int) (my / TAB_H);
        String[] tabs = {"scripts", "commands", "npc", "events", "quests"};
        if (idx < 0 || idx >= tabs.length) return false;

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

    private boolean handleStripClick(double mx, double my) {
        if (!(isScripts() || isCommands()) || sidebarState != SidebarState.STRIP) return false;
        int ax = TAB_W + (STRIP_W - BTN_SZ) / 2;
        int ay = arrowY();
        if (MaredUi.hovered(mx, my, ax, ay, BTN_SZ, BTN_SZ)) {
            sidebarState = SidebarState.FULL;
            recreateEditor();
            return true;
        }
        return false;
    }

    private boolean handleSidebarClick(double mx, double my) {
        int sx = TAB_W, ay = arrowY();
        int arrowX = sx + FULL_W - BTN_SZ - 4;
        int plusX = arrowX - BTN_SZ - 4;

        if (MaredUi.hovered(mx, my, arrowX, ay, BTN_SZ, BTN_SZ)) {
            sidebarState = SidebarState.STRIP;
            recreateEditor();
            return true;
        }
        if (MaredUi.hovered(mx, my, plusX, ay, BTN_SZ, BTN_SZ)) {
            onNew();
            return true;
        }

        int listX = sx + 6;
        int listW = FULL_W - 12 - SCROLLBAR_W;
        int listY = ay + BTN_SZ + 8;
        int filesH = Math.min(FILE_SECTION_H, sidebarBottom() - listY - 6);
        int filesListY = listY + 12;
        int filesListH = fitHeight(filesH - 12, ITEM_H);
        int itemWidth = itemW(listW);

        fileScroll.set(listX, filesListY, listW, filesListH).items(ITEM_H, fileNames.size());
        if (fileScroll.clickScrollbar(mx, my, SCROLLBAR_W, drag, MaredUi.DragKind.FILE_SCROLL))
            return true;

        int visibleFiles = fileScroll.visibleItems();
        for (int i = 0; i < visibleFiles; i++) {
            int idx = i + fileScroll.offset;
            if (idx >= fileNames.size()) break;
            int itemY = filesListY + i * ITEM_H;
            int delX = listX + itemWidth - FILE_DEL_SZ;
            int delY = itemY + (ITEM_H - 2 - FILE_DEL_SZ) / 2;
            if (MaredUi.hovered(mx, my, delX, delY, FILE_DEL_SZ, FILE_DEL_SZ)) {
                onDelete(fileNames.get(idx));
                return true;
            }
        }

        int fileIdx = fileScroll.hitItem(mx, my, SCROLLBAR_W);
        if (fileIdx >= 0) {
            selectedFile = fileNames.get(fileIdx);
            addLog(MaredLang.format("mared.log.info.selected", selectedFile));
            loadIntoEditor();
            return true;
        }

        int commandsY = listY + filesH + 4;
        int toggleX = listX + listW - TOGGLE_W;
        int toggleY = commandsY + 1;
        if (MaredUi.hovered(mx, my, toggleX, toggleY, TOGGLE_W, TOGGLE_H)) {
            showMaredCommands = !showMaredCommands;
            commandFilter = "";
            if (commandFilterBox != null) commandFilterBox.setValue("");
            refreshCommands();
            return true;
        }

        if (isCommands()) {
            int filterY = commandsY + 14;
            int commandsListY = filterY + 16;
            int commandsH = fitHeight(sidebarBottom() - 6 - commandsListY, ITEM_H);

            cmdScroll.set(listX, commandsListY, listW, commandsH).items(ITEM_H, filteredCommands.size());
            if (cmdScroll.clickScrollbar(mx, my, SCROLLBAR_W, drag, MaredUi.DragKind.CMD_SCROLL))
                return true;

            int cmdIdx = cmdScroll.hitItem(mx, my, SCROLLBAR_W);
            if (cmdIdx >= 0) {
                selectedCommandInfo = filteredCommands.get(cmdIdx);
                infoScroll.offset = 0;
                infoPanelCollapsed = false;
                expandedArgs.clear();
                argFilter = "";
                addLog("[info] " + selectedCommandInfo.name);
                recreateEditor();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_DELETE && selectedFile != null) {
            onDelete();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!drag.active()) return super.mouseDragged(mx, my, button, dx, dy);
        switch (drag.kind) {
            case FILE_SCROLL -> fileScroll.dragScrollbar(my, drag);
            case CMD_SCROLL  -> cmdScroll.dragScrollbar(my, drag);
            case INFO_SCROLL -> infoScroll.dragScrollbar(my, drag);
            case LOG_SCROLL  -> logScroll.dragScrollbar(my, drag);
            default -> {}
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        drag.clear();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double deltaX, double deltaY) {
        if (!logCollapsed && mx >= this.width - ACTIVE_BINDS_W && my >= logTop()) {
            List<String> keys = MaredBindRegistry.keys();
            int listY = logTop() + LOG_HEADER + 4;
            int visible = Math.max(1, (this.height - listY - PAD) / ITEM_H);
            int maxOffset = Math.max(0, keys.size() - visible);
            if (deltaY < 0) activeBindsScrollOffset = Math.min(maxOffset, activeBindsScrollOffset + 1);
            else if (deltaY > 0) activeBindsScrollOffset = Math.max(0, activeBindsScrollOffset - 1);
            return true;
        }
        if (selectedCommandInfo != null && isCommands() && !infoPanelCollapsed && mx >= infoPanelX()) {
            infoScroll.wheel(deltaY, 5);
            return true;
        }
        if (my >= logTop() && !logCollapsed) {
            setupLogScrollArea();
            logScroll.wheelLog(deltaY, 1);
            return true;
        }
        if ((isScripts() || isCommands()) && sidebarState == SidebarState.FULL) {
            int listX = TAB_W + 6;
            int listW = FULL_W - 12 - SCROLLBAR_W;
            int listY = arrowY() + BTN_SZ + 8;
            int filesH = Math.min(FILE_SECTION_H, sidebarBottom() - listY - 6);
            int filesListY = listY + 12;
            int filesListH = fitHeight(filesH - 12, ITEM_H);
            if (MaredUi.hovered(mx, my, listX, filesListY, listW, filesListH)) {
                fileScroll.wheel(deltaY, 1);
                return true;
            }
            if (isCommands()) {
                int commandsListY = listY + filesH + 4 + 14 + 16;
                int commandsH = fitHeight(sidebarBottom() - 6 - commandsListY, ITEM_H);
                if (MaredUi.hovered(mx, my, listX, commandsListY, listW, commandsH)) {
                    cmdScroll.wheel(deltaY, 1);
                    return true;
                }
            }
        }
        if (editor != null && editor.isMouseOver(mx, my) && editor.mouseScrolled(mx, my, deltaX, deltaY))
            return true;
        return super.mouseScrolled(mx, my, deltaX, deltaY);
    }

    private void setupLogScrollArea() {
        logScroll.set(0, logTop() + LOG_HEADER, this.width - (logCollapsed ? 0 : ACTIVE_BINDS_W), LOG_H - LOG_HEADER - PAD)
                 .inverted(true)
                 .items(LOG_LINE, logLines.size());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MaredUi.rect(g, 0, 0, this.width, this.height, BG);
        int sbW = sidebarWidth();

        drawLog(g, mouseX, mouseY);

        if (sbW > 0) {
            int bg = (sidebarState == SidebarState.STRIP) ? PANEL_STRIP : PANEL;
            MaredUi.rect(g, TAB_W, 0, TAB_W + sbW, sidebarBottom(), bg);
        }

        MaredUi.rect(g, TAB_W + sbW, 0, this.width, TOOLBAR_H + PAD, TOOLBAR_BG);
        MaredUi.rect(g, 0, 0, TAB_W, this.height, TAB_STRIP);
        drawTabs(g, mouseX, mouseY);

        if ((isScripts() || isCommands()) && sidebarState == SidebarState.STRIP)
            drawArrow(g, TAB_W + (STRIP_W - BTN_SZ) / 2, arrowY(), "►", mouseX, mouseY);
        if ((isScripts() || isCommands()) && sidebarState == SidebarState.FULL)
            drawFullSidebar(g, mouseX, mouseY);

        drawEditorFrame(g, sbW);
        if (selectedCommandInfo != null && isCommands()) drawInfoPanel(g, mouseX, mouseY);

        int dividerY = logTop() - 1;
        MaredUi.rect(g, 0, dividerY, this.width, dividerY + 1, EVENTS_TOP);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawEditorFrame(GuiGraphics g, int sbW) {
        boolean editable = isScripts() || isCommands();
        int frameX = TAB_W + sbW + PAD;
        int frameY = TOOLBAR_H + PAD * 2;
        int frameW = this.width - frameX - PAD - (editable ? infoWidth() : 0);
        int frameH = editorBottom() - frameY - PAD;

        if (editable) {
            MaredUi.panelGradient(g, frameX, frameY, frameW, frameH, EDITOR_BG, accentTop(), accentBottom());
            String type = isScripts() ? "script" : "commands";
            String title = (selectedFile == null)
                ? MaredLang.format("mared.ui.editor_no_selected", type)
                : MaredLang.format("mared.ui.editor_file", selectedFile);
            MaredUi.text(g, this.font, title, frameX + 6, frameY + 5, TEXT);
            MaredUi.dashedLineGradient(g, frameX + 2, frameY + EDITOR_HDR, frameX + frameW - 2, accentTop(), accentBottom());
        } else {
            MaredUi.panel(g, frameX, frameY, frameW, frameH, EDITOR_BG, TEXT_DIM);
            MaredUi.text(g, this.font,
                MaredLang.format("mared.ui.under_development", tabTitle(openTab)),
                frameX + 8, frameY + 8, TEXT_DIM);
        }
    }

    private void drawInfoPanel(GuiGraphics g, int mouseX, int mouseY) {
        int infoX = infoPanelX();
        int infoY = TOOLBAR_H + PAD * 2;
        int infoH = editorBottom() - infoY - PAD;

        int top = accentTop();
        int bottom = isMaredInfo() ? MRED_COLOR : accentBottom();

        MaredUi.panelGradient(g, infoX, infoY, infoWidth(), infoH, INFO_BG, top, bottom);

        int toggleX = infoX + 4, toggleY = infoY + 4;
        boolean toggleHover = MaredUi.hovered(mouseX, mouseY, toggleX, toggleY, 14, 14);
        MaredUi.buttonGradient(g, this.font, toggleX, toggleY, 14, 14,
            infoPanelCollapsed ? "◄" : "►",
            toggleHover ? BTN_HOVER : BTN_BG, top, bottom, TEXT);

        if (infoPanelCollapsed) {
            MaredUi.text(g, this.font, MaredLang.get("mared.ui.info"), infoX + 7, infoY + 24, top);
            return;
        }

        int x = infoX + 8, maxW = INFO_W - 16;
        MaredCommandRegistry.CommandInfo info = selectedCommandInfo;

        int y = infoY + 22;
        MaredUi.text(g, this.font, info.name, x, y, top);                                              y += 14;
        MaredUi.text(g, this.font, MaredLang.get("mared.ui.category") + " " + info.category, x, y, TEXT_DIM); y += 12;
        MaredUi.text(g, this.font, MaredLang.get("mared.ui.op_level") + " " + info.opLevel, x, y, TEXT_DIM);  y += 16;
        MaredUi.text(g, this.font, MaredLang.get("mared.ui.description"), x, y, TEXT);                  y += 12;
        y = MaredUi.wrapped(g, this.font, info.description, x, y, maxW, TEXT_DIM);                      y += 6;
        MaredUi.text(g, this.font, MaredLang.get("mared.ui.example"), x, y, TEXT);                      y += 12;
        y = MaredUi.wrapped(g, this.font, info.example, x, y, maxW, TEXT);                               y += 10;

        int filterTitleColor = isMaredInfo() ? MRED_COLOR : TEXT_WARN;
        MaredUi.text(g, this.font, MaredLang.get("mared.ui.filter_arguments"), x, y, filterTitleColor); y += 12;

        MaredUi.rect(g, infoX + 3, y, infoX + INFO_W - 3, y + FILTER_H, FILTER_BG);
        MaredUi.outlineGradient(g, infoX + 3, y, INFO_W - 6, FILTER_H, top, bottom);
        y += FILTER_H + 4;

        MaredUi.rect(g, infoX + 2, y, infoX + INFO_W - 2, y + 1, DIVIDER);
        y += 3;

        MaredUi.scissorOn(g, infoX + 1, y, infoX + INFO_W - 1, infoY + infoH - 1);
        MaredUi.Content content = buildInfoContent();
        content.render(g, this.font, x, y - infoScroll.offset, maxW);
        MaredUi.scissorOff(g);

        int bodyTop = y;
        int bodyH = (infoY + infoH - PAD) - bodyTop;
        infoScroll.set(infoX, bodyTop, INFO_W, bodyH);
        infoScroll.drawScrollbarGradient(g, top, bottom, SCROLLBAR_W);
    }

    private void drawLog(GuiGraphics g, int mouseX, int mouseY) {
        int logTop = logTop();
        MaredUi.rect(g, 0, logTop, this.width, this.height, LOG_BG);

        if (logCollapsed) {
            MaredUi.text(g, this.font, MaredLang.get("mared.ui.log"), PAD, logTop + 4, EVENTS_TOP);
            boolean hover = MaredUi.hovered(mouseX, mouseY, logToggleX(), logToggleY(), 18, 16);
            MaredUi.button(g, this.font, logToggleX(), logToggleY(), 18, 16,
                "▲", hover ? BTN_HOVER : BTN_BG, EVENTS_TOP, hover, TEXT);
            return;
        }

        int logRight = this.width - ACTIVE_BINDS_W;

        MaredUi.text(g, this.font, MaredLang.get("mared.ui.log"), PAD, logTop + 4, EVENTS_TOP);
        setupLogScrollArea();
        logScroll.clamp();
        int visible = logScroll.visibleItems();
        int end = logLines.size() - logScroll.offset;
        int start = Math.max(0, end - visible);
        for (int i = start; i < end && i < logLines.size(); i++) {
            String line = logLines.get(i);
            MaredUi.text(g, this.font, line,
                PAD + 4, logTop + LOG_HEADER + (i - start) * LOG_LINE, logColor(line));
        }
        logScroll.drawScrollbar(g, EVENTS_TOP, SCROLLBAR_W);

        MaredUi.rect(g, logRight, logTop, logRight + 1, this.height, DIVIDER);

        drawActiveBinds(g, logRight, logTop, mouseX, mouseY);

        boolean hover = MaredUi.hovered(mouseX, mouseY, logToggleX(), logToggleY(), 18, 16);
        MaredUi.button(g, this.font, logToggleX(), logToggleY(), 18, 16,
            "▼", hover ? BTN_HOVER : BTN_BG, EVENTS_TOP, hover, TEXT);
    }

    private void drawActiveBinds(GuiGraphics g, int x, int logTop, int mouseX, int mouseY) {
        MaredUi.rect(g, x, logTop, this.width, this.height, LOG_BG);

        int clearW = 70;
        int clearX = this.width - PAD - clearW;
        int clearY = logTop + 2;

        String header = MaredLang.get("mared.ui.active_binds")
            + " (" + MaredBindRegistry.totalCount() + ")";
        int maxHeaderWidth = (clearX - 4) - (x + PAD);
        if (maxHeaderWidth > 0 && this.font.width(header) > maxHeaderWidth) {
            header = this.font.plainSubstrByWidth(header, Math.max(0, maxHeaderWidth - 4)) + "...";
        }

        MaredUi.text(g, this.font, header, x + PAD, logTop + 4, EVENTS_TOP);

        boolean clearHover = MaredUi.hovered(mouseX, mouseY, clearX, clearY, clearW, 12);
        MaredUi.button(g, this.font, clearX, clearY, clearW, 12, MaredLang.get("mared.ui.clear"),
            clearHover ? BTN_HOVER : BTN_BG, DANGER, clearHover, DANGER);

        int listY = logTop + LOG_HEADER + 4;
        int listH = this.height - listY - PAD;

        List<String> keys = MaredBindRegistry.keys();
        if (keys.isEmpty()) {
            MaredUi.text(g, this.font, MaredLang.get("mared.ui.no_binds"), x + PAD, listY + 2, TEXT_DIM);
            return;
        }

        int visible = Math.max(1, listH / ITEM_H);
        int maxOffset = Math.max(0, keys.size() - visible);
        activeBindsScrollOffset = Math.max(0, Math.min(maxOffset, activeBindsScrollOffset));

        for (int i = 0; i < visible; i++) {
            int idx = i + activeBindsScrollOffset;
            if (idx >= keys.size()) break;
            String key = keys.get(idx);
            List<MaredBindRegistry.Entry> entries = MaredBindRegistry.entries(key);
            boolean blocking = MaredBindRegistry.hasBlocking(key);
            int itemY = listY + i * ITEM_H;

            boolean hov = MaredUi.hovered(mouseX, mouseY, x, itemY, this.width - x, ITEM_H - 2);
            MaredUi.rect(g, x, itemY, this.width, itemY + ITEM_H - 2, hov ? ITEM_HOVER : ITEM_NORMAL);

            String label = key + (blocking ? " (block)" : "") + "  [" + entries.size() + "]";
            MaredUi.text(g, this.font, label, x + PAD, itemY + 2, TEXT);

            int delX = this.width - PAD - BIND_DEL_SZ;
            int delY = itemY + (ITEM_H - 2 - BIND_DEL_SZ) / 2;
            boolean delHover = MaredUi.hovered(mouseX, mouseY, delX, delY, BIND_DEL_SZ, BIND_DEL_SZ);
            MaredUi.rect(g, delX, delY, delX + BIND_DEL_SZ, delY + BIND_DEL_SZ,
                delHover ? 0xFF663333 : 0xFF3A2020);
            MaredUi.outline(g, delX, delY, BIND_DEL_SZ, BIND_DEL_SZ, DANGER);
            MaredUi.text(g, this.font, "✕", delX + 1, delY + 1, DANGER);
        }

        if (keys.size() > visible) {
            int trackX = this.width - SCROLLBAR_W - 2;
            MaredUi.rect(g, trackX, listY, trackX + SCROLLBAR_W, listY + listH, 0xFF15151E);
            int thumbH = Math.max(10, listH * visible / keys.size());
            int thumbY = listY + (listH - thumbH) * activeBindsScrollOffset / Math.max(1, maxOffset);
            MaredUi.rect(g, trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, EVENTS_TOP);
        }
    }

    private int logColor(String line) {
        if (line.contains("[error]") || line.contains("[ошибка]")) return 0xFFFF5555;
        if (line.contains("[warn]") || line.contains("[предупр]"))   return 0xFFFFAA00;
        if (line.contains("[cmd]"))                                  return 0xFF88DDFF;
        if (line.contains("[cmd error]"))                            return 0xFFFF5555;
        if (line.contains("[mared]"))                                return 0xFF55FF88;
        if (line.contains("[mared parse]"))                          return 0xFFFF5555;
        if (line.contains("[bind fire]"))                            return 0xFFAA55FF;
        if (line.contains("[bind]"))                                 return 0xFFFF55FF;
        if (line.contains("[run]") || line.contains("[запуск]"))     return 0xFFFFD700;
        if (line.contains("[info]") || line.contains("[инфо]"))      return 0xFFAAAAAA;
        if (line.contains("[auto-save]") || line.contains("[автосейв]")) return 0xFF88DDFF;
        return TEXT;
    }

    private void drawArrow(GuiGraphics g, int ax, int ay, String symbol, int mouseX, int mouseY) {
        boolean hover = MaredUi.hovered(mouseX, mouseY, ax, ay, BTN_SZ, BTN_SZ);
        MaredUi.buttonGradient(g, this.font, ax, ay, BTN_SZ, BTN_SZ, symbol,
            hover ? BTN_HOVER : BTN_BG, accentTop(), accentBottom(), TEXT);
    }

    private void drawFullSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int sx = TAB_W, ay = arrowY();
        MaredUi.text(g, this.font, tabTitle(openTab), sx + 8, ay + 5, TEXT);

        int arrowX = sx + FULL_W - BTN_SZ - 4;
        int plusX = arrowX - BTN_SZ - 4;

        boolean plusHover = MaredUi.hovered(mouseX, mouseY, plusX, ay, BTN_SZ, BTN_SZ);
        MaredUi.buttonGradient(g, this.font, plusX, ay, BTN_SZ, BTN_SZ, "+",
            plusHover ? BTN_HOVER : BTN_BG, accentTop(), accentTop(), TEXT);

        boolean arrowHover = MaredUi.hovered(mouseX, mouseY, arrowX, ay, BTN_SZ, BTN_SZ);
        MaredUi.buttonGradient(g, this.font, arrowX, ay, BTN_SZ, BTN_SZ, "◄",
            arrowHover ? BTN_HOVER : BTN_BG, accentBottom(), accentBottom(), TEXT);

        int listX = sx + 6, listW = FULL_W - 12 - SCROLLBAR_W;
        int listY = ay + BTN_SZ + 8;
        int filesH = Math.min(FILE_SECTION_H, sidebarBottom() - listY - 6);
        int filesListY = listY + 12;
        int filesListH = fitHeight(filesH - 12, ITEM_H);

        MaredUi.text(g, this.font, MaredLang.get("mared.ui.files"), listX, listY, TEXT_DIM);

        fileScroll.set(listX, filesListY, listW, filesListH).items(ITEM_H, fileNames.size());
        if (fileNames.isEmpty()) {
            MaredUi.text(g, this.font, MaredLang.get("mared.ui.no_files"), listX + 4, filesListY + 4, TEXT_DIM);
        } else {
            MaredUi.listGradient(g, this.font, fileScroll,
                fileNames.indexOf(selectedFile), SCROLLBAR_W,
                accentTop(), accentBottom(),
                selColor(accentTop()), ITEM_HOVER, ITEM_NORMAL,
                (gr, f, idx, ix, iy, iw, ih, hov, sel) -> {
                    MaredUi.text(gr, f, fileNames.get(idx), ix + 4, iy + 2, TEXT);
                    int delX = ix + iw - FILE_DEL_SZ;
                    int delY = iy + (ih - FILE_DEL_SZ) / 2;
                    boolean dHov = MaredUi.hovered(mouseX, mouseY, delX, delY, FILE_DEL_SZ, FILE_DEL_SZ);
                    MaredUi.rect(gr, delX, delY, delX + FILE_DEL_SZ, delY + FILE_DEL_SZ,
                        dHov ? 0xFF663333 : 0xFF3A2020);
                    MaredUi.outline(gr, delX, delY, FILE_DEL_SZ, FILE_DEL_SZ, DANGER);
                    MaredUi.text(gr, f, "✕", delX + 2, delY + 1, DANGER);
                }, mouseX, mouseY);
        }

        if (isCommands()) {
            drawCommandsList(g, mouseX, mouseY, listX, listW, listY, filesH);
        }
    }

    private void drawCommandsList(GuiGraphics g, int mouseX, int mouseY,
                                  int listX, int listW, int listY, int filesH) {
        int commandsY = listY + filesH + 4;
        MaredUi.rect(g, TAB_W + 4, commandsY - 2, TAB_W + FULL_W - 4, commandsY, SECTION_BG);
        MaredUi.text(g, this.font, MaredLang.get("mared.ui.available_commands"), listX, commandsY + 2, TEXT_DIM);

        int toggleX = listX + listW - TOGGLE_W;
        int toggleY = commandsY + 1;
        boolean toggleHover = MaredUi.hovered(mouseX, mouseY, toggleX, toggleY, TOGGLE_W, TOGGLE_H);

        int top = COMMANDS_TOP;
        int bottom = showMaredCommands ? MRED_COLOR : COMMANDS_BOT;
        MaredUi.buttonGradient(g, this.font, toggleX, toggleY, TOGGLE_W, TOGGLE_H,
            showMaredCommands ? "MR" : "MC",
            toggleHover ? BTN_HOVER : BTN_BG, top, bottom, TEXT);

        int filterY = commandsY + 14;
        MaredUi.rect(g, listX, filterY, listX + listW, filterY + FILTER_H, FILTER_BG);
        MaredUi.outlineGradient(g, listX, filterY, listW, FILTER_H, top, bottom);

        int commandsListY = filterY + 16;
        int commandsH = fitHeight(sidebarBottom() - 6 - commandsListY, ITEM_H);
        if (commandsH < ITEM_H) return;

        cmdScroll.set(listX, commandsListY, listW, commandsH).items(ITEM_H, filteredCommands.size());
        MaredUi.listGradient(g, this.font, cmdScroll,
            filteredCommands.indexOf(selectedCommandInfo), SCROLLBAR_W,
            top, bottom,
            selColor(showMaredCommands ? MRED_COLOR : COMMANDS_TOP), ITEM_HOVER, ITEM_NORMAL,
            (gr, f, idx, ix, iy, iw, ih, hov, sel) ->
                MaredUi.text(gr, f, filteredCommands.get(idx).name, ix + 4, iy + 2, TEXT),
            mouseX, mouseY);
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        String[] tabs = {"scripts", "commands", "npc", "events", "quests"};
        String[] letters = {"S", "C", "N", "E", "Q"};
        for (int i = 0; i < tabs.length; i++) {
            int y = i * TAB_H;
            boolean hovered = mouseX < TAB_W && mouseY >= y && mouseY < y + TAB_H;
            boolean active = tabs[i].equals(openTab);

            int top, bottom;
            switch (tabs[i]) {
                case "scripts"  -> { top = SCRIPTS_TOP;  bottom = SCRIPTS_BOT; }
                case "commands" -> { top = COMMANDS_TOP; bottom = COMMANDS_BOT; }
                case "npc"      -> { top = NPC_TOP;      bottom = NPC_BOT; }
                case "events"   -> { top = EVENTS_TOP;   bottom = EVENTS_BOT; }
                default         -> { top = QUESTS_TOP;   bottom = QUESTS_BOT; }
            }

            if (active) {
                MaredUi.gradientV(g, 2, y + 2, TAB_W - 2, y + TAB_H - 2, top, bottom);
                MaredUi.centered(g, this.font, letters[i], TAB_W / 2, y + (TAB_H - 8) / 2 + 1, TEXT);
            } else {
                int bg = hovered ? 0xFF3A3A4A : ITEM_NORMAL;
                MaredUi.rect(g, 2, y + 2, TAB_W - 2, y + TAB_H - 2, bg);
                MaredUi.centered(g, this.font, letters[i], TAB_W / 2, y + (TAB_H - 8) / 2 + 1, top);
            }
        }
    }

    private String tabTitle(String key) {
        return switch (key) {
            case "scripts" -> MaredLang.get("mared.ui.title.scripts");
            case "commands" -> MaredLang.get("mared.ui.title.commands");
            case "npc" -> MaredLang.get("mared.ui.title.npc");
            case "events" -> MaredLang.get("mared.ui.title.events");
            case "quests" -> MaredLang.get("mared.ui.title.quests");
            default -> key;
        };
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
}