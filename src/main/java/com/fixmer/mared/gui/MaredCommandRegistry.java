package com.fixmer.mared.gui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import com.fixmer.mared.Mared;
import com.fixmer.mared.script.MaredLang;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Загрузчик справочника команд из JSON.
 * Читает ВСЕ .json-файлы из папки resources/mared/commands/.
 *
 * FIX 0.2.3: поддержка локализации через поля "_ru".
 *   description       → description_ru       (если язык русский)
 *   arguments[].description  → arguments[].description_ru
 *   nbt[].what        → nbt[].what_ru
 *   nbt[].why         → nbt[].why_ru
 *
 * Если поле "_ru" отсутствует — используется английский вариант.
 */
public class MaredCommandRegistry {

    private static final String COMMANDS_DIR = "/mared/commands";

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

        List<String> files = listCommandFiles();
        if (files.isEmpty()) {
            Mared.LOGGER.warn("No .json files found in {}", COMMANDS_DIR);
            return;
        }

        for (String fileName : files) {
            String fullPath = COMMANDS_DIR + "/" + fileName;
            try (InputStream in = MaredCommandRegistry.class.getResourceAsStream(fullPath)) {
                if (in == null) {
                    Mared.LOGGER.warn("Command file not found, skipping: {}", fullPath);
                    continue;
                }
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                int before = COMMANDS.size();
                parseJson(json);
                Mared.LOGGER.info("Loaded {} commands from {}", COMMANDS.size() - before, fullPath);
            } catch (Exception e) {
                Mared.LOGGER.error("Failed to load {}: {}", fullPath, e.getMessage());
            }
        }

        Mared.LOGGER.info("Total commands loaded: {}", COMMANDS.size());
    }

    /** Перезагрузка — если игрок сменил язык в настройках. */
    public static void reload() {
        loaded = false;
        COMMANDS.clear();
        load();
    }

    private static List<String> listCommandFiles() {
        List<String> result = new ArrayList<>();

        try {
            URL url = MaredCommandRegistry.class.getResource(COMMANDS_DIR);
            if (url == null) {
                Mared.LOGGER.warn("Commands directory not found: {}", COMMANDS_DIR);
                return result;
            }

            if ("jar".equals(url.getProtocol())) {
                try {
                    URI uri = url.toURI();
                    try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                        Path dir = fs.getPath(COMMANDS_DIR);
                        if (Files.exists(dir)) {
                            try (Stream<Path> stream = Files.list(dir)) {
                                stream.filter(Files::isRegularFile)
                                      .map(p -> p.getFileName().toString())
                                      .filter(n -> n.endsWith(".json"))
                                      .sorted()
                                      .forEach(result::add);
                            }
                        }
                    }
                } catch (URISyntaxException | IOException e) {
                    Mared.LOGGER.error("Failed to list commands from JAR", e);
                }
            } else {
                try {
                    Path dir = Paths.get(url.toURI());
                    if (Files.exists(dir)) {
                        try (Stream<Path> stream = Files.list(dir)) {
                            stream.filter(Files::isRegularFile)
                                  .map(p -> p.getFileName().toString())
                                  .filter(n -> n.endsWith(".json"))
                                  .sorted()
                                  .forEach(result::add);
                        }
                    }
                } catch (URISyntaxException | IOException e) {
                    Mared.LOGGER.error("Failed to list commands from folder", e);
                }
            }
        } catch (Exception e) {
            Mared.LOGGER.error("Failed to list commands", e);
        }

        return result;
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

            // FIX: локализованные поля
            String description = optStringLang(obj, "description");
            String example = optString(obj, "example", "/" + name);

            List<Argument> args = new ArrayList<>();
            if (obj.has("arguments")) {
                for (JsonElement aEl : obj.getAsJsonArray("arguments")) {
                    if (!aEl.isJsonObject()) continue;
                    JsonObject a = aEl.getAsJsonObject();
                    String value = optString(a, "value", "");
                    String desc = optStringLang(a, "description");
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
                        optStringLang(n, "what"),
                        optStringLang(n, "why"),
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

    /**
     * FIX: выбирает локализованное поле.
     * Если язык русский и есть "<key>_ru" — вернёт его.
     * Иначе — англ. "<key>".
     */
    private static String optStringLang(JsonObject o, String key) {
        String lang = MaredLang.getCurrentCode();
        if (lang != null && lang.startsWith("ru")) {
            String keyRu = key + "_ru";
            if (o.has(keyRu) && !o.get(keyRu).isJsonNull()) {
                String s = o.get(keyRu).getAsString();
                if (s != null && !s.isEmpty()) return s;
            }
        }
        return optString(o, key, "");
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

    public static List<CommandInfo> search(String query) {
        List<CommandInfo> all = all();
        if (query == null || query.isEmpty()) return all;
        String q = query.toLowerCase();
        List<CommandInfo> result = new ArrayList<>();
        for (CommandInfo c : all) {
            if (c.name.toLowerCase().startsWith(q)) {
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