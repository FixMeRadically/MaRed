package com.fixmer.mared.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.Mared;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.neoforged.fml.loading.FMLPaths;

public class MaredCommandRegistry {

    private static final String SUBDIR = "mared";
    private static final String FILE_NAME = "commands.json";
    private static final String RESOURCE_PATH = "/mared/commands.json";

    // ---- Data classes ----

    public static class NbtHint {
        public final String tag;
        public final String what;
        public final String why;
        public final String example;

        public NbtHint(String tag, String what, String why, String example) {
            this.tag = tag;
            this.what = what;
            this.why = why;
            this.example = example;
        }
    }

    public static class Argument {
        public final String value;
        public final String description;
        public final List<String> examples;

        public Argument(String value, String description, List<String> examples) {
            this.value = value;
            this.description = description;
            this.examples = examples;
        }
    }

    public static class CommandInfo {
        public final String name;
        public final String category;
        public final int opLevel;
        public final String description;
        public final String example;
        public final List<Argument> arguments;
        public final List<NbtHint> nbtHints;

        public CommandInfo(String name, String category, int opLevel, String description, String example,
                           List<Argument> arguments, List<NbtHint> nbtHints) {
            this.name = name;
            this.category = category;
            this.opLevel = opLevel;
            this.description = description;
            this.example = example;
            this.arguments = arguments;
            this.nbtHints = nbtHints;
        }
    }

    // ---- Storage ----

    private static final List<CommandInfo> COMMANDS = new ArrayList<>();
    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;
        loaded = true;
        COMMANDS.clear();

        Path file = getFilePath();

        if (file == null || !Files.exists(file)) {
            if (file != null) copyResourceTo(file);
        }

        if (file != null && Files.exists(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                parseJson(json);
                Mared.LOGGER.info("Loaded {} commands from {}", COMMANDS.size(), file);
                return;
            } catch (Exception e) {
                Mared.LOGGER.error("Failed to parse commands.json: {}", e.getMessage());
            }
        }

        try (InputStream in = MaredCommandRegistry.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in != null) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                parseJson(json);
                Mared.LOGGER.info("Loaded {} commands from built-in resource", COMMANDS.size());
            } else {
                Mared.LOGGER.warn("Built-in commands.json resource not found");
            }
        } catch (Exception e) {
            Mared.LOGGER.error("Failed to load built-in commands: {}", e.getMessage());
        }
    }

    private static Path getFilePath() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve(SUBDIR);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            return dir.resolve(FILE_NAME);
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to create commands config dir", e);
            return null;
        }
    }

    private static void copyResourceTo(Path file) {
        try (InputStream in = MaredCommandRegistry.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) return;
            Files.write(file, in.readAllBytes());
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to copy default commands.json", e);
        }
    }

    private static void parseJson(String json) {
        Gson gson = new Gson();
        JsonObject root = gson.fromJson(json, JsonObject.class);
        if (root == null || !root.has("commands")) return;

        JsonArray arr = root.getAsJsonArray("commands");
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();

            String name = optString(obj, "name", "");
            if (name.isEmpty()) continue;
            String category = optString(obj, "category", "Misc");
            int opLevel = obj.has("opLevel") ? obj.get("opLevel").getAsInt() : 2;
            String description = optString(obj, "description", "");
            String example = optString(obj, "example", "/" + name);

            List<Argument> args = new ArrayList<>();
            if (obj.has("arguments")) {
                for (JsonElement aEl : obj.getAsJsonArray("arguments")) {
                    if (!aEl.isJsonObject()) continue;
                    JsonObject a = aEl.getAsJsonObject();
                    String value = optString(a, "value", "");
                    String desc = optString(a, "description", "");
                    List<String> exs = new ArrayList<>();
                    if (a.has("examples")) {
                        for (JsonElement e : a.getAsJsonArray("examples")) {
                            exs.add(e.getAsString());
                        }
                    }
                    if (!value.isEmpty()) args.add(new Argument(value, desc, exs));
                }
            }

            List<NbtHint> nbt = new ArrayList<>();
            if (obj.has("nbt")) {
                for (JsonElement nEl : obj.getAsJsonArray("nbt")) {
                    if (!nEl.isJsonObject()) continue;
                    JsonObject n = nEl.getAsJsonObject();
                    nbt.add(new NbtHint(
                        optString(n, "tag", ""),
                        optString(n, "what", ""),
                        optString(n, "why", ""),
                        optString(n, "example", "")
                    ));
                }
            }

            COMMANDS.add(new CommandInfo(name, category, opLevel, description, example, args, nbt));
        }
    }

    private static String optString(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    // ---- Public API ----

    public static List<CommandInfo> all() {
        if (!loaded) load();
        return COMMANDS;
    }

    public static CommandInfo findByName(String name) {
        for (CommandInfo c : all()) {
            if (c.name.equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    /** Поиск только по имени команды. */
    public static List<CommandInfo> search(String query) {
        List<CommandInfo> all = all();
        if (query == null || query.isEmpty()) return all;
        String q = query.toLowerCase();
        List<CommandInfo> result = new ArrayList<>();
        for (CommandInfo c : all) {
            if (c.name.toLowerCase().contains(q)) {
                result.add(c);
            }
        }
        return result;
    }

    public static List<String> categories() {
        List<String> cats = new ArrayList<>();
        for (CommandInfo c : all()) {
            if (!cats.contains(c.category)) cats.add(c.category);
        }
        return cats;
    }
}