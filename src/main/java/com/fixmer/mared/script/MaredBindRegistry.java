package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.script.commands.MaredScriptCommand;

import net.minecraft.server.MinecraftServer;

public final class MaredBindRegistry {

    private MaredBindRegistry() {}

    public enum BindMode { PRESS, HOLD, RELEASE }

    public static final class Entry {
        public final String key;
        public final List<MaredScriptCommand> body;
        public final MaredScriptContext ctx;
        public final boolean blockVanilla;
        public final BindMode mode;

        public Entry(String key, List<MaredScriptCommand> body, MaredScriptContext ctx,
                     boolean blockVanilla, BindMode mode) {
            this.key = key;
            this.body = body;
            this.ctx = ctx;
            this.blockVanilla = blockVanilla;
            this.mode = mode;
        }
    }

    private static final Map<String, List<Entry>> BINDINGS = new LinkedHashMap<>();

    private static final Map<Integer, List<String>> BY_KEYCODE = new HashMap<>();
    private static boolean indexDirty = true;

    private static final List<String> ACTIVE_HOLDS = new ArrayList<>();

    // ============================================================
    //  Индекс по keyCode
    // ============================================================

    private static void rebuildIndexIfNeeded() {
        if (!indexDirty) return;
        indexDirty = false;
        BY_KEYCODE.clear();
        for (String keyStr : BINDINGS.keySet()) {
            MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyStr);
            if (pk == null) continue;
            BY_KEYCODE.computeIfAbsent(pk.keyCode, k -> new ArrayList<>(2)).add(keyStr);
        }
    }

    public static List<String> keysForCode(int keyCode) {
        rebuildIndexIfNeeded();
        List<String> list = BY_KEYCODE.get(keyCode);
        return list != null ? list : java.util.Collections.emptyList();
    }

    private static void markDirty() {
        indexDirty = true;
    }

    // ============================================================
    //  Мутации
    // ============================================================

    public static void replace(String key, List<MaredScriptCommand> body, MaredScriptContext ctx,
                               boolean blockVanilla, BindMode mode) {
        removeBlock(key);
        List<Entry> list = new ArrayList<>(1);
        list.add(new Entry(key, body, ctx, blockVanilla, mode));
        BINDINGS.put(key, list);
        markDirty();
        if (blockVanilla) applyBlock(key);
    }

    public static void add(String key, List<MaredScriptCommand> body, MaredScriptContext ctx,
                           boolean blockVanilla, BindMode mode) {
        BINDINGS.computeIfAbsent(key, k -> new ArrayList<>(2))
                .add(new Entry(key, body, ctx, blockVanilla, mode));
        markDirty();
        if (blockVanilla) applyBlock(key);
    }

    public static void clear(String key) {
        BINDINGS.remove(key);
        markDirty();
        removeBlock(key);
        stopHold(key);
    }

    public static void unblock(String key) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(key);
        if (pk != null) MaredKeyBlocker.unblock(pk.keyCode);

        List<Entry> list = BINDINGS.get(key);
        if (list == null) return;
        list.removeIf(e -> e.blockVanilla);
        if (list.isEmpty()) {
            BINDINGS.remove(key);
        }
        markDirty();
    }

    public static void block(String key) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(key);
        if (pk == null) return;
        MaredKeyBlocker.block(pk.keyCode, key);
    }

    public static void clearAll() {
        BINDINGS.clear();
        ACTIVE_HOLDS.clear();
        markDirty();
        MaredKeyBlocker.clear();
    }

    public static void remove(String key, int index) {
        List<Entry> list = BINDINGS.get(key);
        if (list == null) return;
        if (index < 0 || index >= list.size()) return;
        list.remove(index);
        if (list.isEmpty()) {
            BINDINGS.remove(key);
            removeBlock(key);
        }
        markDirty();
    }

    // ============================================================
    //  Чтение
    // ============================================================

    public static List<String> keys() {
        return new ArrayList<>(BINDINGS.keySet());
    }

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
        List<Entry> list = BINDINGS.get(key);
        if (list == null) return false;
        int n = list.size();
        for (int i = 0; i < n; i++) {
            if (list.get(i).blockVanilla) return true;
        }
        return false;
    }

    // ============================================================
    //  Fire
    // ============================================================

    public static void fire(String key, MinecraftServer server) {
        List<Entry> list = BINDINGS.get(key);
        if (list == null || list.isEmpty()) return;
        int n = list.size();
        for (int i = 0; i < n; i++) {
            Entry e = list.get(i);
            if (e.mode != BindMode.PRESS) continue;
            runEntry(e, key, server);
        }
    }

    public static void fireRelease(String key, MinecraftServer server) {
        List<Entry> list = BINDINGS.get(key);
        if (list == null || list.isEmpty()) return;
        int n = list.size();
        for (int i = 0; i < n; i++) {
            Entry e = list.get(i);
            if (e.mode != BindMode.RELEASE) continue;
            runEntry(e, key, server);
        }
    }

    public static void startHold(String key, MinecraftServer server) {
        List<Entry> list = BINDINGS.get(key);
        if (list == null || list.isEmpty()) return;
        boolean hasHold = false;
        int n = list.size();
        for (int i = 0; i < n; i++) {
            Entry e = list.get(i);
            if (e.mode != BindMode.HOLD) continue;
            hasHold = true;
            runEntry(e, key, server);
        }
        if (hasHold && !ACTIVE_HOLDS.contains(key)) {
            ACTIVE_HOLDS.add(key);
        }
    }

    public static void stopHold(String key) {
        ACTIVE_HOLDS.remove(key);
    }

    public static void tickHolds(MinecraftServer server) {
        int size = ACTIVE_HOLDS.size();
        if (size == 0) return;

        for (int i = 0; i < size; i++) {
            if (i >= ACTIVE_HOLDS.size()) break;
            String key = ACTIVE_HOLDS.get(i);
            List<Entry> list = BINDINGS.get(key);
            if (list == null) {
                ACTIVE_HOLDS.remove(i);
                size--;
                i--;
                continue;
            }
            int n = list.size();
            for (int j = 0; j < n; j++) {
                Entry e = list.get(j);
                if (e.mode != BindMode.HOLD) continue;
                runEntry(e, key, server);
            }
        }
    }

    private static void runEntry(Entry e, String key, MinecraftServer server) {
        MaredScriptContext ctx = e.ctx != null
            ? e.ctx
            : new MaredScriptContext(null, server, msg -> {});
        // F9: убран ctx.log(MaredLang.format("mared.log.bind.fire", key)) — слишком шумно
        MaredScriptRunner.start(new MaredScriptExecutor(ctx, e.body));
    }

    // ============================================================
    //  Блокировка
    // ============================================================

    private static void applyBlock(String keyStr) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyStr);
        if (pk != null) {
            MaredKeyBlocker.block(pk.keyCode, keyStr);
        }
    }

    private static void removeBlock(String keyStr) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyStr);
        if (pk != null) {
            MaredKeyBlocker.unblock(pk.keyCode);
        }
    }
}