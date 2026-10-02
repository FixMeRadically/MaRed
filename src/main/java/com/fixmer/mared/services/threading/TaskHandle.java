package com.fixmer.mared.services.threading;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Хэндл асинхронной задачи.
 *
 * 0.3.0: позволяет отменить задачу и проверить её состояние.
 * Реальная отмена зависит от того, поддерживает ли задача прерывание —
 * Future.cancel(true) посылает interrupt, но задача может его игнорировать.
 */
public final class TaskHandle {

    private final CompletableFuture<?> future;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    TaskHandle(CompletableFuture<?> future) {
        this.future = future;
    }

    /** Попробовать отменить. @return true, если отмена принята. */
    public boolean cancel() {
        if (cancelled.compareAndSet(false, true)) {
            return future.cancel(true);
        }
        return false;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isDone() {
        return future.isDone();
    }

    public CompletableFuture<?> future() {
        return future;
    }
}