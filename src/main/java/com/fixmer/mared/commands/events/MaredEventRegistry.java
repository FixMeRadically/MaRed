package com.fixmer.mared.commands.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fixmer.mared.Mared;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.engine.MaredScriptRunner;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class MaredEventRegistry {

    private MaredEventRegistry() {}

    // ============================================================
    //  Entry
    // ============================================================

    public static final class Entry {
        public final String type;
        public final List<MaredScriptCommand> body;
        public final MaredScriptContext ctx;
        public final boolean persistent;

        public Entry(String type, List<MaredScriptCommand> body,
                     MaredScriptContext ctx, boolean persistent) {
            this.type = type;
            this.body = body;
            this.ctx = ctx;
            this.persistent = persistent;
        }
    }

    private static final class EveryListener {
        final int period;
        final List<MaredScriptCommand> body;
        final MaredScriptContext ctx;
        final boolean persistent;
        EveryListener(int period, List<MaredScriptCommand> body,
                      MaredScriptContext ctx, boolean persistent) {
            this.period = period;
            this.body = body;
            this.ctx = ctx;
            this.persistent = persistent;
        }
    }

    private static final class AfterListener {
        final List<MaredScriptCommand> body;
        final MaredScriptContext ctx;
        int remaining;
        AfterListener(int delay, List<MaredScriptCommand> body, MaredScriptContext ctx) {
            this.remaining = delay;
            this.body = body;
            this.ctx = ctx;
        }
    }

    // ============================================================
    //  Storage
    // ============================================================

    private static final Map<String, CopyOnWriteArrayList<Entry>> REGISTRY = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<EveryListener> EVERY = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<AfterListener> AFTER = new CopyOnWriteArrayList<>();

    private static final Set<String> ACTIVE_TYPES = ConcurrentHashMap.newKeySet();
    private static final Set<String> PERSISTENT_TYPES = ConcurrentHashMap.newKeySet();

    private static final Set<String> FIRING = ConcurrentHashMap.newKeySet();
    private static volatile long suppressUntilMs = 0;
    private static volatile long serverTickCounter = 0;
    private static volatile int VERSION = 0;

    private static final ThreadLocal<Entry> CURRENT_ENTRY = new ThreadLocal<>();

    // ============================================================
    //  Version
    // ============================================================

    public static int getVersion() { return VERSION; }
    private static void bumpVersion() { VERSION++; }

    public static Entry currentEntry() { return CURRENT_ENTRY.get(); }
    public static void setCurrentEntry(Entry e) { CURRENT_ENTRY.set(e); }
    public static void clearCurrentEntry() { CURRENT_ENTRY.remove(); }

    // ============================================================
    //  Быстрые проверки
    // ============================================================

    public static boolean has(String type) { return ACTIVE_TYPES.contains(type); }
    public static boolean hasPersistent(String type) { return PERSISTENT_TYPES.contains(type); }

    public static int count(String type) {
        var list = REGISTRY.get(type);
        return list == null ? 0 : list.size();
    }

    public static int totalCount() {
        int n = 0;
        for (var list : REGISTRY.values()) n += list.size();
        n += EVERY.size();
        n += AFTER.size();
        return n;
    }

    public static List<String> types() { return new ArrayList<>(ACTIVE_TYPES); }

    // ============================================================
    //  Регистрация
    // ============================================================

    public static void register(String type, List<MaredScriptCommand> body,
                                MaredScriptContext ctx, boolean replace) {
        register(type, body, ctx, replace, false);
    }

    public static void register(String type, List<MaredScriptCommand> body,
                                MaredScriptContext ctx, boolean replace, boolean persistent) {
        var list = REGISTRY.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());

        if (replace) {
            if (persistent) {
                list.clear();
                PERSISTENT_TYPES.remove(type);
            } else {
                list.removeIf(e -> !e.persistent);
            }
        }

        list.add(new Entry(type, body, ctx, persistent));
        ACTIVE_TYPES.add(type);
        if (persistent) PERSISTENT_TYPES.add(type);
        bumpVersion();
    }

    public static void registerEvery(int period, List<MaredScriptCommand> body,
                                     MaredScriptContext ctx, boolean persistent) {
        EVERY.add(new EveryListener(period, body, ctx, persistent));
        bumpVersion();
    }

    public static void registerAfter(int delay, List<MaredScriptCommand> body,
                                     MaredScriptContext ctx) {
        AFTER.add(new AfterListener(delay, body, ctx));
        bumpVersion();
    }

    // ============================================================
    //  Удаление
    // ============================================================

    public static void clear(String type) {
        REGISTRY.remove(type);
        ACTIVE_TYPES.remove(type);
        PERSISTENT_TYPES.remove(type);
        bumpVersion();
    }

    public static void removeAll(String type) { clear(type); }

    public static void clearAll() {
        for (var e : REGISTRY.entrySet()) {
            var list = e.getValue();
            list.removeIf(x -> !x.persistent);
            if (list.isEmpty()) {
                REGISTRY.remove(e.getKey());
                ACTIVE_TYPES.remove(e.getKey());
            }
        }
        EVERY.removeIf(l -> !l.persistent);
        AFTER.clear();
        FIRING.clear();
        suppressUntilMs = 0;
        bumpVersion();
    }

    public static void clearAllPersistent() {
        REGISTRY.clear();
        ACTIVE_TYPES.clear();
        PERSISTENT_TYPES.clear();
        EVERY.clear();
        AFTER.clear();
        FIRING.clear();
        suppressUntilMs = 0;
        bumpVersion();
    }

    public static void removeAllEvents() {
        REGISTRY.clear();
        ACTIVE_TYPES.clear();
        PERSISTENT_TYPES.clear();
        EVERY.clear();
        AFTER.clear();
        FIRING.clear();
        bumpVersion();
    }

    public static void removeAllEvery() { EVERY.clear(); bumpVersion(); }
    public static void removeAllAfter() { AFTER.clear(); bumpVersion(); }

    public static boolean removeEntry(Entry entry) {
        if (entry == null) return false;
        var list = REGISTRY.get(entry.type);
        if (list == null) return false;

        boolean removed = list.remove(entry);
        if (removed) {
            if (list.isEmpty()) {
                REGISTRY.remove(entry.type);
                ACTIVE_TYPES.remove(entry.type);
                PERSISTENT_TYPES.remove(entry.type);
            } else if (entry.persistent) {
                boolean stillPersistent = false;
                for (var e : list) {
                    if (e.persistent) { stillPersistent = true; break; }
                }
                if (!stillPersistent) PERSISTENT_TYPES.remove(entry.type);
            }
            bumpVersion();
        }
        return removed;
    }

    // ============================================================
    //  Chat suppression
    // ============================================================

    public static void suppressChat(int ms) {
        suppressUntilMs = System.currentTimeMillis() + ms;
    }

    public static boolean isChatSuppressed() {
        return System.currentTimeMillis() < suppressUntilMs;
    }

    // ============================================================
    //  Tick
    // ============================================================

    public static void tickServer(MinecraftServer server) {
        serverTickCounter++;

        if (!EVERY.isEmpty()) {
            long tick = serverTickCounter;
            for (EveryListener l : EVERY) {
                if (tick % l.period != 0) continue;
                try {
                    MaredScriptContext ctx = forkCtx(l.ctx, server, null);
                    ctx.refreshPlayerData();
                    MaredScriptRunner.start(new MaredScriptExecutor(ctx, l.body));
                } catch (Throwable t) {
                    Mared.LOGGER.error("[Mared] every listener error", t);
                }
            }
        }

        if (!AFTER.isEmpty()) {
            List<AfterListener> toRemove = null;
            for (AfterListener l : AFTER) {
                l.remaining--;
                if (l.remaining <= 0) {
                    try {
                        MaredScriptContext ctx = forkCtx(l.ctx, server, null);
                        ctx.refreshPlayerData();
                        MaredScriptRunner.start(new MaredScriptExecutor(ctx, l.body));
                    } catch (Throwable t) {
                        Mared.LOGGER.error("[Mared] after listener error", t);
                    }
                    if (toRemove == null) toRemove = new ArrayList<>(4);
                    toRemove.add(l);
                }
            }
            if (toRemove != null) AFTER.removeAll(toRemove);
        }
    }

    public static long getServerTick() { return serverTickCounter; }

    // ============================================================
    //  Fire
    // ============================================================

    public static void fire(String type, MinecraftServer server, Map<String, Object> data) {
        var list = REGISTRY.get(type);
        if (list == null || list.isEmpty()) return;

        if (!FIRING.add(type)) return;
        try {
            ServerPlayer initiator = resolveInitiator(server, data);
            int n = list.size();
            for (int i = 0; i < n; i++) {
                fireEntry(list.get(i), server, data, initiator);
            }
        } finally {
            FIRING.remove(type);
        }
    }

    private static void fireEntry(Entry e, MinecraftServer server,
                                  Map<String, Object> data, ServerPlayer initiator) {
        MaredScriptContext ctx = forkCtx(e.ctx, server, initiator);
        ctx.refreshPlayerData();
        if (data != null && !data.isEmpty()) ctx.refreshEventData(data);

        CURRENT_ENTRY.set(e);
        try {
            MaredScriptRunner.start(new MaredScriptExecutor(ctx, e.body));
        } finally {
            CURRENT_ENTRY.remove();
        }
    }

    private static MaredScriptContext forkCtx(MaredScriptContext base,
                                              MinecraftServer server,
                                              ServerPlayer initiator) {
        if (base == null) {
            return new MaredScriptContext(initiator, server, msg -> {});
        }
        MaredScriptContext ctx = (initiator != null && initiator != base.getInitiator())
            ? base.forkWith(initiator)
            : base.fork();
        return ctx;
    }

    private static ServerPlayer resolveInitiator(MinecraftServer server, Map<String, Object> data) {
        if (server == null || data == null) return null;

        Object uuidObj = data.get("uuid");
        if (uuidObj instanceof String uuidStr) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                if (sp != null) return sp;
            } catch (IllegalArgumentException ignored) {}
        }

        Object nameObj = data.get("player");
        if (nameObj instanceof String name) {
            ServerPlayer sp = server.getPlayerList().getPlayerByName(name);
            if (sp != null) return sp;
        }
        return null;
    }
}