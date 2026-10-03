package com.fixmer.mared;

import com.fixmer.mared.services.settings.LayoutSettings;
import com.fixmer.mared.services.settings.SettingsService;

/**
 * Глобальные настройки Mared. Тонкий фасад над SettingsService.
 *
 * 0.3.2:
 *   - isReducedMotion() — аудит #76/#104.
 *   - getMaRedButtonCorner()/getMaRedButtonOffset() — аудит #114.
 */
public final class MaredSettings {

    private MaredSettings() {}

    public enum AutoIndent { OFF, SIMPLE, SMART, FULL }
    public enum IndentStyle { TAB, SPACES_4, SPACES_2 }
    public enum ServerMode { AUTO, CLIENT_ONLY, FULL }

    // ============================================================
    //  EDITOR
    // ============================================================

    public static AutoIndent getAutoIndent() {
        return SettingsService.get().editor().autoIndent();
    }

    public static void setAutoIndent(AutoIndent value) {
        SettingsService.get().editor().setAutoIndent(value);
        save();
    }

    public static IndentStyle getIndentStyle() {
        return SettingsService.get().editor().indentStyle();
    }

    public static void setIndentStyle(IndentStyle value) {
        SettingsService.get().editor().setIndentStyle(value);
        save();
    }

    public static boolean isBackspaceRemovesIndent() {
        return SettingsService.get().editor().backspaceRemovesIndent();
    }

    public static void setBackspaceRemovesIndent(boolean value) {
        SettingsService.get().editor().setBackspaceRemovesIndent(value);
        save();
    }

    public static String indentUnit() {
        return SettingsService.get().editor().indentUnit();
    }

    // ============================================================
    //  SERVER
    // ============================================================

    public static ServerMode getServerMode() {
        return SettingsService.get().server().serverMode();
    }

    public static void setServerMode(ServerMode value) {
        SettingsService.get().server().setServerMode(value);
        save();
    }

    // ============================================================
    //  LOG BEHAVIOR
    // ============================================================

    public static boolean isLogChatToEditor() {
        return SettingsService.get().logBehavior().logChatToEditor();
    }

    public static void setLogChatToEditor(boolean value) {
        SettingsService.get().logBehavior().setLogChatToEditor(value);
        save();
    }

    public static boolean isVerboseScriptLog() {
        return SettingsService.get().logBehavior().verboseScriptLog();
    }

    public static void setVerboseScriptLog(boolean value) {
        SettingsService.get().logBehavior().setVerboseScriptLog(value);
        save();
    }

    // ============================================================
    //  THEME
    // ============================================================

    public static String getThemeId() {
        return SettingsService.get().theme().themeId();
    }

    public static void setThemeId(String value) {
        SettingsService.get().theme().setThemeId(value);
    }

    public static boolean isMonotoneTabs() {
        return SettingsService.get().theme().monotoneTabs();
    }

    public static void setMonotoneTabs(boolean value) {
        SettingsService.get().theme().setMonotoneTabs(value);
    }

    public static boolean isPatternsEnabled() {
        return SettingsService.get().theme().patternsEnabled();
    }

    public static void setPatternsEnabled(boolean value) {
        SettingsService.get().theme().setPatternsEnabled(value);
    }

    public static boolean isTornEdgesEnabled() {
        return SettingsService.get().theme().tornEdgesEnabled();
    }

    public static void setTornEdgesEnabled(boolean value) {
        SettingsService.get().theme().setTornEdgesEnabled(value);
    }

    // 0.3.2 (audit #76 / #104)
    public static boolean isReducedMotion() {
        return SettingsService.get().theme().reducedMotion();
    }

    public static void setReducedMotion(boolean value) {
        SettingsService.get().theme().setReducedMotion(value);
    }

    // ============================================================
    //  SETTINGS TAB
    // ============================================================

    public static String getSettingsActiveTab() {
        return SettingsService.get().layout().settingsActiveTab();
    }

    public static void setSettingsActiveTab(String value) {
        SettingsService.get().layout().setSettingsActiveTab(value);
        save();
    }

