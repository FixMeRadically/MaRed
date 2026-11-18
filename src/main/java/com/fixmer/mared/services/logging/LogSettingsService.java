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
        defaults142();
    }

    public int version() { return version; }
    private void bumpVersion() { version++; }

    public synchronized Set<LogSettings.Level> enabledLevels() {
        ensureLoaded();
        return Set.copyOf(enabledLevels);
    }

    public synchronized Set<String> enabledCategories() {
        ensureLoaded();
        return Set.copyOf(enabledCategories);
    }

    public synchronized boolean isLevelEnabled(LogSettings.Level l) {
        ensureLoaded();
        return enabledLevels.contains(l);
    }

    public synchronized boolean isCategoryEnabled(String c) {
        ensureLoaded();
        return enabledCategories.contains(c);
    }

    public synchronized void toggleLevel(LogSettings.Level l) {
        ensureLoaded();
        if (enabledLevels.contains(l)) {
            if (enabledLevels.size() <= 1) return;
            enabledLevels.remove(l);
        } else {
            enabledLevels.add(l);
        }
        bumpVersion();
        save();
    }

    public synchronized void toggleCategory(String c) {
        ensureLoaded();
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
    public synchronized boolean shouldShow(String line) {
        ensureLoaded();
        return shouldShow(
            LogSettings.parseLevel(line),
            LogSettings.parseCategory(line));
    }

    /** Проверка по structured полям — основной путь. */
    public synchronized boolean shouldShow(LogSettings.Level level, String category) {
        ensureLoaded();
        if (level == null || category == null) return true;
        return enabledLevels.contains(level)
            && enabledCategories.contains(category);
    }

    public synchronized void applyDraft(Set<LogSettings.Level> newLevels,
                           Set<String> newCategories) {
        ensureLoaded();
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

    public synchronized void resetDefaults(){
        ensureLoaded();defaults142();bumpVersion();save();
    }
    private void defaults142() {
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

    public synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Path p = configFile();
        if (!Files.exists(p)) {
            bumpVersion();
            return;
        }

        try {
            if(Files.size(p)>64*1024)throw new IOException("Log settings exceed 64 KiB");
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
            defaults142();
        }
        bumpVersion();
    }

    public synchronized void save() {
        ensureLoaded();
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
            com.fixmer.genesis.technology.storage.AtomicSettings111.write(p, gson.toJson(o));
        } catch (IOException e) {
            Mared.LOGGER.error("[Mared] Failed to save log_settings.json", e);
        }
    }
}