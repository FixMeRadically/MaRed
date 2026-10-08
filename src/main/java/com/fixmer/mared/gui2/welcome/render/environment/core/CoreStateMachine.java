package com.fixmer.mared.gui2.welcome.render.environment.core;

public final class CoreStateMachine {

    private CoreStateMachine() {}

    public static final float TOTAL_CYCLE_SEC = computeTotal();

    private static float computeTotal() {
        float s = 0f;
        for (CoreState st : CoreState.values()) s += st.durationSec;
        return s;
    }

    public static CoreState stateAt(float timeSec) {
        float t = mod(timeSec, TOTAL_CYCLE_SEC);
        float acc = 0f;
        for (CoreState st : CoreState.values()) {
            if (t < acc + st.durationSec) return st;
            acc += st.durationSec;
        }
        return CoreState.IDLE;
    }

    public static float progressAt(float timeSec) {
        float t = mod(timeSec, TOTAL_CYCLE_SEC);
        float acc = 0f;
        for (CoreState st : CoreState.values()) {
            if (t < acc + st.durationSec) {
                return (t - acc) / st.durationSec;
            }
            acc += st.durationSec;
        }
        return 0f;
    }

    public static int glyphAlpha(CoreState state) {
        return switch (state) {
            case IDLE         -> 80;
            case SCANNING     -> 110;
            case PARSING      -> 160;
            case BUILDING     -> 220;
            case STABILIZING  -> 190;
            case READY        -> 100;
        };
    }

    public static float streamActivity(CoreState state) {
        return switch (state) {
            case IDLE         -> 0.15f;
            case SCANNING     -> 0.40f;
            case PARSING      -> 0.85f;
            case BUILDING     -> 0.70f;
            case STABILIZING  -> 0.55f;
            case READY        -> 0.30f;
        };
    }

    public static boolean prototypeVisible(CoreState state) {
        return state == CoreState.BUILDING
            || state == CoreState.STABILIZING
            || state == CoreState.READY;
    }

    private static float mod(float a, float b) {
        float r = a % b;
        return r < 0 ? r + b : r;
    }
}