    // ============================================================
    //  LAYOUT
    // ============================================================

    public static MaredLayoutPreset getLayoutPreset() {
        return SettingsService.get().layout().preset();
    }

    public static void setLayoutPreset(MaredLayoutPreset value) {
        SettingsService.get().layout().setPreset(value);
    }

    public static boolean isLayoutShowSidebar() {
        return SettingsService.get().layout().showSidebar();
    }

    public static void setLayoutShowSidebar(boolean v) {
        SettingsService.get().layout().setShowSidebar(v);
    }

    public static boolean isLayoutShowRightPanel() {
        return SettingsService.get().layout().showRightPanel();
    }

    public static void setLayoutShowRightPanel(boolean v) {
        SettingsService.get().layout().setShowRightPanel(v);
    }

    public static boolean isLayoutShowConsole() {
        return SettingsService.get().layout().showConsole();
    }

    public static void setLayoutShowConsole(boolean v) {
        SettingsService.get().layout().setShowConsole(v);
    }

    public static boolean isLayoutShowStatusBar() {
        return SettingsService.get().layout().showStatusBar();
    }

    public static void setLayoutShowStatusBar(boolean v) {
        SettingsService.get().layout().setShowStatusBar(v);
    }

    public static boolean isLayoutShowToolbar() {
        return SettingsService.get().layout().showToolbar();
    }

    public static void setLayoutShowToolbar(boolean v) {
        SettingsService.get().layout().setShowToolbar(v);
    }

    public static int getLayoutSidebarWidth() {
        return SettingsService.get().layout().sidebarWidth();
    }

    public static void setLayoutSidebarWidth(int v) {
        SettingsService.get().layout().setSidebarWidth(v);
    }

    public static int getLayoutRightPanelWidth() {
        return SettingsService.get().layout().rightPanelWidth();
    }

    public static void setLayoutRightPanelWidth(int v) {
        SettingsService.get().layout().setRightPanelWidth(v);
    }

    public static int getLayoutConsoleHeight() {
        return SettingsService.get().layout().consoleHeight();
    }

    public static void setLayoutConsoleHeight(int v) {
        SettingsService.get().layout().setConsoleHeight(v);
    }

    public static String getLayoutRightMode() {
        return SettingsService.get().layout().rightMode();
    }

    public static void setLayoutRightMode(String v) {
        SettingsService.get().layout().setRightMode(v);
    }

    public static boolean isLayoutSplitEnabled() {
        return SettingsService.get().layout().splitEnabled();
    }

    public static void setLayoutSplitEnabled(boolean v) {
        SettingsService.get().layout().setSplitEnabled(v);
    }

    public static float getLayoutSplitRatio() {
        return SettingsService.get().layout().splitRatio();
    }

    public static void setLayoutSplitRatio(float v) {
        SettingsService.get().layout().setSplitRatio(v);
    }

    public static String getLayoutSplitFileRight() {
        return SettingsService.get().layout().splitFileRight();
    }

    public static void setLayoutSplitFileRight(String v) {
        SettingsService.get().layout().setSplitFileRight(v);
    }

    public static int getLayoutActiveColumn() {
        return SettingsService.get().layout().activeColumn();
    }

    public static void setLayoutActiveColumn(int v) {
        SettingsService.get().layout().setActiveColumn(v);
    }

    public static String getLayoutActiveTab() {
        return SettingsService.get().layout().activeTab();
    }

    public static void setLayoutActiveTab(String v) {
        SettingsService.get().layout().setActiveTab(v);
    }

    public static String getLayoutSidebarState() {
        return SettingsService.get().layout().sidebarState();
    }

    public static void setLayoutSidebarState(String v) {
        SettingsService.get().layout().setSidebarState(v);
    }

    public static boolean isLayoutLogCollapsed() {
        return SettingsService.get().layout().logCollapsed();
    }

    public static void setLayoutLogCollapsed(boolean v) {
        SettingsService.get().layout().setLogCollapsed(v);
    }

    // ============================================================
    //  MARED BUTTON (PauseScreen / TitleScreen) — 0.3.2
    // ============================================================

