package com.fixmer.mared.gui2.settings;

import com.fixmer.mared.MaredLayoutPreset;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.services.settings.LayoutConstraints;

/**
 * Контекст для табов настроек.
 *
 * 0.3.2:
 *   5 view-классов вынесены в top-level файлы (EditorSettingsView,
 *   LayoutSettingsView, ThemeSettingsView, LogsSettingsView,
 *   KeybindsSettingsView). SettingsContext теперь только держит
 *   ссылки и координирует lifecycle.
 *
 *   527 строк → ~120 строк.
 *
 *    Границы:
 *      - Tab видит только свой view (ctx.editor(), ctx.theme(), ...).
 *      - Ни один tab не видит снапшот напрямую.
 *      - Ни один tab не дёргает глобальные реестры напрямую.
 *
 *    Draft-модель:
 *      - Все изменения идут в Snapshot (nested в MaredSettings),
 *        пока открыт SettingsScreen.
 *      - apply() фиксирует снапшот + commit'ит logs/keybinds.
 *      - Cancel → SettingsContext выбрасывается.
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

    MaredSettings.Snapshot snapshot() { return snapshot; }

    // ============================================================
    //  Apply / Cancel
    // ============================================================

    /**
     * Зафиксировать draft в глобальное состояние.
     * Вызывается MaredSettingsScreen.onSave().
     */
    public void apply() {
        snapshot.apply();
        logsView.commit();
        keybindsView.commit();
    }

    // ============================================================
    //  Reset
    // ============================================================

    public void resetToDefaults() {
        editorView.resetToDefaults();
        layoutView.resetToDefaults();
        themeView.resetToDefaults();
        logsView.resetToDefaults();
        snapshot.serverMode = MaredSettings.ServerMode.AUTO;
    }

    // ============================================================
    //  Layout preset
    // ============================================================

    /**
     * Детерминированный preset: все поля layout перезаписываются.
     */
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
                layoutView.setSidebarWidth(LayoutConstraints.SIDEBAR_DEFAULT);
                layoutView.setRightPanelWidth(LayoutConstraints.RIGHT_PANEL_DEFAULT);
                layoutView.setConsoleHeight(LayoutConstraints.CONSOLE_DEFAULT);
                layoutView.setRightMode("INFO");
                layoutView.setSplitEnabled(false);
                layoutView.setSplitRatio(0.5f);
                layoutView.setActiveTab("scripts");
                layoutView.setSidebarState("CLOSED");
                layoutView.setActiveColumn(0);
            }
            case FOCUS -> {
                layoutView.setShowSidebar(false);
                layoutView.setShowRightPanel(false);
                layoutView.setShowConsole(false);
                layoutView.setShowStatusBar(true);
                layoutView.setShowToolbar(true);
                layoutView.setSidebarWidth(LayoutConstraints.SIDEBAR_DEFAULT);
                layoutView.setRightPanelWidth(LayoutConstraints.RIGHT_PANEL_DEFAULT);
                layoutView.setConsoleHeight(LayoutConstraints.CONSOLE_DEFAULT);
                layoutView.setRightMode("INFO");
                layoutView.setSplitEnabled(false);
                layoutView.setSplitRatio(0.5f);
                layoutView.setActiveTab("scripts");
                layoutView.setSidebarState("CLOSED");
                layoutView.setActiveColumn(0);
            }
            case DEBUGGER -> {
                layoutView.setShowSidebar(false);
                layoutView.setShowRightPanel(true);
                layoutView.setShowConsole(true);
                layoutView.setShowStatusBar(true);
                layoutView.setShowToolbar(true);
                layoutView.setSidebarWidth(LayoutConstraints.SIDEBAR_DEFAULT);
                layoutView.setRightPanelWidth(LayoutConstraints.RIGHT_PANEL_DEFAULT);
                layoutView.setConsoleHeight(LayoutConstraints.CONSOLE_DEFAULT);
                layoutView.setRightMode("INFO");
                layoutView.setSplitEnabled(false);
                layoutView.setSplitRatio(0.5f);
                layoutView.setActiveTab("scripts");
                layoutView.setSidebarState("CLOSED");
                layoutView.setActiveColumn(0);
            }
            case MULTI_FILE -> {
                layoutView.setShowSidebar(true);
                layoutView.setShowRightPanel(false);
                layoutView.setShowConsole(true);
                layoutView.setShowStatusBar(true);
                layoutView.setShowToolbar(true);
                layoutView.setSidebarWidth(LayoutConstraints.SIDEBAR_DEFAULT);
                layoutView.setRightPanelWidth(LayoutConstraints.RIGHT_PANEL_DEFAULT);
                layoutView.setConsoleHeight(LayoutConstraints.CONSOLE_DEFAULT);
                layoutView.setRightMode("INFO");
                layoutView.setSplitEnabled(true);
                layoutView.setSplitRatio(0.5f);
                layoutView.setActiveTab("scripts");
                layoutView.setSidebarState("CLOSED");
                layoutView.setActiveColumn(0);
            }
        }
    }
}