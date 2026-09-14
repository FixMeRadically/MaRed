package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.Mared;
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

    /** Клавиши, для которых сейчас активен HOLD-биндинг. */
    private static final List<String> ACTIVE_HOLDS = new ArrayList<>();

    public static void replace(String key, List<MaredScriptCommand> body, MaredScriptContext ctx,
                               boolean blockVanilla, BindMode mode) {
        Mared.LOGGER.info("[Mared] MaredBindRegistry.replace: key={}, block={}, mode={}", key, blockVanilla, mode);
        removeBlock(key);
        List<Entry> list = new ArrayList<>();
        list.add(new Entry(key, body, ctx, blockVanilla, mode));
        BINDINGS.put(key, list);
        if (blockVanilla) applyBlock(key);
    }

    public static void add(String key, List<MaredScriptCommand> body, MaredScriptContext ctx,
                           boolean blockVanilla, BindMode mode) {
        Mared.LOGGER.info("[Mared] MaredBindRegistry.add: key={}, block={}, mode={}", key, blockVanilla, mode);
        BINDINGS.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new Entry(key, body, ctx, blockVanilla, mode));
        if (blockVanilla) applyBlock(key);
    }

    public static void clear(String key) {
        BINDINGS.remove(key);
        removeBlock(key);
        stopHold(key);
    }

    /** Снять блокировку с клавиши, оставив не-blocking бинды. */
    public static void unblock(String key) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(key);
        if (pk != null) MaredKeyBlocker.unblock(pk.keyCode);

        List<Entry> list = BINDINGS.get(key);
        if (list == null) return;
        list.removeIf(e -> e.blockVanilla);
        if (list.isEmpty()) {
            BINDINGS.remove(key);
        }
    }

    /** Включить блокировку клавиши без создания бинда. */
    public static void block(String key) {
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(key);
        if (pk == null) return;
        MaredKeyBlocker.block(pk.keyCode, key);
    }

    public static void clearAll() {
        BINDINGS.clear();
        ACTIVE_HOLDS.clear();
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
    }

    public static List<String> keys() { return new ArrayList<>(BINDINGS.keySet()); }
    public static List<Entry> entries(String key) { return BINDINGS.getOrDefault(key, List.of()); }
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

    // ============================================================
    //  Fire
    // ============================================================

    /** Fire всех PRESS-биндов для клавиши (обычный режим). */
    public static void fire(String key, MinecraftServer server) {
        List<Entry> list = BINDINGS.get(key);
        if (list == null || list.isEmpty()) return;
        for (Entry e : list) {
            if (e.mode != BindMode.PRESS) continue;
            runEntry(e, key, server);
        }
    }

    /** Fire всех RELEASE-биндов для клавиши. */
    public static void fireRelease(String key, MinecraftServer server) {
        List<Entry> list = BINDINGS.get(key);
        if (list == null || list.isEmpty()) return;
        for (Entry e : list) {
            if (e.mode != BindMode.RELEASE) continue;
            runEntry(e, key, server);
        }
    }

    /** Начать активный HOLD для клавиши (запускает первую итерацию). */
    public static void startHold(String key, MinecraftServer server) {
        List<Entry> list = BINDINGS.get(key);
        if (list == null || list.isEmpty()) return;
        boolean hasHold = false;
        for (Entry e : list) {
            if (e.mode != BindMode.HOLD) continue;
            hasHold = true;
            runEntry(e, key, server);
        }
        if (hasHold && !ACTIVE_HOLDS.contains(key)) {
            ACTIVE_HOLDS.add(key);
        }
    }

    /** Остановить активный HOLD. */
    public static void stopHold(String key) {
        ACTIVE_HOLDS.remove(key);
    }

    /** Вызывается каждый client-tick. */
    public static void tickHolds(MinecraftServer server) {
        if (ACTIVE_HOLDS.isEmpty()) return;
        for (String key : new ArrayList<>(ACTIVE_HOLDS)) {
            List<Entry> list = BINDINGS.get(key);
            if (list == null) { ACTIVE_HOLDS.remove(key); continue; }
            for (Entry e : list) {
                if (e.mode != BindMode.HOLD) continue;
                runEntry(e, key, server);
            }
        }
    }

    private static void runEntry(Entry e, String key, MinecraftServer server) {
        MaredScriptContext ctx = e.ctx != null
            ? e.ctx
            : new MaredScriptContext(null, server, msg -> {});
        ctx.log(MaredLang.format("mared.log.bind.fire", key));
        MaredScriptRunner.start(new MaredScriptExecutor(ctx, e.body));
    }

    // ---- Блокировка ----

    private static void applyBlock(String keyStr) {
        Mared.LOGGER.info("[Mared] applyBlock: {}", keyStr);
        MaredKeyNames.ParsedKey pk = MaredKeyNames.parseAny(keyStr);
        Mared.LOGGER.info("[Mared] applyBlock: parsed keyCode={}",
            pk != null ? pk.keyCode : "null");
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