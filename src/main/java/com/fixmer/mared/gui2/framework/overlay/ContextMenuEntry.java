package com.fixmer.mared.gui2.framework.overlay;

/**
 * Запись контекстного меню.
 *
 * 0.3.1:
 *   Статический фабричный метод для разделителя переименован в
 *   divider() — record автоматически генерирует accessor separator(),
 *   который возвращает boolean, а объявленный нами static
 *   separator() возвращал ContextMenuEntry и конфликтовал по
 *   сигнатуре с accessor'ом.
 *
 *   label поддерживает "\t" для разделения левой части и правой (hotkey):
 *     "Save\tCtrl+S" → слева "Save", справа "Ctrl+S".
 */
public record ContextMenuEntry(
    String label,
    Runnable action,
    boolean enabled,
    boolean separator
) {

    public static ContextMenuEntry of(String label, Runnable action) {
        return new ContextMenuEntry(label, action, true, false);
    }

    public static ContextMenuEntry disabled(String label) {
        return new ContextMenuEntry(label, null, false, false);
    }

    /** Разделитель. Раньше был separator() — конфликт с accessor'ом. */
    public static ContextMenuEntry divider() {
        return new ContextMenuEntry("", null, false, true);
    }

    public static ContextMenuEntry hotkey(String label, String key, Runnable action) {
        return new ContextMenuEntry(label + "\t" + key, action, true, false);
    }
}