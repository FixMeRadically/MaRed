package com.fixmer.mared.services.threading;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Абстракция над "главным потоком".
 *
 * 0.3.0: в клиенте реализуется через Minecraft.execute(). В 0.3.5 при
 * добавлении side-aware runtime можно будет подменить на no-op
 * (standalone) или на серверный dispatcher.
 *
 * Все взаимодействия с UI из worker-потока ДОЛЖНЫ идти через этот
 * интерфейс — иначе получим race conditions и краши.
 */
public interface MainThreadDispatcher {

    /** Выполнить в главном потоке. Если уже в нём — выполнит сразу. */
    void execute(Runnable task);

    /** Выполнить в главном потоке и вернуть future. */
    <T> CompletableFuture<T> submit(Callable<T> task);

    /** true — текущий поток и есть главный. */
    boolean isMainThread();
}