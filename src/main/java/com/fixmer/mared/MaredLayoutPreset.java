package com.fixmer.mared;

/**
 * Пресеты раскладки редактора.
 *
 * 0.3.0 (Stage B7): перенесён из gui.editor.layout.MaredLayoutPreset
 * в корневой пакет com.fixmer.mared. Причина — MaredSettings (mod-класс,
 * не должен зависеть от gui/) использует его. Теперь gui/ можно удалить
 * целиком без потери этой зависимости.
 */
public enum MaredLayoutPreset {

    CLASSIC("Classic", "Всё видно сразу"),
    FOCUS("Focus", "Только редактор"),
    DEBUGGER("Debugger", "Отладка при Run"),
    MULTI_FILE("Multi-file", "Несколько файлов");

    public final String displayName;
    public final String description;

    MaredLayoutPreset(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public static MaredLayoutPreset fromId(String id) {
        if (id == null) return CLASSIC;
        try { return valueOf(id); }
        catch (IllegalArgumentException e) { return CLASSIC; }
    }
}