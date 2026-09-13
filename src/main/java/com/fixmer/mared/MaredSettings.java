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

    // ---- Режим авто-формата ----

    public enum AutoIndent {
        OFF,        // ничего не делает
        SIMPLE,     // { → новая строка + отступ + }
        SMART,      // Enter сохраняет отступ
        FULL        // оба
    }

    public enum IndentStyle {
        TAB,        // \t
        SPACES_4,   // 4 пробела
        SPACES_2    // 2 пробела
    }

    // ---- Значения ----

    private static AutoIndent autoIndent = AutoIndent.FULL;
    private static IndentStyle indentStyle = IndentStyle.SPACES_4;
    private static boolean backspaceRemovesIndent = true;

    private static boolean loaded = false;

    // ---- Get / Set ----

    public static AutoIndent getAutoIndent() { return autoIndent; }
    public static void setAutoIndent(AutoIndent v) { autoIndent = v; save(); }

    public static IndentStyle getIndentStyle() { return indentStyle; }
    public static void setIndentStyle(IndentStyle v) { indentStyle = v; save(); }

    public static boolean isBackspaceRemovesIndent() { return backspaceRemovesIndent; }
    public static void setBackspaceRemovesIndent(boolean v) { backspaceRemovesIndent = v; save(); }

    /** Строка отступа (Tab или пробелы). */
    public static String indentUnit() {
        return switch (indentStyle) {
            case TAB -> "\t";
            case SPACES_4 -> "    ";
            case SPACES_2 -> "  ";
        };
    }

    // ---- Загрузка / сохранение ----

    private static Path getConfigPath() {
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
        Path path = getConfigPath();
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
            if (obj.has("backspaceRemovesIndent")) {
                backspaceRemovesIndent = obj.get("backspaceRemovesIndent").getAsBoolean();
            }
        } catch (Exception e) {
            Mared.LOGGER.error("Failed to load settings", e);
        }
    }

    public static synchronized void save() {
        Path path = getConfigPath();
        if (path == null) return;
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("autoIndent", autoIndent.name());
            obj.addProperty("indentStyle", indentStyle.name());
            obj.addProperty("backspaceRemovesIndent", backspaceRemovesIndent);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(path, gson.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to save settings", e);
        }
    }
}