package com.fixmer.genesis.technology.workspace.live122;

import java.util.Map;

/** BUSY proves rejection. Only exact EDIT batches may be replayed after a lost response. */
public final class WorkspaceRequest123 {
    public enum Action { WAIT, SEND, UNKNOWN }
    private static final long TIMEOUT = 3_000_000_000L, BACKOFF = 500_000_000L;
    private final Map<String,String> request;
    private long sent;
    private final long started;
    private boolean rejected;

    public WorkspaceRequest123(Map<String,String> request, long now) {
        WorkspaceHub122.request(request);
        this.request = Map.copyOf(request);
        sent = started = now;
    }
    public Map<String,String> request() { return request; }
    public void busy(long now) { rejected = true; sent = now; }
    public Action tick(long now) {
        if (rejected && now - started >= 15_000_000_000L) return Action.UNKNOWN;
        if (now - sent < (rejected ? BACKOFF : TIMEOUT)) return Action.WAIT;
        if (!rejected && !request.get("op").equals("EDIT")) return Action.UNKNOWN;
        rejected = false;
        sent = now;
        return Action.SEND;
    }
}
