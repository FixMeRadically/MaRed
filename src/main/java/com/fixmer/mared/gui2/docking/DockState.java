package com.fixmer.mared.gui2.docking;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * DTO состояния docking-системы.
 *
 * 0.3.2:
 *   ratios + visibility + activeTab + collapsed.
 *
 * Формат — простой POJO, сериализуется в JSON через DockStateStorage.
 * Не потокобезопасен: снимок делается на main thread.
 */
public final class DockState {

    public final Map<DockPosition, Float> ratios =
        new EnumMap<>(DockPosition.class);

    public final Map<String, Boolean> visibility = new HashMap<>(8);

    public final Map<DockPosition, String> activeTab =
        new EnumMap<>(DockPosition.class);

    public final Map<DockPosition, Boolean> collapsed =
        new EnumMap<>(DockPosition.class);

    public DockState() {}

    // ============================================================
    //  Ratios
    // ============================================================

    public float ratio(DockPosition pos, float def) {
        Float v = ratios.get(pos);
        return v != null ? v : def;
    }

    public void setRatio(DockPosition pos, float value) {
        ratios.put(pos, value);
    }

    // ============================================================
    //  Visibility
    // ============================================================

    public boolean isVisible(String panelId, boolean def) {
        Boolean v = visibility.get(panelId);
        return v != null ? v : def;
    }

    public void setVisible(String panelId, boolean visible) {
        visibility.put(panelId, visible);
    }

    // ============================================================
    //  Active tab
    // ============================================================

    public String activeTab(DockPosition pos) {
        return activeTab.get(pos);
    }

    public void setActiveTab(DockPosition pos, String panelId) {
        if (panelId == null) activeTab.remove(pos);
        else activeTab.put(pos, panelId);
    }

    // ============================================================
    //  Collapsed
    // ============================================================

    public boolean isCollapsed(DockPosition pos, boolean def) {
        Boolean v = collapsed.get(pos);
        return v != null ? v : def;
    }

    public void setCollapsed(DockPosition pos, boolean value) {
        collapsed.put(pos, value);
    }

    // ============================================================
    //  Snapshot / restore
    // ============================================================

    /**
     * Сделать снимок layout'а в DockState.
     * Panel visibility берётся из PanelRegistry по gate'ам — «видима»
     * значит gate сейчас true.
     */
    public static DockState capture(DockManager manager) {
        DockState state = new DockState();
        if (manager == null) return state;

        DockLayout layout = manager.layout();

        for (DockPosition pos : new DockPosition[]{
            DockPosition.LEFT, DockPosition.RIGHT, DockPosition.BOTTOM}) {
            state.setRatio(pos, layout.ratio(pos));
        }

        for (DockNode node : manager.nodes()) {
            DockPosition pos = node.position();

            DockPanel active = node.activePanel();
            if (active != null) {
                state.setActiveTab(pos, active.id());
            }

            // 0.3.2: сохраняем collapse для всех позиций — включая
            // TOP/CENTER, чтобы формат был единообразным.
            state.setCollapsed(pos, node.isCollapsed());

            for (DockPanel panel : node.panels()) {
                state.setVisible(panel.id(), true);
            }
        }

        return state;
    }

    /**
     * Применить ratios. activeTab/collapsed — отдельными методами
     * после регистрации панелей.
     */
    public static void applyRatios(DockLayout layout, DockState state) {
        if (layout == null || state == null) return;

        for (Map.Entry<DockPosition, Float> e : state.ratios.entrySet()) {
            layout.setRatio(e.getKey(), e.getValue());
        }
    }

    /**
     * 0.3.2: применить collapsed к узлам. Узлы уже созданы
     * (пустые EnumMap в DockLayout), поэтому collapse применяется
     * до регистрации панелей.
     */
    public static void applyCollapsed(DockLayout layout, DockState state) {
        if (layout == null || state == null) return;

        for (Map.Entry<DockPosition, Boolean> e : state.collapsed.entrySet()) {
            DockNode node = layout.node(e.getKey());
            if (node != null) {
                node.setCollapsed(Boolean.TRUE.equals(e.getValue()));
            }
        }
    }

    /**
     * Применить activeTab к существующим узлам.
     * Вызывается ПОСЛЕ регистрации панелей.
     */
    public static void applyActiveTabs(DockManager manager, DockState state) {
        if (manager == null || state == null) return;

        for (DockNode node : manager.nodes()) {
            String activeId = state.activeTab(node.position());
            if (activeId == null) continue;

            for (DockPanel panel : node.panels()) {
                if (activeId.equals(panel.id())) {
                    node.activate(panel);
                    break;
                }
            }
        }
    }

    // ============================================================
    //  Diagnostics
    // ============================================================

    public Map<DockPosition, Float> ratiosView() {
        return Collections.unmodifiableMap(ratios);
    }

    public Map<String, Boolean> visibilityView() {
        return Collections.unmodifiableMap(visibility);
    }
}