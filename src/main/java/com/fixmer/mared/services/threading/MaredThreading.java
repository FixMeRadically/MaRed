package com.fixmer.mared.services.threading;

import com.fixmer.mared.Mared;

/**
 * Глобальная точка доступа к threading-сервисам MaRed.
 *
 * 0.3.0:
 *   - cores = max(1, availableProcessors - 2) — оставляем 2 ядра
 *     на main thread Minecraft + GC/render.
 *   - shutdown() вызывается через MaredClientSetup на ClientStoppingEvent.
 */
public final class MaredThreading {

    private MaredThreading() {}

    private static volatile MainThreadDispatcher mainThread;
    private static volatile TaskScheduler scheduler;
    private static volatile boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;

        mainThread = new ClientMainThreadDispatcher();

        int cores = Runtime.getRuntime().availableProcessors();
        int workers = Math.max(1, cores - 2);
        scheduler = new AsyncTaskScheduler(workers, mainThread);

        initialized = true;
        Mared.LOGGER.info("[threading] initialized: cores={} workers={}", cores, workers);
    }

    public static MainThreadDispatcher main() {
        if (mainThread == null) init();
        return mainThread;
    }

    public static TaskScheduler scheduler() {
        if (scheduler == null) init();
        return scheduler;
    }

    public static synchronized void shutdown() {
        if (!initialized) return;
        if (scheduler != null) {
            scheduler.shutdown();
        }
        scheduler = null;
        mainThread = null;
        initialized = false;
        Mared.LOGGER.info("[threading] shutdown");
    }

    public static boolean isInitialized() {
        return initialized;
    }
}