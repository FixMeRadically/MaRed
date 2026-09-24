package com.fixmer.mared.commands.events;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fixmer.mared.Mared;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.engine.MaredScriptRunner;

public final class MaredEventRegistry {

    private MaredEventRegistry() {}

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

    private static final Map<String, List<Entry>> REGISTRY = new LinkedHashMap<>();
    private static final List<EveryListener> EVERY_LISTENERS = new ArrayList<>();
    private static final List<AfterListener> AFTER_LISTENERS = new ArrayList<>();

    private static final Set<String> FIRING = ConcurrentHashMap.newKeySet();
    private static volatile long suppressUntilMs = 0;

    private static long serverTickCounter = 0;

    private static volatile int VERSION = 0;

    public static int getVersion() { return VERSION; }
    private static void bumpVersion() { VERSION++; }

    private static final ThreadLocal<Entry> CURRENT_ENTRY = new ThreadLocal<>();

    public static Entry currentEntry() { return CURRENT_ENTRY.get(); }
    public static void setCurrentEntry(Entry e) { CURRENT_ENTRY.set(e); }
    public static void clearCurrentEntry() { CURRENT_ENTRY.remove(); }

    // ============================================================
    //  Регистрация
    // ============================================================

    public static void register(String type, List<MaredScriptCommand> body,
                                MaredScriptContext ctx, boolean replace) {
        register(type, body, ctx, replace, false);
    }

    public static void register(String type, List<MaredScriptCommand> body,
                                MaredScriptContext ctx, boolean replace, boolean persistent) {
        synchronized (REGISTRY) {
            if (replace) {
                List<Entry> list = REGISTRY.get(type);
                if (list != null) {
                    if (persistent) {
                        list.clear();
                    } else {
                        list.removeIf(e -> !e.persistent);
                    }
                    if (list.isEmpty()) REGISTRY.remove(type);
                }
            }

            REGISTRY.computeIfAbsent(type, k -> new ArrayList<>())
                    .add(new Entry(type, body, ctx, persistent));
        }
        bumpVersion();
    }

    public static void registerEvery(int period, List<MaredScriptCommand> body,
                                     MaredScriptContext ctx, boolean persistent) {
        synchronized (EVERY_LISTENERS) {
            EVERY_LISTENERS.add(new EveryListener(period, body, ctx, persistent));
        }
        bumpVersion();
    }

    public static void registerAfter(int delay, List<MaredScriptCommand> body,
                                     MaredScriptContext ctx) {
        synchronized (AFTER_LISTENERS) {
            AFTER_LISTENERS.add(new AfterListener(delay, body, ctx));
        }
        bumpVersion();
    }

    public static void clear(String type) {
        synchronized (REGISTRY) {
            REGISTRY.remove(type);
        }
        bumpVersion();
    }

