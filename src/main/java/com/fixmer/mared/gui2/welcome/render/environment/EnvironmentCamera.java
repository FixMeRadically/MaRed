package com.fixmer.mared.gui2.welcome.render.environment;

/**
 * Genesis: РєР°РјРµСЂР° СЃС†РµРЅС‹ Welcome.
 *
 * РџРѕР·РёС†РёСЏ (x, y) + zoom + РЅР°РєР»РѕРЅ (rotationX/Y) + focusDepth.
 *
 * position: Р»РёРЅРµР№РЅС‹Р№ СЃРґРІРёРі, used by offsetPx (Nebula, Glow).
 * rotation: shear-СЌС„С„РµРєС‚, used by Core (РѕР±СЉРµРєС‚ РЅР°РєР»РѕРЅСЏРµС‚СЃСЏ).
 * focus:    Р·Р°РіРѕС‚РѕРІРєР° РїРѕРґ 1.5.37.7 (РїРѕСЂС‚Р°Р»С‹) - РїРѕР»Рµ Р¶РёРІС‘С‚, Р·РЅР°С‡РµРЅРёРµ
 *           РїРѕРєР° РЅРµ РїСЂРёРјРµРЅСЏРµС‚СЃСЏ.
 *
 * Р’СЃРµ Р·РЅР°С‡РµРЅРёСЏ lerp'СЏС‚СЃСЏ Рє target РІ tick() СЃ SMOOTHING. Р­С‚Рѕ РґР°С‘С‚
 * "РѕРїРµСЂР°С‚РѕСЂСЃРєРѕРµ" РґРІРёР¶РµРЅРёРµ РґР°Р¶Рµ РїСЂРё СЂРµР·РєРѕРј РґРІРёР¶РµРЅРёРё РјС‹С€Рё.
 */
public final class EnvironmentCamera {

    private static final float SMOOTHING = 0.15f;
    private static final float DEFAULT_ZOOM = 1.0f;

    private static final float ROT_X_GAIN = 0.15f;
    private static final float ROT_Y_GAIN = 0.10f;

    // Р¦РµР»Рё
    private float targetX, targetY;
    private float targetZoom = DEFAULT_ZOOM;
    private float targetRotX, targetRotY;
    private float targetFocus;

    // РўРµРєСѓС‰РёРµ
    private float x, y;
    private float zoom = DEFAULT_ZOOM;
    private float rotX, rotY;
    private float focus;

    public void setTarget(float nx, float ny) {
        this.targetX = clamp(nx, -1f, 1f);
        this.targetY = clamp(ny, -1f, 1f);
    }

    public void setRotationTarget(float nx, float ny) {
        this.targetRotX = clamp(nx, -1f, 1f) * ROT_X_GAIN;
        this.targetRotY = clamp(ny, -1f, 1f) * ROT_Y_GAIN;
    }

    public void setZoomTarget(float z) {
        this.targetZoom = clamp(z, 0.5f, 2.0f);
    }

    public void setFocus(float f) {
        this.targetFocus = clamp(f, 0f, 2f);
    }

    public void reset() {
        targetX = targetY = 0f;
        targetZoom = DEFAULT_ZOOM;
        targetRotX = targetRotY = 0f;
        targetFocus = 0f;
    }

    public void tick() {
        x     += (targetX     - x)     * SMOOTHING;
        y     += (targetY     - y)     * SMOOTHING;
        zoom  += (targetZoom  - zoom)  * SMOOTHING;
        rotX  += (targetRotX  - rotX)  * SMOOTHING;
        rotY  += (targetRotY  - rotY)  * SMOOTHING;
        focus += (targetFocus - focus) * SMOOTHING;
    }

    public float x()     { return x; }
    public float y()     { return y; }
    public float zoom()  { return zoom; }
    public float rotX()  { return rotX; }
    public float rotY()  { return rotY; }
    public float focus() { return focus; }

    /** РЎРґРІРёРі СЃР»РѕСЏ РІ РїРёРєСЃРµР»СЏС… РїРѕ X. depth 0..1. */
    public int offsetPxX(int width, float depth) {
        return (int)(x * width * depth);
    }

    public int offsetPxY(int height, float depth) {
        return (int)(y * height * depth);
    }

    /**
     * Shear-С‚СЂР°РЅСЃС„РѕСЂРјР°С†РёСЏ С‚РѕС‡РєРё. Р”Р°С‘С‚ 2D-РѕС‰СѓС‰РµРЅРёРµ РЅР°РєР»РѕРЅР° Р±РµР·
     * СЂРµР°Р»СЊРЅРѕРіРѕ 3D. РљРѕСЌС„С„РёС†РёРµРЅС‚ k РѕРїСЂРµРґРµР»СЏРµС‚ СЃРёР»Сѓ: 0 = РїР»РѕСЃРєРѕ,
     * 0.15 = Р·Р°РјРµС‚РЅРѕ, 0.3 = СЃРёР»СЊРЅРѕ.
     *
     * РС‚РѕРі: P' = P + (rotX * dy, rotY * dx) * k
     */
    public int[] shear(int px, int py, int cx, int cy, float k) {
        int dx = px - cx;
        int dy = py - cy;
        return new int[]{
            px + (int)(rotX * dy * k),
            py + (int)(rotY * dx * k)
        };
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}