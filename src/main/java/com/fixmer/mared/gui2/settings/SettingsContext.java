package com.fixmer.mared.gui2.settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fixmer.mared.MaredLayoutPreset;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.commands.input.KeybindItem;
import com.fixmer.mared.commands.input.MaredBindRegistry;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.services.logging.LogSettings;
import com.fixmer.mared.services.theme.ThemeService;

/**
 * Контекст для табов настроек.
 *
 * 0.3.0 (Phase B5): перенос legacy gui.settings.SettingsContext в gui2.
 * 0.3.0 (Phase E2a): snapshot больше не публичное поле; под-контексты.
 * 0.3.0 (Phase F2): табы перестают дёргать глобальные реестры напрямую.
 * 0.3.0 (Phase F3a): единая Save/Cancel semantics.
 *   - ВСЁ — draft (включая log filters и keybinds reset).
 *   - ctx.apply() фиксирует draft в глобальное (вызывается onSave).
 *   - Cancel → ctx выбрасывается, draft не применяется.
 */
public final class SettingsContext {

    private final MaredSettings.Snapshot snapshot;

    private final EditorSettingsView   editorView;
    private final LayoutSettingsView   layoutView;
    private final ThemeSettingsView    themeView;
    private final LogsSettingsView     logsView;
    private final KeybindsSettingsView keybindsView;

    public SettingsContext(MaredSettings.Snapshot snapshot) {
        this.snapshot = snapshot;
        this.editorView   = new EditorSettingsView(snapshot);
        this.layoutView   = new LayoutSettingsView(snapshot);
        this.themeView    = new ThemeSettingsView(snapshot);
        this.logsView     = new LogsSettingsView(snapshot);
        this.keybindsView = new KeybindsSettingsView();
    }

    public EditorSettingsView   editor()   { return editorView; }
    public LayoutSettingsView   layout()   { return layoutView; }
    public ThemeSettingsView    theme()    { return themeView; }
    public LogsSettingsView     logs()     { return logsView; }
    public KeybindsSettingsView keybinds() { return keybindsView; }

    /** Только для MaredSettingsScreen.onSave() — package-private. */
    MaredSettings.Snapshot snapshot() { return snapshot; }

    // ============================================================
    //  Apply / Cancel
    // ============================================================

    /**
     * Зафиксировать draft в глобальное состояние. Вызывается
     * MaredSettingsScreen.onSave() (после того как нажали Save).
     *
     * Порядок:
     *   1. snapshot.apply() — editor/layout/theme/log-behavior/server;
     *   2. logs.commit() — draft уровней и категорий;
     *   3. keybinds.commit() — reset, если был запрошен.
     *
     * Одна финальная запись settings.json — внутри snapshot.apply().
     */
    public void apply() {
        snapshot.apply();
        logsView.commit();
        keybindsView.commit();
    }

    // ============================================================
    //  Composite operations
    // ============================================================

    public void resetToDefaults() {
        editorView.resetToDefaults();
        layoutView.resetToDefaults();
        themeView.resetToDefaults();
        logsView.resetToDefaults();
        snapshot.serverMode = MaredSettings.ServerMode.AUTO;
    }

    public void applyLayoutPreset(MaredLayoutPreset p) {
        if (p == null) return;
        layoutView.setPreset(p);

        switch (p) {
            case CLASSIC -> {
                layoutView.setShowSidebar(true);
                layoutView.setShowRightPanel(true);
                layoutView.setShowConsole(true);
                layoutView.setShowStatusBar(true);
                layoutView.setShowToolbar(true);
                layoutView.setSplitEnabled(false);
            }
            case FOCUS -> {
                layoutView.setShowSidebar(false);
                layoutView.setShowRightPanel(false);
                layoutView.setShowConsole(false);
                layoutView.setShowStatusBar(true);
                layoutView.setShowToolbar(true);
                layoutView.setSplitEnabled(false);
            }
            case DEBUGGER -> {
                layoutView.setShowSidebar(false);
                layoutView.setShowRightPanel(true);
                layoutView.setShowConsole(true);
                layoutView.setShowStatusBar(true);
                layoutView.setShowToolbar(true);
                layoutView.setRightMode("INFO");
                layoutView.setSplitEnabled(false);
            }
            case MULTI_FILE -> {
                layoutView.setShowSidebar(true);
                layoutView.setShowRightPanel(false);
                layoutView.setShowConsole(true);
                layoutView.setShowStatusBar(true);
                layoutView.setShowToolbar(true);
                layoutView.setSplitEnabled(true);
            }
        }
    }

    // ============================================================
    //  Editor view
    // ============================================================

