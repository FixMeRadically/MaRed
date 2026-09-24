package com.fixmer.mared.script;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.Mared;

import net.minecraft.server.MinecraftServer;

public class MaredScriptRunner {

    private static final int MAX_ACTIVE = 100;
    private static final long MAX_LIFETIME_MS = 10_000;
    private static final int MAX_ADD_PER_TICK = 50;

    private static final class Entry {
        final MaredScriptExecutor exec;
        final long createdAt;

        Entry(MaredScriptExecutor exec) {
            this.exec = exec;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private static final List<Entry> ACTIVE = new ArrayList<>();

    private static int addedThisTick = 0;

    public static void start(MaredScriptExecutor executor) {
        synchronized (ACTIVE) {
            if (ACTIVE.size() >= MAX_ACTIVE) {
                Mared.LOGGER.warn("[Mared] ACTIVE full ({}), dropping new executor",
                    ACTIVE.size());
                return;
            }
            if (addedThisTick >= MAX_ADD_PER_TICK) {
                Mared.LOGGER.warn("[Mared] MAX_ADD_PER_TICK reached ({}), dropping executor",
                    addedThisTick);
                return;
            }
            ACTIVE.add(new Entry(executor));
            addedThisTick++;
        }
    }

    public static void stopAll() {
        synchronized (ACTIVE) {
            ACTIVE.clear();
            addedThisTick = 0;
        }
    }

    public static void tick(MinecraftServer server) {
        MaredEventRegistry.tickServer(server);

        // FIX: сбрасываем счётчик ВСЕГДА
        synchronized (ACTIVE) {
            addedThisTick = 0;
            if (ACTIVE.isEmpty()) return;
        }

        List<Entry> snapshot;
        synchronized (ACTIVE) {
            snapshot = new ArrayList<>(ACTIVE);
        }

        long now = System.currentTimeMillis();
        List<Entry> toRemove = null;

        int n = snapshot.size();
        for (int i = 0; i < n; i++) {
            Entry entry = snapshot.get(i);
            MaredScriptExecutor executor = entry.exec;

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
            } catch (Throwable t) {
                Mared.LOGGER.error("[Mared] Executor tick error", t);
                executor.getContext().log("[error] " + t.getMessage());
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