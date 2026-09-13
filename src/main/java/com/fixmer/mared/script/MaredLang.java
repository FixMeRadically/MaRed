package com.fixmer.mared.script;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.fixmer.mared.Mared;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;

/**
 * Локализация Mared.
 *
 * Загружает строки из assets/mared/lang/<code>.json.
 * Fallback — en_us.
 * Автовыбор языка — по Minecraft.getLanguageManager().getSelected().
 */
public final class MaredLang {

    private MaredLang() {}

    /** Текущий язык — код, который выбрал игрок в настройках. */
    private static String currentCode = "en_us";

    /** Загруженные словари. */
    private static final Map<String, Map<String, String>> DICTS = new HashMap<>();

    /** Флаг — загружено ли. */
    private static boolean loaded = false;

    /** Fallback-язык. */
    private static final String FALLBACK = "en_us";

    // -------------------------------------------------------------------
    //  Public API
    // -------------------------------------------------------------------

    /** Перевести ключ. */
    public static String get(String key) {
        ensureLoaded();
        Map<String, String> dict = DICTS.get(currentCode);
        if (dict != null && dict.containsKey(key)) return dict.get(key);
        Map<String, String> fallback = DICTS.get(FALLBACK);
        if (fallback != null && fallback.containsKey(key)) return fallback.get(key);
        return key; // ключ, если перевода нет
    }

    /** Перевести и подставить параметры. %s, %d — как в String.format. */
    public static String format(String key, Object... args) {
        return String.format(get(key), args);
    }

    /** Перезагрузить — например, если игрок сменил язык в настройках. */
    public static void reload() {
        loaded = false;
        DICTS.clear();
        ensureLoaded();
    }

    /** Установить язык вручную (для отладки). */
    public static void setLanguage(String code) {
        currentCode = code;
    }

    public static String getCurrentCode() { return currentCode; }

    // -------------------------------------------------------------------
    //  Загрузка
    // -------------------------------------------------------------------

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        // Определить язык из Minecraft
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getLanguageManager() != null) {
                currentCode = mc.getLanguageManager().getSelected();
            }
        } catch (Exception e) {
            currentCode = FALLBACK;
        }
        if (currentCode == null || currentCode.isEmpty()) currentCode = FALLBACK;

        // Загрузить текущий язык
        loadLanguage(currentCode);

        // Загрузить fallback — если отличается
        if (!FALLBACK.equals(currentCode)) {
            loadLanguage(FALLBACK);
        }
    }

    private static void loadLanguage(String code) {
        String path = "/assets/mared/lang/" + code + ".json";
        try (InputStream in = MaredLang.class.getResourceAsStream(path)) {
            if (in == null) {
                Mared.LOGGER.warn("Lang file not found: {}", path);
                return;
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            Map<String, String> dict = new HashMap<>();
            for (var entry : obj.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    dict.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            DICTS.put(code, dict);
        } catch (Exception e) {
            Mared.LOGGER.error("Failed to load lang {}", code, e);
        }
    }
}