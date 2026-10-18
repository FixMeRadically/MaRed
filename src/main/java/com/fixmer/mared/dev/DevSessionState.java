package com.fixmer.mared.dev;

import com.fixmer.mared.Mared;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Шаг 14.5: маленькое состояние dev-сессии.
 *
 * Файл: config/mared/.dev-state — одна строка, timestamp последнего
 * успешного входа в мир (millis). Используется:
 *
 *   1. Для логов: «это первый заход за N секунд» vs «быстрый перезаход».
 *   2. Для будущих шагов (10, 11): dev mode может по этому файлу
 *      понимать, что мы уже видели этот мир и можно срезать часть
 *      прогрева.
 *
 * Не управляет логикой сам — только хранит факт.
 */
public final class DevSessionState {

    private DevSessionState() {}

    private static final String FILE_NAME = ".dev-state";

    public static Path path() {
        return com.fixmer.mared.dev.DevPaths.state("session.state", FILE_NAME);
    }

    public static long lastWorldReadyMillis() {
        Path p = path();
        if (!Files.isRegularFile(p)) return 0L;
        try {
            String s = Files.readString(p, StandardCharsets.UTF_8).trim();
            return Long.parseLong(s);
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static boolean isQuickRestart() {
        long last = lastWorldReadyMillis();
        if (last == 0L) return false;
        long delta = System.currentTimeMillis() - last;
        return delta >= 0 && delta < 10 * 60 * 1000L; // 10 минут
    }

    public static void markWorldReady() {
        if (!DevMode.isEnabled()) return;
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, Long.toString(System.currentTimeMillis()),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mared.LOGGER.debug("[Mared] cannot write dev-state: {}", e.toString());
        }
    }
}