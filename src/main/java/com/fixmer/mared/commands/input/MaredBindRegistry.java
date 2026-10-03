package com.fixmer.mared.commands.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.engine.MaredScriptRunner;

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

    private static final Map<String, List<Entry>> BINDINGS = new LinkedHashMap<>(16);
    private static final Map<Integer, List<String>> BY_KEYCODE = new HashMap<>(32);
    private static boolean indexDirty = true;

    private static final List<String> ACTIVE_HOLDS = new ArrayList<>(4);

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
        return list != null ? list : Collections.emptyList();
    }

    private static void markDirty() { indexDirty = true; }

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

    /**
     * 0.3.2 (audit #100): перенести все бинды с oldKey на newKey.
     *
     * Возвращает false, если:
     *   - oldKey отсутствует,
     *   - newKey уже занят,
     *   - oldKey == newKey,
     *   - oldKey или newKey null.
     *
     * Переносит blockVanilla: снимает блокировку с oldKey, ставит
     * на newKey, если хотя бы один Entry имел blockVanilla.
     */
    public static boolean rename(String oldKey, String newKey) {
        if (oldKey == null || newKey == null) return false;
        if (oldKey.equals(newKey)) return false;
        if (!BINDINGS.containsKey(oldKey)) return false;
        if (BINDINGS.containsKey(newKey)) return false;

        List<Entry> oldList = BINDINGS.remove(oldKey);
        if (oldList == null || oldList.isEmpty()) return false;

        List<Entry> newList = new ArrayList<>(oldList.size());
        boolean hadBlock = false;
        for (Entry e : oldList) {
            if (e.blockVanilla) hadBlock = true;
            newList.add(new Entry(newKey, e.body, e.ctx, e.blockVanilla, e.mode));
        }
        BINDINGS.put(newKey, newList);
        markDirty();

        // Снять блокировку со старого keyCode.
        MaredKeyNames.ParsedKey oldPk = MaredKeyNames.parseAny(oldKey);
        if (oldPk != null) {
            MaredKeyBlocker.unblock(oldPk.keyCode);
        }

        // Поставить блокировку на новый keyCode, если был хоть один
        // blocking entry.
        if (hadBlock) {
            MaredKeyNames.ParsedKey newPk = MaredKeyNames.parseAny(newKey);
            if (newPk != null) {
                MaredKeyBlocker.block(newPk.keyCode, newKey);
            }
        }

        stopHold(oldKey);
        return true;
    }

    public static void unblock(String key) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(key);
        if (pk != null) MaredKeyBlocker.unblock(pk.keyCode);

        List<Entry> list = BINDINGS.get(key);
        if (list == null) return;
        list.removeIf(e -> e.blockVanilla);
        if (list.isEmpty()) BINDINGS.remove(key);
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

    // ============================================================
    //  Чтение
    // ============================================================

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
            if (e.mode == BindMode.PRESS) runEntry(e, server);
        }
    }

    public static void fireRelease(String key, MinecraftServer server) {
        List<Entry> list = BINDINGS.get(key);
        if (list == null || list.isEmpty()) return;
        int n = list.size();
        for (int i = 0; i < n; i++) {
            Entry e = list.get(i);
            if (e.mode == BindMode.RELEASE) runEntry(e, server);
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
            runEntry(e, server);
        }
        if (hasHold && !ACTIVE_HOLDS.contains(key)) ACTIVE_HOLDS.add(key);
    }

    public static void stopHold(String key) { ACTIVE_HOLDS.remove(key); }

    public static void tickHolds(MinecraftServer server) {
        if (ACTIVE_HOLDS.isEmpty()) return;

        String[] snapshot = ACTIVE_HOLDS.toArray(new String[0]);
        for (String key : snapshot) {
            List<Entry> list = BINDINGS.get(key);
            if (list == null) { ACTIVE_HOLDS.remove(key); continue; }
            int n = list.size();
            for (int i = 0; i < n; i++) {
                Entry e = list.get(i);
                if (e.mode != BindMode.HOLD) continue;
                runEntry(e, server);
            }
        }
    }

    private static void runEntry(Entry e, MinecraftServer server) {
        MaredScriptContext base = e.ctx != null
            ? e.ctx
            : new MaredScriptContext(null, server, msg -> {});
        MaredScriptContext ctx = base.fork();
        MaredScriptRunner.start(new MaredScriptExecutor(ctx, e.body));
    }

    // ============================================================
    //  Блокировка
    // ============================================================

    private static void applyBlock(String keyStr) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyStr);
        if (pk != null) MaredKeyBlocker.block(pk.keyCode, keyStr);
    }

    private static void removeBlock(String keyStr) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyStr);
        if (pk != null) MaredKeyBlocker.unblock(pk.keyCode);
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
}