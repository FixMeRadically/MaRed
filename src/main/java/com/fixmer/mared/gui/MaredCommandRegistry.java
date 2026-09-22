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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * FIX 0.2.4: merge дубликатов по name.
 * Раньше: 3 × effect, 3 × execute, 2 × scoreboard и т.д. — все записи
 * попадали в COMMANDS отдельно, в списке было месиво.
 * Теперь: записи с одинаковым name (case-insensitive) объединяются:
 *   - arguments и nbt складываются (с дедупликацией по value)
 *   - description/example — первый непустой
 *   - category/opLevel — первый непустой
 *
 * FIX 0.2.4: локализация через поля "_ru" (как было).
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

        // Собираем "сырые" записи во временный список, потом мерджим
        List<CommandInfo> raw = new ArrayList<>();

        for (String fileName : files) {
            String fullPath = COMMANDS_DIR + "/" + fileName;
            try (InputStream in = MaredCommandRegistry.class.getResourceAsStream(fullPath)) {
                if (in == null) {
                    Mared.LOGGER.warn("Command file not found, skipping: {}", fullPath);
                    continue;
                }
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                int before = raw.size();
                parseJsonInto(json, raw);
                Mared.LOGGER.info("Loaded {} commands from {}", raw.size() - before, fullPath);
            } catch (Exception e) {
                Mared.LOGGER.error("Failed to load {}: {}", fullPath, e.getMessage());
            }
        }

        int rawCount = raw.size();
        List<CommandInfo> merged = mergeByName(raw);
        COMMANDS.addAll(merged);

        Mared.LOGGER.info("Total commands loaded: {} ({} raw, merged to {} unique names)",
            COMMANDS.size(), rawCount, merged.size());
    }

    /** Перезагрузка — если игрок сменил язык в настройках. */
    public static void reload() {
        loaded = false;
        COMMANDS.clear();
        load();
    }

    /**
     * FIX 0.2.4: merge записей с одинаковым name (case-insensitive).
     * Порядок сохраняется — первая встреченная запись задаёт позицию.
     */
    private static List<CommandInfo> mergeByName(List<CommandInfo> raw) {
        Map<String, CommandInfo> byName = new LinkedHashMap<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();

        for (CommandInfo c : raw) {
            String key = c.name.toLowerCase();
            if (!seen.containsKey(key)) {
                // Первая запись — берём как основу
                byName.put(key, c);
                seen.put(key, Boolean.TRUE);
                continue;
            }
            // Дубликат — мерджим в существующую
            CommandInfo existing = byName.get(key);
            byName.put(key, mergeTwo(existing, c));
        }
        return new ArrayList<>(byName.values());
    }

    private static CommandInfo mergeTwo(CommandInfo a, CommandInfo b) {
        // description / example — первый непустой
        String description = !isEmpty(a.description) ? a.description : b.description;
        String example = !isEmpty(a.example) ? a.example : b.example;

        // category / opLevel — первый непустой/недефолтный
        String category = !isEmpty(a.category) ? a.category : b.category;
        int opLevel = a.opLevel != 0 ? a.opLevel : b.opLevel;

        // arguments — дедупликация по value
        List<Argument> args = new ArrayList<>(a.arguments);
        for (Argument argB : b.arguments) {
            boolean dup = false;
            for (Argument argA : args) {
                if (argA.value.equalsIgnoreCase(argB.value)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) args.add(argB);
        }

        // nbt — дедупликация по tag
        List<NbtHint> nbt = new ArrayList<>(a.nbtHints);
        for (NbtHint nbtB : b.nbtHints) {
            boolean dup = false;
            for (NbtHint nbtA : nbt) {
                if (nbtA.tag.equalsIgnoreCase(nbtB.tag)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) nbt.add(nbtB);
        }

        return new CommandInfo(a.name, category, opLevel, description, example, args, nbt);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
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

    private static void parseJsonInto(String json, List<CommandInfo> out) {
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

            out.add(new CommandInfo(name, category, opLevel, description, example, args, nbt));
        }
    }

    private static String optString(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

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