    public static final class EditorSettingsView {
        private final MaredSettings.Snapshot s;
        EditorSettingsView(MaredSettings.Snapshot s) { this.s = s; }

        public MaredSettings.AutoIndent autoIndent() { return s.autoIndent; }
        public void setAutoIndent(MaredSettings.AutoIndent v) { s.autoIndent = v; }

        public MaredSettings.IndentStyle indentStyle() { return s.indentStyle; }
        public void setIndentStyle(MaredSettings.IndentStyle v) { s.indentStyle = v; }

        public boolean backspaceRemovesIndent() { return s.backspaceRemovesIndent; }
        public void setBackspaceRemovesIndent(boolean v) { s.backspaceRemovesIndent = v; }

        public MaredSettings.AutoIndent[] allAutoIndentModes() {
            return MaredSettings.AutoIndent.values();
        }

        public MaredSettings.IndentStyle[] allIndentStyles() {
            return MaredSettings.IndentStyle.values();
        }

        void resetToDefaults() {
            s.autoIndent = MaredSettings.AutoIndent.FULL;
            s.indentStyle = MaredSettings.IndentStyle.SPACES_4;
            s.backspaceRemovesIndent = true;
        }
    }

    // ============================================================
    //  Layout view
    // ============================================================

    public static final class LayoutSettingsView {
        private final MaredSettings.Snapshot s;
        LayoutSettingsView(MaredSettings.Snapshot s) { this.s = s; }

        public MaredLayoutPreset preset() {
            try { return MaredLayoutPreset.valueOf(s.layoutPreset); }
            catch (Exception e) { return MaredLayoutPreset.CLASSIC; }
        }
        public void setPreset(MaredLayoutPreset p) {
            if (p != null) s.layoutPreset = p.name();
        }

        public MaredLayoutPreset[] allPresets() {
            return MaredLayoutPreset.values();
        }

        public boolean showSidebar()    { return s.layoutShowSidebar; }
        public void setShowSidebar(boolean v) { s.layoutShowSidebar = v; }

        public boolean showRightPanel() { return s.layoutShowRightPanel; }
        public void setShowRightPanel(boolean v) { s.layoutShowRightPanel = v; }

        public boolean showConsole()    { return s.layoutShowConsole; }
        public void setShowConsole(boolean v) { s.layoutShowConsole = v; }

        public boolean showStatusBar()  { return s.layoutShowStatusBar; }
        public void setShowStatusBar(boolean v) { s.layoutShowStatusBar = v; }

        public boolean showToolbar()    { return s.layoutShowToolbar; }
        public void setShowToolbar(boolean v) { s.layoutShowToolbar = v; }

        public int sidebarWidth()       { return s.layoutSidebarWidth; }
        public void setSidebarWidth(int v) { s.layoutSidebarWidth = v; }

        public int rightPanelWidth()    { return s.layoutRightPanelWidth; }
        public void setRightPanelWidth(int v) { s.layoutRightPanelWidth = v; }

        public int consoleHeight()      { return s.layoutConsoleHeight; }
        public void setConsoleHeight(int v) { s.layoutConsoleHeight = v; }

        public String rightMode()       { return s.layoutRightMode; }
        public void setRightMode(String v) { s.layoutRightMode = v; }

        public boolean splitEnabled()   { return s.layoutSplitEnabled; }
        public void setSplitEnabled(boolean v) { s.layoutSplitEnabled = v; }

        public float splitRatio()       { return s.layoutSplitRatio; }
        public void setSplitRatio(float v) { s.layoutSplitRatio = v; }

        /**
         * 0.3.0 (Phase F3a): сбросить ТОЛЬКО layout, не весь snapshot.
         * Вызывается из MaredLayoutTab — кнопка "Reset layout" не должна
         * трогать editor/theme/logs.
         */
        public void resetToDefaults() {
            s.layoutPreset         = MaredLayoutPreset.CLASSIC.name();
            s.layoutShowSidebar    = true;
            s.layoutShowRightPanel = true;
            s.layoutShowConsole    = true;
            s.layoutShowStatusBar  = true;
            s.layoutShowToolbar    = true;
            s.layoutSidebarWidth    = 220;
            s.layoutRightPanelWidth = 320;
            s.layoutConsoleHeight   = 140;
            s.layoutRightMode       = "INFO";
            s.layoutSplitEnabled    = false;
            s.layoutSplitRatio      = 0.5f;
        }
    }

    // ============================================================
    //  Theme view
    // ============================================================

    public static final class ThemeSettingsView {
        private final MaredSettings.Snapshot s;
        ThemeSettingsView(MaredSettings.Snapshot s) { this.s = s; }

        public String themeId()              { return s.themeId; }
        public void setThemeId(String v)     { s.themeId = v; }

