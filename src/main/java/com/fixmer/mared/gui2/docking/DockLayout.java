package com.fixmer.mared.gui2.docking;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Схема расположения Dock интерфейса.
 *
 * 0.3.0:
 *   - Ratios для LEFT/RIGHT/BOTTOM (доли от размера экрана).
 *   - DockConstraints per-позиция — min/max в пикселях.
 *   - nodes() возвращает immutable-вью.
 */
public class DockLayout {

    /** Fallback-границы ratio: применяются, если у позиции нет constraints. */
    private static final float RATIO_MIN = 0.05f;
    private static final float RATIO_MAX = 0.60f;

    private final Map<DockPosition, DockNode> nodes = new EnumMap<>(DockPosition.class);
    private final Map<DockPosition, Float> ratios = new EnumMap<>(DockPosition.class);
    private final Map<DockPosition, DockConstraints> constraints = new EnumMap<>(DockPosition.class);

    public DockLayout() {
        for (DockPosition position : DockPosition.values()) {
            nodes.put(position, new DockNode(position));
        }

        // Дефолты — как в текущей версии, но теперь per-позиция.
        constraints.put(DockPosition.LEFT,   DockConstraints.resizable(140, 500));
        constraints.put(DockPosition.RIGHT,  DockConstraints.resizable(200, 600));
        constraints.put(DockPosition.BOTTOM, DockConstraints.resizable(100, 420));
        constraints.put(DockPosition.TOP,    DockConstraints.fixed(26));
        constraints.put(DockPosition.CENTER, DockConstraints.resizable(80, Integer.MAX_VALUE));

        ratios.put(DockPosition.LEFT,   defaultRatio(DockPosition.LEFT));
        ratios.put(DockPosition.RIGHT,  defaultRatio(DockPosition.RIGHT));
        ratios.put(DockPosition.BOTTOM, defaultRatio(DockPosition.BOTTOM));
    }

    // ============================================================
    //  Nodes
    // ============================================================

    public DockNode node(DockPosition position) {
        return nodes.get(position);
    }

    /** Immutable-вью — нельзя мутировать коллекцию извне. */
    public Collection<DockNode> values() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    // ============================================================
    //  Ratios
    // ============================================================

    public float ratio(DockPosition position) {
        return ratios.getOrDefault(position, defaultRatio(position));
    }

    public void setRatio(DockPosition position, float value) {
        if (position == null) return;
        ratios.put(position, clampRatio(value));
    }

    /** Immutable-копия для сохранения. */
    public Map<DockPosition, Float> ratios() {
        return new EnumMap<>(ratios);
    }

    // ============================================================
    //  Constraints
    // ============================================================

    public DockConstraints constraints(DockPosition position) {
        DockConstraints c = constraints.get(position);
        if (c != null) return c;
        // На всякий случай — если позиция не была инициализирована.
        return DockConstraints.resizable(80, Integer.MAX_VALUE);
    }

    public void setConstraints(DockPosition position, DockConstraints c) {
        if (position == null || c == null) return;
        constraints.put(position, c);
    }

    // ============================================================
    //  Defaults
    // ============================================================

    private static float defaultRatio(DockPosition p) {
        return switch (p) {
            case LEFT   -> 0.20f;
            case RIGHT  -> 0.18f;
            case BOTTOM -> 0.15f;
            default     -> 0f;
        };
    }

    private static float clampRatio(float v) {
        if (v < RATIO_MIN) return RATIO_MIN;
        if (v > RATIO_MAX) return RATIO_MAX;
        return v;
    }
}