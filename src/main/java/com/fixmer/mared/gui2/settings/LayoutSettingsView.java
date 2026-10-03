package com.fixmer.mared.gui2.settings;

import com.fixmer.mared.MaredLayoutPreset;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.services.settings.LayoutConstraints;
import com.fixmer.mared.services.settings.LayoutSettings.ButtonCorner;

/**
 * Draft-view настроек layout'а.
 *
 * 0.3.2: вынесено из SettingsContext.
 * 0.3.2 (audit #114): buttonCorner/buttonOffset — позиция кнопки MaRed.
 *
 * resetToDefaults() — public, потому что вызывается из MaredLayoutTab
 * (другой пакет, gui2.settings.tabs).
 */
public final class LayoutSettingsView {

    private final MaredSettings.Snapshot s;

    LayoutSettingsView(MaredSettings.Snapshot s) {
        this.s = s;
    }

    // ---- Preset ----

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

    // ---- Visibility toggles ----

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

    // ---- Sizes ----

    public int sidebarWidth()       { return s.layoutSidebarWidth; }
    public void setSidebarWidth(int v) { s.layoutSidebarWidth = v; }

    public int rightPanelWidth()    { return s.layoutRightPanelWidth; }
    public void setRightPanelWidth(int v) { s.layoutRightPanelWidth = v; }

    public int consoleHeight()      { return s.layoutConsoleHeight; }
    public void setConsoleHeight(int v) { s.layoutConsoleHeight = v; }

    // ---- Split ----

    public String rightMode()       { return s.layoutRightMode; }
    public void setRightMode(String v) { s.layoutRightMode = v; }

    public boolean splitEnabled()   { return s.layoutSplitEnabled; }
    public void setSplitEnabled(boolean v) { s.layoutSplitEnabled = v; }

    public float splitRatio()       { return s.layoutSplitRatio; }
    public void setSplitRatio(float v) { s.layoutSplitRatio = v; }

    public int activeColumn()       { return s.layoutActiveColumn; }
    public void setActiveColumn(int v) { s.layoutActiveColumn = v; }

    public String activeTab()       { return s.layoutActiveTab; }
    public void setActiveTab(String v) { s.layoutActiveTab = v; }

    public String sidebarState()    { return s.layoutSidebarState; }
    public void setSidebarState(String v) { s.layoutSidebarState = v; }

    // ---- MaRed button position (0.3.2 / #114) ----

    public ButtonCorner buttonCorner() {
        return ButtonCorner.fromId(s.maredButtonCorner);
    }
    public void setButtonCorner(ButtonCorner c) {
        if (c != null) s.maredButtonCorner = c.name();
    }

    public int buttonOffset()       { return s.maredButtonOffset; }
    public void setButtonOffset(int v) {
        s.maredButtonOffset = Math.max(0, Math.min(100, v));
    }

    public ButtonCorner[] allCorners() {
        return ButtonCorner.values();
    }

    // ---- Reset ----

    /**
     * Public: вызывается из MaredLayoutTab для кнопки "Reset layout".
     * Также вызывается внутри пакета из SettingsContext.resetToDefaults().
     */
    public void resetToDefaults() {
        s.layoutPreset         = MaredLayoutPreset.CLASSIC.name();
        s.layoutShowSidebar    = true;
        s.layoutShowRightPanel = true;
        s.layoutShowConsole    = true;
        s.layoutShowStatusBar  = true;
        s.layoutShowToolbar    = true;
        s.layoutSidebarWidth    = LayoutConstraints.SIDEBAR_DEFAULT;
        s.layoutRightPanelWidth = LayoutConstraints.RIGHT_PANEL_DEFAULT;
        s.layoutConsoleHeight   = LayoutConstraints.CONSOLE_DEFAULT;
        s.layoutRightMode       = "INFO";
        s.layoutSplitEnabled    = false;
        s.layoutSplitRatio      = 0.5f;
        s.layoutActiveColumn    = 0;
        s.layoutActiveTab       = "scripts";
        s.layoutSidebarState    = "CLOSED";
        s.maredButtonCorner     = ButtonCorner.TOP_RIGHT.name();
        s.maredButtonOffset     = 10;
    }
}