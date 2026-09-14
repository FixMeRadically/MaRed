package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fixmer.mared.Mared;
import com.fixmer.mared.script.commands.MaredScriptCommand;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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

    private static final Map<String, List<Entry>> REGISTRY = new LinkedHashMap<>();

    private static final Set<String> FIRING = ConcurrentHashMap.newKeySet();

    private static volatile long suppressUntilMs = 0;

    // ============================================================
    //  Регистрация
    // ============================================================

    public static void register(String type, List<MaredScriptCommand> body,
                                MaredScriptContext ctx, boolean replace) {
        register(type, body, ctx, replace, false);
    }

    public static void register(String type, List<MaredScriptCommand> body,
                                MaredScriptContext ctx, boolean replace, boolean persistent) {
        Mared.LOGGER.info("[Mared] register: type={}, bodySize={}, replace={}, persistent={}",
            type, body != null ? body.size() : 0, replace, persistent);

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

    public static void clear(String type) {
        REGISTRY.remove(type);
    }

    /** Очищает только не-persistent Entry. */
    public static void clearAll() {
        int before = totalCount();
        for (List<Entry> list : REGISTRY.values()) {
            list.removeIf(e -> !e.persistent);
        }
        REGISTRY.entrySet().removeIf(e -> e.getValue().isEmpty());
        FIRING.clear();
        suppressUntilMs = 0;
        Mared.LOGGER.info("[Mared] clearAll: {} -> {} (kept persistent)", before, totalCount());
    }

    /** Очищает всё, включая persistent. */
    public static void clearAllPersistent() {
        int before = totalCount();
        REGISTRY.clear();
        FIRING.clear();
        suppressUntilMs = 0;
        Mared.LOGGER.info("[Mared] clearAllPersistent: {} -> 0", before);
    }

    public static boolean has(String type) {
        List<Entry> list = REGISTRY.get(type);
        return list != null && !list.isEmpty();
    }

    public static int count(String type) {
        List<Entry> list = REGISTRY.get(type);
        return list != null ? list.size() : 0;
    }

    public static int totalCount() {
        int n = 0;
        for (List<Entry> list : REGISTRY.values()) n += list.size();
        return n;
    }

    public static List<String> types() {
        return new ArrayList<>(REGISTRY.keySet());
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
    //  Fire
    // ============================================================

    public static void fire(String type, MinecraftServer server, Map<String, Object> data) {
        if (!FIRING.add(type)) {
            Mared.LOGGER.info("[Mared] fire: type={} — SKIPPED (already firing)", type);
            return;
        }

        try {
            List<Entry> list = REGISTRY.get(type);
            if (list == null || list.isEmpty()) {
                Mared.LOGGER.info("[Mared] fire: type={} — no handlers", type);
                return;
            }

            Mared.LOGGER.info("[Mared] fire: type={}, count={}", type, list.size());

            // Найти актуального игрока-инициатора из данных события
            ServerPlayer initiator = resolveInitiator(server, data);

            for (Entry e : list) {
                MaredScriptContext ctx = e.ctx;
                if (ctx == null) {
                    ctx = new MaredScriptContext(initiator, server, msg -> {});
                } else if (initiator != null) {
                    // persistent-контекст создан с initiator=null,
                    // обновляем его актуальным игроком
                    ctx.setInitiator(initiator);
                }

                ctx.refreshPlayerData();

                if (data != null) {
                    ctx.refreshEventData(data);
                }

                MaredScriptRunner.start(new MaredScriptExecutor(ctx, e.body));
            }
        } finally {
            FIRING.remove(type);
        }
    }

    /**
     * Достаёт ServerPlayer из данных события.
     * Сначала пробует по uuid (самый надёжный), потом по имени.
     */
    private static ServerPlayer resolveInitiator(MinecraftServer server, Map<String, Object> data) {
        if (server == null || data == null) return null;

        // 1. По UUID
        Object uuidObj = data.get("uuid");
        if (uuidObj instanceof String uuidStr) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                if (sp != null) return sp;
            } catch (IllegalArgumentException ignored) {}
        }

        // 2. По имени
        Object nameObj = data.get("player");
        if (nameObj instanceof String name) {
            ServerPlayer sp = server.getPlayerList().getPlayerByName(name);
            if (sp != null) return sp;
        }
        if (nameObj instanceof String selfName) {
            ServerPlayer sp = server.getPlayerList().getPlayerByName(selfName);
            if (sp != null) return sp;
        }

        return null;
    }
}