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
import com.fixmer.mared.MaredLang;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class MaredCommandRegistry {

    private MaredCommandRegistry() {}

    private static final String COMMANDS_DIR = "/mared/commands";
    private static final Gson GSON = new Gson();

    // ============================================================
    //  Data classes
    // ============================================================

    public static class NbtHint {
        public final String tag, what, why, example;
        public NbtHint(String tag, String what, String why, String example) {
            this.tag = tag; this.what = what; this.why = why; this.example = example;
        }
    }

    public static class Argument {
        public final String value, description;
        public final List<String> examples;
        public Argument(String value, String description, List<String> examples) {
            this.value = value; this.description = description; this.examples = examples;
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
        final String lowerName;

        public CommandInfo(String name, String category, int opLevel,
                           String description, String example,
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

    // ============================================================
    //  Storage
    // ============================================================

    private static final List<CommandInfo> COMMANDS = new ArrayList<>(64);
    private static final Map<String, CommandInfo> BY_LOWER_NAME = new HashMap<>(64);
    private static final Set<String> CATEGORIES_SET = new LinkedHashSet<>(8);

    private static boolean loaded = false;
    private static List<String> cachedFileList = null;

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

        List<CommandInfo> raw = new ArrayList<>(64);

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
        mergeByName(raw, COMMANDS);

        int m = COMMANDS.size();
        for (int i = 0; i < m; i++) {
            CommandInfo c = COMMANDS.get(i);
            BY_LOWER_NAME.put(c.lowerName, c);
            if (c.category != null && !c.category.isEmpty()) CATEGORIES_SET.add(c.category);
        }

        Mared.LOGGER.info("Total commands loaded: {} ({} raw, merged to {} unique names)",
            COMMANDS.size(), rawCount, m);
    }

    public static void reload() {
        loaded = false;
        load();
    }

    public static void invalidateFileCache() { cachedFileList = null; }

    // ============================================================
    //  Merge
    // ============================================================

    private static void mergeByName(List<CommandInfo> raw, List<CommandInfo> out) {
        Map<String, CommandInfo> byName = new LinkedHashMap<>(raw.size() * 2);
        int n = raw.size();
        for (int i = 0; i < n; i++) {
            CommandInfo c = raw.get(i);
            byName.merge(c.lowerName, c, MaredCommandRegistry::mergeTwo);
        }
        out.addAll(byName.values());
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

    private static boolean isEmpty(String s) { return s == null || s.isEmpty(); }

    // ============================================================
    //  FS
    // ============================================================

    private static List<String> listCommandFilesCached() {
        if (cachedFileList != null) return cachedFileList;
        cachedFileList = listCommandFiles();
        return cachedFileList;
    }

    private static List<String> listCommandFiles() {
        List<String> result = new ArrayList<>(8);

        try {
            URL url = MaredCommandRegistry.class.getResource(COMMANDS_DIR);
            if (url == null) {
                Mared.LOGGER.warn("Commands directory not found: {}", COMMANDS_DIR);
                return result;
            }

            if ("jar".equals(url.getProtocol())) {
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
            } else {
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
            }
        } catch (URISyntaxException | IOException e) {
            Mared.LOGGER.error("Failed to list commands", e);
        }
        return result;
    }

    // ============================================================
    //  JSON
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

            List<Argument> args = parseArgs(obj);
            List<NbtHint> nbt = parseNbt(obj);

            out.add(new CommandInfo(name, category, opLevel, description, example, args, nbt));
        }
    }

    private static List<Argument> parseArgs(JsonObject obj) {
        if (!obj.has("arguments")) return new ArrayList<>(2);
        JsonArray arrA = obj.getAsJsonArray("arguments");
        int an = arrA.size();
        List<Argument> args = new ArrayList<>(an);
        for (int j = 0; j < an; j++) {
            JsonElement aEl = arrA.get(j);
            if (!aEl.isJsonObject()) continue;
            JsonObject a = aEl.getAsJsonObject();
            String value = optString(a, "value", "");
            if (value.isEmpty()) continue;
            String desc = optStringLang(a, "description");
            List<String> exs = new ArrayList<>(2);
            if (a.has("examples")) {
                JsonArray arrE = a.getAsJsonArray("examples");
                int en = arrE.size();
                for (int k = 0; k < en; k++) exs.add(arrE.get(k).getAsString());
            }
            args.add(new Argument(value, desc, exs));
        }
        return args;
    }

    private static List<NbtHint> parseNbt(JsonObject obj) {
        if (!obj.has("nbt")) return new ArrayList<>(2);
        JsonArray arrN = obj.getAsJsonArray("nbt");
        int nn = arrN.size();
        List<NbtHint> nbt = new ArrayList<>(nn);
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
        return nbt;
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

    public static CommandInfo findByName(String name) {
        if (!loaded) load();
        if (name == null) return null;
        return BY_LOWER_NAME.get(name.toLowerCase());
    }

    public static List<CommandInfo> search(String query) {
        if (!loaded) load();
        if (query == null || query.isEmpty()) return COMMANDS;

        String q = query.toLowerCase();
        List<CommandInfo> result = new ArrayList<>(16);
        int n = COMMANDS.size();
        for (int i = 0; i < n; i++) {
            CommandInfo c = COMMANDS.get(i);
            if (c.lowerName.startsWith(q)) result.add(c);
        }
        return result;
    }

    public static List<String> categories() {
        if (!loaded) load();
        return new ArrayList<>(CATEGORIES_SET);
    }
}