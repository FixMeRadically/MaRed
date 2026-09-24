package com.fixmer.mared;

import java.util.concurrent.atomic.AtomicLong;
import com.fixmer.mared.commands.hooks.server.MaredServerEvents;

/**
 * Глобальный счётчик tick'ов с начала сессии (сервера).
 * Обновляется в MaredServerEvents.onServerTick.
 * Сбрасывается при onServerStopped.
 */
public final class MaredTicks {

    private MaredTicks() {}

    private static final AtomicLong TICK = new AtomicLong(0);

    public static long get() {
        return TICK.get();
    }

    public static void increment() {
        TICK.incrementAndGet();
    }

    public static void reset() {
        TICK.set(0);
    }
}