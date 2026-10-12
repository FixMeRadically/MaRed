package com.fixmer.mared.technology.search;

import com.fixmer.genesis.technology.editor.DocumentSearch;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import java.util.concurrent.*;

/** One UI owner, one worker and one queued request; closing releases all owned work. */
public final class DocumentSearchTask implements AutoCloseable {
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(1), r -> { var thread = new Thread(r, "genesis-document-search"); thread.setDaemon(true); return thread; });
    private Future<DocumentSearch.Result> active;
    public Future<DocumentSearch.Result> start(String query, DocumentSearch.Mode mode, boolean matchCase) {
        cancel();
        active = worker.submit(() -> MaredCommandStorage.search(query, mode, matchCase,
            () -> Thread.currentThread().isInterrupted()));
        return active;
    }
    public void cancel() {
        if (active != null) active.cancel(true);
        active = null; worker.purge();
    }
    @Override public void close() { cancel(); worker.shutdownNow(); }
}
