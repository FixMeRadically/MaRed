package com.fixmer.mared.commands.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fixmer.mared.Mared;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

public final class MaredGlobalStorage {

    private MaredGlobalStorage() {}

    private static final Map<String, Object> GLOBALS = new ConcurrentHashMap<>(16);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile boolean loaded = false;
    private static volatile boolean dirty = false;
    private static long lastSaveMs = 0;

    /** Минимальный интервал между сохранениями visited.json. */
    private static final long SAVE_INTERVAL_MS = 5000;

    private static Path visitedFile() {
        return FMLPaths.CONFIGDIR.get().resolve("mared").resolve("visited.json");
    }

    // ============================================================
    //  API
    // ============================================================

    public static Object get(String key) {
        ensureLoaded();
        if (key == null) return null;
        return GLOBALS.get(key);
    }

    public static void set(String key, Object value) {
        ensureLoaded();
        if (key == null) return;

        if (value == null) GLOBALS.remove(key);
        else GLOBALS.put(key, value);

        if (key.startsWith("__")) {
            dirty = true;
            maybeSave();
        }
    }

    public static boolean has(String key) {
        ensureLoaded();
        if (key == null) return false;
        return GLOBALS.containsKey(key);
    }

    public static void remove(String key) {
        ensureLoaded();
        if (key == null) return;
        GLOBALS.remove(key);
        if (key.startsWith("__")) {
            dirty = true;
            maybeSave();
        }
    }

    public static void clear() { GLOBALS.clear(); }

    public static Map<String, Object> all() { return GLOBALS; }
    public static int size() { return GLOBALS.size(); }

    /** Принудительно сохранить, если есть изменения. Вызывать при выходе. */
    public static void flush() {
        if (dirty) saveVisited();
    }

    // ============================================================
    //  Load / Save
    // ============================================================

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (MaredGlobalStorage.class) {
            if (loaded) return;
            loaded = true;
            loadVisited();
        }
    }

    private static void loadVisited() {
        Path file = visitedFile();
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            int n = obj.size();
            for (Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                GLOBALS.put(e.getKey(), e.getValue().getAsBoolean());
            }
            Mared.LOGGER.info("[Mared] Loaded {} visited entries", n);
        } catch (Exception e) {
            Mared.LOGGER.warn("[Mared] Failed to load visited.json: {}", e.getMessage());
        }
    }

    /** Троттлинг: не чаще раза в SAVE_INTERVAL_MS, кроме случаев flush(). */
    private static void maybeSave() {
        long now = System.currentTimeMillis();
        if (now - lastSaveMs < SAVE_INTERVAL_MS) return;
        lastSaveMs = now;
        saveVisited();
    }

    private static synchronized void saveVisited() {
        dirty = false;
        Path file = visitedFile();
        try {
            Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            for (Map.Entry<String, Object> e : GLOBALS.entrySet()) {
                if (e.getKey().startsWith("__")) {
                    obj.addProperty(e.getKey(), Boolean.TRUE.equals(e.getValue()));
                }
            }
            Files.writeString(file, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to save visited.json: {}", e.getMessage());
        }
    }
}