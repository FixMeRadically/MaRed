package com.fixmer.mared.gui2.framework.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fixmer.mared.Mared;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Загрузка кастомных тем из config/mared/themes.json.
 *
 * 0.3.0: перенесено из gui.common.
 * 0.3.2 (audit #28, #29):
 *   - reload() реально перезагружает: удаляет все ранее загруженные
 *     custom темы, затем грузит заново. Раньше reload просто пытался
 *     догрузить, а старая тема с тем же ID блокировала обновление.
 *   - Schema validation перед регистрацией.
 *   - Регистрация через registerCustom() — реестр знает, что это custom.
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
        doLoad();
    }

    public static synchronized void reload() {
        MaredThemeRegistry.unregisterAllCustom();
        loaded = false;
        load();
    }

    // ============================================================
    //  Internals
    // ============================================================

    private static void doLoad() {
        Path path = file();
        if (!Files.exists(path)) {
            writeExample(path);
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            for (MaredThemeSchema.Diagnostic d
                    : MaredThemeSchema.validateRoot(root)) {
                if (d.severity == MaredThemeSchema.Diagnostic.Severity.ERROR) {
                    Mared.LOGGER.warn("[theme] schema: {}", d);
                }
            }

            if (!root.has("themes")) return;
            JsonArray arr = root.getAsJsonArray("themes");

            int ok = 0, skipped = 0;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();

                List<MaredThemeSchema.Diagnostic> diags =
                    MaredThemeSchema.validate(o);

                boolean hasError = false;
                for (MaredThemeSchema.Diagnostic d : diags) {
                    if (d.severity == MaredThemeSchema.Diagnostic.Severity.ERROR) {
                        hasError = true;
                        Mared.LOGGER.warn("[theme] rejected '{}': {}",
                            safeId(o), d);
                    } else if (d.severity
                            == MaredThemeSchema.Diagnostic.Severity.WARNING) {
                        Mared.LOGGER.warn("[theme] '{}': {}", safeId(o), d);
                    }
                }
                if (hasError) { skipped++; continue; }

                try {
                    MaredTheme theme = parseTheme(o);
                    if (theme != null) {
                        MaredThemeRegistry.registerCustom(theme);
                        ok++;
                    }
                } catch (Exception e) {
                    Mared.LOGGER.warn(
                        "[theme] failed to parse '{}': {}",
                        safeId(o), e.getMessage());
                    skipped++;
                }
            }

            if (ok > 0 || skipped > 0) {
                Mared.LOGGER.info(
                    "[Mared] Custom themes: {} loaded, {} skipped",
                    ok, skipped);
            }
        } catch (Exception e) {
            Mared.LOGGER.warn("[Mared] Failed to load themes.json: {}",
                e.getMessage());
        }
    }

    private static String safeId(JsonObject o) {
        try {
            return o.has("id") ? o.get("id").getAsString() : "<no-id>";
        } catch (Exception e) { return "<bad-id>"; }
    }

    private static MaredTheme parseTheme(JsonObject o) {
        String id = o.get("id").getAsString();
        String displayName = o.has("displayName")
            ? o.get("displayName").getAsString() : id;
        boolean light = o.has("light") && o.get("light").getAsBoolean();

        MaredTheme.Builder b = MaredTheme.builder(id, displayName).light(light);

        if (o.has("bgScreen"))      b.bgScreen(o.get("bgScreen").getAsInt());
        if (o.has("bgPanel"))       b.bgPanel(o.get("bgPanel").getAsInt());
        if (o.has("bgPanelRaised")) b.bgPanelRaised(o.get("bgPanelRaised").getAsInt());
        if (o.has("bgSunken"))      b.bgSunken(o.get("bgSunken").getAsInt());
        if (o.has("bgHover"))       b.bgHover(o.get("bgHover").getAsInt());
        if (o.has("bgSelected"))    b.bgSelected(o.get("bgSelected").getAsInt());

        if (o.has("text"))        b.text(o.get("text").getAsInt());
        if (o.has("textDim"))     b.textDim(o.get("textDim").getAsInt());
        if (o.has("textFaint"))   b.textFaint(o.get("textFaint").getAsInt());
        if (o.has("textInverse")) b.textInverse(o.get("textInverse").getAsInt());

        if (o.has("accent"))    b.accent(o.get("accent").getAsInt());
        if (o.has("accentAlt")) b.accentAlt(o.get("accentAlt").getAsInt());
        if (o.has("accentDim")) b.accentDim(o.get("accentDim").getAsInt());

        if (o.has("success")) b.success(o.get("success").getAsInt());
        if (o.has("warn"))    b.warn(o.get("warn").getAsInt());
        if (o.has("danger"))  b.danger(o.get("danger").getAsInt());
        if (o.has("info"))    b.info(o.get("info").getAsInt());

        if (o.has("border"))       b.border(o.get("border").getAsInt());
        if (o.has("borderAccent")) b.borderAccent(o.get("borderAccent").getAsInt());
        if (o.has("divider"))      b.divider(o.get("divider").getAsInt());

        if (o.has("tabScripts"))  b.tabScripts(o.get("tabScripts").getAsInt());
        if (o.has("tabCommands")) b.tabCommands(o.get("tabCommands").getAsInt());
        if (o.has("tabNpc"))      b.tabNpc(o.get("tabNpc").getAsInt());
        if (o.has("tabEvents"))   b.tabEvents(o.get("tabEvents").getAsInt());
        if (o.has("tabQuests"))   b.tabQuests(o.get("tabQuests").getAsInt());

        if (o.has("monotoneTabs"))
            b.monotoneTabs(o.get("monotoneTabs").getAsBoolean());

        if (o.has("padSmall"))  b.padSmall(o.get("padSmall").getAsInt());
        if (o.has("padMedium")) b.padMedium(o.get("padMedium").getAsInt());
        if (o.has("padLarge"))  b.padLarge(o.get("padLarge").getAsInt());
        if (o.has("padHuge"))   b.padHuge(o.get("padHuge").getAsInt());

        if (o.has("radiusSmall"))  b.radiusSmall(o.get("radiusSmall").getAsInt());
        if (o.has("radiusMedium")) b.radiusMedium(o.get("radiusMedium").getAsInt());
        if (o.has("radiusLarge"))  b.radiusLarge(o.get("radiusLarge").getAsInt());

        return b.build();
    }

    private static void writeExample(Path path) {
        try {
            Files.createDirectories(path.getParent());
            String example = "{\n" +
                "  \"_comment\": \"Custom themes. All fields optional except id.\",\n" +
                "  \"_schema\": {\n" +
                "    \"id\": \"[a-z0-9_\\\\-:]+\",\n" +
                "    \"colors\": \"ARGB int\",\n" +
                "    \"pad*\": \"0..32\",\n" +
                "    \"radius*\": \"0..16\"\n" +
                "  },\n" +
                "  \"themes\": []\n" +
                "}\n";
            Files.writeString(path, example, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mared.LOGGER.warn(
                "[Mared] Failed to create themes.json example: {}",
                e.getMessage());
        }
    }
}