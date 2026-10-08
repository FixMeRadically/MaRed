package com.fixmer.mared.gui2.navigation.transition;

import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.navigation.MaredSpace;
import com.fixmer.mared.gui2.navigation.SpaceCameraState;
import com.fixmer.mared.gui2.navigation.TransitionKind;

/**
 * Визуальный эффект перехода.
 *
 * Контракт: эффект знает, как выглядит промежуточный кадр между
 * двумя пространствами, но НЕ знает, куда мы летим — этим занимается
 * MaredNavigation. Эффект получает готовые space'ы и progress [0..1].
 *
 * v1: FadeZoomTransition. В будущем — цепочка эффектов:
 *   FadeEffect + BlurEffect + ParticleEffect + CameraWarpEffect.
 * API v1 уже готов к композиции.
 */
public interface TransitionEffect {

    /**
     * Длительность перехода в секундах.
     */
    float duration();

    /**
     * Отрисовать промежуточный кадр.
     *
     * @param rctx        контекст рендера (graphics + font + theme)
     * @param from        уходящее пространство (может быть null, если
     *                    входим извне)
     * @param to          приходящее пространство
     * @param fromCamera  камера источника, если есть
     * @param progress    0..1, linear (уже прошло duration * t)
     * @param kind        тип перехода (для будущей специализации)
     */
    void render(MaredRenderContext rctx,
                MaredSpace from,
                MaredSpace to,
                SpaceCameraState fromCamera,
                float progress,
                TransitionKind kind);
}