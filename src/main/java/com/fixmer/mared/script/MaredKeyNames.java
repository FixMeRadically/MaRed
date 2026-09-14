package com.fixmer.mared.script;

import org.lwjgl.glfw.GLFW;

public final class MaredKeyNames {

    private MaredKeyNames() {}

    public static final class ParsedKey {
        public final int keyCode;
        public final int modifiers;
        public final String raw;

        public ParsedKey(int keyCode, int modifiers, String raw) {
            this.keyCode = keyCode;
            this.modifiers = modifiers;
            this.raw = raw;
        }
    }

    // ---- Мышь (специальные коды) ----

    public static final int MOUSE_LEFT   = -1001;
    public static final int MOUSE_RIGHT  = -1002;
    public static final int MOUSE_MIDDLE = -1003;
    public static final int MOUSE_MOVE   = -1004;

    // ---- Клавиатура ----

    public static ParsedKey parse(String name) {
        if (name == null || name.isEmpty()) return null;
        String raw = name;
        int mods = 0;

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

    public static int keyCode(String name) {
        if (name == null || name.isEmpty()) return -1;
        String n = name.trim();

        if (n.length() == 1) {
            char c = Character.toUpperCase(n.charAt(0));
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }

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

        if (n.matches("[Ff][0-9]{1,2}")) {
            int num = Integer.parseInt(n.substring(1));
            if (num >= 1 && num <= 25) return GLFW.GLFW_KEY_F1 + (num - 1);
        }

        if (n.matches("[Nn]umpad[0-9]")) {
            int num = n.charAt(6) - '0';
            return GLFW.GLFW_KEY_KP_0 + num;
        }

        return -1;
    }

    public static String display(ParsedKey k) {
        StringBuilder sb = new StringBuilder();
        if ((k.modifiers & GLFW.GLFW_MOD_CONTROL) != 0) sb.append("Ctrl+");
        if ((k.modifiers & GLFW.GLFW_MOD_SHIFT) != 0)   sb.append("Shift+");
        if ((k.modifiers & GLFW.GLFW_MOD_ALT) != 0)     sb.append("Alt+");
        if ((k.modifiers & GLFW.GLFW_MOD_SUPER) != 0)   sb.append("Super+");
        sb.append(nameForKeyCode(k.keyCode));
        return sb.toString();
    }

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
        switch (code) {
            case GLFW.GLFW_KEY_SPACE:      return "Space";
            case GLFW.GLFW_KEY_ENTER:      return "Enter";
            case GLFW.GLFW_KEY_ESCAPE:     return "Escape";
            case GLFW.GLFW_KEY_TAB:        return "Tab";
            case GLFW.GLFW_KEY_BACKSPACE:  return "Backspace";
            case GLFW.GLFW_KEY_DELETE:     return "Delete";
            case GLFW.GLFW_KEY_INSERT:     return "Insert";
            case GLFW.GLFW_KEY_HOME:       return "Home";
            case GLFW.GLFW_KEY_END:        return "End";
            case GLFW.GLFW_KEY_PAGE_UP:    return "PageUp";
            case GLFW.GLFW_KEY_PAGE_DOWN:  return "PageDown";
            case GLFW.GLFW_KEY_UP:         return "Up";
            case GLFW.GLFW_KEY_DOWN:       return "Down";
            case GLFW.GLFW_KEY_LEFT:       return "Left";
            case GLFW.GLFW_KEY_RIGHT:      return "Right";
            case MOUSE_LEFT:               return "LeftClick";
            case MOUSE_RIGHT:              return "RightClick";
            case MOUSE_MIDDLE:             return "MiddleClick";
            case MOUSE_MOVE:               return "MouseMove";
            default:                       return "Key#" + code;
        }
    }

    // ---- Мышь ----

    public static ParsedKey parseMouse(String name) {
        if (name == null) return null;
        switch (name.toLowerCase()) {
            case "leftclick":   return new ParsedKey(MOUSE_LEFT, 0, name);
            case "rightclick":  return new ParsedKey(MOUSE_RIGHT, 0, name);
            case "middleclick": return new ParsedKey(MOUSE_MIDDLE, 0, name);
            case "mousemove":   return new ParsedKey(MOUSE_MOVE, 0, name);
            case "mousemotion": return new ParsedKey(MOUSE_MOVE, 0, name);
            default: return null;
        }
    }

    /** Универсальный парсинг: клавиатура или мышь. */
    public static ParsedKey parseAny(String name) {
        ParsedKey key = parse(name);
        if (key != null) return key;
        return parseMouse(name);
    }
}