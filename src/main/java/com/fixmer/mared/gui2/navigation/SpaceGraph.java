package com.fixmer.mared.gui2.navigation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Граф пространств MaRed.
 *
 * Знает:
 *   - какие пространства существуют (id → supplier);
 *   - какие переходы допустимы (from → set<to>);
 *   - какой TransitionKind использовать для каждого перехода.
 *
 * Не инстанцирует пространства сам — только хранит supplier'ы и
 * предоставляет API «дай мне инстанс для перехода из X в Y».
 *
 * Зачем граф, а не enum switch:
 *   через год плагин-мод добавит MarketplaceSpace одной строкой:
 *       graph.register(MARKETPLACE, MarketplaceSpace::new);
 *       graph.connect(CONTENT, MARKETPLACE, TransitionKind.ZOOM_THROUGH);
 *   и никакой код навигации не меняется.
 */
public final class SpaceGraph {

    private final Map<SpaceId, Supplier<MaredSpace>> factories =
        new EnumMap<>(SpaceId.class);

    /** from → set of allowed to. Пустая карта = все рёбра разрешены. */
    private final Map<SpaceId, Set<SpaceId>> edges =
        new EnumMap<>(SpaceId.class);

    /** from + to → kind. Если нет — берётся DEFAULT. */
    private final Map<SpaceId, Map<SpaceId, TransitionKind>> kinds =
        new EnumMap<>(SpaceId.class);

    // ---------------------------------------------------------
    //  Регистрация
    // ---------------------------------------------------------

    public void register(SpaceId id, Supplier<MaredSpace> factory) {
        if (id == null || factory == null) return;
        factories.put(id, factory);
    }

    public void connect(SpaceId from, SpaceId to, TransitionKind kind) {
        if (from == null || to == null) return;
        edges.computeIfAbsent(from, k -> EnumSet.noneOf(SpaceId.class)).add(to);
        kinds.computeIfAbsent(from, k -> new EnumMap<>(SpaceId.class))
             .put(to, kind == null ? TransitionKind.DEFAULT : kind);
    }

    public void connect(SpaceId from, SpaceId to) {
        connect(from, to, TransitionKind.DEFAULT);
    }

    // ---------------------------------------------------------
    //  Запросы
    // ---------------------------------------------------------

    public boolean has(SpaceId id) {
        return id != null && factories.containsKey(id);
    }

    public boolean canGo(SpaceId from, SpaceId to) {
        if (to == null) return false;
        if (!factories.containsKey(to)) return false;
        if (edges.isEmpty()) return true;
        if (from == null) return true;
        Set<SpaceId> allowed = edges.get(from);
        return allowed == null || allowed.contains(to);
    }

    public TransitionKind kindFor(SpaceId from, SpaceId to) {
        if (from == null || to == null) return TransitionKind.DEFAULT;
        Map<SpaceId, TransitionKind> m = kinds.get(from);
        if (m == null) return TransitionKind.DEFAULT;
        TransitionKind k = m.get(to);
        return k != null ? k : TransitionKind.DEFAULT;
    }

    /**
     * Инстанцировать пространство. Каждый вызов — новый инстанс:
     * навигация владеет стеком, а не граф.
     */
    public MaredSpace instantiate(SpaceId id) {
        Supplier<MaredSpace> f = factories.get(id);
        if (f == null) return null;
        return f.get();
    }

    public Set<SpaceId> neighbours(SpaceId from) {
        if (from == null) return Collections.emptySet();
        Set<SpaceId> s = edges.get(from);
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
    }
}