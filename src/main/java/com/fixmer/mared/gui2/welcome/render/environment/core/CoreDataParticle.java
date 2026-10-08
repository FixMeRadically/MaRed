package com.fixmer.mared.gui2.welcome.render.environment.core;

public final class CoreDataParticle {

    public enum Kind { SYMBOL, KEYWORD, VALUE }

    public final Kind kind;
    public final String glyph;
    public final int orbitIndex;

    public final float baseAngle;
    public final float lifespanSec;
    public final float phaseOffsetSec;
    public final float baseAlphaMultiplier;
    public final float twinklePhase;

    public CoreDataParticle(Kind kind, String glyph, int orbitIndex,
                            float baseAngle, float lifespanSec,
                            float phaseOffsetSec, float baseAlphaMultiplier,
                            float twinklePhase) {
        this.kind = kind;
        this.glyph = glyph;
        this.orbitIndex = orbitIndex;
        this.baseAngle = baseAngle;
        this.lifespanSec = lifespanSec;
        this.phaseOffsetSec = phaseOffsetSec;
        this.baseAlphaMultiplier = baseAlphaMultiplier;
        this.twinklePhase = twinklePhase;
    }

    public float life01(float timeSec) {
        float local = (timeSec + phaseOffsetSec) % lifespanSec;
        if (local < 0f) local += lifespanSec;
        return local / lifespanSec;
    }

    public float angleAt(float timeSec) {
        CoreOrbit orbit = CoreOrbit.ORBITS[orbitIndex];
        return baseAngle + timeSec * orbit.angularSpeed();
    }

    public float[] positionAt(float R, float timeSec) {
        CoreOrbit orbit = CoreOrbit.ORBITS[orbitIndex];
        float radius = R * orbit.radiusRatio();
        float squash = orbit.squashY();
        return CoreGeometry.orbitPoint(radius, squash, angleAt(timeSec));
    }

    public int alphaAt(float timeSec, int baseAlpha) {
        float life = life01(timeSec);

        float fade;
        if (life < 0.10f) {
            fade = life / 0.10f;
        } else if (life > 0.90f) {
            fade = (1f - life) / 0.10f;
        } else {
            fade = 1f;
        }

        float twinkle = 0.75f
            + 0.25f * (float)Math.sin(timeSec * 3.0f + twinklePhase);

        return (int)(baseAlpha * fade * twinkle * baseAlphaMultiplier);
    }
}