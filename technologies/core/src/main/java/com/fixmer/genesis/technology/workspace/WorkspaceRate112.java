package com.fixmer.genesis.technology.workspace;
/** Server-thread token bucket: permits a bounded VIEW -> READ sequence without disabling rate limits. */
public final class WorkspaceRate112 {
 private double tokens=4;private long updated;private boolean initialized;
 public boolean take(long now){if(initialized)tokens=Math.min(4,tokens+Math.max(0,now-updated)*4.0/1_000_000_000L);initialized=true;updated=now;if(tokens<1)return false;tokens--;return true;}
}
