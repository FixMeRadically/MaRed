package com.fixmer.mared.services.logging;

import java.util.Set;

/**
 * Настройки фильтрации лога.
 *
 * 0.3.0 (Phase C): превращён в тонкий статический фасад.
 * 0.3.0 (Phase F3): переехал в services/logging, переименован
 * MaredLogSettings → LogSettings.
 * 0.3.0 (Phase F3a): добавлен applyDraft для транзакционного Save.
 */
public final class LogSettings {

    private LogSettings() {}

    public enum Level { TRACE, DEBUG, INFO, WARN, ERROR }

    public static final String[] CATEGORIES = {
        "mared", "say", "cmd", "mc", "give", "bind", "on",
        "run", "assert", "auto-save", "log", "info", "error", "other"
    };

    public static int getVersion() {
        return LogSettingsService.get().version();
    }

    public static boolean isLevelEnabled(Level l) {
        return LogSettingsService.get().isLevelEnabled(l);
    }

    public static boolean isCategoryEnabled(String c) {
        return LogSettingsService.get().isCategoryEnabled(c);
    }

    public static Set<Level> getEnabledLevels() {
        return LogSettingsService.get().enabledLevels();
    }

    public static Set<String> getEnabledCategories() {
        return LogSettingsService.get().enabledCategories();
    }

    public static void toggleLevel(Level l) {
        LogSettingsService.get().toggleLevel(l);
    }

    public static void toggleCategory(String c) {
        LogSettingsService.get().toggleCategory(c);
    }

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
        return LogSettingsService.get().shouldShow(line);
    }

    public static void load() {
        LogSettingsService.get().ensureLoaded();
    }

    public static void save() {
        LogSettingsService.get().save();
    }

    /**
     * 0.3.0 (Phase F3a): применить draft целиком.
     * Используется SettingsContext.apply() — Save в SettingsScreen.
     * Одна запись на диск.
     */
    public static void applyDraft(Set<Level> levels, Set<String> categories) {
        LogSettingsService.get().applyDraft(levels, categories);
    }
}