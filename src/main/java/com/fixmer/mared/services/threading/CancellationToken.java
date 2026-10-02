package com.fixmer.mared.services.threading;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Токен отмены задачи.
 *
 * 0.3.0: интерфейс для кооперативной отмены long-running задач.
 * TaskHandle.cancel() посылает interrupt, но задача может его
 * проигнорировать (например, блокирующий Files.write). Токен
 * позволяет задаче самой проверять "не отменён ли я" в безопасных
 * точках.
 *
 * Использование:
 *   CancellationToken token = new CancellationToken();
 *   // в задаче:
 *   while (!done) {
 *       token.throwIfCancelled();
 *       ...
 *   }
 *   // снаружи:
 *   token.cancel();
 */
public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Если токен отменён — бросить CancellationException.
     * Вызывать внутри задачи в безопасных точках.
     */
    public void throwIfCancelled() {
        if (cancelled.get()) {
            throw new CancellationException("task cancelled");
        }
    }
}