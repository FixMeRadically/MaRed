package com.fixmer.mared.commands.storage;

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

    private static final String EXT = ".mared";

    private static Path scriptsDir() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("mared").resolve("scripts");
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to create scripts dir: {}", e.getMessage());
        }
        return dir;
    }

    private static Path fileFor(String name) {
        return scriptsDir().resolve(name + EXT);
    }

    public static List<String> listScripts() {
        List<String> result = new ArrayList<>(16);
        Path dir = scriptsDir();
        if (dir == null || !Files.exists(dir)) return result;

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .map(p -> p.getFileName().toString())
                  .filter(n -> n.endsWith(EXT))
                  .map(n -> n.substring(0, n.length() - EXT.length()))
                  .sorted()
                  .forEach(result::add);
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to list scripts: {}", e.getMessage());
        }
        return result;
    }

    public static boolean exists(String name) {
        if (name == null || name.isEmpty()) return false;
        return Files.exists(fileFor(name));
    }

    public static String readScript(String name) {
        if (name == null || name.isEmpty()) return "";
        Path file = fileFor(name);
        if (!Files.exists(file)) return "";
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to read script '{}': {}", name, e.getMessage());
            return "";
        }
    }

    public static boolean writeScript(String name, String content) {
        if (name == null || name.isEmpty()) return false;
        try {
            Files.writeString(fileFor(name), content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to write script '{}': {}", name, e.getMessage());
            return false;
        }
    }

    public static boolean createScript(String name) {
        if (name == null || name.isEmpty()) return false;
        Path file = fileFor(name);
        if (Files.exists(file)) return false;
        try {
            Files.writeString(file, "{\n    \n}\n", StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to create script '{}': {}", name, e.getMessage());
            return false;
        }
    }

    public static boolean deleteScript(String name) {
        if (name == null || name.isEmpty()) return false;
        try {
            return Files.deleteIfExists(fileFor(name));
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to delete script '{}': {}", name, e.getMessage());
            return false;
        }
    }

    public static boolean rename(String from, String to) {
        if (from == null || to == null || from.isEmpty() || to.isEmpty()) return false;
        Path src = fileFor(from);
        Path dst = fileFor(to);
        if (!Files.exists(src) || Files.exists(dst)) return false;
        try {
            Files.move(src, dst);
            return true;
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to rename script '{}' -> '{}': {}",
                from, to, e.getMessage());
            return false;
        }
    }
}