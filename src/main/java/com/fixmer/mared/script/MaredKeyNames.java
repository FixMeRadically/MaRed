package com.fixmer.mared.script;

import org.lwjgl.glfw.GLFW;

/**
 * Сопоставление имён клавиш (R, F5, Ctrl+S) с GLFW-кодами и модификаторами.
 */
public final class MaredKeyNames {

    private MaredKeyNames() {}

    /** Разобранная клавиша. */
    public static final class ParsedKey {
        public final int keyCode;
        public final int modifiers;   // битовая маска GLFW_MOD_*
        public final String raw;      // как было записано в bind

        public ParsedKey(int keyCode, int modifiers, String raw) {
            this.keyCode = keyCode;
            this.modifiers = modifiers;
            this.raw = raw;
        }
    }

    /** Разобрать строку вроде "Ctrl+Shift+S", "F5", "Space". null — если не распознано. */
    public static ParsedKey parse(String name) {
        if (name == null || name.isEmpty()) return null;
        String raw = name;
        int mods = 0;

        // Разбиваем по '+'
        String[] parts = name.split("\\+");
        for (int i = 0; i < parts.length - 1; i++) {
            String m = parts[i].trim().toLowerCase();
            switch (m) {
                case "ctrl", "control" -> mods |= GLFW.GLFW_MOD_CONTROL;
                case "shift"           -> mods |= GLFW.GLFW_MOD_SHIFT;
                case "alt"             -> mods |= GLFW.GLFW_MOD_ALT;
                case "super", "meta"   -> mods |= GLFW.GLFW_MOD_SUPER;
                default -> { return null; }
            }
        }
        String keyPart = parts[parts.length - 1].trim();
        int code = keyCode(keyPart);
        if (code < 0) return null;
        return new ParsedKey(code, mods, raw);
    }

    /** Код GLFW по имени. -1 — если не найдено. */
    public static int keyCode(String name) {
        if (name == null || name.isEmpty()) return -1;
        String n = name.trim();

        // Одна буква
        if (n.length() == 1) {
            char c = Character.toUpperCase(n.charAt(0));
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }

        // Специальные
        switch (n.toLowerCase()) {
            case "space":      return GLFW.GLFW_KEY_SPACE;
            case "enter":      return GLFW.GLFW_KEY_ENTER;
            case "return":     return GLFW.GLFW_KEY_ENTER;
            case "escape":     return GLFW.GLFW_KEY_ESCAPE;
            case "esc":        return GLFW.GLFW_KEY_ESCAPE;
            case "tab":        return GLFW.GLFW_KEY_TAB;
            case "backspace":  return GLFW.GLFW_KEY_BACKSPACE;
            case "delete":     return GLFW.GLFW_KEY_DELETE;
            case "insert":     return GLFW.GLFW_KEY_INSERT;
            case "home":       return GLFW.GLFW_KEY_HOME;
            case "end":        return GLFW.GLFW_KEY_END;
            case "pageup":     return GLFW.GLFW_KEY_PAGE_UP;
            case "pagedown":   return GLFW.GLFW_KEY_PAGE_DOWN;
            case "up":         return GLFW.GLFW_KEY_UP;
            case "down":       return GLFW.GLFW_KEY_DOWN;
            case "left":       return GLFW.GLFW_KEY_LEFT;
            case "right":      return GLFW.GLFW_KEY_RIGHT;
            case "minus":      return GLFW.GLFW_KEY_MINUS;
            case "equal":      return GLFW.GLFW_KEY_EQUAL;
            case "comma":      return GLFW.GLFW_KEY_COMMA;
            case "period":     return GLFW.GLFW_KEY_PERIOD;
            case "slash":      return GLFW.GLFW_KEY_SLASH;
            case "semicolon":  return GLFW.GLFW_KEY_SEMICOLON;
            case "quote":      return GLFW.GLFW_KEY_APOSTROPHE;
        }

        // F1..F12
        if (n.matches("[Ff][0-9]{1,2}")) {
            int num = Integer.parseInt(n.substring(1));
            if (num >= 1 && num <= 25) return GLFW.GLFW_KEY_F1 + (num - 1);
        }

        // Numpad0..9
        if (n.matches("[Nn]umpad[0-9]")) {
            int num = n.charAt(6) - '0';
            return GLFW.GLFW_KEY_KP_0 + num;
        }

        return -1;
    }

    /** Человекочитаемое имя для отображения в панели. */
    public static String display(ParsedKey k) {
        StringBuilder sb = new StringBuilder();
        if ((k.modifiers & GLFW.GLFW_MOD_CONTROL) != 0) sb.append("Ctrl+");
        if ((k.modifiers & GLFW.GLFW_MOD_SHIFT) != 0)   sb.append("Shift+");
        if ((k.modifiers & GLFW.GLFW_MOD_ALT) != 0)     sb.append("Alt+");
        if ((k.modifiers & GLFW.GLFW_MOD_SUPER) != 0)   sb.append("Super+");
        sb.append(nameForKeyCode(k.keyCode));
        return sb.toString();
    }

    /** Имя по коду GLFW. */
    public static String nameForKeyCode(int code) {
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) ('A' + (code - GLFW.GLFW_KEY_A)));
        }
        if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) ('0' + (code - GLFW.GLFW_KEY_0)));
        }
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) {
            return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
        }
        if (code >= GLFW.GLFW_KEY_KP_0 && code <= GLFW.GLFW_KEY_KP_9) {
            return "Numpad" + (code - GLFW.GLFW_KEY_KP_0);
        }
        return switch (code) {
            case GLFW.GLFW_KEY_SPACE      -> "Space";
            case GLFW.GLFW_KEY_ENTER      -> "Enter";
            case GLFW.GLFW_KEY_ESCAPE     -> "Escape";
            case GLFW.GLFW_KEY_TAB        -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE  -> "Backspace";
            case GLFW.GLFW_KEY_DELETE     -> "Delete";
            case GLFW.GLFW_KEY_INSERT     -> "Insert";
            case GLFW.GLFW_KEY_HOME       -> "Home";
            case GLFW.GLFW_KEY_END        -> "End";
            case GLFW.GLFW_KEY_PAGE_UP    -> "PageUp";
            case GLFW.GLFW_KEY_PAGE_DOWN  -> "PageDown";
            case GLFW.GLFW_KEY_UP         -> "Up";
            case GLFW.GLFW_KEY_DOWN       -> "Down";
            case GLFW.GLFW_KEY_LEFT       -> "Left";
            case GLFW.GLFW_KEY_RIGHT      -> "Right";
            default -> "Key#" + code;
        };
    }
}