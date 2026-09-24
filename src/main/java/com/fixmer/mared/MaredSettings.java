package com.fixmer.mared;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;

/**
 * Глобальные настройки Mared.
 * Хранятся в <config>/mared/settings.json.
 */
public final class MaredSettings {

    private MaredSettings() {}

    // ============================================================
    //  Enums
    // ============================================================

    public enum AutoIndent {
        OFF,     // ничего не делает
        SIMPLE,  // { → новая строка + отступ + }
        SMART,   // Enter сохраняет отступ
        FULL     // оба
    }

    public enum IndentStyle {
        TAB,        // \t
        SPACES_4,   // 4 пробела
        SPACES_2    // 2 пробела
    }

    /**
     * Режим работы с сервером.
     *
     * AUTO        — автоматически: singleplayer → FULL, multiplayer → CLIENT_ONLY
     * CLIENT_ONLY — все Mared-команды работают локально, ванильные команды без OP не отправляются
     * FULL        — разрешить отправку ванильных команд, если игрок имеет OP
     */
    public enum ServerMode {
        AUTO, CLIENT_ONLY, FULL
    }

    // ============================================================
    //  Значения
    // ============================================================

    private static AutoIndent  autoIndent             = AutoIndent.FULL;
    private static IndentStyle indentStyle            = IndentStyle.SPACES_4;
    private static ServerMode  serverMode             = ServerMode.AUTO;
    private static boolean backspaceRemovesIndent     = true;
    private static boolean logChatToEditor            = true;
    private static boolean verboseScriptLog           = false;

    private static boolean loaded = false;

    // ============================================================
    //  Getters / Setters
    // ============================================================

    public static AutoIndent getAutoIndent() { return autoIndent; }
    public static void setAutoIndent(AutoIndent v) { autoIndent = v; save(); }

    public static IndentStyle getIndentStyle() { return indentStyle; }
    public static void setIndentStyle(IndentStyle v) { indentStyle = v; save(); }

    public static ServerMode getServerMode() { return serverMode; }
    public static void setServerMode(ServerMode v) { serverMode = v; save(); }

    public static boolean isBackspaceRemovesIndent() { return backspaceRemovesIndent; }
    public static void setBackspaceRemovesIndent(boolean v) { backspaceRemovesIndent = v; save(); }

    public static boolean isLogChatToEditor() { return logChatToEditor; }
    public static void setLogChatToEditor(boolean v) { logChatToEditor = v; save(); }

    public static boolean isVerboseScriptLog() { return verboseScriptLog; }
    public static void setVerboseScriptLog(boolean v) { verboseScriptLog = v; save(); }

    /** Строка отступа (Tab или пробелы). */
    public static String indentUnit() {
        return switch (indentStyle) {
            case TAB      -> "\t";
            case SPACES_4 -> "    ";
            case SPACES_2 -> "  ";
        };
    }

    // ============================================================
    //  Load / Save
    // ============================================================

    private static Path configPath() {
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("config/mared");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            return dir.resolve("settings.json");
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to prepare config path", e);
            return null;
        }
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;

        Path path = configPath();
        if (path == null || !Files.exists(path)) return;

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            if (obj.has("autoIndent")) {
                try { autoIndent = AutoIndent.valueOf(obj.get("autoIndent").getAsString()); }
                catch (Exception ignored) {}
            }
            if (obj.has("indentStyle")) {
                try { indentStyle = IndentStyle.valueOf(obj.get("indentStyle").getAsString()); }
                catch (Exception ignored) {}
            }
            if (obj.has("serverMode")) {
                try { serverMode = ServerMode.valueOf(obj.get("serverMode").getAsString()); }
                catch (Exception ignored) {}
            }
            if (obj.has("backspaceRemovesIndent")) {
                backspaceRemovesIndent = obj.get("backspaceRemovesIndent").getAsBoolean();
            }
            if (obj.has("logChatToEditor")) {
                logChatToEditor = obj.get("logChatToEditor").getAsBoolean();
            }
            if (obj.has("verboseScriptLog")) {
                verboseScriptLog = obj.get("verboseScriptLog").getAsBoolean();
            }
        } catch (Exception e) {
            Mared.LOGGER.error("Failed to load settings", e);
        }
    }

    public static synchronized void save() {
        Path path = configPath();
        if (path == null) return;
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("autoIndent", autoIndent.name());
            obj.addProperty("indentStyle", indentStyle.name());
            obj.addProperty("serverMode", serverMode.name());
            obj.addProperty("backspaceRemovesIndent", backspaceRemovesIndent);
            obj.addProperty("logChatToEditor", logChatToEditor);
            obj.addProperty("verboseScriptLog", verboseScriptLog);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(path, gson.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to save settings", e);
        }
    }
}