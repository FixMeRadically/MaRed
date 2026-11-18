package com.fixmer.mared.services.threading;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.fixmer.mared.Mared;

/**
 * ThreadPoolExecutor с bounded queue и опциональной отменой через токен.
 *
 * 0.3.0: CancellationToken — если токен отменён до старта задачи, future
 * завершается как cancelled, тело не выполняется.
 */
public final class AsyncTaskScheduler implements TaskScheduler {

    private static final int QUEUE_CAPACITY = 256;

    private final ThreadPoolExecutor pool;
    private final MainThreadDispatcher mainThread;
    private final AtomicInteger active = new AtomicInteger(0);
    private volatile boolean shutdown = false;

    public AsyncTaskScheduler(int poolSize, MainThreadDispatcher mainThread) {
        if (poolSize < 1) poolSize = 1;
        this.mainThread = mainThread;

        this.pool = new ThreadPoolExecutor(
            poolSize,
            poolSize,
            30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            new WorkerFactory(),
            new ThreadPoolExecutor.AbortPolicy()
        );
        this.pool.allowCoreThreadTimeOut(false);
    }

    // ============================================================
    //  Runnable
    // ============================================================

    @Override
    public TaskHandle submit(Runnable task) {
        return submit("task", task);
    }

    @Override
    public TaskHandle submit(String name, Runnable task) {
        if (shutdown) throw new IllegalStateException("scheduler is shut down");
        if (task == null) throw new IllegalArgumentException("task is null");

        final String taskName = (name == null || name.isEmpty()) ? "task" : name;

        active.incrementAndGet();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            String prevName = Thread.currentThread().getName();
            Thread.currentThread().setName(prevName + " [" + taskName + "]");
            try {
                task.run();
            } catch (Throwable t) {
                Mared.LOGGER.error("[threading] task '{}' failed", taskName, t);
            } finally {
                Thread.currentThread().setName(prevName);
                active.decrementAndGet();
            }
        }, pool);

        return new TaskHandle(future);
    }

    // ============================================================
    //  Callable
    // ============================================================

    @Override
    public <T> TaskHandle submit(Callable<T> task,
                                  Consumer<T> onSuccess,
                                  Consumer<Throwable> onError) {
        return submit("task", task, onSuccess, onError, null);
    }

    @Override
    public <T> TaskHandle submit(String name,
                                  Callable<T> task,
                                  Consumer<T> onSuccess,
                                  Consumer<Throwable> onError,
                                  CancellationToken token) {
        if (shutdown) throw new IllegalStateException("scheduler is shut down");
        if (task == null) throw new IllegalArgumentException("task is null");

        final String taskName = (name == null || name.isEmpty()) ? "task" : name;

        // Токен уже отменён — не запускаем.
        if (token != null && token.isCancelled()) {
            CompletableFuture<T> cancelled = new CompletableFuture<>();
            cancelled.cancel(false);
            return new TaskHandle(cancelled);
        }

        active.incrementAndGet();
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            String prevName = Thread.currentThread().getName();
            Thread.currentThread().setName(prevName + " [" + taskName + "]");
            try {
                if (token != null) token.throwIfCancelled();
                return task.call();
            } catch (CancellationException ce) {
                throw ce;
            } catch (Throwable t) {
                throw new CompletionException(t);
            } finally {
                Thread.currentThread().setName(prevName);
                active.decrementAndGet();
            }
        }, pool);

        future.whenComplete((result, error) -> {
            if (error != null) {
                Throwable cause = unwrap(error);

                // Отмена — это норма, не логируем как ошибку.
                if (cause instanceof CancellationException) {
                    return;
                }
                if (onError != null) {
                    mainThread.execute(() -> {
                        if (token == null || !token.isCancelled()) {
                            onError.accept(cause);
                        }
                    });
                } else {
                    Mared.LOGGER.error("[threading] task '{}' failed", taskName, cause);
                }
            } else {
                if (onSuccess != null) {
                    mainThread.execute(() -> {
                        if (token == null || !token.isCancelled()) {
                            onSuccess.accept(result);
                        }
                    });
                }
            }
        });

        return new TaskHandle(future);
    }

    // ============================================================
    //  shutdown
    // ============================================================

    @Override
    public void shutdown() {
        if (shutdown) return;
        shutdown = true;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public int activeTaskCount() {
        return active.get();
    }

    @Override
    public int queueSize() {
        return pool.getQueue().size();
    }

    // ============================================================
    //  Утилиты
    // ============================================================

    private static Throwable unwrap(Throwable t) {
        if (t instanceof CompletionException ce && ce.getCause() != null) {
            return ce.getCause();
        }
        return t;
    }

    private static final class WorkerFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "mared-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    }
}