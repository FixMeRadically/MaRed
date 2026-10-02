package com.fixmer.mared.gui2.docking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.fixmer.mared.Mared;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Persistence ratios docking-панелей.
 *
 * 0.3.0:
 *   - version=1 в JSON — миграционная готовность.
 *   - atomic save через .tmp + ATOMIC_MOVE (fallback — обычный move).
 *   - type-check при парсинге (isJsonPrimitive) — защита от битого JSON.
 *
 * Файл: config/mared/dock_state.json
 */
public final class DockStateStorage {

    private DockStateStorage() {}

    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TMP_SUFFIX = ".tmp";

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("mared").resolve("dock_state.json");
    }

    // ============================================================
    //  Load
    // ============================================================

    public static void load(DockLayout layout) {
        if (layout == null) return;
        Path p = file();
        if (!Files.exists(p)) return;

        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            int version = readInt(obj, "version", FORMAT_VERSION);
            if (version != FORMAT_VERSION) {
                Mared.LOGGER.warn("[docking] unsupported dock_state.json version {} (expected {})",
                    version, FORMAT_VERSION);
                return;
            }

            readRatio(obj, "left",   layout, DockPosition.LEFT);
            readRatio(obj, "right",  layout, DockPosition.RIGHT);
            readRatio(obj, "bottom", layout, DockPosition.BOTTOM);

            Mared.LOGGER.info("[docking] loaded ratios: L={} R={} B={}",
                layout.ratio(DockPosition.LEFT),
                layout.ratio(DockPosition.RIGHT),
                layout.ratio(DockPosition.BOTTOM));
        } catch (Exception e) {
            Mared.LOGGER.warn("[docking] failed to load dock_state.json: {}", e.getMessage());
        }
    }

    private static int readInt(JsonObject obj, String key, int def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return def;
        try { return el.getAsInt(); }
        catch (Exception e) { return def; }
    }

    private static void readRatio(JsonObject obj, String key,
                                   DockLayout layout, DockPosition pos) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return;
        try {
            layout.setRatio(pos, el.getAsFloat());
        } catch (Exception e) {
            Mared.LOGGER.warn("[docking] invalid ratio for '{}': {}", key, el);
        }
    }

    // ============================================================
    //  Save (atomic)
    // ============================================================

    public static void save(DockLayout layout) {
        if (layout == null) return;
        Path p = file();

        try {
            Files.createDirectories(p.getParent());

            JsonObject obj = new JsonObject();
            obj.addProperty("version", FORMAT_VERSION);
            obj.addProperty("left",   layout.ratio(DockPosition.LEFT));
            obj.addProperty("right",  layout.ratio(DockPosition.RIGHT));
            obj.addProperty("bottom", layout.ratio(DockPosition.BOTTOM));

            atomicWrite(p, GSON.toJson(obj));
        } catch (IOException e) {
            Mared.LOGGER.warn("[docking] failed to save dock_state.json: {}", e.getMessage());
        }
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName().toString() + TMP_SUFFIX);
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}