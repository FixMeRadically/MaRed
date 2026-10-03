package com.fixmer.mared.services.logging;

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

/**
 * Сервис настроек фильтрации лога.
 *
 * 0.3.1:
 *   - enabledLevels()/enabledCategories() отдают immutable-копию.
 *   - shouldShow(String) использует те же parseLevel/parseCategory,
 *     что и LogEntry. Единый источник правды.
 */
public final class LogSettingsService {

    private static final LogSettingsService INSTANCE = new LogSettingsService();

    public static LogSettingsService get() { return INSTANCE; }

    private final Set<LogSettings.Level> enabledLevels = new LinkedHashSet<>();
    private final Set<String> enabledCategories = new LinkedHashSet<>();

    private boolean loaded = false;
    private volatile int version = 0;

    private LogSettingsService() {
        resetDefaults();
    }

    public int version() { return version; }
    private void bumpVersion() { version++; }

    public Set<LogSettings.Level> enabledLevels() {
        return Set.copyOf(enabledLevels);
    }

    public Set<String> enabledCategories() {
        return Set.copyOf(enabledCategories);
    }

    public boolean isLevelEnabled(LogSettings.Level l) {
        return enabledLevels.contains(l);
    }

    public boolean isCategoryEnabled(String c) {
        return enabledCategories.contains(c);
    }

    public void toggleLevel(LogSettings.Level l) {
        if (enabledLevels.contains(l)) {
            if (enabledLevels.size() <= 1) return;
            enabledLevels.remove(l);
        } else {
            enabledLevels.add(l);
        }
        bumpVersion();
        save();
    }

    public void toggleCategory(String c) {
        if (enabledCategories.contains(c)) {
            if (enabledCategories.size() <= 1) return;
            enabledCategories.remove(c);
        } else {
            enabledCategories.add(c);
        }
        bumpVersion();
        save();
    }

    /**
     * Проверка отображения по СЫРОЙ строке.
     * Используется, если caller не имеет LogEntry под рукой.
     * Предпочтительно использовать shouldShow(LogEntry).
     */
    public boolean shouldShow(String line) {
        return shouldShow(
            LogSettings.parseLevel(line),
            LogSettings.parseCategory(line));
    }

    /** Проверка по structured полям — основной путь. */
    public boolean shouldShow(LogSettings.Level level, String category) {
        if (level == null || category == null) return true;
        return enabledLevels.contains(level)
            && enabledCategories.contains(category);
    }

    public void applyDraft(Set<LogSettings.Level> newLevels,
                           Set<String> newCategories) {
        if (newLevels != null && !newLevels.isEmpty()) {
            enabledLevels.clear();
            enabledLevels.addAll(newLevels);
        }
        if (newCategories != null && !newCategories.isEmpty()) {
            enabledCategories.clear();
            enabledCategories.addAll(newCategories);
        }
        bumpVersion();
        save();
    }

    public void resetDefaults() {
        enabledLevels.clear();
        for (LogSettings.Level l : LogSettings.Level.values()) {
            enabledLevels.add(l);
        }
        enabledCategories.clear();
        enabledCategories.addAll(LogSettings.CATEGORIES);
    }

    private static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve("mared").resolve("log_settings.json");
    }

    public void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Path p = configFile();
        if (!Files.exists(p)) {
            bumpVersion();
            return;
        }

        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();

            if (o.has("levels")) {
                enabledLevels.clear();
                for (JsonElement e : o.getAsJsonArray("levels")) {
                    try {
                        enabledLevels.add(LogSettings.Level.valueOf(e.getAsString()));
                    } catch (Exception ignored) {}
                }
                if (enabledLevels.isEmpty()) {
                    for (LogSettings.Level l : LogSettings.Level.values()) {
                        enabledLevels.add(l);
                    }
                }
            }
            if (o.has("categories")) {
                enabledCategories.clear();
                for (JsonElement e : o.getAsJsonArray("categories")) {
                    enabledCategories.add(e.getAsString());
                }
                if (enabledCategories.isEmpty()) {
                    enabledCategories.addAll(LogSettings.CATEGORIES);
                }
            }
        } catch (Exception e) {
            Mared.LOGGER.error("[Mared] Failed to load log_settings.json", e);
            resetDefaults();
        }
        bumpVersion();
    }

    public void save() {
        Path p = configFile();
        try {
            Files.createDirectories(p.getParent());
            JsonObject o = new JsonObject();

            JsonArray levels = new JsonArray();
            for (LogSettings.Level l : enabledLevels) levels.add(l.name());
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