    public static LayoutSettings.ButtonCorner getMaRedButtonCorner() {
        return SettingsService.get().layout().buttonCorner();
    }

    public static void setMaRedButtonCorner(LayoutSettings.ButtonCorner c) {
        SettingsService.get().layout().setButtonCorner(c);
        save();
    }

    public static int getMaRedButtonOffset() {
        return SettingsService.get().layout().buttonOffset();
    }

    public static void setMaRedButtonOffset(int v) {
        SettingsService.get().layout().setButtonOffset(v);
        save();
    }

    // ============================================================
    //  LOAD / SAVE
    // ============================================================

    public static synchronized void load() {
        SettingsService.get().ensureLoaded();
    }

    public static synchronized void save() {
        SettingsService.get().save();
    }

    public static void flush() { save(); }

    // ============================================================
    //  Snapshot
    // ============================================================

    @Deprecated
    public static class Snapshot {

        public AutoIndent autoIndent;
        public IndentStyle indentStyle;
        public ServerMode serverMode;

        public boolean backspaceRemovesIndent;
        public boolean logChatToEditor;
        public boolean verboseScriptLog;

        public String themeId;
        public boolean monotoneTabs;
        public boolean patternsEnabled;
        public boolean tornEdgesEnabled;
        public boolean reducedMotion;

        public String layoutPreset;
        public boolean layoutShowSidebar;
        public boolean layoutShowRightPanel;
        public boolean layoutShowConsole;
        public boolean layoutShowStatusBar;
        public boolean layoutShowToolbar;

        public int layoutSidebarWidth;
        public int layoutRightPanelWidth;
        public int layoutConsoleHeight;

        public String layoutRightMode;
        public boolean layoutSplitEnabled;
        public float layoutSplitRatio;
        public String layoutSplitFileRight;
        public int layoutActiveColumn;
        public String layoutActiveTab;
        public String layoutSidebarState;
        public boolean layoutLogCollapsed;

        public String maredButtonCorner = "TOP_RIGHT";
        public int maredButtonOffset = 10;

        public static Snapshot capture() {
            Snapshot s = new Snapshot();

            s.autoIndent  = getAutoIndent();
            s.indentStyle = getIndentStyle();
            s.serverMode  = getServerMode();

            s.backspaceRemovesIndent = isBackspaceRemovesIndent();
            s.logChatToEditor        = isLogChatToEditor();
            s.verboseScriptLog       = isVerboseScriptLog();

            s.themeId          = getThemeId();
            s.monotoneTabs     = isMonotoneTabs();
            s.patternsEnabled  = isPatternsEnabled();
            s.tornEdgesEnabled = isTornEdgesEnabled();
            s.reducedMotion    = isReducedMotion();

            s.layoutPreset         = getLayoutPreset().name();
            s.layoutShowSidebar    = isLayoutShowSidebar();
            s.layoutShowRightPanel = isLayoutShowRightPanel();
            s.layoutShowConsole    = isLayoutShowConsole();
            s.layoutShowStatusBar  = isLayoutShowStatusBar();
            s.layoutShowToolbar    = isLayoutShowToolbar();

            s.layoutSidebarWidth    = getLayoutSidebarWidth();
            s.layoutRightPanelWidth = getLayoutRightPanelWidth();
            s.layoutConsoleHeight   = getLayoutConsoleHeight();

            s.layoutRightMode      = getLayoutRightMode();
            s.layoutSplitEnabled   = isLayoutSplitEnabled();
            s.layoutSplitRatio     = getLayoutSplitRatio();
            s.layoutSplitFileRight = getLayoutSplitFileRight();
            s.layoutActiveColumn   = getLayoutActiveColumn();
            s.layoutActiveTab      = getLayoutActiveTab();
            s.layoutSidebarState   = getLayoutSidebarState();
            s.layoutLogCollapsed   = isLayoutLogCollapsed();

            s.maredButtonCorner = getMaRedButtonCorner().name();
            s.maredButtonOffset = getMaRedButtonOffset();

            return s;
        }

        public void apply() {
            SettingsService.get().commit(this);
        }
    }
}