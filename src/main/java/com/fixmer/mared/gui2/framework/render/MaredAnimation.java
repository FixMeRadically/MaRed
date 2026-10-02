package com.fixmer.mared.gui2.framework.render;

/**
 * Анимации.
 *
 * 0.3.0 (Phase A): рефакторинг.
 *  - Animated: поля private, доступ через get()/setTarget()/setSpeed().
 *  - nowSec(): оставлен как утилита (абсолютное время, не UI clock).
 */
public final class MaredAnimation {

    private MaredAnimation() {}

    public static final class Animated {
        private float value;
        private float target;
        private float speed = 8f;

        public Animated() { this(0f); }

        public Animated(float initial) {
            this.value  = initial;
            this.target = initial;
        }

        public float get()    { return value; }
        public float target() { return target; }
        public float speed()  { return speed; }

        public void setTarget(float t) { this.target = t; }
        public void setSpeed(float s)  { this.speed  = s; }

        public void tick(float deltaSec) {
            tick(deltaSec, speed);
        }

        public void tick(float deltaSec, float speed) {
            if (deltaSec <= 0f) return;
            float diff = target - value;
            if (Math.abs(diff) < 0.001f) { value = target; return; }
            float step = Math.min(1f, deltaSec * speed);
            value += diff * step;
        }

        public void snap(float v) { value = v; target = v; }
        public boolean settled()  { return Math.abs(target - value) < 0.001f; }
    }

    private static final long startMs = System.currentTimeMillis();

    public static float nowSec() {
        return (System.currentTimeMillis() - startMs) / 1000f;
    }
}