package com.fixmer.mared.services.logging;

import java.util.List;
import java.util.Set;

/**
 * Настройки фильтрации лога.
 *
 * 0.3.0 (Phase C): тонкий статический фасад.
 * 0.3.0 (Phase F3): переехал в services/logging.
 * 0.3.0 (Phase F3a): добавлен applyDraft.
 * 0.3.1:
 *   - CATEGORIES — List<String> (List.of).
 *   - parseLevel/parseCategory распознают ТОЛЬКО ПЕРВЫЙ тег строки.
 *
 *     Раньше использовался line.contains("[error]") — это давало
 *     false-positive: say "This [error] is expected" классифицировалось
 *     как ERROR, потому что вся строка содержала "[error]" в тексте.
 *
 *     Теперь: "[say] This [error] is expected" → первый тег [say] →
 *     category=say, level=INFO. Правильно.
 */
public final class LogSettings {

    private LogSettings() {}

    public enum Level { TRACE, DEBUG, INFO, WARN, ERROR }

    public static final List<String> CATEGORIES = List.of(
        "mared", "say", "cmd", "mc", "give", "bind", "on",
        "run", "assert", "auto-save", "log", "info", "error", "other"
    );

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

    // ============================================================
    //  Parsing
    // ============================================================

    /**
     * Распознать уровень по ПЕРВОМУ тегу строки.
     * "[error] ..." → ERROR. "[say] ..." → INFO.
     */
    public static Level parseLevel(String line) {
        String tag = extractFirstTag(line);
        if (tag == null) return Level.INFO;
        return switch (tag) {
            case "error", "give error", "mc error", "cmd error",
                 "mared parse", "assert fail", "ошибка" -> Level.ERROR;
            case "warn", "предупр" -> Level.WARN;
            case "debug" -> Level.DEBUG;
            case "trace" -> Level.TRACE;
            default -> Level.INFO;
        };
    }

    /**
     * Распознать категорию по ПЕРВОМУ тегу строки.
     */
    public static String parseCategory(String line) {
        String tag = extractFirstTag(line);
        if (tag == null) return "other";
        return switch (tag) {
            case "say" -> "say";
            case "cmd" -> "cmd";
            case "mc", "mc error" -> "mc";
            case "give", "give error" -> "give";
            case "bind", "bind fire" -> "bind";
            case "run", "запуск" -> "run";
            case "assert", "assert ok", "assert fail" -> "assert";
            case "auto-save", "автосейв" -> "auto-save";
            case "log" -> "log";
            case "info", "инфо" -> "info";
            case "error", "ошибка", "cmd error", "mared parse" -> "error";
            case "mared" -> "mared";
            case "on" -> "on";
            case "print" -> "print";
            case "chat" -> "chat";
            case "unblock" -> "unblock";
            case "block" -> "block";
            case "toggle" -> "toggle";
            case "studio" -> "studio";
            case "docking" -> "other";
            case "threading" -> "other";
            case "debug", "warn", "trace" -> "other";
            default -> "other";
        };
    }

    /**
     * Первый тег строки "[tag]" без скобок, либо null.
     * Длина тега ограничена 20 символами — защита от мусора.
     */
    private static String extractFirstTag(String line) {
        if (line == null) return null;
        int n = line.length();
        if (n < 3) return null;
        if (line.charAt(0) != '[') return null;
        int close = line.indexOf(']');
        if (close <= 1 || close > 21) return null;
        return line.substring(1, close);
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

    public static void applyDraft(Set<Level> levels, Set<String> categories) {
        LogSettingsService.get().applyDraft(levels, categories);
    }
}