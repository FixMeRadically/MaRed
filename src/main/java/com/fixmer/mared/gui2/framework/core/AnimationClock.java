package com.fixmer.mared.gui2.framework.core;

/**
 * Часы анимаций, привязанные к одному UiContext.
 *
 * 0.3.0 (Phase A): заменяет глобальный MaredAnimState.
 * Один экран — одни часы. Нет двойного tick, нет влияния чужих экранов.
 */
public final class AnimationClock {

    private float deltaSec;
    private float elapsedSec;
    private long  lastNanos;

    public void tick() {
        long now = System.nanoTime();
        if (lastNanos == 0L) {
            lastNanos = now;
            deltaSec = 0f;
            return;
        }
        float d = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        if (d > 0.1f) d = 0.1f;
        if (d < 0f)   d = 0f;
        deltaSec = d;
        elapsedSec += d;
    }

    public float deltaSec()   { return deltaSec; }
    public float elapsedSec() { return elapsedSec; }

    public void reset() {
        lastNanos = 0L;
        deltaSec = 0f;
        elapsedSec = 0f;
    }
}