package com.fixmer.mared.services.settings;

import com.fixmer.mared.MaredLayoutPreset;

/**
 * Настройки layout + UI state редактора.
 *
 * 0.3.2 (audit #114): добавлены buttonCorner/buttonOffset —
 * позиция кнопки MaRed на PauseScreen/TitleScreen.
 */
public final class LayoutSettings {

    public enum ButtonCorner {
        TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT;

        public static ButtonCorner fromId(String id) {
            if (id == null) return TOP_RIGHT;
            try { return valueOf(id); }
            catch (IllegalArgumentException e) { return TOP_RIGHT; }
        }
    }

    private MaredLayoutPreset preset = MaredLayoutPreset.CLASSIC;

    private boolean showSidebar    = true;
    private boolean showRightPanel = true;
    private boolean showConsole    = true;
    private boolean showStatusBar  = true;
    private boolean showToolbar    = true;

    private int sidebarWidth    = LayoutConstraints.SIDEBAR_DEFAULT;
    private int rightPanelWidth = LayoutConstraints.RIGHT_PANEL_DEFAULT;
    private int consoleHeight   = LayoutConstraints.CONSOLE_DEFAULT;

    private String  rightMode      = "INFO";
    private boolean splitEnabled   = false;
    private float   splitRatio     = 0.5f;
    private String  splitFileRight = "";
    private int     activeColumn   = 0;
    private String  activeTab      = "scripts";
    private String  sidebarState   = "CLOSED";
    private boolean logCollapsed   = false;

    private String settingsActiveTab = "layout";

    // 0.3.2
    private ButtonCorner buttonCorner = ButtonCorner.TOP_RIGHT;
    private int buttonOffset = 10;

    public MaredLayoutPreset preset() { return preset; }
    public void setPreset(MaredLayoutPreset value) {
        preset = value == null ? MaredLayoutPreset.CLASSIC : value;
    }

    public boolean showSidebar()    { return showSidebar; }
    public void setShowSidebar(boolean v)    { showSidebar = v; }

    public boolean showRightPanel() { return showRightPanel; }
    public void setShowRightPanel(boolean v) { showRightPanel = v; }

    public boolean showConsole()    { return showConsole; }
    public void setShowConsole(boolean v)    { showConsole = v; }

    public boolean showStatusBar()  { return showStatusBar; }
    public void setShowStatusBar(boolean v)  { showStatusBar = v; }

    public boolean showToolbar()    { return showToolbar; }
    public void setShowToolbar(boolean v)    { showToolbar = v; }

    public int sidebarWidth()    { return sidebarWidth; }
    public void setSidebarWidth(int v)    { sidebarWidth = LayoutConstraints.clampSidebar(v); }

    public int rightPanelWidth() { return rightPanelWidth; }
    public void setRightPanelWidth(int v) { rightPanelWidth = LayoutConstraints.clampRightPanel(v); }

    public int consoleHeight()   { return consoleHeight; }
    public void setConsoleHeight(int v)   { consoleHeight = LayoutConstraints.clampConsole(v); }

    public String rightMode()    { return rightMode; }
    public void setRightMode(String v)    { rightMode = v == null ? "INFO" : v; }

    public boolean splitEnabled() { return splitEnabled; }
    public void setSplitEnabled(boolean v) { splitEnabled = v; }

    public float splitRatio() { return splitRatio; }
    public void setSplitRatio(float v) {
        if (!Float.isFinite(v)) v = 0.5f;
        splitRatio = Math.max(0.15f, Math.min(0.85f, v));
    }

    public String splitFileRight() { return splitFileRight; }
    public void setSplitFileRight(String v) { splitFileRight = v == null ? "" : v; }

    public int activeColumn()  { return activeColumn; }
    public void setActiveColumn(int v) { activeColumn = Math.max(0, v); }

    public String activeTab()  { return activeTab; }
    public void setActiveTab(String v) { activeTab = v == null ? "scripts" : v; }

    public String sidebarState() { return sidebarState; }
    public void setSidebarState(String v) { sidebarState = v == null ? "CLOSED" : v; }

    public boolean logCollapsed() { return logCollapsed; }
    public void setLogCollapsed(boolean v) { logCollapsed = v; }

    public String settingsActiveTab() { return settingsActiveTab; }
    public void setSettingsActiveTab(String v) {
        settingsActiveTab = v == null ? "" : v;
    }

    // 0.3.2
    public ButtonCorner buttonCorner() { return buttonCorner; }
    public void setButtonCorner(ButtonCorner c) {
        buttonCorner = c == null ? ButtonCorner.TOP_RIGHT : c;
    }

    public int buttonOffset() { return buttonOffset; }
    public void setButtonOffset(int v) {
        buttonOffset = Math.max(0, Math.min(100, v));
    }

    void resetToDefaults() {
        preset         = MaredLayoutPreset.CLASSIC;
        showSidebar    = true;
        showRightPanel = true;
        showConsole    = true;
        showStatusBar  = true;
        showToolbar    = true;
        sidebarWidth    = LayoutConstraints.SIDEBAR_DEFAULT;
        rightPanelWidth = LayoutConstraints.RIGHT_PANEL_DEFAULT;
        consoleHeight   = LayoutConstraints.CONSOLE_DEFAULT;
        rightMode       = "INFO";
        splitEnabled    = false;
        splitRatio      = 0.5f;
        splitFileRight  = "";
        activeColumn    = 0;
        activeTab       = "scripts";
        sidebarState    = "CLOSED";
        logCollapsed    = false;
        settingsActiveTab = "layout";
        buttonCorner    = ButtonCorner.TOP_RIGHT;
        buttonOffset    = 10;
    }
}