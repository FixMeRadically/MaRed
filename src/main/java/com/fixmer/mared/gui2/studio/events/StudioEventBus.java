package com.fixmer.mared.gui2.studio.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Шина событий Studio.
 *
 * 0.3.1:
 *   - Приоритеты (HIGH/NORMAL/LOW) — детерминированный порядок вызова.
 *   - Subscription.active теперь честно проверяется при публикации.
 *   - Быстрая копия списка перед обходом — можно отписываться
 *     из обработчика.
 *
 * Не потокобезопасная: все публикации идут из render/tick на main thread.
 */
public final class StudioEventBus {

    // ============================================================
    //  Listener
    // ============================================================

    @FunctionalInterface
    public interface Listener<T extends StudioEvent> {
        void onEvent(T event);
    }

    // ============================================================
    //  Subscription
    // ============================================================

    public static final class Subscription {
        private final Class<?> type;
        private final Listener<?> listener;
        private final StudioPriority priority;
        private volatile boolean active = true;

        Subscription(Class<?> type, Listener<?> listener, StudioPriority priority) {
            this.type = type;
            this.listener = listener;
            this.priority = priority;
        }

        public void unsubscribe() {
            active = false;
        }

        public boolean isActive() {
            return active;
        }

        Class<?> type() { return type; }
        Listener<?> listener() { return listener; }
        StudioPriority priority() { return priority; }
    }

    // ============================================================
    //  Storage
    // ============================================================

    /** Порядок в списке = порядок регистрации. Сортировка — перед вызовом. */
    private final Map<Class<?>, List<Subscription>> subs = new HashMap<>();

    private static final Comparator<Subscription> BY_PRIORITY =
        Comparator.comparingInt(s -> s.priority.ordinal());

    // ============================================================
    //  Подписка
    // ============================================================

    public <T extends StudioEvent> Subscription subscribe(Class<T> type,
                                                          Listener<T> listener) {
        return subscribe(type, listener, StudioPriority.NORMAL);
    }

    public <T extends StudioEvent> Subscription subscribe(Class<T> type,
                                                          Listener<T> listener,
                                                          StudioPriority priority) {
        if (type == null || listener == null) {
            throw new IllegalArgumentException("type and listener required");
        }
        if (priority == null) priority = StudioPriority.NORMAL;

        Subscription sub = new Subscription(type, listener, priority);
        subs.computeIfAbsent(type, k -> new ArrayList<>(4)).add(sub);
        return sub;
    }

    public void unsubscribe(Class<?> type, Listener<?> listener) {
        List<Subscription> list = subs.get(type);
        if (list == null) return;
        list.removeIf(s -> s.listener() == listener);
        if (list.isEmpty()) subs.remove(type);
    }

    // ============================================================
    //  Публикация
    // ============================================================

    @SuppressWarnings("unchecked")
    public <T extends StudioEvent> void publish(T event) {
        if (event == null) return;

        List<Subscription> list = subs.get(event.getClass());
        if (list == null || list.isEmpty()) return;

        // Сортированная копия — порядок не зависит от порядка регистрации.
        List<Subscription> ordered = new ArrayList<>(list);
        ordered.sort(BY_PRIORITY);

        for (Subscription sub : ordered) {
            if (!sub.isActive()) continue;
            try {
                ((Listener<T>) sub.listener()).onEvent(event);
            } catch (Throwable t) {
                com.fixmer.mared.Mared.LOGGER.error(
                    "[studio] listener threw on {}",
                    event.getClass().getSimpleName(), t);
            }
        }
    }

    // ============================================================
    //  Диагностика
    // ============================================================

    public boolean hasSubscribers(Class<? extends StudioEvent> type) {
        List<Subscription> list = subs.get(type);
        return list != null && !list.isEmpty();
    }

    public int subscriberCount(Class<? extends StudioEvent> type) {
        List<Subscription> list = subs.get(type);
        return list == null ? 0 : list.size();
    }

    public void clear() {
        subs.clear();
    }
}