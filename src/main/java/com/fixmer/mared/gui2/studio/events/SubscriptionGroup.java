package com.fixmer.mared.gui2.studio.events;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.gui2.framework.core.Disposable;

/**
 * Группа подписок с общим dispose().
 *
 * 0.3.1: компонент держит группу вместо разрозненных Subscription.
 * На dispose() группа отписывает всё разом — не нужно помнить, какие
 * подписки были созданы.
 */
public final class SubscriptionGroup implements Disposable {

    private final List<StudioEventBus.Subscription> subs = new ArrayList<>(4);
    private boolean disposed = false;

    public void add(StudioEventBus.Subscription sub) {
        if (sub == null) return;
        if (disposed) {
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
            try { sub.unsubscribe(); }
            catch (Throwable ignored) { }
        }
        subs.clear();
    }
}