package com.fixmer.mared.gui2.framework.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fixmer.mared.Mared;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Загрузка кастомных тем из config/mared/themes.json.
 *
 * 0.3.0: перенесено из gui.common.MaredCustomThemes.
 */
public final class MaredCustomThemes {

    private MaredCustomThemes() {}

    private static boolean loaded = false;

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("mared").resolve("themes.json");
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;

        Path path = file();
        if (!Files.exists(path)) {
            writeExample(path);
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("themes")) return;

            JsonArray arr = root.getAsJsonArray("themes");
            int count = 0;

            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                if (!o.has("id")) continue;
                String id = o.get("id").getAsString();
                if (id == null || id.isEmpty()) continue;
                if (MaredThemeRegistry.get(id) != null) continue;

                try {
                    MaredTheme theme = parseTheme(o);
                    if (theme != null) {
                        MaredThemeRegistry.register(theme);
                        count++;
                    }
                } catch (Exception e) {
                    Mared.LOGGER.warn("[Mared] Failed to parse theme '{}': {}",
                        id, e.getMessage());
                }
            }

            if (count > 0) {
                Mared.LOGGER.info("[Mared] Loaded {} custom theme(s) from themes.json", count);
            }
        } catch (Exception e) {
            Mared.LOGGER.warn("[Mared] Failed to load themes.json: {}", e.getMessage());
        }
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    private static MaredTheme parseTheme(JsonObject o) {
        String id = o.get("id").getAsString();
        String displayName = o.has("displayName")
            ? o.get("displayName").getAsString() : id;
        boolean light = o.has("light") && o.get("light").getAsBoolean();

        MaredTheme.Builder b = MaredTheme.builder(id, displayName).light(light);

        if (o.has("bgScreen")) b.bgScreen(o.get("bgScreen").getAsInt());
        if (o.has("bgPanel")) b.bgPanel(o.get("bgPanel").getAsInt());
        if (o.has("bgPanelRaised")) b.bgPanelRaised(o.get("bgPanelRaised").getAsInt());
        if (o.has("bgSunken")) b.bgSunken(o.get("bgSunken").getAsInt());
        if (o.has("bgHover")) b.bgHover(o.get("bgHover").getAsInt());
        if (o.has("bgSelected")) b.bgSelected(o.get("bgSelected").getAsInt());

        if (o.has("text")) b.text(o.get("text").getAsInt());
        if (o.has("textDim")) b.textDim(o.get("textDim").getAsInt());
        if (o.has("textFaint")) b.textFaint(o.get("textFaint").getAsInt());
        if (o.has("textInverse")) b.textInverse(o.get("textInverse").getAsInt());

        if (o.has("accent")) b.accent(o.get("accent").getAsInt());
        if (o.has("accentAlt")) b.accentAlt(o.get("accentAlt").getAsInt());
        if (o.has("accentDim")) b.accentDim(o.get("accentDim").getAsInt());

        if (o.has("success")) b.success(o.get("success").getAsInt());
        if (o.has("warn")) b.warn(o.get("warn").getAsInt());
        if (o.has("danger")) b.danger(o.get("danger").getAsInt());
        if (o.has("info")) b.info(o.get("info").getAsInt());

        if (o.has("border")) b.border(o.get("border").getAsInt());
        if (o.has("borderAccent")) b.borderAccent(o.get("borderAccent").getAsInt());
        if (o.has("divider")) b.divider(o.get("divider").getAsInt());

        if (o.has("tabScripts")) b.tabScripts(o.get("tabScripts").getAsInt());
        if (o.has("tabCommands")) b.tabCommands(o.get("tabCommands").getAsInt());
        if (o.has("tabNpc")) b.tabNpc(o.get("tabNpc").getAsInt());
        if (o.has("tabEvents")) b.tabEvents(o.get("tabEvents").getAsInt());
        if (o.has("tabQuests")) b.tabQuests(o.get("tabQuests").getAsInt());

        if (o.has("monotoneTabs")) b.monotoneTabs(o.get("monotoneTabs").getAsBoolean());

        return b.build();
    }

    private static void writeExample(Path path) {
        try {
            Files.createDirectories(path.getParent());
            String example = "{\n" +
                "  \"_comment\": \"Custom themes. All fields optional except id.\",\n" +
                "  \"themes\": []\n" +
                "}\n";
            Files.writeString(path, example, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mared.LOGGER.warn("[Mared] Failed to create themes.json example: {}", e.getMessage());
        }
    }
}