        public boolean monotoneTabs()        { return s.monotoneTabs; }
        public void setMonotoneTabs(boolean v) { s.monotoneTabs = v; }

        public boolean patternsEnabled()     { return s.patternsEnabled; }
        public void setPatternsEnabled(boolean v) { s.patternsEnabled = v; }

        public boolean tornEdgesEnabled()    { return s.tornEdgesEnabled; }
        public void setTornEdgesEnabled(boolean v) { s.tornEdgesEnabled = v; }

        public List<MaredTheme> availableThemes() {
            return ThemeService.get().all();
        }

        public int themeCount() {
            return ThemeService.get().count();
        }

        void resetToDefaults() {
            s.themeId          = "mared";
            s.monotoneTabs     = false;
            s.patternsEnabled  = true;
            s.tornEdgesEnabled = false;
        }
    }

    // ============================================================
    //  Logs view — draft внутри view
    // ============================================================

    public static final class LogsSettingsView {
        private final MaredSettings.Snapshot s;

        // draft-состояние (не применяется до commit())
        private final Set<LogSettings.Level> draftLevels;
        private final Set<String>            draftCategories;

        LogsSettingsView(MaredSettings.Snapshot s) {
            this.s = s;
            this.draftLevels = new LinkedHashSet<>(LogSettings.getEnabledLevels());
            this.draftCategories = new LinkedHashSet<>(LogSettings.getEnabledCategories());
        }

        public boolean logChatToEditor()         { return s.logChatToEditor; }
        public void setLogChatToEditor(boolean v) { s.logChatToEditor = v; }

        public boolean verboseScriptLog()        { return s.verboseScriptLog; }
        public void setVerboseScriptLog(boolean v) { s.verboseScriptLog = v; }

        // ---- draft access ----

        public LogSettings.Level[] allLevels() {
            return LogSettings.Level.values();
        }

        public List<String> allCategories() {
            return List.copyOf(java.util.Arrays.asList(LogSettings.CATEGORIES));
        }

        public boolean levelEnabled(LogSettings.Level l) {
            return draftLevels.contains(l);
        }

        public void toggleLevel(LogSettings.Level l) {
            if (draftLevels.contains(l)) {
                if (draftLevels.size() <= 1) return;
                draftLevels.remove(l);
            } else {
                draftLevels.add(l);
            }
        }

        public boolean categoryEnabled(String cat) {
            return draftCategories.contains(cat);
        }

        public void toggleCategory(String cat) {
            if (draftCategories.contains(cat)) {
                if (draftCategories.size() <= 1) return;
                draftCategories.remove(cat);
            } else {
                draftCategories.add(cat);
            }
        }

        /** Зафиксировать draft в глобальный LogSettings (вызывается из ctx.apply()). */
        void commit() {
            LogSettings.applyDraft(draftLevels, draftCategories);
        }

        void resetToDefaults() {
            s.logChatToEditor  = true;
            s.verboseScriptLog = false;
            draftLevels.clear();
            for (LogSettings.Level l : LogSettings.Level.values()) draftLevels.add(l);
            draftCategories.clear();
            for (String c : LogSettings.CATEGORIES) draftCategories.add(c);
        }
    }

    // ============================================================
    //  Keybinds view — draft внутри view
    // ============================================================

        public static final class KeybindsSettingsView {
        private boolean resetRequested = false;
        private List<KeybindItem> cached;

        KeybindsSettingsView() {}

        /** 0.3.0 (Phase F3c): кэшируем список — render loop не должен
         * строить DTO каждый кадр. Инвалидация при resetRequested. */
        public List<KeybindItem> allBinds() {
            if (cached != null) return cached;

            List<String> keys = MaredBindRegistry.keys();
            List<KeybindItem> out = new ArrayList<>(keys.size());
            for (String key : keys) {
                out.add(new KeybindItem(
                    key,
                    humanize(key),
                    MaredBindRegistry.hasBlocking(key)
                ));
            }
            cached = out;
            return cached;
        }

        public void requestReset() {
            resetRequested = true;
        }

        public boolean isResetRequested() { return resetRequested; }

        void commit() {
            if (resetRequested) {
                MaredBindRegistry.clearAll();
                resetRequested = false;
                cached = null;  // инвалидация
            }
        }

        private static String humanize(String key) {
            if (key == null || key.isEmpty()) return "";
            String s = key.replace('_', ' ').toLowerCase(Locale.ROOT);
            StringBuilder sb = new StringBuilder(s.length());
            boolean cap = true;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (cap) { sb.append(Character.toUpperCase(c)); cap = false; }
                else sb.append(c);
                if (c == ' ') cap = true;
            }
            return sb.toString();
        }
    }
}