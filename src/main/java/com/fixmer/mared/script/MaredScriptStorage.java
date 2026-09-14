package com.fixmer.mared.script;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.fixmer.mared.Mared;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Хранилище скриптов (.mared файлы).
 * Путь: .minecraft/config/mared/scripts/
 */
public final class MaredScriptStorage {

    private MaredScriptStorage() {}

    private static Path scriptsDir() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("mared").resolve("scripts");
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to create scripts dir: {}", e.getMessage());
        }
        return dir;
    }

    /** Список имён скриптов (без расширения). */
    public static List<String> listScripts() {
        List<String> result = new ArrayList<>();
        Path dir = scriptsDir();
        if (dir == null || !Files.exists(dir)) return result;

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .map(p -> p.getFileName().toString())
                  .filter(n -> n.endsWith(".mared"))
                  .map(n -> n.substring(0, n.length() - ".mared".length()))
                  .sorted()
                  .forEach(result::add);
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to list scripts: {}", e.getMessage());
        }
        return result;
    }

    /** Читает содержимое скрипта. */
    public static String readScript(String name) {
        if (name == null) return "";
        Path file = scriptsDir().resolve(name + ".mared");
        if (!Files.exists(file)) return "";
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to read script '{}': {}", name, e.getMessage());
            return "";
        }
    }

    /** Записывает содержимое. */
    public static boolean writeScript(String name, String content) {
        if (name == null) return false;
        try {
            Files.writeString(scriptsDir().resolve(name + ".mared"), content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to write script '{}': {}", name, e.getMessage());
            return false;
        }
    }

    /** Создаёт пустой скрипт. */
    public static boolean createScript(String name) {
        if (name == null || name.isEmpty()) return false;
        Path file = scriptsDir().resolve(name + ".mared");
        if (Files.exists(file)) return false;
        try {
            Files.writeString(file, "{\n    \n}\n", StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to create script '{}': {}", name, e.getMessage());
            return false;
        }
    }

    /** Удаляет скрипт. */
    public static boolean deleteScript(String name) {
        if (name == null) return false;
        try {
            return Files.deleteIfExists(scriptsDir().resolve(name + ".mared"));
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to delete script '{}': {}", name, e.getMessage());
            return false;
        }
    }
}