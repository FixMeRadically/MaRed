package com.fixmer.mared;

/**
 * Пресеты раскладки редактора.
 *
 * 0.3.0 (Stage B7): перенесён из gui.editor.layout.
 * 0.3.1: enum больше не хранит пользовательские строки. Вместо
 * displayName/description — ключи локализации. UI резолвит их через
 * MaredLang.get().
 */
public enum MaredLayoutPreset {

    CLASSIC,
    FOCUS,
    DEBUGGER,
    MULTI_FILE;

    public String displayNameKey() {
        return "mared.settings.layout.preset."
            + name().toLowerCase(java.util.Locale.ROOT) + ".name";
    }

    public String descriptionKey() {
        return "mared.settings.layout.preset."
            + name().toLowerCase(java.util.Locale.ROOT) + ".desc";
    }

    public static MaredLayoutPreset fromId(String id) {
        if (id == null) return CLASSIC;
        try {
            return valueOf(id);
        } catch (IllegalArgumentException e) {
            return CLASSIC;
        }
    }
}