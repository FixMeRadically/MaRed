package com.fixmer.mared.script;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.fixmer.mared.Mared;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public class MaredScriptStorage {

    private static final String SUBDIR = "mared/scripts";

    public static Path getScriptsDir() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return null;

        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path dir = worldPath.resolve(SUBDIR);

        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to create scripts dir: {}", dir, e);
            return null;
        }
        return dir;
    }

    public static List<String> listScripts() {
        List<String> result = new ArrayList<>();
        Path dir = getScriptsDir();
        if (dir == null) return result;

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .map(p -> p.getFileName().toString())
                  .filter(n -> n.endsWith(".js"))
                  .map(n -> n.substring(0, n.length() - 3))
                  .sorted()
                  .forEach(result::add);
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to read scripts list", e);
        }
        return result;
    }

    public static String readScript(String name) {
        Path dir = getScriptsDir();
        if (dir == null) return "";
        try {
            return Files.readString(dir.resolve(name + ".js"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public static boolean writeScript(String name, String content) {
        Path dir = getScriptsDir();
        if (dir == null) return false;
        try {
            Files.writeString(dir.resolve(name + ".js"), content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to save script {}", name, e);
            return false;
        }
    }

    public static boolean deleteScript(String name) {
        Path dir = getScriptsDir();
        if (dir == null) return false;
        try {
            return Files.deleteIfExists(dir.resolve(name + ".js"));
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to delete script {}", name, e);
            return false;
        }
    }

    public static boolean createScript(String name) {
        Path dir = getScriptsDir();
        if (dir == null) return false;
        Path file = dir.resolve(name + ".js");
        if (Files.exists(file)) return false;
        try {
            Files.writeString(file, "// Mared script\n", StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to create script {}", name, e);
            return false;
        }
    }

    public static boolean isValidName(String name) {
        return name != null && !name.isEmpty()
            && name.matches("[a-zA-Z0-9_\\-]+")
            && !name.equals(".");
    }
}