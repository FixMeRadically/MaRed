package com.fixmer.mared.commands.storage;

import java.util.HashMap;
import java.util.Map;
import com.fixmer.mared.Mared;

/**
 * Глобальное хранилище переменных, сохраняющихся между запусками скриптов.
 *
 * Ключи — с префиксом "global.". То есть "global.hp" в Mared-скрипте
 * становится ключом "global.hp" в этой мапе.
 *
 * Очищается при ServerStoppedEvent (выход из мира).
 */
public final class MaredGlobalStorage {

    private MaredGlobalStorage() {}

    private static final Map<String, Object> GLOBALS = new HashMap<>();

    public static Object get(String key) {
        return GLOBALS.get(key);
    }

    public static void set(String key, Object value) {
        GLOBALS.put(key, value);
    }

    public static boolean has(String key) {
        return GLOBALS.containsKey(key);
    }

    public static void remove(String key) {
        GLOBALS.remove(key);
    }

    public static void clear() {
        GLOBALS.clear();
    }

    public static Map<String, Object> all() {
        return GLOBALS;
    }

    public static int size() {
        return GLOBALS.size();
    }
}