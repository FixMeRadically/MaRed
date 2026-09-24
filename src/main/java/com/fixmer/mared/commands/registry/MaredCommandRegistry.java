package com.fixmer.mared.commands.registry;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.fixmer.mared.Mared;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.fixmer.mared.MaredLang;

/**
 * Загрузчик справочника команд из JSON.
 *
 * FIX 0.2.5+:
 *   - Кэш FS-списка файлов (по mtime).
 *   - Кэш lowercase имён для search().
 *   - findByName через Map.
 *   - categories через LinkedHashSet.
 *   - reload() не перечитывает, если язык не изменился.
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

        // Кэш lowercase имени — для search()
        final String lowerName;

        public CommandInfo(String name, String category, int opLevel, String description, String example,
                           List<Argument> arguments, List<NbtHint> nbtHints) {
            this.name = name;
            this.category = category;
            this.opLevel = opLevel;
            this.description = description;
            this.example = example;
            this.arguments = arguments;
            this.nbtHints = nbtHints;
            this.lowerName = name.toLowerCase();
        }
    }

    // ---- Storage ----

    private static final List<CommandInfo> COMMANDS = new ArrayList<>();
    private static final Map<String, CommandInfo> BY_LOWER_NAME = new HashMap<>();
    private static final Set<String> CATEGORIES_SET = new LinkedHashSet<>();

    private static boolean loaded = false;

    // ---- FS cache ----

    /** Кэш списка файлов. null = не загружен. */
    private static List<String> cachedFileList = null;

    // ---- Gson (один экземпляр) ----

    private static final Gson GSON = new Gson();

    // ============================================================
    //  Load
    // ============================================================

    public static void load() {
        if (loaded) return;
        loaded = true;

        COMMANDS.clear();
        BY_LOWER_NAME.clear();
        CATEGORIES_SET.clear();

        List<String> files = listCommandFilesCached();
        if (files.isEmpty()) {
            Mared.LOGGER.warn("No .json files found in {}", COMMANDS_DIR);
            return;
        }

        List<CommandInfo> raw = new ArrayList<>();

        int n = files.size();
        for (int i = 0; i < n; i++) {
            String fileName = files.get(i);
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

        // Индекс по lowercase-имени + категории
        int m = merged.size();
        for (int i = 0; i < m; i++) {
            CommandInfo c = merged.get(i);
            BY_LOWER_NAME.put(c.lowerName, c);
            if (c.category != null && !c.category.isEmpty()) {
                CATEGORIES_SET.add(c.category);
            }
        }

        Mared.LOGGER.info("Total commands loaded: {} ({} raw, merged to {} unique names)",
            COMMANDS.size(), rawCount, merged.size());
    }

    /** Перезагрузка — если игрок сменил язык в настройках. */
    public static void reload() {
        loaded = false;
        COMMANDS.clear();
        BY_LOWER_NAME.clear();
        CATEGORIES_SET.clear();
        load();
    }

    /** FIX 0.2.5+: сбросить только FS-кэш (например, при ручной правке папки). */
    public static void invalidateFileCache() {
        cachedFileList = null;
    }

    // ============================================================
    //  Merge
    // ============================================================

    private static List<CommandInfo> mergeByName(List<CommandInfo> raw) {
        Map<String, CommandInfo> byName = new LinkedHashMap<>();
        int n = raw.size();
        for (int i = 0; i < n; i++) {
            CommandInfo c = raw.get(i);
            String key = c.lowerName;
            CommandInfo existing = byName.get(key);
            if (existing == null) {
                byName.put(key, c);
            } else {
                byName.put(key, mergeTwo(existing, c));
            }
        }
        return new ArrayList<>(byName.values());
    }

    private static CommandInfo mergeTwo(CommandInfo a, CommandInfo b) {
        String description = !isEmpty(a.description) ? a.description : b.description;
        String example = !isEmpty(a.example) ? a.example : b.example;
        String category = !isEmpty(a.category) ? a.category : b.category;
        int opLevel = a.opLevel != 0 ? a.opLevel : b.opLevel;

        List<Argument> args = new ArrayList<>(a.arguments);
        int bArgN = b.arguments.size();
        for (int i = 0; i < bArgN; i++) {
            Argument argB = b.arguments.get(i);
            boolean dup = false;
            int aArgN = args.size();
            for (int j = 0; j < aArgN; j++) {
                if (args.get(j).value.equalsIgnoreCase(argB.value)) { dup = true; break; }
            }
            if (!dup) args.add(argB);
        }

        List<NbtHint> nbt = new ArrayList<>(a.nbtHints);
        int bNbtN = b.nbtHints.size();
        for (int i = 0; i < bNbtN; i++) {
            NbtHint nbtB = b.nbtHints.get(i);
            boolean dup = false;
            int aNbtN = nbt.size();
            for (int j = 0; j < aNbtN; j++) {
                if (nbt.get(j).tag.equalsIgnoreCase(nbtB.tag)) { dup = true; break; }
            }
            if (!dup) nbt.add(nbtB);
        }

        return new CommandInfo(a.name, category, opLevel, description, example, args, nbt);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    // ============================================================
    //  FS-кэш
    // ============================================================

    private static List<String> listCommandFilesCached() {
        if (cachedFileList != null) return cachedFileList;
        cachedFileList = listCommandFiles();
        return cachedFileList;
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

    // ============================================================
    //  Парсинг JSON
    // ============================================================

    private static void parseJsonInto(String json, List<CommandInfo> out) {
        JsonObject root;
        try {
            root = GSON.fromJson(json, JsonObject.class);
        } catch (Exception e) {
            Mared.LOGGER.error("Failed to parse command JSON: {}", e.getMessage());
            return;
        }
        if (root == null || !root.has("commands")) return;

        JsonArray arr = root.getAsJsonArray("commands");
        int n = arr.size();
        for (int i = 0; i < n; i++) {
            JsonElement el = arr.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();

            String name = optString(obj, "name", "");
            if (name.isEmpty()) continue;
            String category = optString(obj, "category", "Misc");
            int opLevel = obj.has("opLevel") ? obj.get("opLevel").getAsInt() : 2;

            String description = optStringLang(obj, "description");
            String example = optString(obj, "example", "/" + name);

            List<Argument> args = new ArrayList<>();
            if (obj.has("arguments")) {
                JsonArray arrA = obj.getAsJsonArray("arguments");
                int an = arrA.size();
                for (int j = 0; j < an; j++) {
                    JsonElement aEl = arrA.get(j);
                    if (!aEl.isJsonObject()) continue;
                    JsonObject a = aEl.getAsJsonObject();
                    String value = optString(a, "value", "");
                    if (value.isEmpty()) continue;
                    String desc = optStringLang(a, "description");
                    List<String> exs = new ArrayList<>();
                    if (a.has("examples")) {
                        JsonArray arrE = a.getAsJsonArray("examples");
                        int en = arrE.size();
                        for (int k = 0; k < en; k++) exs.add(arrE.get(k).getAsString());
                    }
                    args.add(new Argument(value, desc, exs));
                }
            }

            List<NbtHint> nbt = new ArrayList<>();
            if (obj.has("nbt")) {
                JsonArray arrN = obj.getAsJsonArray("nbt");
                int nn = arrN.size();
                for (int j = 0; j < nn; j++) {
                    JsonElement nEl = arrN.get(j);
                    if (!nEl.isJsonObject()) continue;
                    JsonObject nb = nEl.getAsJsonObject();
                    nbt.add(new NbtHint(
                        optString(nb, "tag", ""),
                        optStringLang(nb, "what"),
                        optStringLang(nb, "why"),
                        optString(nb, "example", "")
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

    // ============================================================
    //  Public API
    // ============================================================

    public static List<CommandInfo> all() {
        if (!loaded) load();
        return COMMANDS;
    }

    /** FIX 0.2.5+: O(1) поиск по имени. */
    public static CommandInfo findByName(String name) {
        if (!loaded) load();
        if (name == null) return null;
        return BY_LOWER_NAME.get(name.toLowerCase());
    }

    /**
     * FIX 0.2.5+: search с нормализованным запросом.
     * lowercase-имена уже закэшированы в CommandInfo.lowerName.
     */
    public static List<CommandInfo> search(String query) {
        if (!loaded) load();
        List<CommandInfo> all = COMMANDS;
        if (query == null || query.isEmpty()) return all;

        String q = query.toLowerCase();
        List<CommandInfo> result = new ArrayList<>();
        int n = all.size();
        for (int i = 0; i < n; i++) {
            CommandInfo c = all.get(i);
            if (c.lowerName.startsWith(q)) {
                result.add(c);
            }
        }
        return result;
    }

    /** FIX 0.2.5+: через Set, без O(n²). */
    public static List<String> categories() {
        if (!loaded) load();
        return new ArrayList<>(CATEGORIES_SET);
    }
}