package com.fixmer.mared.gui2.welcome.render.environment.core;

public enum CoreState {

    IDLE(20f),
    SCANNING(5f),
    PARSING(8f),
    BUILDING(10f),
    STABILIZING(5f),
    READY(7f);

    public final float durationSec;

    CoreState(float durationSec) {
        this.durationSec = durationSec;
    }

    public CoreState next() {
        CoreState[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}