package com.fixmer.genesis.technology.workspace.live122;

import java.util.Map;

/** BUSY proves rejection. Exact EDIT batches and bounded source reads may be retried. */
public final class WorkspaceRequest123 {
    public enum Action { WAIT, SEND, UNKNOWN }
    private static final long TIMEOUT = 3_000_000_000L, BACKOFF = 500_000_000L;
    private final Map<String,String> request;
    private long sent;
    private final long started;
    private boolean rejected;
    private int sourceRetries;

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
        if (!rejected && !request.get("op").equals("EDIT")) {
            if (!java.util.Set.of("ADMIN153","BUILD_SOURCE","BUILD_CHUNK","VIEW","OPEN","SYNC","LIST","SOURCE_PULL","SOURCE_BASE132","INBOX131","DISCOVER131","OFFER131","RIGHTS131","HLOG131","JOIN_CHUNK131","MODEL_STAT147","MODEL_WATCH147","TEST_PUT152","TEST_REVIEW152","TEST_APPLY152","MODEL_GET147","MODEL_PUT147","MODEL_COMMIT147").contains(request.get("op")) || sourceRetries++ >= 3) return Action.UNKNOWN;
        }
        rejected = false;
        sent = now;
        return Action.SEND;
    }
}
