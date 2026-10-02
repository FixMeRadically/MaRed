package com.fixmer.mared.services.settings;

import com.fixmer.mared.MaredSettings;

/**
 * Настройки редактора.
 *
 * 0.3.0 (Phase F1): вынесено из MaredSettings. Хранит только состояние.
 * Сохранение (save) — на уровне MaredSettings-фасада.
 */
public final class EditorSettings {

    private MaredSettings.AutoIndent autoIndent = MaredSettings.AutoIndent.FULL;
    private MaredSettings.IndentStyle indentStyle = MaredSettings.IndentStyle.SPACES_4;
    private boolean backspaceRemovesIndent = true;

    public MaredSettings.AutoIndent autoIndent() { return autoIndent; }
    public void setAutoIndent(MaredSettings.AutoIndent value) {
        autoIndent = value == null ? MaredSettings.AutoIndent.FULL : value;
    }

    public MaredSettings.IndentStyle indentStyle() { return indentStyle; }
    public void setIndentStyle(MaredSettings.IndentStyle value) {
        indentStyle = value == null ? MaredSettings.IndentStyle.SPACES_4 : value;
    }

    public boolean backspaceRemovesIndent() { return backspaceRemovesIndent; }
    public void setBackspaceRemovesIndent(boolean value) {
        backspaceRemovesIndent = value;
    }

    public String indentUnit() {
        return switch (indentStyle) {
            case TAB      -> "\t";
            case SPACES_4 -> "    ";
            case SPACES_2 -> "  ";
        };
    }

    void resetToDefaults() {
        autoIndent = MaredSettings.AutoIndent.FULL;
        indentStyle = MaredSettings.IndentStyle.SPACES_4;
        backspaceRemovesIndent = true;
    }
}