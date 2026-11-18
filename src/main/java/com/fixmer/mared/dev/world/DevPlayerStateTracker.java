package com.fixmer.mared.dev.world;

/**
 * Compatibility shell. Automatic full-NBT snapshots/restores were retired in patch95.
 * Vanilla owns playerdata and the normal final world save. Old snapshots are retained
 * on disk for explicit recovery; they are never applied merely because dev mode is on.
 */
public final class DevPlayerStateTracker {
    private DevPlayerStateTracker(){}
}
