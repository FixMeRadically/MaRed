package com.fixmer.mared.gui2.settings;

import com.fixmer.mared.MaredSettings;

/**
 * Draft-view настроек редактора.
 *
 * 0.3.2: вынесено из SettingsContext (был nested-класс).
 * Живёт только на время открытого MaredSettingsScreen; при
 * Cancel мусорится вместе с SettingsContext.
 */
public final class EditorSettingsView {

    private final MaredSettings.Snapshot s;

    EditorSettingsView(MaredSettings.Snapshot s) {
        this.s = s;
    }

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

    /** Только для SettingsContext.resetToDefaults(). */
    void resetToDefaults() {
        s.autoIndent = MaredSettings.AutoIndent.FULL;
        s.indentStyle = MaredSettings.IndentStyle.SPACES_4;
        s.backspaceRemovesIndent = true;
    }
}