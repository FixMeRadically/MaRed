package com.fixmer.mared.dev;

import com.fixmer.mared.Mared;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Определение dev-режима MaRed.
 *
 * Приоритет источников (первый сработавший побеждает):
 *
 *   1. System property: -Dmared.dev=true|false
 *   2. Переменная окружения GENESIS_DEV_MODE (её ставит супервизор)
 *   3. Файл-маркер .genesis-dev в одном из мест:
 *        - CWD                       (обычно run/ для gradlew runClient)
 *        - родитель CWD              (обычно корень MaRed)
 *        - рядом с jar мода
 *
 * Результат кэшируется: среда не меняется во время жизни процесса.
 *
 * Шаг 2 использует флаг для логов и маркера .dev-running.
 * Шаг 3 использует флаг для пропуска главного меню (TitleScreenMixin).
 */
public final class DevMode {

    private DevMode() {}

    private static final String PROP    = "mared.dev";
    private static final String ENV     = "GENESIS_DEV_MODE";
    private static final String MARKER  = ".genesis-dev";
    private static final String RUNNING = ".dev-running";

    private static volatile Boolean cached;
    private static volatile boolean enabledForSession;
    public static void enableForSession() { enabledForSession = true; }

    /** Включён ли dev-режим. Потокобезопасно, результат кэшируется. */
    public static boolean isEnabled() {
        if (enabledForSession) return true;
        Boolean v = cached;
        if (v != null) return v;
        synchronized (DevMode.class) {
            if (cached == null) cached = detect();
            return cached;
        }
    }

    /** Сброс кэша. Только для тестов. */
    public static void resetForTests() {
        cached = null;
    }

    private static boolean detect() {
        String prop = System.getProperty(PROP);
        if (prop != null && !prop.isBlank()) {
            return Boolean.parseBoolean(prop.trim());
        }

        String env = System.getenv(ENV);
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env.trim());
        }

        Path marker = locateMarker();
        return marker != null;
    }

    private static Path locateMarker() {
        try {
            Path cwd = Path.of("").toAbsolutePath().normalize();
            Path direct = cwd.resolve(MARKER);
            if (Files.isRegularFile(direct)) return direct;

            Path parent = cwd.getParent();
            if (parent != null) {
                Path viaParent = parent.resolve(MARKER);
                if (Files.isRegularFile(viaParent)) return viaParent;
            }

            var src = DevMode.class.getProtectionDomain().getCodeSource();
            if (src != null && src.getLocation() != null) {
                Path loc = Path.of(src.getLocation().toURI());
                Path dir = Files.isDirectory(loc) ? loc : loc.getParent();
                if (dir != null) {
                    Path viaJar = dir.resolve(MARKER);
                    if (Files.isRegularFile(viaJar)) return viaJar;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Описание того, откуда взялось текущее значение. Для логов. */
    public static String sourceDescription() {
        if (enabledForSession) return "automatic development session";
        String prop = System.getProperty(PROP);
        if (prop != null && !prop.isBlank()) {
            return "system property -D" + PROP + "=" + prop;
        }
        String env = System.getenv(ENV);
        if (env != null && !env.isBlank()) {
            return "environment " + ENV + "=" + env;
        }
        Path marker = locateMarker();
        if (marker != null) {
            return "marker file " + marker;
        }
        return "default";
    }

    /** Однократный лог статуса. Вызывается из конструктора мода. */
    public static void logStatus() {
        if (isEnabled()) {
            Mared.LOGGER.info("[Mared] DEV MODE enabled (source: {})", sourceDescription());
        } else {
            Mared.LOGGER.debug("[Mared] DEV MODE disabled (source: {})", sourceDescription());
        }
    }

    // ============================================================
    //  Маркер «клиент запущен»
    // ============================================================

    /**
     * Пишет config/mared/.dev-running с PID, временем старта и фазой.
     * Шаг 11 (файловый watcher) и Шаг 12 (параллельные фазы) будут
     * читать этот маркер.
     */
    public static void markRunning(String phase) {
        if (!isEnabled()) return;
        Path file = runningFile();
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            String content =
                "pid=" + ProcessHandle.current().pid() + "\n" +
                "startedAt=" + System.currentTimeMillis() + "\n" +
                "phase=" + phase + "\n" +
                "version=" + Mared.VERSION + "\n";
            Files.writeString(file, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            Mared.LOGGER.debug("[Mared] cannot write dev marker: {}", e.toString());
        }
    }

    /** Удаляет маркер. Вызывается при штатном завершении клиента. */
    public static void clearRunning() {
        if (!isEnabled()) return;
        Path file = runningFile();
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {}
    }

    private static Path runningFile() {
        try {
            return com.fixmer.mared.dev.DevPaths.file("running.state");
        } catch (Throwable t) {
            return null;
        }
    }
}