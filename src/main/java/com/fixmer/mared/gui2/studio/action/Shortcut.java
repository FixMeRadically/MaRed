package com.fixmer.mared.gui2.studio.action;

import org.lwjgl.glfw.GLFW;

/**
 * Клавиатурный shortcut.
 *
 * 0.3.1: парсер "Ctrl+Shift+S" → {keyCode, modifiers}.
 * Сравнение в EditorActionRegistry.executeByKey(keyCode, modifiers).
 *
 * Модификаторы — точное совпадение, не "хотя бы". Это правильно:
 * Ctrl+S != S != Ctrl+Shift+S.
 *
 * Формат:
 *   modifier (Ctrl|Shift|Alt|Super) — через '+', в любом порядке,
 *   последний сегмент — клавиша.
 *   Регистр: "Ctrl+S" == "CTRL+S" == "ctrl+s".
 *
 * Supported keys:
 *   A-Z, 0-9,
 *   F1..F25,
 *   Esc/Escape, Enter/Return, Tab, Space, Backspace, Delete/Del,
 *   Home, End, PageUp/PgUp, PageDown/PgDn,
 *   Up, Down, Left, Right,
 *   Insert/Ins,
 *   Numpad0..9.
 *
 * Неизвестное — null.
 */
public final class Shortcut {

    public final int keyCode;
    public final int modifiers;

    public Shortcut(int keyCode, int modifiers) {
        this.keyCode = keyCode;
        this.modifiers = modifiers;
    }

    public boolean matches(int keyCode, int modifiers) {
        return this.keyCode == keyCode && this.modifiers == modifiers;
    }

    public boolean isModifierOnly() {
        return keyCode == 0;
    }

    /**
     * Парсит строку. Возвращает null, если пусто или невалидно.
     */
    public static Shortcut parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        int mods = 0;
        String keyPart = null;

        String[] parts = t.split("\\+");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) return null;

            String lower = p.toLowerCase(java.util.Locale.ROOT);
            switch (lower) {
                case "ctrl", "control" -> { mods |= GLFW.GLFW_MOD_CONTROL; continue; }
                case "shift"           -> { mods |= GLFW.GLFW_MOD_SHIFT;   continue; }
                case "alt"             -> { mods |= GLFW.GLFW_MOD_ALT;     continue; }
                case "super", "meta", "cmd", "command"
                                       -> { mods |= GLFW.GLFW_MOD_SUPER;   continue; }
                default -> {}
            }

            if (keyPart != null) return null; // вторая неквалифицированная часть
            keyPart = p;
        }

        if (keyPart == null) return null;
        int code = parseKeyCode(keyPart);
        if (code < 0) return null;
        return new Shortcut(code, mods);
    }

    private static int parseKeyCode(String key) {
        if (key == null || key.isEmpty()) return -1;
        int len = key.length();

        // Одиночные
        if (len == 1) {
            char c = key.charAt(0);
            if (c >= 'a' && c <= 'z') return GLFW.GLFW_KEY_A + (c - 'a');
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }

        // F1..F25
        if (len >= 2 && len <= 3
            && (key.charAt(0) == 'F' || key.charAt(0) == 'f')) {
            int num = 0;
            boolean ok = true;
            for (int i = 1; i < len; i++) {
                char c = key.charAt(i);
                if (c < '0' || c > '9') { ok = false; break; }
                num = num * 10 + (c - '0');
            }
            if (ok && num >= 1 && num <= 25) {
                return GLFW.GLFW_KEY_F1 + (num - 1);
            }
        }

        // Numpad0..9
        if (len == 7
            && key.regionMatches(true, 0, "Numpad", 0, 6)) {
            char c = key.charAt(6);
            if (c >= '0' && c <= '9') {
                return GLFW.GLFW_KEY_KP_0 + (c - '0');
            }
        }

        // Named keys
        switch (key.toLowerCase(java.util.Locale.ROOT)) {
            case "esc": case "escape":
                return GLFW.GLFW_KEY_ESCAPE;
            case "enter": case "return":
                return GLFW.GLFW_KEY_ENTER;
            case "tab":
                return GLFW.GLFW_KEY_TAB;
            case "space":
                return GLFW.GLFW_KEY_SPACE;
            case "backspace":
                return GLFW.GLFW_KEY_BACKSPACE;
            case "delete": case "del":
                return GLFW.GLFW_KEY_DELETE;
            case "insert": case "ins":
                return GLFW.GLFW_KEY_INSERT;
            case "home":
                return GLFW.GLFW_KEY_HOME;
            case "end":
                return GLFW.GLFW_KEY_END;
            case "pageup": case "pgup":
                return GLFW.GLFW_KEY_PAGE_UP;
            case "pagedown": case "pgdn":
                return GLFW.GLFW_KEY_PAGE_DOWN;
            case "up":
                return GLFW.GLFW_KEY_UP;
            case "down":
                return GLFW.GLFW_KEY_DOWN;
            case "left":
                return GLFW.GLFW_KEY_LEFT;
            case "right":
                return GLFW.GLFW_KEY_RIGHT;
        }
        return -1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) sb.append("Ctrl+");
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0)   sb.append("Shift+");
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0)     sb.append("Alt+");
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0)   sb.append("Super+");
        sb.append(nameOf(keyCode));
        return sb.toString();
    }

    private static String nameOf(int code) {
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) ('A' + (code - GLFW.GLFW_KEY_A)));
        }
        if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) ('0' + (code - GLFW.GLFW_KEY_0)));
        }
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) {
            return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
        }
        return switch (code) {
            case GLFW.GLFW_KEY_ESCAPE    -> "Esc";
            case GLFW.GLFW_KEY_ENTER     -> "Enter";
            case GLFW.GLFW_KEY_TAB       -> "Tab";
            case GLFW.GLFW_KEY_SPACE     -> "Space";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_DELETE    -> "Delete";
            case GLFW.GLFW_KEY_INSERT    -> "Insert";
            case GLFW.GLFW_KEY_HOME      -> "Home";
            case GLFW.GLFW_KEY_END       -> "End";
            case GLFW.GLFW_KEY_PAGE_UP   -> "PageUp";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PageDown";
            case GLFW.GLFW_KEY_UP        -> "Up";
            case GLFW.GLFW_KEY_DOWN      -> "Down";
            case GLFW.GLFW_KEY_LEFT      -> "Left";
            case GLFW.GLFW_KEY_RIGHT     -> "Right";
            default -> "Key#" + code;
        };
    }
}