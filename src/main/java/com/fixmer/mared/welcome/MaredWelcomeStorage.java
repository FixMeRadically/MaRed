package com.fixmer.mared.welcome;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fixmer.mared.Mared;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Хранит флаг "welcome_seen" и дополнительные данные онбординга.
 * Файл: config/mared/welcome_seen.json
 */
public final class MaredWelcomeStorage {

    private MaredWelcomeStorage() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean loaded = false;
    private static boolean seen = false;
    private static long seenAt = 0L;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("mared").resolve("welcome_seen.json");
    }

    // ============================================================
    //  Public API
    // ============================================================

    /** true, если онбординг был пройден ранее. */
    public static boolean hasSeen() {
        ensureLoaded();
        return seen;
    }

    /** Помечает онбординг как пройденный. */
    public static void markSeen() {
        ensureLoaded();
        if (seen) return;
        seen = true;
        seenAt = System.currentTimeMillis();
        save();
    }

    /** Сбрасывает флаг — следующий заход снова покажет онбординг. */
    public static void reset() {
        seen = false;
        seenAt = 0L;
        save();
    }

    public static long seenAt() { return seenAt; }

    // ============================================================
    //  Load / Save
    // ============================================================

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Path p = file();
        if (!Files.exists(p)) return;

        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("seen"))   seen   = obj.get("seen").getAsBoolean();
            if (obj.has("seenAt")) seenAt = obj.get("seenAt").getAsLong();
        } catch (Exception e) {
            Mared.LOGGER.warn("[Mared] Failed to load welcome_seen.json: {}", e.getMessage());
        }
    }

    private static void save() {
        Path p = file();
        try {
            Files.createDirectories(p.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("seen", seen);
            obj.addProperty("seenAt", seenAt);
            Files.writeString(p, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to save welcome_seen.json: {}", e.getMessage());
        }
    }
}
