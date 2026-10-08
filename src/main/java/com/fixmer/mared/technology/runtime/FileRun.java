package com.fixmer.mared.technology.runtime;

import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One editor invocation, including all of its MR blocks. GUI only reads snapshots. */
public final class FileRun {
    public enum State { QUEUED, RUNNING, WAITING, BACKGROUND, STOPPING, COMPLETED, CANCELLED, FAILED }
    public record Snapshot(String id, String title, State state, long elapsedMillis,
                           long steps, int activeBlocks, int sentCommands, int backgroundResources, String error) {
        public boolean terminal() { return state == State.COMPLETED || state == State.CANCELLED || state == State.FAILED; }
    }
    private final String id = UUID.randomUUID().toString();
    private final String title;
    private final long started = System.nanoTime();
    private final RunJournal journal = new RunJournal();
    private final com.fixmer.genesis.technology.runtime.ExecutionScope scope = new com.fixmer.genesis.technology.runtime.ExecutionScope();
    public com.fixmer.genesis.technology.runtime.ExecutionScope scope() { return scope; }
    private final List<MaredScriptExecutor> executors = new ArrayList<>();
    private volatile boolean stopRequested;
    private boolean submissionDone, begun;
    private int pending, sent;
    private String error;
    private State terminal;
    private long ended;

    public FileRun(String title) { this.title = title == null ? "Untitled" : title; journal.add("[run] queued: " + this.title); }
    public RunJournal journal() { return journal; }
    public String id() { return id; }
    public boolean stopRequested() { return stopRequested || scope.cancelled(); }
    /** MP owner-thread cleanup when normal client execution cannot tick (e.g. logout). */
    public void finishClientCancellation() {
        if (!stopRequested) return;
        List<MaredScriptExecutor> owned;
        synchronized (this) { owned = List.copyOf(executors); }
        com.fixmer.mared.commands.engine.MaredScriptRunner.finishOwnedCancellation(scope, null);
        for (var executor : owned) if (executor.getContext().getServer() == null) executor.tick();
    }
    public synchronized void requestStop() {
        if (terminal != null) { scope.close(); return; }
        if (stopRequested) return;
        stopRequested = true;
        scope.close();
        for (var executor : executors) executor.requestStop();
        journal.add("[run] stop requested; already executed commands cannot be undone");
    }
    public synchronized boolean reserveBlock() {
        if (stopRequested || terminal != null) return false;
        begun = true; pending++; return true;
    }
    public synchronized void attach(MaredScriptExecutor executor) {
        executors.add(executor);
        if (stopRequested) executor.requestStop();
    }
    public synchronized void blockSubmitted() { pending--; }
    public synchronized void commandSent() { begun = true; sent++; }
    public synchronized void submissionDone() { submissionDone = true; }
    /** The captured world is gone; queued server work may never be dispatched. */
    public synchronized void disconnected() {
        requestStop(); submissionDone = true;
        if (terminal == null) {
            terminal = State.CANCELLED; ended = System.nanoTime();
            journal.add("[run] CANCELLED — connection/world changed");
        }
    }
    public synchronized void fail(Throwable failure) {
        if (terminal != null) return;
        error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        journal.add("[error] " + error);
        requestStop(); submissionDone = true;
    }
    public synchronized Snapshot snapshot() {
        var resources = scope.snapshot();
        if (resources.failure() != null && error == null) {
            error = resources.failure().getClass().getSimpleName() + ": " + resources.failure().getMessage();
            journal.add("[error] owned background task: " + error); requestStop(); submissionDone = true;
        }
        if (resources.cancelled() && terminal == null && !stopRequested) requestStop();
        long steps = 0; int active = 0; boolean waiting = true;
        for (var executor : executors) {
            var status = executor.snapshot(); steps += status.steps();
            if (status.failure() != null && error == null) {
                error = status.failure().getClass().getSimpleName() + ": " + status.failure().getMessage();
                requestStop(); submissionDone = true;
            }
            if (!status.finished()) { active++; waiting &= status.waiting() || status.paused(); }
        }
        if (terminal == null && submissionDone && pending == 0 && active == 0 && resources.resources() == 0) {
            terminal = error != null ? State.FAILED : stopRequested ? State.CANCELLED : State.COMPLETED;
            ended = System.nanoTime();
            journal.add("[run] " + terminal + " — MC sent: " + sent + ", MR steps: " + steps);
        }
        State state = terminal != null ? terminal : stopRequested ? State.STOPPING
            : submissionDone && pending == 0 && active == 0 && resources.resources() > 0 ? State.BACKGROUND
            : !begun ? State.QUEUED : active > 0 && waiting && submissionDone ? State.WAITING : State.RUNNING;
        return new Snapshot(id, title, state, ((ended == 0 ? System.nanoTime() : ended) - started)/1_000_000,
            steps, active, sent, resources.resources(), error);
    }
}
