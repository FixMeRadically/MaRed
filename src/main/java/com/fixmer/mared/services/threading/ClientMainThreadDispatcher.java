package com.fixmer.mared.services.threading;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;

/**
 * Реализация MainThreadDispatcher для клиента Minecraft.
 *
 * 0.3.0: если мы уже в главном потоке — выполняем сразу без очереди
 * (ускоряет синхронные пути). Если в worker — отправляем через
 * Minecraft.execute(), который добавляет в очередь главного потока.
 */
public final class ClientMainThreadDispatcher implements MainThreadDispatcher {

    @Override
    public void execute(Runnable task) {
        if (task == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            task.run();
        } else {
            mc.execute(task);
        }
    }

    @Override
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @Override
    public boolean isMainThread() {
        return Minecraft.getInstance().isSameThread();
    }
}