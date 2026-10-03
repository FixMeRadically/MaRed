package com.fixmer.mared.gui2.studio.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.Mared;

/**
 * Шина событий Studio.
 *
 * 0.3.1:
 *   - Subscription.unsubscribe() реально удаляет подписку из списка
 *     (не только флаг). Раньше мёртвые подписки накапливались в
 *     HashMap'е до bus.clear().
 *   - Публикация идёт по immutable-снимку списка: можно отписываться
 *     из обработчика без ConcurrentModificationException.
 *   - Список подписок держится в отсортированном по приоритету виде —
 *     сортировка не запускается на каждый publish.
 *   - Старый overload unsubscribe(type, listener) удалён. Единственный
 *     корректный путь — держать ссылку на Subscription и вызвать
 *     у него unsubscribe().
 *
 * Не потокобезопасная: все публикации идут из render/tick на main
 * thread. Если понадобится cross-thread — заменить HashMap на
 * ConcurrentHashMap + CopyOnWriteArrayList.
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

    /**
     * 0.3.1: Subscription держит ссылку на owner-bus и на конкретный
     * type. unsubscribe() удаляет подписку из списка владельца.
     * Идемпотентно.
     */
    public final class Subscription {
        private final Class<?> type;
        private final Listener<?> listener;
        private final StudioPriority priority;
        private boolean active = true;

        Subscription(Class<?> type, Listener<?> listener, StudioPriority priority) {
            this.type = type;
            this.listener = listener;
            this.priority = priority;
        }

        public void unsubscribe() {
            if (!active) return;
            active = false;
            removeSubscription(this);
        }

        public boolean isActive() { return active; }

        Class<?> type() { return type; }
        Listener<?> listener() { return listener; }
        StudioPriority priority() { return priority; }
    }

    // ============================================================
    //  Storage
    // ============================================================

    /**
     * Список всегда отсортирован по приоритету (HIGH → LOW).
     * При добавлении подписки вставляем в правильное место —
     * порядок регистрации сохраняется для одного приоритета.
     */
    private final Map<Class<?>, List<Subscription>> subs = new HashMap<>();

    /**
     * Сортировка по ordinal: HIGH=0, NORMAL=1, LOW=2.
     * Стабильная — для одного приоритета сохраняется порядок вставки.
     */
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
        List<Subscription> list = subs.computeIfAbsent(
            type, k -> new ArrayList<>(4));

        insertSorted(list, sub);
        return sub;
    }

    /**
     * Вставка с сохранением порядка по приоритету.
     * Список маленький (обычно < 10 подписок на тип) — линейный поиск
     * дешевле любой сортировки.
     */
    private static void insertSorted(List<Subscription> list, Subscription sub) {
        int n = list.size();
        int insertAt = n;
        int subOrd = sub.priority.ordinal();
        for (int i = 0; i < n; i++) {
            if (list.get(i).priority.ordinal() > subOrd) {
                insertAt = i;
                break;
            }
        }
        list.add(insertAt, sub);
    }

    /**
     * 0.3.1: единственный внутренний путь удаления. Вызывается из
     * Subscription.unsubscribe().
     */
    private void removeSubscription(Subscription sub) {
        List<Subscription> list = subs.get(sub.type());
        if (list == null) return;
        list.remove(sub);
        if (list.isEmpty()) subs.remove(sub.type());
    }

    // ============================================================
    //  Публикация
    // ============================================================

    @SuppressWarnings("unchecked")
    public <T extends StudioEvent> void publish(T event) {
        if (event == null) return;

        List<Subscription> list = subs.get(event.getClass());
        if (list == null || list.isEmpty()) return;

        // 0.3.1: снимок списка — обработчик может отписаться во время
        // итерации. Сам список уже отсортирован, копия — только для
        // защиты от ConcurrentModification.
        List<Subscription> snapshot = new ArrayList<>(list);

        int n = snapshot.size();
        for (int i = 0; i < n; i++) {
            Subscription sub = snapshot.get(i);
            if (!sub.isActive()) continue;
            try {
                ((Listener<T>) sub.listener()).onEvent(event);
            } catch (Throwable t) {
                Mared.LOGGER.error(
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

    public int totalSubscriberCount() {
        int n = 0;
        for (List<Subscription> list : subs.values()) n += list.size();
        return n;
    }

    public void clear() {
        subs.clear();
    }
}