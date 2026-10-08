package com.fixmer.mared.gui2.navigation;

import com.fixmer.mared.gui2.framework.core.MaredRenderContext;

/**
 * Одно пространство MaRed Studio.
 *
 * Не «вкладка», не «экран». Пространство — это измерение проекта.
 * Content, World, Logic — это разные миры, между которыми игрок
 * перелетает на камере, а не переключается вкладками.
 *
 * Lifecycle:
 *   onEnter(prevCamera) — переход завершён, мы теперь активны.
 *                         prevCamera — откуда прилетели, для
 *                         интерполяции собственной камеры.
 *   tick(dt)            — каждый кадр.
 *   render(rctx)        — каждый кадр.
 *   onExit()            — мы уходим, освобождаем ресурсы.
 *
 * Input приходит ТОЛЬКО когда navigation не в transition. Если
 * space.acceptsInput() == false, navigation не рассылает ему input
 * даже в active-фазе (палитра команд, например, перехватывает).
 */
public interface MaredSpace {

    /** Уникальный id пространства. */
    SpaceId id();

    /** Ключ локализации заголовка. */
    String titleKey();

    /** Акцентный цвет (breadcrumb, orbit, рамки). */
    int accentColor();

    /**
     * Если false — input не рассылается (например, overlay перехватил).
     * По умолчанию — true.
     */
    default boolean acceptsInput() { return true; }

    /**
     * Снимок камеры. Может быть null для 2D-пространств (Tools UI).
     */
    default SpaceCameraState cameraState() { return null; }

    // ---------------------------------------------------------
    //  Lifecycle
    // ---------------------------------------------------------

    default void onEnter(SpaceCameraState prevCamera) {}
    default void onExit() {}
    default void tick(float dt) {}
    default void render(MaredRenderContext rctx) {}

    // ---------------------------------------------------------
    //  Input (все опциональны)
    // ---------------------------------------------------------

    default boolean mouseClicked(double mx, double my, int button) { return false; }
    default boolean mouseDragged(double mx, double my, int button,
                                 double dx, double dy) { return false; }
    default boolean mouseReleased(double mx, double my, int button) { return false; }
    default boolean mouseScrolled(double mx, double my, double dx, double dy) { return false; }
    default boolean keyPressed(int key, int scan, int mods) { return false; }
    default boolean charTyped(char c, int mods) { return false; }
}