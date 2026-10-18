package com.fixmer.mared.dev.world;

import com.fixmer.mared.Mared;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Шаг 4: конфигурация dev-автозагрузки мира.
 *
 * Файл: config/mared/dev-worlds.properties
 *
 * Формат (все ключи опциональны):
 *
 *   lastWorld=<id>       автоматически обновляется при заходе игрока в мир
 *   defaultWorld=<id>    если не пусто, всегда грузится вместо lastWorld
 *   updateLast=true      обновлять ли lastWorld при заходе (по умолчанию true)
 *   autoLoad=true        вообще выполнять ли автозагрузку (по умолчанию true)
 *
 * Приоритет выбора мира при старте клиента в dev-режиме:
 *   1. -Dmared.dev.world=<id>   (system property, задаётся супервизором или IDE)
 *   2. GENESIS_DEV_WORLD=<id>   (переменная окружения)
 *   3. defaultWorld, если не пуст
 *   4. lastWorld, если не пуст
 *   5. (нет автозагрузки — обычный TitleScreen)
 *
 * Если id указывает на несуществующий мир, автозагрузка отменяется и
 * показывается TitleScreen — клиент не должен падать и не должен
 * показывать ванильный экран «world not found».
 */
public final class DevWorldConfig {

    private DevWorldConfig() {}

    private static final String FILE_NAME   = "dev-worlds.properties";
    private static final String PROP_WORLD  = "mared.dev.world";
    private static final String ENV_WORLD   = "GENESIS_DEV_WORLD";
    private static final int    MAX_ID      = 512;

    // ============================================================
    //  Загрузка файла
    // ============================================================

    private static Properties loadProperties() {
        Properties props = new Properties();
        Path file = configFile();
        if (file == null || !Files.isRegularFile(file)) return props;
        try (var in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(in);
        } catch (IOException e) {
            Mared.LOGGER.debug("[Mared] cannot read {}: {}", file, e.toString());
        }
        return props;
    }

    private static Path configFile() {
        try {
            return com.fixmer.mared.dev.DevPaths.state("worlds.properties", FILE_NAME);
        } catch (Throwable t) {
            return null;
        }
    }

    // ============================================================
    //  Флаги
    // ============================================================

    /** true если автозагрузка разрешена (по умолчанию да). */
    public static boolean autoLoadEnabled() {
        Properties p = loadProperties();
        String v = p.getProperty("autoLoad", "true").trim();
        return Boolean.parseBoolean(v);
    }

    /** true если при заходе игрока обновлять lastWorld (по умолчанию да). */
    public static boolean updateLastEnabled() {
        Properties p = loadProperties();
        String v = p.getProperty("updateLast", "true").trim();
        return Boolean.parseBoolean(v);
    }

    // ============================================================
    //  Выбор мира
    // ============================================================

    /**
     * Какой мир грузить при старте. null → не грузить, показать TitleScreen.
     * Порядок приоритета см. в javadoc класса.
     */
    public static String worldToLoad() {
        if (!autoLoadEnabled()) return null;

        String sys = System.getProperty(PROP_WORLD);
        if (sys != null && !sys.isBlank()) return clean(sys);

        String env = System.getenv(ENV_WORLD);
        if (env != null && !env.isBlank()) return clean(env);

        Properties p = loadProperties();

        String def = p.getProperty("defaultWorld", "").trim();
        if (!def.isEmpty()) return clean(def);

        String last = p.getProperty("lastWorld", "").trim();
        if (!last.isEmpty()) return clean(last);

        return null;
    }

    /** Источник выбранного мира — для логов. */
    public static String worldSource() {
        if (!autoLoadEnabled()) return "autoLoad=false";
        String sys = System.getProperty(PROP_WORLD);
        if (sys != null && !sys.isBlank()) return "system property -D" + PROP_WORLD;
        String env = System.getenv(ENV_WORLD);
        if (env != null && !env.isBlank()) return "environment " + ENV_WORLD;
        Properties p = loadProperties();
        if (!p.getProperty("defaultWorld", "").trim().isEmpty()) return "defaultWorld";
        if (!p.getProperty("lastWorld", "").trim().isEmpty()) return "lastWorld";
        return "none";
    }

    // ============================================================
    //  Запись lastWorld
    // ============================================================

    /**
     * Обновить lastWorld. Полная перезапись файла — атомарно,
     * чтобы не словить полу-записанный конфиг при краше.
     */
    public static void recordLast(String levelId) {
        if (levelId == null) return;
        if (!updateLastEnabled()) return;
        String cleaned = clean(levelId);
        if (cleaned == null) return;

        Path file = configFile();
        if (file == null) return;

        Properties p = loadProperties();
        String current = p.getProperty("lastWorld", "").trim();
        if (cleaned.equals(current)) return;

        p.setProperty("lastWorld", cleaned);

        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder(128);
            sb.append("# Managed by MaRed dev mode. Safe to edit by hand.\n");
            sb.append("# Key order is stable so diffs are readable.\n");
            writeIfPresent(sb, p, "autoLoad");
            writeIfPresent(sb, p, "updateLast");
            writeIfPresent(sb, p, "defaultWorld");
            writeIfPresent(sb, p, "lastWorld");
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, file,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Mared.LOGGER.debug("[Mared] cannot record last world: {}", e.toString());
        }
    }

    private static void writeIfPresent(StringBuilder sb, Properties p, String key) {
        String v = p.getProperty(key);
        if (v != null) sb.append(key).append('=').append(v).append('\n');
    }

    // ============================================================
    //  Вспомогательное
    // ============================================================

    /**
     * Проверяет, что id — валидное имя папки в saves/ и что такая папка есть.
     * Не использует Minecraft API, чтобы не зависеть от клиента.
     */
    public static boolean worldExists(String levelId) {
        if (levelId == null || clean(levelId) == null) return false;
        Path saves = savesDir();
        if (saves == null) return false;
        Path candidate = saves.resolve(levelId);
        return Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve("level.dat"));
    }

    private static Path savesDir() {
        try {
            return FMLPaths.GAMEDIR.get().resolve("saves");
        } catch (Throwable t) {
            return null;
        }
    }

    private static String clean(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        // снимаем парные кавычки, которые иногда приходят из cmd
        if (s.length() >= 2) {
            char a = s.charAt(0), b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        if (s.isEmpty() || s.chars().anyMatch(c -> c < 32)) return null;
        if (s.length() > MAX_ID) return null;
        // защита от выхода из saves/
        if (s.indexOf('/') >= 0 || s.indexOf('\\') >= 0) return null;
        if (s.equals(".") || s.equals("..")) return null;
        if (s.toLowerCase(Locale.ROOT).contains("..")) return null;
        return s;
    }
}