    public static void clearAll() {
        synchronized (REGISTRY) {
            for (List<Entry> list : REGISTRY.values()) {
                list.removeIf(e -> !e.persistent);
            }
            REGISTRY.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
        synchronized (EVERY_LISTENERS) {
            EVERY_LISTENERS.removeIf(l -> !l.persistent);
        }
        synchronized (AFTER_LISTENERS) {
            AFTER_LISTENERS.clear();
        }
        FIRING.clear();
        suppressUntilMs = 0;
        bumpVersion();
    }

    public static void clearAllPersistent() {
        synchronized (REGISTRY) { REGISTRY.clear(); }
        synchronized (EVERY_LISTENERS) { EVERY_LISTENERS.clear(); }
        synchronized (AFTER_LISTENERS) { AFTER_LISTENERS.clear(); }
        FIRING.clear();
        suppressUntilMs = 0;
        bumpVersion();
    }

    // ============================================================
    //  Удаление
    // ============================================================

    public static void removeAll(String type) {
        synchronized (REGISTRY) {
            REGISTRY.remove(type);
        }
        bumpVersion();
    }

    public static void removeAllEvents() {
        synchronized (REGISTRY) { REGISTRY.clear(); }
        synchronized (EVERY_LISTENERS) { EVERY_LISTENERS.clear(); }
        synchronized (AFTER_LISTENERS) { AFTER_LISTENERS.clear(); }
        FIRING.clear();
        bumpVersion();
    }

    public static void removeAllEvery() {
        synchronized (EVERY_LISTENERS) { EVERY_LISTENERS.clear(); }
        bumpVersion();
    }

    public static void removeAllAfter() {
        synchronized (AFTER_LISTENERS) { AFTER_LISTENERS.clear(); }
        bumpVersion();
    }

    public static boolean removeEntry(Entry entry) {
        if (entry == null) return false;
        boolean removed = false;
        synchronized (REGISTRY) {
            List<Entry> list = REGISTRY.get(entry.type);
            if (list != null) {
                removed = list.remove(entry);
                if (list.isEmpty()) REGISTRY.remove(entry.type);
            }
        }
        if (removed) bumpVersion();
        return removed;
    }

    public static boolean has(String type) {
        synchronized (REGISTRY) {
            List<Entry> list = REGISTRY.get(type);
            return list != null && !list.isEmpty();
        }
    }

    public static int count(String type) {
        synchronized (REGISTRY) {
            List<Entry> list = REGISTRY.get(type);
            return list != null ? list.size() : 0;
        }
    }

    public static int totalCount() {
        synchronized (REGISTRY) {
            int n = 0;
            for (List<Entry> list : REGISTRY.values()) n += list.size();
            n += EVERY_LISTENERS.size();
            n += AFTER_LISTENERS.size();
            return n;
        }
    }

    public static List<String> types() {
        synchronized (REGISTRY) {
            return new ArrayList<>(REGISTRY.keySet());
        }
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

        List<EveryListener> everySnapshot;
        synchronized (EVERY_LISTENERS) {
            if (EVERY_LISTENERS.isEmpty()) return;
            everySnapshot = new ArrayList<>(EVERY_LISTENERS);
        }
        for (EveryListener l : everySnapshot) {
            if (serverTickCounter % l.period != 0) continue;
            try {
                if (l.ctx != null) l.ctx.refreshPlayerData();
                MaredScriptRunner.start(new MaredScriptExecutor(l.ctx, l.body));
            } catch (Throwable t) {
                Mared.LOGGER.error("[Mared] every listener error", t);
            }
        }

        List<AfterListener> afterSnapshot;
        synchronized (AFTER_LISTENERS) {
            if (AFTER_LISTENERS.isEmpty()) return;
            afterSnapshot = new ArrayList<>(AFTER_LISTENERS);
        }
        List<AfterListener> toRemove = new ArrayList<>();
        for (AfterListener l : afterSnapshot) {
            l.remaining--;
            if (l.remaining <= 0) {
                try {
                    if (l.ctx != null) l.ctx.refreshPlayerData();
                    MaredScriptRunner.start(new MaredScriptExecutor(l.ctx, l.body));
                } catch (Throwable t) {
                    Mared.LOGGER.error("[Mared] after listener error", t);
                }
                toRemove.add(l);
            }
        }
        if (!toRemove.isEmpty()) {
            synchronized (AFTER_LISTENERS) {
                AFTER_LISTENERS.removeAll(toRemove);
            }
        }
    }

    public static long getServerTick() { return serverTickCounter; }

    // ============================================================
    //  Fire
    // ============================================================

    public static void fire(String type, MinecraftServer server, Map<String, Object> data) {
        if (!FIRING.add(type)) return;

        try {
            List<Entry> list;
            synchronized (REGISTRY) {
                List<Entry> registryList = REGISTRY.get(type);
                if (registryList == null || registryList.isEmpty()) return;
                list = new ArrayList<>(registryList);
            }

            ServerPlayer initiator = resolveInitiator(server, data);

            for (Entry e : list) {
                MaredScriptContext ctx = e.ctx;
                if (ctx == null) {
                    ctx = new MaredScriptContext(initiator, server, msg -> {});
                } else if (initiator != null) {
                    ctx.setInitiator(initiator);
                }

                ctx.refreshPlayerData();

                if (data != null) {
                    ctx.refreshEventData(data);
                }

                CURRENT_ENTRY.set(e);
                try {
                    MaredScriptRunner.start(new MaredScriptExecutor(ctx, e.body));
                } finally {
                    CURRENT_ENTRY.remove();
                }
            }
        } finally {
            FIRING.remove(type);
        }
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