package com.fixmer.mared.commands.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fixmer.mared.Mared;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Глобальные переменные MaRed (сохраняются в visited.json).
 *
 * 0.3.2 (audit #87):
 *   - revision-based save: dirty сбрасывается только если за время
 *     записи не пришли новые set/remove. Раньше dirty = false
 *     выставлялся до записи, и при IOException данные терялись
 *     до следующего flush.
 *   - atomic write через .tmp + ATOMIC_MOVE.
 *   - maybeSave() вызывается из set/remove только для ключей с
 *     префиксом "__" (служебные, типа __once_...).
 */
public final class MaredGlobalStorage {

    private MaredGlobalStorage() {}

    private static final Map<String, Object> GLOBALS = new ConcurrentHashMap<>(16);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile boolean loaded = false;
    private static volatile boolean dirty = false;
    private static volatile long dataRevision = 0;
    private static long lastSaveMs = 0;

    private static final long SAVE_INTERVAL_MS = 5000;
    private static final String TMP_SUFFIX = ".tmp";

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
            dataRevision++;
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
            dataRevision++;
            dirty = true;
            maybeSave();
        }
    }

    public static void clear() { GLOBALS.clear(); }

    public static Map<String, Object> all() { return GLOBALS; }
    public static int size() { return GLOBALS.size(); }

    /** Принудительно сохранить, если есть изменения. */
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
            Mared.LOGGER.warn("[Mared] Failed to load visited.json: {}",
                e.getMessage());
        }
    }

    private static void maybeSave() {
        long now = System.currentTimeMillis();
        if (now - lastSaveMs < SAVE_INTERVAL_MS) return;
        lastSaveMs = now;
        saveVisited();
    }

    /**
     * 0.3.2: revision-based.
     * dirty сбрасывается только если dataRevision не изменился
     * за время записи. Иначе следующая попытка подхватит изменения.
     */
    private static synchronized void saveVisited() {
        if (!dirty) return;

        long myRev = dataRevision;

        Path file = visitedFile();
        try {
            Files.createDirectories(file.getParent());
            JsonObject obj = new JsonObject();
            for (Map.Entry<String, Object> e : GLOBALS.entrySet()) {
                if (e.getKey().startsWith("__")) {
                    obj.addProperty(e.getKey(), Boolean.TRUE.equals(e.getValue()));
                }
            }

            Path tmp = file.resolveSibling(
                file.getFileName().toString() + TMP_SUFFIX);
            Files.writeString(tmp, GSON.toJson(obj), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }

            // 0.3.2: dirty сбрасываем только если ничего не пришло
            // за время записи.
            if (dataRevision == myRev) {
                dirty = false;
            }
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to save visited.json: {}",
                e.getMessage());
            // dirty намеренно НЕ сбрасываем — попробуем при flush().
        }
    }
}