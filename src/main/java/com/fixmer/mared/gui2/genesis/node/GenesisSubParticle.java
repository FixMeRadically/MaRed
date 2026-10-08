package com.fixmer.mared.gui2.genesis.node;

/**
 * v10: offsetAt РІРѕР·РІСЂР°С‰Р°РµС‚ 2D СЌРєСЂР°РЅРЅС‹Р№ offset РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅРѕ С†РµРЅС‚СЂР°
 * РѕР±СЉРµРєС‚Р°. Р Р°РґРёСѓСЃ parentScreenRadius вЂ” СѓР¶Рµ РІ screen-space (СЃ zoom).
 * Р­С‚Рѕ РґР°С‘С‚ РїРѕСЃС‚РѕСЏРЅРЅРѕРµ РІРёР·СѓР°Р»СЊРЅРѕРµ СЂР°СЃСЃС‚РѕСЏРЅРёРµ СЃСѓР±-С‡Р°СЃС‚РёС† РѕС‚ С†РµРЅС‚СЂР°
 * РѕР±СЉРµРєС‚Р° РїСЂРё Р»СЋР±РѕРј Р·СѓРјРµ.
 *
 * tiltX вЂ” Р±Р°Р·РѕРІС‹Р№ polar angle [0..ПЂ].
 * tiltZ вЂ” СЃРєРѕСЂРѕСЃС‚СЊ РІРµСЂС‚РёРєР°Р»СЊРЅРѕРіРѕ РїРѕРєР°С‡РёРІР°РЅРёСЏ.
 */
public final class GenesisSubParticle {

    public final float orbitMul;
    public final float orbitSpeed;
    public final float phase;
    public final float tiltX;
    public final float tiltZ;
    public final float size;
    public final float lifeSpeed;
    public final String glyph;

    private float life;

    public GenesisSubParticle(float orbitMul, float orbitSpeed,
                              float phase, float tiltX, float tiltZ,
                              float size, float lifeSpeed,
                              String glyph, float initialLife) {
        this.orbitMul   = orbitMul;
        this.orbitSpeed = orbitSpeed;
        this.phase      = phase;
        this.tiltX      = tiltX;
        this.tiltZ      = tiltZ;
        this.size       = size;
        this.lifeSpeed  = lifeSpeed;
        this.glyph      = glyph;
        this.life       = initialLife;
    }

    public void tick(float dt) {
        life += lifeSpeed * dt;
        while (life > 1f) life -= 1f;
        if (life < 0f) life = 0f;
    }

    /**
     * Screen-space offset РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅРѕ С†РµРЅС‚СЂР° РѕР±СЉРµРєС‚Р°.
     * parentScreenRadius вЂ” СЌРєСЂР°РЅРЅС‹Р№ СЂР°РґРёСѓСЃ РѕР±СЉРµРєС‚Р° (СѓР¶Рµ СЃ zoom).
     *
     * Р’РѕР·РІСЂР°С‰Р°РµС‚ [x, y] РІ РїРёРєСЃРµР»СЏС…. y РІРЅРёР· (screen-space).
     */
    public float[] offsetAt(float parentScreenRadius, float timeSec) {
        float theta = phase + timeSec * orbitSpeed;
        float phi = tiltX + (float)Math.sin(timeSec * tiltZ + phase * 0.7f) * 0.4f;
        if (phi < 0.15f) phi = 0.15f;
        if (phi > (float)Math.PI - 0.15f) phi = (float)Math.PI - 0.15f;

        float r = parentScreenRadius * orbitMul;
        float sp = (float)Math.sin(phi);
        float cp = (float)Math.cos(phi);

        float x = r * sp * (float)Math.cos(theta);
        float y = r * cp;

        return new float[]{x, y};
    }

    public float alphaAt() {
        if (life < 0.15f) return life / 0.15f;
        if (life > 0.85f) return (1f - life) / 0.15f;
        return 1f;
    }

    public float life() { return life; }
}