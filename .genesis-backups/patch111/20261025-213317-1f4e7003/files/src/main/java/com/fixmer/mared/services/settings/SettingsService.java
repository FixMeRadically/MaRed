package com.fixmer.mared.services.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.fixmer.mared.Mared;
import com.fixmer.mared.MaredLayoutPreset;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.services.theme.ThemeService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Сервис настроек.
 *
 * 0.3.2: добавлены reducedMotion + maredButtonCorner/Offset.
 */
public final class SettingsService {

    private static final SettingsService INSTANCE = new SettingsService();
    private static final int SCHEMA_VERSION = 2;

    public static SettingsService get() { return INSTANCE; }

    private final EditorSettings      editor      = new EditorSettings();
    private final ThemeSettings       theme       = new ThemeSettings();
    private final LogBehaviorSettings logBehavior = new LogBehaviorSettings();
    private final ServerSettings      server      = new ServerSettings();
    private final LayoutSettings      layout      = new LayoutSettings();

    private boolean loaded = false;

    private SettingsService() {}

    public EditorSettings      editor()      { return editor; }
    public ThemeSettings       theme()       { return theme; }
    public LogBehaviorSettings logBehavior() { return logBehavior; }
    public ServerSettings      server()      { return server; }
    public LayoutSettings      layout()      { return layout; }

    // ============================================================
    //  Load / Save
    // ============================================================

    public synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Path path = configPath();
        if (path == null || !Files.exists(path)) return;

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            int schemaVersion = obj.has("schemaVersion")
                ? obj.get("schemaVersion").getAsInt() : 0;
            if (schemaVersion > SCHEMA_VERSION) {
                Mared.LOGGER.warn(
                    "[Mared] settings.json has future schemaVersion={} (current={}), reading anyway",
                    schemaVersion, SCHEMA_VERSION);
            }

            if (obj.has("autoIndent")) {
                try { editor.setAutoIndent(
                    MaredSettings.AutoIndent.valueOf(obj.get("autoIndent").getAsString()));
                } catch (Exception ignored) {}
            }
            if (obj.has("indentStyle")) {
                try { editor.setIndentStyle(
                    MaredSettings.IndentStyle.valueOf(obj.get("indentStyle").getAsString()));
                } catch (Exception ignored) {}
            }
            if (obj.has("backspaceRemovesIndent"))
                editor.setBackspaceRemovesIndent(obj.get("backspaceRemovesIndent").getAsBoolean());

            if (obj.has("serverMode")) {
                try { server.setServerMode(
                    MaredSettings.ServerMode.valueOf(obj.get("serverMode").getAsString()));
                } catch (Exception ignored) {}
            }

            if (obj.has("logChatToEditor"))
                logBehavior.setLogChatToEditor(obj.get("logChatToEditor").getAsBoolean());
            if (obj.has("verboseScriptLog"))
                logBehavior.setVerboseScriptLog(obj.get("verboseScriptLog").getAsBoolean());

            if (obj.has("themeId")) theme.setThemeId(obj.get("themeId").getAsString());
            if (obj.has("monotoneTabs")) theme.setMonotoneTabs(obj.get("monotoneTabs").getAsBoolean());
            if (obj.has("patternsEnabled")) theme.setPatternsEnabled(obj.get("patternsEnabled").getAsBoolean());
            if (obj.has("tornEdgesEnabled")) theme.setTornEdgesEnabled(obj.get("tornEdgesEnabled").getAsBoolean());
            if (obj.has("reducedMotion")) theme.setReducedMotion(obj.get("reducedMotion").getAsBoolean());
            if (obj.has("settingsActiveTab"))
                layout.setSettingsActiveTab(obj.get("settingsActiveTab").getAsString());

