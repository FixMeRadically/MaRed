package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.Mared;

import net.minecraft.server.MinecraftServer;

public class MaredScriptRunner {

    /** BUG-14: максимальное количество активных executor'ов. */
    private static final int MAX_ACTIVE = 500;

    /** BUG-14: максимальное время жизни executor'а (30 сек). */
    private static final long MAX_LIFETIME_MS = 30_000;

    /** BUG-14: обёртка над executor'ом с временем создания. */
    private static final class Entry {
        final MaredScriptExecutor exec;
        final long createdAt;

        Entry(MaredScriptExecutor exec) {
            this.exec = exec;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private static final List<Entry> ACTIVE = new ArrayList<>();

    public static void start(MaredScriptExecutor executor) {
        synchronized (ACTIVE) {
            if (ACTIVE.size() >= MAX_ACTIVE) {
                Mared.LOGGER.warn("[Mared] ACTIVE full ({}), dropping new executor",
                    ACTIVE.size());
                return;
            }
            ACTIVE.add(new Entry(executor));
        }
    }

    public static void stopAll() {
        synchronized (ACTIVE) {
            ACTIVE.clear();
        }
    }

    public static void tick(MinecraftServer server) {
        MaredEventRegistry.tickServer(server);

        List<Entry> snapshot;
        synchronized (ACTIVE) {
            if (ACTIVE.isEmpty()) return;
            snapshot = new ArrayList<>(ACTIVE);
        }

        long now = System.currentTimeMillis();
        List<Entry> toRemove = null;

        int n = snapshot.size();
        for (int i = 0; i < n; i++) {
            Entry entry = snapshot.get(i);
            MaredScriptExecutor executor = entry.exec;

            // BUG-14: принудительный стоп по возрасту
            if (now - entry.createdAt > MAX_LIFETIME_MS) {
                executor.stopAll();
                Mared.LOGGER.warn("[Mared] Force-stopped executor (age > {}ms)",
                    MAX_LIFETIME_MS);
                if (toRemove == null) toRemove = new ArrayList<>(4);
                toRemove.add(entry);
                continue;
            }

            try {
                executor.tick();
            } catch (Exception e) {
                executor.getContext().log("[error] " + e.getMessage());
                if (toRemove == null) toRemove = new ArrayList<>(4);
                toRemove.add(entry);
                continue;
            }
            if (executor.isFinished()) {
                if (toRemove == null) toRemove = new ArrayList<>(4);
                toRemove.add(entry);
            }
        }

        if (toRemove != null) {
            synchronized (ACTIVE) {
                ACTIVE.removeAll(toRemove);
            }
        }
    }

    public static int getActiveCount() {
        synchronized (ACTIVE) {
            return ACTIVE.size();
        }
    }
}