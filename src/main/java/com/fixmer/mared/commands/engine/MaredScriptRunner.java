package com.fixmer.mared.commands.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fixmer.mared.Mared;
import com.fixmer.mared.commands.events.MaredEventRegistry;

import net.minecraft.server.MinecraftServer;

/**
 * Глобальный менеджер активных executor'ов.
 *
 * Работает без блокировок на горячем пути: ACTIVE — это immutable-снимок,
 * который атомарно подменяется при мутациях. Tick читает один снимок,
 * собирает «живые» executor'ы, затем одним CAS'ом заменяет список.
 */
public final class MaredScriptRunner {

    private MaredScriptRunner() {}

    private static final int MAX_ACTIVE = 100;
    private static final long MAX_LIFETIME_MS = 10_000;

    private static final class Entry {
        final MaredScriptExecutor exec;
        final long createdAt;
        Entry(MaredScriptExecutor exec) {
            this.exec = exec;
            this.createdAt = System.currentTimeMillis();
        }
    }

    /** Immutable-снимок. Заменяется целиком. */
    private static volatile List<Entry> active = Collections.emptyList();
    private static final Object WRITE_LOCK = new Object();

    /** Счётчик добавлений в текущем tick'е. */
    private static final AtomicInteger addedThisTick = new AtomicInteger(0);

    // ============================================================
    //  Public
    // ============================================================

    public static void start(MaredScriptExecutor executor) {
        if (executor == null) return;

        synchronized (WRITE_LOCK) {
            List<Entry> cur = active;
            if (cur.size() >= MAX_ACTIVE) {
                Mared.LOGGER.warn("[Mared] ACTIVE full ({}), dropping executor", cur.size());
                return;
            }
            List<Entry> next = new ArrayList<>(cur.size() + 1);
            next.addAll(cur);
            next.add(new Entry(executor));
            active = next;
            addedThisTick.incrementAndGet();
        }
    }

    public static void stopAll() {
        synchronized (WRITE_LOCK) {
            for (Entry e : active) e.exec.stopAll();
            active = Collections.emptyList();
            addedThisTick.set(0);
        }
    }

    public static int getActiveCount() { return active.size(); }

    // ============================================================
    //  Tick
    // ============================================================

    public static void tick(MinecraftServer server) {
        MaredEventRegistry.tickServer(server);

        List<Entry> snapshot = active;
        if (snapshot.isEmpty()) {
            addedThisTick.set(0);
            return;
        }

        long now = System.currentTimeMillis();
        List<Entry> survivors = null;

        int n = snapshot.size();
        for (int i = 0; i < n; i++) {
            Entry entry = snapshot.get(i);
            MaredScriptExecutor exec = entry.exec;

            if (now - entry.createdAt > MAX_LIFETIME_MS) {
                exec.stopAll();
                Mared.LOGGER.warn("[Mared] Force-stopped executor (age > {}ms)", MAX_LIFETIME_MS);
                if (survivors == null) survivors = new ArrayList<>(n - 1);
                continue;
            }

            try {
                exec.tick();
            } catch (Throwable t) {
                Mared.LOGGER.error("[Mared] Executor tick error", t);
                exec.getContext().log("[error] " + t.getMessage());
                if (survivors == null) survivors = new ArrayList<>(n - 1);
                continue;
            }

            if (exec.isFinished()) {
                if (survivors == null) survivors = new ArrayList<>(n - 1);
                continue;
            }

            if (survivors != null) survivors.add(entry);
        }

        // Если никто не умер — оставляем тот же снимок (не аллоцируем).
        if (survivors != null) {
            synchronized (WRITE_LOCK) {
                // На случай, если за время tick'а кто-то добавился —
                // добавляем новые в конец.
                List<Entry> cur = active;
                if (cur.size() != snapshot.size()) {
                    List<Entry> merged = new ArrayList<>(survivors.size() + 4);
                    merged.addAll(survivors);
                    int curN = cur.size();
                    for (int i = 0; i < curN; i++) {
                        Entry e = cur.get(i);
                        if (!containsByIdentity(snapshot, e)) merged.add(e);
                    }
                    active = merged;
                } else {
                    active = survivors;
                }
            }
        }

        addedThisTick.set(0);
    }

    private static boolean containsByIdentity(List<Entry> list, Entry e) {
        int n = list.size();
        for (int i = 0; i < n; i++) {
            if (list.get(i) == e) return true;
        }
        return false;
    }
}