package com.fixmer.mared.gui2.navigation;

/**
 * Снимок камеры пространства.
 *
 * Используется для:
 *   - интерполяции камеры между пространствами при переходе;
 *   - persist'а (последняя позиция в пространстве);
 *   - диагностики.
 *
 * immutable record.
 */
public record SpaceCameraState(
    float yaw,
    float pitch,
    float zoom,
    float focusX,
    float focusY,
    float focusZ
) {
    public static final SpaceCameraState DEFAULT =
        new SpaceCameraState(0f, 0f, 1f, 0f, 0f, 0f);

    public static SpaceCameraState lerp(SpaceCameraState a, SpaceCameraState b, float t) {
        if (a == null) return b;
        if (b == null) return a;
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        float u = t * t * (3f - 2f * t);
        return new SpaceCameraState(
            a.yaw    + (b.yaw    - a.yaw)    * u,
            a.pitch  + (b.pitch  - a.pitch)  * u,
            a.zoom   + (b.zoom   - a.zoom)   * u,
            a.focusX + (b.focusX - a.focusX) * u,
            a.focusY + (b.focusY - a.focusY) * u,
            a.focusZ + (b.focusZ - a.focusZ) * u
        );
    }
}