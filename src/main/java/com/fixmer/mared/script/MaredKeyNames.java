package com.fixmer.mared.script;

import java.util.HashMap;
import java.util.Map;

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

    // ============================================================
    //  Кэш ParsedKey
    // ============================================================

    private static final Map<String, ParsedKey> CACHE = new HashMap<>(64);

    // ============================================================
    //  Парсинг
    // ============================================================

    public static ParsedKey parse(String name) {
        if (name == null || name.isEmpty()) return null;

        ParsedKey cached = CACHE.get(name);
        if (cached != null) return cached;

        ParsedKey parsed = parseUncached(name);
        if (parsed != null) {
            CACHE.put(name, parsed);
        }
        return parsed;
    }

    private static ParsedKey parseUncached(String name) {
        String raw = name;
        int mods = 0;

        // Ручной split по '+' без regex
        int lastPlus = name.lastIndexOf('+');
        if (lastPlus >= 0) {
            // Разбираем модификаторы: "Ctrl+Shift+R"
            int from = 0;
            while (from <= lastPlus) {
                int idx = name.indexOf('+', from);
                if (idx < 0) idx = lastPlus;
                String m = name.substring(from, idx).trim().toLowerCase();
                switch (m) {
                    case "ctrl", "control" -> mods |= GLFW.GLFW_MOD_CONTROL;
                    case "shift"           -> mods |= GLFW.GLFW_MOD_SHIFT;
                    case "alt"             -> mods |= GLFW.GLFW_MOD_ALT;
                    case "super", "meta"   -> mods |= GLFW.GLFW_MOD_SUPER;
                    default -> { return null; }
                }
                from = idx + 1;
            }
        }

        String keyPart = name.substring(lastPlus + 1).trim();
        int code = keyCode(keyPart);
        if (code < 0) return null;
        return new ParsedKey(code, mods, raw);
    }

    public static int keyCode(String name) {
        if (name == null || name.isEmpty()) return -1;
        String n = name.trim();
        int len = n.length();

        // Одиночный символ
        if (len == 1) {
            char c = n.charAt(0);
            if (c >= 'a' && c <= 'z') return GLFW.GLFW_KEY_A + (c - 'a');
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }

        // F1..F25 — ручная проверка
        if (len >= 2 && len <= 3 && (n.charAt(0) == 'F' || n.charAt(0) == 'f')) {
            int num = 0;
            boolean ok = true;
            for (int i = 1; i < len; i++) {
                char c = n.charAt(i);
                if (c < '0' || c > '9') { ok = false; break; }
                num = num * 10 + (c - '0');
            }
            if (ok && num >= 1 && num <= 25) {
                return GLFW.GLFW_KEY_F1 + (num - 1);
            }
        }

        // Numpad0..Numpad9 — ручная проверка
        if (len == 7
            && (n.charAt(0) == 'N' || n.charAt(0) == 'n')
            && n.regionMatches(true, 1, "umpad", 0, 5)) {
            char c = n.charAt(6);
            if (c >= '0' && c <= '9') {
                return GLFW.GLFW_KEY_KP_0 + (c - '0');
            }
        }

        // Именованные клавиши — switch по нижнему регистру
        // Чтобы не аллоцировать toLowerCase, работаем вручную:
        // сначала быстрый switch по точному совпадению (частые случаи),
        // затем — по lowercase.
        switch (n) {
            case "Space":      return GLFW.GLFW_KEY_SPACE;
            case "Enter":      return GLFW.GLFW_KEY_ENTER;
            case "Escape":     return GLFW.GLFW_KEY_ESCAPE;
            case "Tab":        return GLFW.GLFW_KEY_TAB;
        }

        // Fallback — toLowerCase
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

        return -1;
    }

    // ============================================================
    //  Display
    // ============================================================

    public static String display(ParsedKey k) {
        if (k == null) return "";
        StringBuilder sb = new StringBuilder(16);
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

    // ============================================================
    //  Мышь
    // ============================================================

    public static ParsedKey parseMouse(String name) {
        if (name == null) return null;
        String n = name.toLowerCase();
        switch (n) {
            case "leftclick":   return new ParsedKey(MOUSE_LEFT, 0, name);
            case "rightclick":  return new ParsedKey(MOUSE_RIGHT, 0, name);
            case "middleclick": return new ParsedKey(MOUSE_MIDDLE, 0, name);
            case "mousemove":   return new ParsedKey(MOUSE_MOVE, 0, name);
            case "mousemotion": return new ParsedKey(MOUSE_MOVE, 0, name);
            default: return null;
        }
    }

    /** Универсальный парсинг с кэшем: клавиатура или мышь. */
    public static ParsedKey parseAny(String name) {
        if (name == null || name.isEmpty()) return null;

        ParsedKey cached = CACHE.get(name);
        if (cached != null) return cached;

        ParsedKey key = parse(name);
        if (key == null) key = parseMouse(name);
        if (key != null) CACHE.put(name, key);
        return key;
    }

    /** Очистить кэш (например, при смене раскладки — на всякий случай). */
    public static void clearCache() {
        CACHE.clear();
    }
}