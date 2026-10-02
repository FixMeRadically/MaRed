package com.fixmer.mared.services.threading;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Планировщик фоновых задач.
 *
 * 0.3.0:
 *   - submit(name, task) — имя идёт в логи и в имя worker-потока.
 *   - submit(name, callable, onSuccess, onError, token) — с поддержкой
 *     кооперативной отмены через CancellationToken.
 *
 * Колбэки onSuccess/onError выполняются в главном потоке.
 *
 * ЗАПРЕЩЕНО из worker-задач:
 *   - трогать Minecraft world / entity / level;
 *   - мутировать UI;
 *   - вызывать render.
 */
public interface TaskScheduler {

    TaskHandle submit(Runnable task);

    TaskHandle submit(String name, Runnable task);

    <T> TaskHandle submit(Callable<T> task,
                          Consumer<T> onSuccess,
                          Consumer<Throwable> onError);

    /**
     * Задача с токеном отмены. Если token.cancel() вызван до старта —
     * задача не запустится. Если внутри задачи — task должна сама
     * вызвать token.throwIfCancelled() в безопасной точке.
     */
    <T> TaskHandle submit(String name,
                          Callable<T> task,
                          Consumer<T> onSuccess,
                          Consumer<Throwable> onError,
                          CancellationToken token);

    void shutdown();

    boolean isShutdown();

    int activeTaskCount();

    int queueSize();
}