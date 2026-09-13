package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.script.commands.MaredScriptCommand;

import net.minecraft.server.MinecraftServer;

public final class MaredBindRegistry {

    private MaredBindRegistry() {}

    public static final class Entry {
        public final String key;
        public final List<MaredScriptCommand> body;
        public final MaredScriptContext ctx;
        public final boolean blockVanilla;

        public Entry(String key, List<MaredScriptCommand> body, MaredScriptContext ctx, boolean blockVanilla) {
            this.key = key;
            this.body = body;
            this.ctx = ctx;
            this.blockVanilla = blockVanilla;
        }
    }

    private static final Map<String, List<Entry>> BINDINGS = new LinkedHashMap<>();

    public static void replace(String key, List<MaredScriptCommand> body, MaredScriptContext ctx, boolean blockVanilla) {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry(key, body, ctx, blockVanilla));
        BINDINGS.put(key, list);
    }

    public static void add(String key, List<MaredScriptCommand> body, MaredScriptContext ctx, boolean blockVanilla) {
        BINDINGS.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new Entry(key, body, ctx, blockVanilla));
    }

    public static void clear(String key) { BINDINGS.remove(key); }

    public static void clearAll() { BINDINGS.clear(); }

    public static void remove(String key, int index) {
        List<Entry> list = BINDINGS.get(key);
        if (list == null) return;
        if (index < 0 || index >= list.size()) return;
        list.remove(index);
        if (list.isEmpty()) BINDINGS.remove(key);
    }

    public static List<String> keys() { return new ArrayList<>(BINDINGS.keySet()); }

    public static List<Entry> entries(String key) {
        return BINDINGS.getOrDefault(key, List.of());
    }

    public static Map<String, List<Entry>> all() { return BINDINGS; }

    public static int totalCount() {
        int n = 0;
        for (List<Entry> list : BINDINGS.values()) n += list.size();
        return n;
    }

    public static boolean hasBlocking(String key) {
        for (Entry e : entries(key)) if (e.blockVanilla) return true;
        return false;
    }

    public static void fire(String key, MinecraftServer server) {
        List<Entry> list = BINDINGS.get(key);
        if (list == null || list.isEmpty()) return;
        for (Entry e : list) {
            MaredScriptContext ctx = e.ctx != null
                ? e.ctx
                : new MaredScriptContext(null, server, msg -> {});
            ctx.log("[bind fire] " + key);
            MaredScriptRunner.start(new MaredScriptExecutor(ctx, e.body));
        }
    }
}