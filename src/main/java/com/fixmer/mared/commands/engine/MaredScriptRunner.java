package com.fixmer.mared.commands.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import com.fixmer.mared.Mared;
import com.fixmer.mared.commands.events.MaredEventRegistry;
import net.minecraft.server.MinecraftServer;

/** Immutable active snapshots; mutations never invoke executors while holding WRITE_LOCK. */
public final class MaredScriptRunner {
    private MaredScriptRunner() {}
    private static final int MAX_ACTIVE = 100;
    private static final long TICK_BUDGET_NANOS = 4_000_000;
    private static final Object WRITE_LOCK = new Object();
    private static volatile List<MaredScriptExecutor> active = List.of();
    private static final Map<MinecraftServer, Integer> cursors = new WeakHashMap<>();

    public static void start(MaredScriptExecutor executor) {
        if (executor == null || executor.isFinished()) return;
        var owner = executor.getContext().executionScope();
        if (owner != null && owner.cancelled()) { executor.stopAll(); return; }
        boolean rejected = false;
        synchronized (WRITE_LOCK) {
            for (var existing : active) if (existing == executor) return;
            if (active.size() >= MAX_ACTIVE) rejected = true;
            else {
                var next = new ArrayList<>(active);
                next.add(executor);
                active = List.copyOf(next);
            }
        }
        if (rejected) {
            executor.fail(new IllegalStateException("Active-script limit exceeded: " + MAX_ACTIVE));
            Mared.LOGGER.warn("[Mared] ACTIVE full ({}), executor rejected", MAX_ACTIVE);
        }
    }

    public static void stopAll() {
        List<MaredScriptExecutor> stopped;
        synchronized (WRITE_LOCK) {
            stopped = active;
            active = List.of();
            cursors.clear();
        }
        for (var executor : stopped) executor.stopAll();
    }

    /** Must run on the matching owner thread. Used after MP logout when ordinary ticks stop. */
    public static void finishOwnedCancellation(com.fixmer.genesis.technology.runtime.ExecutionScope owner, MinecraftServer server) {
        if (owner == null) return;
        for (var executor : active) if (executor.getContext().executionScope() == owner
                && executor.getContext().getServer() == server && owner.cancelled()) executor.tick();
        synchronized (WRITE_LOCK) {
            active = active.stream().filter(executor -> !executor.isFinished()).toList();
        }
    }

    public static int getActiveCount() { return active.size(); }

    public static void tick(MinecraftServer server) {
        MaredEventRegistry.tickServer(server);
        List<MaredScriptExecutor> snapshot = active;
        if (snapshot.isEmpty()) return;
        int cursor;
        synchronized (WRITE_LOCK) { cursor = cursors.getOrDefault(server, 0); }
        int n = snapshot.size();
        int nextCursor = Math.floorMod(cursor, n);
        long started = System.nanoTime();
        int dispatched = 0;
        for (int offset = 0; offset < n; offset++) {
            int index = (Math.floorMod(cursor, n) + offset) % n;
            var executor = snapshot.get(index);
            nextCursor = (index + 1) % n;
            if (executor.getContext().getServer() != server || executor.isFinished()) continue;
            if (dispatched > 0 && System.nanoTime() - started >= TICK_BUDGET_NANOS) {
                nextCursor = index;
                break;
            }
            try { executor.tick(); }
            catch (Throwable error) {
                executor.fail(error);
                Mared.LOGGER.error("[Mared] Executor tick failed", error);
            }
            dispatched++;
        }
        synchronized (WRITE_LOCK) {
            cursors.put(server, nextCursor);
            // Filter the CURRENT snapshot, preserving new starts and never resurrecting a stop.
            var next = new ArrayList<MaredScriptExecutor>(active.size());
            for (var executor : active) if (!executor.isFinished()) next.add(executor);
            if (next.size() != active.size()) active = List.copyOf(next);
        }
    }
}
