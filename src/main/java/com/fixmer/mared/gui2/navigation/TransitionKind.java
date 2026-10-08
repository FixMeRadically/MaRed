package com.fixmer.mared.gui2.navigation;

/**
 * Тип перехода между пространствами.
 *
 * В v1 все переходы рендерятся одинаково (FadeZoomTransition), но
 * kind сохраняется в навигационном стеке — на будущее, когда у
 * каждого типа будет своя визуальная семантика:
 *
 *   CAMERA_FLIGHT — «полёт» вдоль оси Z, ядро уходит в глубину.
 *   ZOOM_THROUGH  — «влёт» внутрь объекта, scale растёт.
 *   FADE          — простое растворение.
 *   WARP          — искажение, для cross-dimension.
 *
 * Пока — только метка.
 */
public enum TransitionKind {
    CAMERA_FLIGHT,
    ZOOM_THROUGH,
    FADE,
    WARP;

    public static final TransitionKind DEFAULT = CAMERA_FLIGHT;
}