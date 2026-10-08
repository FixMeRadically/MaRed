package com.fixmer.mared.gui2.genesis;

public final class GenesisCamera {

    private static final float PITCH_LIMIT = 1.30f;

    /**
     * Р‘Р°Р·РѕРІР°СЏ РґРёСЃС‚Р°РЅС†РёСЏ РєР°РјРµСЂС‹. Р‘РѕР»СЊС€Рµ = СЃР»Р°Р±РµРµ РїРµСЂСЃРїРµРєС‚РёРІР°.
     * 2400 РґР°С‘С‚ РјСЏРіРєРѕРµ СЂР°Р·Р»РёС‡РёРµ РїРµСЂРµРґРЅРµРіРѕ/Р·Р°РґРЅРµРіРѕ РїР»Р°РЅР°, РЅРѕ РЅРµ В«РІС‹РІРѕСЂР°С‡РёРІР°РµС‚В»
     * РѕСЂР±РёС‚С‹ РїСЂРё РїРѕРІРѕСЂРѕС‚Рµ.
     */
    private static final float BASE_CAMERA_DIST = 2400f;

    private float yaw, pitch;
    private float targetYaw, targetPitch;

    private float zoom = 1.0f;
    private float targetZoom = 1.0f;

    private float focusX, focusY, focusZ;
    private float targetFocusX, targetFocusY, targetFocusZ;

    private float cosYaw = 1f, sinYaw = 0f;
    private float cosPitch = 1f, sinPitch = 0f;

    public void addRotation(float dYaw, float dPitch) {
        this.yaw   += dYaw;
        this.pitch  = clamp(this.pitch + dPitch, -PITCH_LIMIT, PITCH_LIMIT);
        this.targetYaw = this.yaw;
        this.targetPitch = this.pitch;
        refreshTrig();
    }

    public void setZoomTarget(float z) {
        this.targetZoom = clamp(z, 0.4f, 8.0f);
    }

    public void setZoomDirect(float z) {
        this.zoom = clamp(z, 0.4f, 8.0f);
        this.targetZoom = this.zoom;
    }

    public void setFocusTarget(float x, float y, float z) {
        this.targetFocusX = x;
        this.targetFocusY = y;
        this.targetFocusZ = z;
    }

    public void setRotationTarget(float yaw, float pitch) {
        this.targetYaw = yaw;
        this.targetPitch = clamp(pitch, -PITCH_LIMIT, PITCH_LIMIT);
    }

    public void reset() {
        this.targetYaw = 0f;
        this.targetPitch = 0f;
        this.targetZoom = 1.0f;
        this.targetFocusX = 0f;
        this.targetFocusY = 0f;
        this.targetFocusZ = 0f;
    }

    public void tick() { tick(0.05f); }

    public void tick(float dt) {
        if (dt <= 0f) return;
        if (dt > 0.1f) dt = 0.1f;
        float k = Math.min(1f, dt * 2.0f);

        yaw    += (targetYaw    - yaw)    * k;
        pitch  += (targetPitch  - pitch)  * k;
        zoom   += (targetZoom   - zoom)   * k;
        focusX += (targetFocusX - focusX) * k;
        focusY += (targetFocusY - focusY) * k;
        focusZ += (targetFocusZ - focusZ) * k;

        refreshTrig();
    }

    private void refreshTrig() {
        cosYaw = (float)Math.cos(yaw);
        sinYaw = (float)Math.sin(yaw);
        cosPitch = (float)Math.cos(pitch);
        sinPitch = (float)Math.sin(pitch);
    }

    public float yaw()   { return yaw; }
    public float pitch() { return pitch; }
    public float zoom()  { return zoom; }
    public float zoomTarget() { return targetZoom; }

    public float focusX() { return focusX; }
    public float focusY() { return focusY; }
    public float focusZ() { return focusZ; }

    public float[] toCameraSpace(float x, float y, float z) {
        float dx = x - focusX;
        float dy = y - focusY;
        float dz = z - focusZ;

        float xr = dx * cosYaw - dz * sinYaw;
        float zr = dx * sinYaw + dz * cosYaw;
        float yr = dy * cosPitch - zr * sinPitch;
        float zr2 = dy * sinPitch + zr * cosPitch;

        return new float[]{xr, yr, zr2};
    }

    /**
     * v10: РїРµСЂСЃРїРµРєС‚РёРІР° Р‘Р•Р— zoom. Zoom РЅРµ РІР»РёСЏРµС‚ РЅР° РїРѕР»РѕР¶РµРЅРёРµ РѕР±СЉРµРєС‚РѕРІ
     * РЅР° СЌРєСЂР°РЅРµ вЂ” С‚РѕР»СЊРєРѕ РЅР° РёС… СЂР°Р·РјРµСЂ (СЃРј. sizeScale()).
     */
    public float[] worldToScreen(float x, float y, float z, int sw, int sh) {
        float[] c = toCameraSpace(x, y, z);
        // v14: zoom применяется к ПОЗИЦИЯМ всех узлов. Focus делает так,
        // что focused-узел остаётся в центре, остальные разъезжаются
        // наружу — это ощущается как "камера подъезжает".
        // Размер увеличивается отдельно (см. GenesisNodeRenderer):
        // только у focused-узла.
        float p = perspectiveScale(c[2]) * zoom;
        return new float[]{
            sw / 2f + c[0] * p,
            sh / 2f - c[1] * p,
            c[2]
        };
    }

    /** РџРµСЂСЃРїРµРєС‚РёРІР° РЅР° РіР»СѓР±РёРЅРµ depth. Р‘РµР· zoom. */
    public float perspectiveScale(float depth) {
        float d = depth + BASE_CAMERA_DIST;
        if (d < 50f) d = 50f;
        float p = BASE_CAMERA_DIST / d;
        if (p < 0.10f) p = 0.10f;
        if (p > 4.5f) p = 4.5f;
        return p;
    }

    /**
     * v10: zoom РїСЂРёРјРµРЅСЏРµС‚СЃСЏ С‚РѕР»СЊРєРѕ Р·РґРµСЃСЊ вЂ” Рє СЂР°Р·РјРµСЂР°Рј.
     */
    public float sizeScale(float depth) {
        return perspectiveScale(depth) * zoom;
    }

    /**
     * @deprecated РёСЃРїРѕР»СЊР·СѓР№ perspectiveScale(depth) РґР»СЏ РїРѕР·РёС†РёР№ Рё
     *             sizeScale(depth) РґР»СЏ СЂР°Р·РјРµСЂРѕРІ. РћСЃС‚Р°РІР»РµРЅРѕ РґР»СЏ
     *             СЃРѕРІРјРµСЃС‚РёРјРѕСЃС‚Рё СЃ РІРЅРµС€РЅРёРј РєРѕРґРѕРј.
     */
    @Deprecated
    public float depthScale(float depth) {
        return sizeScale(depth);
    }

    public int depthAlpha(float depth) {
        float a = 1f - depth / 2000f;
        if (a < 0.25f) a = 0.25f;
        if (a > 1f)    a = 1f;
        return (int)(a * 255f);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}