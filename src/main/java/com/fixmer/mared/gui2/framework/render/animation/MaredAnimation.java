package com.fixmer.mared.gui2.framework.render.animation;

/**
 * Анимации.
 *
 * 0.3.0 (Phase A): Animated — поля private, доступ через get()/setTarget().
 * 0.3.1:
 *   - setTarget/setSpeed/tick/snap валидируют значения:
 *     NaN, Infinity, отрицательный speed — отклоняются.
 *   - nowSec() использует System.nanoTime() вместо currentTimeMillis.
 *     Раньше NTP-коррекция / смена системного времени / выход из сна
 *     могли дёрнуть анимацию.
 */
public final class MaredAnimation {

    private MaredAnimation() {}

    public static final class Animated {
        private float value;
        private float target;
        private float speed = 8f;

        public Animated() { this(0f); }

        public Animated(float initial) {
            if (!Float.isFinite(initial)) initial = 0f;
            this.value  = initial;
            this.target = initial;
        }

        public float get()    { return value; }
        public float target() { return target; }
        public float speed()  { return speed; }

        public void setTarget(float t) {
            if (!Float.isFinite(t)) return;
            this.target = t;
        }

        public void setSpeed(float s) {
            if (!Float.isFinite(s) || s < 0f) return;
            this.speed = s;
        }

        public void tick(float deltaSec) {
            tick(deltaSec, speed);
        }

        public void tick(float deltaSec, float speed) {
            if (!Float.isFinite(deltaSec) || deltaSec <= 0f) return;
            if (!Float.isFinite(speed) || speed < 0f) return;

            float diff = target - value;
            if (Math.abs(diff) < 0.001f) { value = target; return; }
            float step = Math.min(1f, deltaSec * speed);
            value += diff * step;
        }

        public void snap(float v) {
            if (!Float.isFinite(v)) return;
            value = v; target = v;
        }

        public boolean settled() {
            return Math.abs(target - value) < 0.001f;
        }
    }

    private static final long startNanos = System.nanoTime();

    public static float nowSec() {
        return (System.nanoTime() - startNanos) / 1_000_000_000f;
    }
}