            if (obj.has("layoutPreset")) {
                try { layout.setPreset(
                    MaredLayoutPreset.valueOf(obj.get("layoutPreset").getAsString()));
                } catch (Exception ignored) {}
            }
            if (obj.has("layoutShowSidebar"))    layout.setShowSidebar(obj.get("layoutShowSidebar").getAsBoolean());
            if (obj.has("layoutShowRightPanel")) layout.setShowRightPanel(obj.get("layoutShowRightPanel").getAsBoolean());
            if (obj.has("layoutShowConsole"))    layout.setShowConsole(obj.get("layoutShowConsole").getAsBoolean());
            if (obj.has("layoutShowStatusBar"))  layout.setShowStatusBar(obj.get("layoutShowStatusBar").getAsBoolean());
            if (obj.has("layoutShowToolbar"))    layout.setShowToolbar(obj.get("layoutShowToolbar").getAsBoolean());
            if (obj.has("layoutSidebarWidth"))    layout.setSidebarWidth(obj.get("layoutSidebarWidth").getAsInt());
            if (obj.has("layoutRightPanelWidth")) layout.setRightPanelWidth(obj.get("layoutRightPanelWidth").getAsInt());
            if (obj.has("layoutConsoleHeight"))   layout.setConsoleHeight(obj.get("layoutConsoleHeight").getAsInt());
            if (obj.has("layoutRightMode"))    layout.setRightMode(obj.get("layoutRightMode").getAsString());
            if (obj.has("layoutSplitEnabled")) layout.setSplitEnabled(obj.get("layoutSplitEnabled").getAsBoolean());
            if (obj.has("layoutSplitRatio"))   layout.setSplitRatio(obj.get("layoutSplitRatio").getAsFloat());
            if (obj.has("layoutSplitFileRight")) layout.setSplitFileRight(obj.get("layoutSplitFileRight").getAsString());
            if (obj.has("layoutActiveColumn")) layout.setActiveColumn(obj.get("layoutActiveColumn").getAsInt());
            if (obj.has("layoutActiveTab"))    layout.setActiveTab(obj.get("layoutActiveTab").getAsString());
            if (obj.has("layoutSidebarState")) layout.setSidebarState(obj.get("layoutSidebarState").getAsString());
            if (obj.has("layoutLogCollapsed")) layout.setLogCollapsed(obj.get("layoutLogCollapsed").getAsBoolean());

