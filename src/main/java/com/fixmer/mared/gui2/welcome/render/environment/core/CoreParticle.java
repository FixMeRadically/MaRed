package com.fixmer.mared.gui2.welcome.render.environment.core;

/**
 * Genesis: РѕРґРЅР° С‡Р°СЃС‚РёС†Р° РЅР° РѕСЂР±РёС‚Рµ.
 *
 * РџРѕР»РЅРѕСЃС‚СЊСЋ stateless. "Р–РёР·РЅСЊ" С‡Р°СЃС‚РёС†С‹ - РґРµС‚РµСЂРјРёРЅРёСЂРѕРІР°РЅРЅР°СЏ
 * С„СѓРЅРєС†РёСЏ РѕС‚ РІСЂРµРјРµРЅРё:
 *
 *   lifePhase = (time + phaseOffset) mod lifespan
 *   life01    = lifePhase / lifespan       // 0..1
 *
 * РџСЂРё life01 < 0.1  - fade in
 * РџСЂРё life01 > 0.9  - fade out
 * РњРµР¶РґСѓ              - СЃС‚Р°Р±РёР»СЊРЅР°, СЃР»РµРіРєР° РјРµСЂС†Р°РµС‚ (twinkle)
 */
public final class CoreParticle {

    public final int   orbitIndex;
    public final float baseAngle;
    public final float lifespanSec;
    public final float phaseOffsetSec;
    public final float size;
    public final float baseAlpha;
    public final float twinklePhase;

    public CoreParticle(int orbitIndex, float baseAngle,
                        float lifespanSec, float phaseOffsetSec,
                        float size, float baseAlpha, float twinklePhase) {
        this.orbitIndex     = orbitIndex;
        this.baseAngle      = baseAngle;
        this.lifespanSec    = lifespanSec;
        this.phaseOffsetSec = phaseOffsetSec;
        this.size           = size;
        this.baseAlpha      = baseAlpha;
        this.twinklePhase   = twinklePhase;
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
        float radius = R * orbit.radiusRatio();      // record: РјРµС‚РѕРґ
        float squash = orbit.squashY();              // record: РјРµС‚РѕРґ
        return CoreGeometry.orbitPoint(radius, squash, angleAt(timeSec));
    }

    public int alphaAt(float timeSec) {
        float life = life01(timeSec);

        float fade;
        if (life < 0.10f) {
            fade = life / 0.10f;
        } else if (life > 0.90f) {
            fade = (1f - life) / 0.10f;
        } else {
            fade = 1f;
        }

        float twinkle = 0.7f
            + 0.3f * (float)Math.sin(timeSec * 3.0f + twinklePhase);

        return (int)(baseAlpha * fade * twinkle);
    }
}