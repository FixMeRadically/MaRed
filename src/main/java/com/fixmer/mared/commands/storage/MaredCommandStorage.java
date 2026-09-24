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
 * Работа с файлами команд в <config>/mared/commands/.
 * Не привязано к миру — доступно на любом сервере.
 */
public class MaredCommandStorage {

    private static final String SUBDIR = "mared/commands";

    public static Path getCommandsDir() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(SUBDIR);
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to create commands dir: {}", dir, e);
            return null;
        }
        return dir;
    }

    public static List<String> listCommands() {
        List<String> result = new ArrayList<>();
        Path dir = getCommandsDir();
        if (dir == null) return result;

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .map(p -> p.getFileName().toString())
                  .filter(n -> n.endsWith(".txt"))
                  .map(n -> n.substring(0, n.length() - 4))
                  .sorted()
                  .forEach(result::add);
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to read commands list", e);
        }
        return result;
    }

    public static String readCommand(String name) {
        Path dir = getCommandsDir();
        if (dir == null) return "";
        try {
            return Files.readString(dir.resolve(name + ".txt"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public static boolean writeCommand(String name, String content) {
        Path dir = getCommandsDir();
        if (dir == null) return false;
        try {
            Files.writeString(dir.resolve(name + ".txt"), content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to save command {}", name, e);
            return false;
        }
    }

    public static boolean deleteCommand(String name) {
        Path dir = getCommandsDir();
        if (dir == null) return false;
        try {
            return Files.deleteIfExists(dir.resolve(name + ".txt"));
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to delete command {}", name, e);
            return false;
        }
    }

    public static boolean createCommand(String name) {
        Path dir = getCommandsDir();
        if (dir == null) return false;
        Path file = dir.resolve(name + ".txt");
        if (Files.exists(file)) return false;
        try {
            Files.writeString(file,
                "// Write Minecraft commands here, one per line.\n// Lines starting with / are sent as-is.\n// Other lines get / prepended.\n",
                StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to create command {}", name, e);
            return false;
        }
    }

    public static boolean isValidName(String name) {
        return name != null && !name.isEmpty()
            && name.matches("[a-zA-Z0-9_\\-]+")
            && !name.equals(".");
    }
}