            // 0.3.2
            if (obj.has("maredButtonCorner")) {
                layout.setButtonCorner(LayoutSettings.ButtonCorner.fromId(
                    obj.get("maredButtonCorner").getAsString()));
            }
            if (obj.has("maredButtonOffset")) {
                layout.setButtonOffset(obj.get("maredButtonOffset").getAsInt());
            }

        } catch (Exception e) {
            Mared.LOGGER.error("Failed to load settings", e);
        }

        try {
            MaredThemeRegistry.setActive(theme.themeId());
        } catch (Throwable ignored) {}
    }

    public synchronized void save() {
        Path path = configPath();
        if (path == null) return;

        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("schemaVersion", SCHEMA_VERSION);

            obj.addProperty("autoIndent", editor.autoIndent().name());
            obj.addProperty("indentStyle", editor.indentStyle().name());
            obj.addProperty("backspaceRemovesIndent", editor.backspaceRemovesIndent());

            obj.addProperty("serverMode", server.serverMode().name());

            obj.addProperty("logChatToEditor", logBehavior.logChatToEditor());
            obj.addProperty("verboseScriptLog", logBehavior.verboseScriptLog());

            obj.addProperty("themeId", theme.themeId());
            obj.addProperty("monotoneTabs", theme.monotoneTabs());
            obj.addProperty("patternsEnabled", theme.patternsEnabled());
            obj.addProperty("tornEdgesEnabled", theme.tornEdgesEnabled());
            obj.addProperty("reducedMotion", theme.reducedMotion());
            obj.addProperty("settingsActiveTab", layout.settingsActiveTab());

            obj.addProperty("layoutPreset", layout.preset().name());
            obj.addProperty("layoutShowSidebar", layout.showSidebar());
            obj.addProperty("layoutShowRightPanel", layout.showRightPanel());
            obj.addProperty("layoutShowConsole", layout.showConsole());
            obj.addProperty("layoutShowStatusBar", layout.showStatusBar());
            obj.addProperty("layoutShowToolbar", layout.showToolbar());
            obj.addProperty("layoutSidebarWidth", layout.sidebarWidth());
            obj.addProperty("layoutRightPanelWidth", layout.rightPanelWidth());
            obj.addProperty("layoutConsoleHeight", layout.consoleHeight());
            obj.addProperty("layoutRightMode", layout.rightMode());
            obj.addProperty("layoutSplitEnabled", layout.splitEnabled());
            obj.addProperty("layoutSplitRatio", layout.splitRatio());
            obj.addProperty("layoutSplitFileRight", layout.splitFileRight());
            obj.addProperty("layoutActiveColumn", layout.activeColumn());
            obj.addProperty("layoutActiveTab", layout.activeTab());
            obj.addProperty("layoutSidebarState", layout.sidebarState());
            obj.addProperty("layoutLogCollapsed", layout.logCollapsed());

            obj.addProperty("maredButtonCorner", layout.buttonCorner().name());
            obj.addProperty("maredButtonOffset", layout.buttonOffset());

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            writeAtomic(path, gson.toJson(obj));

        } catch (IOException e) {
            Mared.LOGGER.error("Failed to save settings", e);
        }
    }

    public synchronized void commit(MaredSettings.Snapshot s) {
        if (s == null) return;

        editor.setAutoIndent(s.autoIndent);
        editor.setIndentStyle(s.indentStyle);
        editor.setBackspaceRemovesIndent(s.backspaceRemovesIndent);

        server.setServerMode(s.serverMode);

        logBehavior.setLogChatToEditor(s.logChatToEditor);
        logBehavior.setVerboseScriptLog(s.verboseScriptLog);

        if (s.themeId != null && !s.themeId.isBlank()) {
            if (ThemeService.get().byId(s.themeId) == null) {
                Mared.LOGGER.warn(
                    "[Mared] Unknown themeId '{}', keeping previous '{}'",
                    s.themeId, theme.themeId());
            } else {
                theme.setThemeId(s.themeId);
                MaredThemeRegistry.setActive(s.themeId);
            }
        }
        theme.setMonotoneTabs(s.monotoneTabs);
        theme.setPatternsEnabled(s.patternsEnabled);
        theme.setTornEdgesEnabled(s.tornEdgesEnabled);
        theme.setReducedMotion(s.reducedMotion);

        try {
            layout.setPreset(MaredLayoutPreset.valueOf(s.layoutPreset));
        } catch (Exception e) {
            layout.setPreset(MaredLayoutPreset.CLASSIC);
        }
        layout.setShowSidebar(s.layoutShowSidebar);
        layout.setShowRightPanel(s.layoutShowRightPanel);
        layout.setShowConsole(s.layoutShowConsole);
        layout.setShowStatusBar(s.layoutShowStatusBar);
        layout.setShowToolbar(s.layoutShowToolbar);
        layout.setSidebarWidth(s.layoutSidebarWidth);
        layout.setRightPanelWidth(s.layoutRightPanelWidth);
        layout.setConsoleHeight(s.layoutConsoleHeight);
        layout.setRightMode(s.layoutRightMode);
        layout.setSplitEnabled(s.layoutSplitEnabled);
        layout.setSplitRatio(s.layoutSplitRatio);
        layout.setSplitFileRight(s.layoutSplitFileRight);
        layout.setActiveColumn(s.layoutActiveColumn);
        layout.setActiveTab(s.layoutActiveTab);
        layout.setSidebarState(s.layoutSidebarState);
        layout.setLogCollapsed(s.layoutLogCollapsed);

        layout.setButtonCorner(LayoutSettings.ButtonCorner.fromId(s.maredButtonCorner));
        layout.setButtonOffset(s.maredButtonOffset);

        save();
    }

    public synchronized void reset() {
        editor.resetToDefaults();
        theme.resetToDefaults();
        logBehavior.resetToDefaults();
        server.resetToDefaults();
        layout.resetToDefaults();
    }

    private static Path configPath() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("mared");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            return dir.resolve("settings.json");
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to prepare config path", e);
            return null;
        }
    }

    private static void writeAtomic(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}