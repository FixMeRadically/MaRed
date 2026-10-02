package com.fixmer.mared;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

/**
 * Локализация Mared.
 *
 * Загружает строки из assets/mared/lang/<code>.json.
 * Fallback — en_us.
 * Автовыбор языка — по Minecraft.getLanguageManager().getSelected().
 *
 * 0.3.0: загрузка через ModList → modFile.findResource — работает в dev
 * и production. Раньше через getResourceAsStream: в dev-runtime mod-ресурсы
 * часто не попадают в classpath → ключи показывались сырыми.
 */
public final class MaredLang {

    private MaredLang() {}

    private static String currentCode = "en_us";

    private static final Map<String, Map<String, String>> DICTS = new HashMap<>();
    private static boolean loaded = false;

    private static final String FALLBACK = "en_us";

    // -------------------------------------------------------------------
    //  Public API
    // -------------------------------------------------------------------

    public static String get(String key) {
        ensureLoaded();
        Map<String, String> dict = DICTS.get(currentCode);
        if (dict != null && dict.containsKey(key)) return dict.get(key);
        Map<String, String> fallback = DICTS.get(FALLBACK);
        if (fallback != null && fallback.containsKey(key)) return fallback.get(key);
        return key;
    }

    public static String format(String key, Object... args) {
        return String.format(get(key), args);
    }

    public static void reload() {
        loaded = false;
        DICTS.clear();
        ensureLoaded();
    }

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

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getLanguageManager() != null) {
                currentCode = mc.getLanguageManager().getSelected();
            }
        } catch (Exception e) {
            currentCode = FALLBACK;
        }
        if (currentCode == null || currentCode.isEmpty()) currentCode = FALLBACK;

        loadLanguage(currentCode);

        if (!FALLBACK.equals(currentCode)) {
            loadLanguage(FALLBACK);
        }
    }

    private static void loadLanguage(String code) {
        String relative = "assets/mared/lang/" + code + ".json";
        try (InputStream in = openModResource(relative)) {
            if (in == null) {
                Mared.LOGGER.warn("[Mared] Lang file not found: {}", relative);
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
            Mared.LOGGER.debug("[Mared] Loaded lang {} ({} keys)", code, dict.size());
        } catch (Exception e) {
            Mared.LOGGER.error("[Mared] Failed to load lang {}", code, e);
        }
    }

    /**
     * 0.3.0: mod-ресурсы читаются через ModList. Fallback — classpath
     * (для unit-тестов или раннего старта).
     */
    private static InputStream openModResource(String relative) throws IOException {
        try {
            var modFile = ModList.get()
                .getModFileById(Mared.MOD_ID)
                .getFile();
            Path found = modFile.findResource(relative);
            if (found != null && Files.exists(found)) {
                return Files.newInputStream(found);
            }
        } catch (Throwable t) {
            Mared.LOGGER.debug("[Mared] ModList lookup failed for {}: {}",
                relative, t.getMessage());
        }
        return MaredLang.class.getResourceAsStream("/" + relative);
    }
}