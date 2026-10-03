package com.fixmer.mared.gui2.docking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import com.fixmer.mared.Mared;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Persistence состояния docking-системы.
 *
 * 0.3.0:
 *   - version=1, только ratios.
 *   - atomic save через .tmp + ATOMIC_MOVE.
 *   - type-check при парсинге.
 *
 * 0.3.2 (v2):
 *   - Полное состояние: ratios + visibility + activeTab + collapsed.
 *   - Обратная совместимость: v1 (только ratios) читается и
 *     конвертируется в v2 с дефолтами.
 *   - Один метод load(DockManager) возвращает DockState для
 *     дальнейшего применения контроллером.
 *
 * Формат JSON:
 * {
 *   "version": 2,
 *   "ratios": { "left": 0.20, "right": 0.18, "bottom": 0.15 },
 *   "visibility": { "explorer": true, "inspector": true, ... },
 *   "activeTab": { "left": "explorer", "center": "workspace", ... },
 *   "collapsed": { "bottom": false, ... }
 * }
 *
 * Файл: config/mared/dock_state.json
 */
public final class DockStateStorage {

    private DockStateStorage() {}

    private static final int FORMAT_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TMP_SUFFIX = ".tmp";

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("mared").resolve("dock_state.json");
    }

    // ============================================================
    //  Load
    // ============================================================

    /**
     * Прочитать состояние с диска. Не применяет к manager — вызывающий
     * решает, когда (после регистрации панелей).
     *
     * @return всегда не-null DockState. Пустой, если файла нет
     *         или он битый.
     */
    public static DockState load() {
        DockState state = new DockState();
        Path p = file();
        if (!Files.exists(p)) return state;

        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            int version = readInt(obj, "version", 1);
            if (version > FORMAT_VERSION) {
                Mared.LOGGER.warn(
                    "[docking] dock_state.json version {} (current={}), reading best-effort",
                    version, FORMAT_VERSION);
            }

            readRatios(obj, state);

            if (version >= 2) {
                readVisibility(obj, state);
                readActiveTabs(obj, state);
                readCollapsed(obj, state);
            }

            Mared.LOGGER.info("[docking] loaded state: L={} R={} B={} tabs={}",
                state.ratio(DockPosition.LEFT, 0f),
                state.ratio(DockPosition.RIGHT, 0f),
                state.ratio(DockPosition.BOTTOM, 0f),
                state.activeTab.size());
        } catch (Exception e) {
            Mared.LOGGER.warn("[docking] failed to load dock_state.json: {}",
                e.getMessage());
        }
        return state;
    }

    private static int readInt(JsonObject obj, String key, int def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return def;
        try { return el.getAsInt(); }
        catch (Exception e) { return def; }
    }

    private static void readRatios(JsonObject obj, DockState state) {
        JsonElement el = obj.get("ratios");
        if (el != null && el.isJsonObject()) {
            JsonObject ratios = el.getAsJsonObject();
            readRatio(ratios, "left",   state, DockPosition.LEFT);
            readRatio(ratios, "right",  state, DockPosition.RIGHT);
            readRatio(ratios, "bottom", state, DockPosition.BOTTOM);
            return;
        }

        // Fallback v1: ratios лежали прямо в корне.
        readRatio(obj, "left",   state, DockPosition.LEFT);
        readRatio(obj, "right",  state, DockPosition.RIGHT);
        readRatio(obj, "bottom", state, DockPosition.BOTTOM);
    }

    private static void readRatio(JsonObject obj, String key,
                                  DockState state, DockPosition pos) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return;
        try {
            state.setRatio(pos, el.getAsFloat());
        } catch (Exception e) {
            Mared.LOGGER.warn("[docking] invalid ratio '{}': {}", key, el);
        }
    }

    private static void readVisibility(JsonObject obj, DockState state) {
        JsonElement el = obj.get("visibility");
        if (el == null || !el.isJsonObject()) return;
        JsonObject vis = el.getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : vis.entrySet()) {
            JsonElement v = e.getValue();
            if (v == null || !v.isJsonPrimitive()) continue;
            try {
                state.setVisible(e.getKey(), v.getAsBoolean());
            } catch (Exception ignored) {}
        }
    }

    private static void readActiveTabs(JsonObject obj, DockState state) {
        JsonElement el = obj.get("activeTab");
        if (el == null || !el.isJsonObject()) return;
        JsonObject tabs = el.getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : tabs.entrySet()) {
            DockPosition pos = parsePosition(e.getKey());
            if (pos == null) continue;
            JsonElement v = e.getValue();
            if (v == null || !v.isJsonPrimitive()) continue;
            try {
                state.setActiveTab(pos, v.getAsString());
            } catch (Exception ignored) {}
        }
    }

    private static void readCollapsed(JsonObject obj, DockState state) {
        JsonElement el = obj.get("collapsed");
        if (el == null || !el.isJsonObject()) return;
        JsonObject col = el.getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : col.entrySet()) {
            DockPosition pos = parsePosition(e.getKey());
            if (pos == null) continue;
            JsonElement v = e.getValue();
            if (v == null || !v.isJsonPrimitive()) continue;
            try {
                state.setCollapsed(pos, v.getAsBoolean());
            } catch (Exception ignored) {}
        }
    }

    private static DockPosition parsePosition(String s) {
        if (s == null) return null;
        try { return DockPosition.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    // ============================================================
    //  Save
    // ============================================================

    public static void save(DockState state) {
        if (state == null) return;
        Path p = file();

        try {
            Files.createDirectories(p.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("version", FORMAT_VERSION);

            // ratios
            JsonObject ratios = new JsonObject();
            for (Map.Entry<DockPosition, Float> e : state.ratios.entrySet()) {
                ratios.addProperty(e.getKey().name().toLowerCase(), e.getValue());
            }
            root.add("ratios", ratios);

            // visibility
            JsonObject vis = new JsonObject();
            for (Map.Entry<String, Boolean> e : state.visibility.entrySet()) {
                vis.addProperty(e.getKey(), e.getValue());
            }
            root.add("visibility", vis);

            // activeTab
            JsonObject tabs = new JsonObject();
            for (Map.Entry<DockPosition, String> e : state.activeTab.entrySet()) {
                if (e.getValue() != null) {
                    tabs.addProperty(e.getKey().name().toLowerCase(), e.getValue());
                }
            }
            root.add("activeTab", tabs);

            // collapsed
            JsonObject col = new JsonObject();
            for (Map.Entry<DockPosition, Boolean> e : state.collapsed.entrySet()) {
                col.addProperty(e.getKey().name().toLowerCase(), e.getValue());
            }
            root.add("collapsed", col);

            atomicWrite(p, GSON.toJson(root));
        } catch (IOException e) {
            Mared.LOGGER.warn("[docking] failed to save dock_state.json: {}",
                e.getMessage());
        }
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(
            target.getFileName().toString() + TMP_SUFFIX);
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