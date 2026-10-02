package com.fixmer.mared.gui2.framework.render;

/**
 * Глобальное состояние анимаций.
 *
 * 0.3.0 (Phase A): рефакторинг. Скрытого статического clock больше нет.
 * Теперь MaredAnimState — статический фасад над AnimationClock
 * текущего UiContext.
 *
 * @deprecated after 0.4.0 — использовать UiContext.clock().
 */
@Deprecated
public final class MaredAnimState {

    private MaredAnimState() {}

    public static void tick() {
        MaredScale.context().clock().tick();
    }

    public static float deltaSec()   { return MaredScale.context().clock().deltaSec(); }
    public static float elapsedSec() { return MaredScale.context().clock().elapsedSec(); }
}