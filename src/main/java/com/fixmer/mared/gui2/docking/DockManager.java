package com.fixmer.mared.gui2.docking;

import java.util.Collection;
import java.util.Optional;

import com.fixmer.mared.Mared;

/**
 * Главный менеджер Dock системы MaRed.
 *
 * 0.3.0: register() логирует ошибки вместо молчаливого ignore.
 */
public final class DockManager {

    private final DockLayout layout = new DockLayout();

    // ============================================================
    //  Панели
    // ============================================================

    public void register(DockPosition position, DockPanel panel) {
        if (position == null) {
            Mared.LOGGER.warn("[docking] register: null position, panel={}",
                panel == null ? "null" : panel.id());
            return;
        }
        if (panel == null) {
            Mared.LOGGER.warn("[docking] register: null panel for position={}", position);
            return;
        }
        DockNode node = layout.node(position);
        if (node != null) node.add(panel);
    }

    public void unregister(DockPanel panel) {
        if (panel == null) return;
        for (DockNode node : layout.values()) {
            node.remove(panel);
        }
    }

    public Optional<DockPanel> find(String id) {
        if (id == null) return Optional.empty();
        return layout.values().stream()
            .flatMap(node -> node.panels().stream())
            .filter(panel -> panel.id().equals(id))
            .findFirst();
    }

    public boolean contains(String id) {
        return find(id).isPresent();
    }

    // ============================================================
    //  Nodes
    // ============================================================

    public Collection<DockNode> nodes() {
        return layout.values();
    }

    public DockLayout layout() {
        return layout;
    }

    // ============================================================
    //  Ratios
    // ============================================================

    public float ratio(DockPosition position) {
        return layout.ratio(position);
    }

    public void setRatio(DockPosition position, float value) {
        layout.setRatio(position, value);
    }
}