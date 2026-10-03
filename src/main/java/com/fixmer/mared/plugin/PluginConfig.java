package com.fixmer.mared.plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.fixmer.mared.Mared;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Пользовательский конфиг плагинов.
 *
 * Файл: config/mared/plugins.json
 * {
 *   "_comment": "...",
 *   "disabled": ["some-plugin-id", "another"],
 *   "enabled":  []
 * }
 *
 * Семантика:
 *   - disabled содержит id — плагин не загружается.
 *   - enabled — force-enable (перебивает disabled для того же id,
 *     если по какой-то причине оба списка содержат id).
 *
 * Файл создаётся с примером при первом запуске, если его нет.
 */
public final class PluginConfig {

    private PluginConfig() {}

    private static volatile Set<String> disabled = null;
    private static volatile Set<String> enabled  = null;

    public static Path file() {
        return FMLPaths.CONFIGDIR.get()
            .resolve("mared").resolve("plugins.json");
    }

    public static boolean isDisabled(String id) {
        if (id == null) return false;
        ensureLoaded();
        if (enabled.contains(id)) return false;
        return disabled.contains(id);
    }

    public static Set<String> disabledIds() {
        ensureLoaded();
        return Collections.unmodifiableSet(disabled);
    }

    public static Set<String> enabledIds() {
        ensureLoaded();
        return Collections.unmodifiableSet(enabled);
    }

    public static synchronized void reload() {
        disabled = null;
        enabled = null;
        ensureLoaded();
    }

    // ============================================================
    //  Internals
    // ============================================================

    private static void ensureLoaded() {
        if (disabled != null && enabled != null) return;
        synchronized (PluginConfig.class) {
            if (disabled != null && enabled != null) return;

            Set<String> dis = new HashSet<>(4);
            Set<String> en  = new HashSet<>(4);

            Path p = file();
            if (!Files.exists(p)) {
                writeExample(p);
            } else {
                try {
                    String json = Files.readString(p, StandardCharsets.UTF_8);
                    JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                    readIds(o, "disabled", dis);
                    readIds(o, "enabled", en);
                } catch (Exception e) {
                    Mared.LOGGER.warn(
                        "[plugin] failed to read plugins.json: {}",
                        e.getMessage());
                }
            }
            disabled = dis;
            enabled = en;
        }
    }

    private static void readIds(JsonObject o, String key, Set<String> out) {
        if (!o.has(key)) return;
        JsonElement el = o.get(key);
        if (el == null || !el.isJsonArray()) return;
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement e = arr.get(i);
            if (e != null && e.isJsonPrimitive()) {
                try {
                    String s = e.getAsString();
                    if (s != null && !s.isBlank()) out.add(s);
                } catch (Exception ignored) {}
            }
        }
    }

    private static void writeExample(Path p) {
        try {
            Files.createDirectories(p.getParent());
            String example = "{\n" +
                "  \"_comment\": \"Plugin control. 'disabled' — plugin ids to skip. 'enabled' — force-enable.\",\n" +
                "  \"disabled\": [],\n" +
                "  \"enabled\":  []\n" +
                "}\n";
            Files.writeString(p, example, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mared.LOGGER.warn(
                "[plugin] failed to create plugins.json: {}",
                e.getMessage());
        }
    }
}