package com.fixmer.mared.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fixmer.mared.Mared;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

public final class MaredLogSettings {

    private MaredLogSettings() {}

    public enum Level { TRACE, DEBUG, INFO, WARN, ERROR }

    public static final String[] CATEGORIES = {
        "mared", "say", "cmd", "mc", "give", "bind", "on",
        "run", "assert", "auto-save", "log", "info", "error", "other"
    };

    private static final Set<Level> enabledLevels = new LinkedHashSet<>();
    private static final Set<String> enabledCategories = new LinkedHashSet<>();
    private static boolean loaded = false;

    static {
        resetDefaults();
    }

    private static void resetDefaults() {
        enabledLevels.clear();
        for (Level l : Level.values()) enabledLevels.add(l);
        enabledCategories.clear();
        for (String c : CATEGORIES) enabledCategories.add(c);
    }

    public static boolean isLevelEnabled(Level l) { return enabledLevels.contains(l); }
    public static boolean isCategoryEnabled(String c) { return enabledCategories.contains(c); }

    public static void toggleLevel(Level l) {
        if (enabledLevels.contains(l)) {
            // Не даём отключить последний уровень
            if (enabledLevels.size() <= 1) return;
            enabledLevels.remove(l);
        } else {
            enabledLevels.add(l);
        }
        save();
    }

    public static void toggleCategory(String c) {
        if (enabledCategories.contains(c)) {
            // Не даём отключить последнюю категорию
            if (enabledCategories.size() <= 1) return;
            enabledCategories.remove(c);
        } else {
            enabledCategories.add(c);
        }
        save();
    }

    public static Set<Level> getEnabledLevels() { return enabledLevels; }
    public static Set<String> getEnabledCategories() { return enabledCategories; }

    public static Level parseLevel(String line) {
        if (line.contains("[error]") || line.contains("[ошибка]")
            || line.contains("[give error]") || line.contains("[mc error]")
            || line.contains("[cmd error]") || line.contains("[mared parse]")
            || line.contains("[assert fail]")) {
            return Level.ERROR;
        }
        if (line.contains("[warn]") || line.contains("[предупр]")) {
            return Level.WARN;
        }
        if (line.contains("[debug]")) {
            return Level.DEBUG;
        }
        if (line.contains("[trace]")) {
            return Level.TRACE;
        }
        return Level.INFO;
    }

    public static String parseCategory(String line) {
        if (line.contains("[say]"))       return "say";
        if (line.contains("[cmd]"))       return "cmd";
        if (line.contains("[mc]"))        return "mc";
        if (line.contains("[give]"))      return "give";
        if (line.contains("[bind]"))      return "bind";
        if (line.contains("[run]") || line.contains("[запуск]")) return "run";
        if (line.contains("[assert"))     return "assert";
        if (line.contains("[auto-save]") || line.contains("[автосейв]")) return "auto-save";
        if (line.contains("[log]"))       return "log";
        if (line.contains("[info]") || line.contains("[инфо]")) return "info";
        if (line.contains("[error]") || line.contains("[ошибка]")) return "error";
        if (line.contains("[mared]"))     return "mared";
        if (line.contains("on ") && (line.contains("replace") || line.contains("add"))) return "on";
        return "other";
    }

    public static boolean shouldShow(String line) {
        Level lvl = parseLevel(line);
        if (!enabledLevels.contains(lvl)) return false;
        String cat = parseCategory(line);
        return enabledCategories.contains(cat);
    }

    // ---- Save / Load ----

    private static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("mared").resolve("log_settings.json");
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path p = configFile();
        if (!Files.exists(p)) return;

        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();

            if (o.has("levels")) {
                enabledLevels.clear();
                for (JsonElement e : o.getAsJsonArray("levels")) {
                    try { enabledLevels.add(Level.valueOf(e.getAsString())); }
                    catch (Exception ignored) {}
                }
                // Защита: если пользователь отключил всё — вернём все
                if (enabledLevels.isEmpty()) {
                    for (Level l : Level.values()) enabledLevels.add(l);
                }
            }
            if (o.has("categories")) {
                enabledCategories.clear();
                for (JsonElement e : o.getAsJsonArray("categories")) {
                    enabledCategories.add(e.getAsString());
                }
                // Защита: если пусто — все категории
                if (enabledCategories.isEmpty()) {
                    for (String c : CATEGORIES) enabledCategories.add(c);
                }
            }
        } catch (Exception e) {
            Mared.LOGGER.error("[Mared] Failed to load log_settings.json", e);
            resetDefaults();
        }
    }

    public static synchronized void save() {
        Path p = configFile();
        try {
            Files.createDirectories(p.getParent());
            JsonObject o = new JsonObject();
            JsonArray levels = new JsonArray();
            for (Level l : enabledLevels) levels.add(l.name());
            o.add("levels", levels);
            JsonArray cats = new JsonArray();
            for (String c : enabledCategories) cats.add(c);
            o.add("categories", cats);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(p, gson.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mared.LOGGER.error("[Mared] Failed to save log_settings.json", e);
        }
    }
}