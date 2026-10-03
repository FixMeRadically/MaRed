package com.fixmer.mared.gui2.studio.events;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.framework.core.Disposable;

/**
 * Группа подписок с общим dispose().
 *
 * 0.3.1:
 *   - dispose() не глотает Throwable молча — логирует с trace.
 *   - После dispose() добавление новых подписок автоматически
 *     отписывает их (иначе компонент, добавляющий подписку после
 *     dispose, утекал бы в шине).
 */
public final class SubscriptionGroup implements Disposable {

    private final List<StudioEventBus.Subscription> subs = new ArrayList<>(4);
    private boolean disposed = false;

    public void add(StudioEventBus.Subscription sub) {
        if (sub == null) return;
        if (disposed) {
            // Группа уже disposed — сразу отписываем, чтобы не утечь.
            sub.unsubscribe();
            return;
        }
        subs.add(sub);
    }

    public boolean isDisposed() {
        return disposed;
    }

    public int size() {
        return subs.size();
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;

        for (StudioEventBus.Subscription sub : subs) {
            try {
                sub.unsubscribe();
            } catch (Throwable t) {
                Mared.LOGGER.warn("[studio] unsubscribe failed", t);
            }
        }
        subs.clear();
    }
}