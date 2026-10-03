package com.fixmer.mared.gui2.docking;

import java.util.Collection;
import java.util.Optional;

import com.fixmer.mared.Mared;

/**
 * Главный менеджер Dock системы MaRed.
 *
 * 0.3.0: register() логирует ошибки вместо молчаливого ignore.
 * 0.3.2: captureState()/applyState() — полный снимок состояния
 * (ratios + activeTab + collapsed) для persist.
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
            Mared.LOGGER.warn("[docking] register: null panel for position={}",
                position);
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

    // ============================================================
    //  Collapse (0.3.2)
    // ============================================================

    public boolean isCollapsed(DockPosition position) {
        DockNode node = layout.node(position);
        return node != null && node.isCollapsed();
    }

    public void setCollapsed(DockPosition position, boolean value) {
        DockNode node = layout.node(position);
        if (node != null) node.setCollapsed(value);
    }

    public void toggleCollapsed(DockPosition position) {
        DockNode node = layout.node(position);
        if (node != null && node.isCollapsible()) {
            node.toggleCollapsed();
        }
    }

    // ============================================================
    //  State (0.3.2)
    // ============================================================

    public DockState captureState() {
        return DockState.capture(this);
    }

    /**
     * Применить ratios + collapsed из DockState.
     * activeTab применяется отдельно, после регистрации панелей —
     * вызывайте applyActiveTabs() вручную после bootstrap.
     */
    public void applyState(DockState state) {
        if (state == null) return;
        DockState.applyRatios(layout, state);
        DockState.applyCollapsed(layout, state);
    }

    public void applyActiveTabs(DockState state) {
        if (state == null) return;
        DockState.applyActiveTabs(this, state);
    }
}