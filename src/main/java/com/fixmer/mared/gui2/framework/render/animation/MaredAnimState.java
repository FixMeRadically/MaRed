package com.fixmer.mared.gui2.framework.render.animation;

import com.fixmer.mared.gui2.framework.render.MaredScale;

/**
 * Глобальное состояние анимаций — статический фасад над AnimationClock
 * текущего UiContext.
 *
 * 0.3.1: без изменений по логике, но явно документирует, что требует
 * MaredScale.bind(). Если UiContext не привязан — context() бросит
 * IllegalStateException.
 *
 * @deprecated after 0.4.0 — использовать UiContext.clock() явно.
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