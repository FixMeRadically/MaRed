package com.fixmer.mared.gui2.docking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fixmer.mared.gui2.docking.layout.DockBounds;

/**
 * Узел Dock-дерева MaRed.
 *
 * 0.3.1 (audit #30/#31):
 *   activeIndex — единственная активная панель в узле.
 *
 * 0.3.2 (collapse):
 *   collapsed — если true, узел занимает минимум места (28 px для
 *   LEFT/RIGHT, 22 px для BOTTOM), рендерится только header со
 *   стрелкой. Состояние сохраняется в DockState.collapsed.
 *
 *   TOP и CENTER не сворачиваются — TOP сам по себе header-only,
 *   CENTER — основная рабочая область.
 */
public final class DockNode {

    private final DockPosition position;
    private final List<DockPanel> panels = new ArrayList<>();
    private int activeIndex = 0;
    private DockBounds bounds = new DockBounds(0, 0, 0, 0);

    // 0.3.2: collapse state.
    private boolean collapsed = false;

    public DockNode(DockPosition position) {
        this.position = position;
    }

    public DockPosition position() {
        return position;
    }

    // ============================================================
    //  Панели
    // ============================================================

    public void add(DockPanel panel) {
        if (panel == null) return;
        if (panels.contains(panel)) return;

        panels.add(panel);
        if (panels.size() == 1) activeIndex = 0;
    }

    public void remove(DockPanel panel) {
        int removed = panels.indexOf(panel);
        if (removed < 0) return;

        panels.remove(removed);

        if (panels.isEmpty()) {
            activeIndex = 0;
            return;
        }
        if (activeIndex >= panels.size()) {
            activeIndex = panels.size() - 1;
        }
    }

    public boolean hasPanels() {
        return !panels.isEmpty();
    }

    public List<DockPanel> panels() {
        return Collections.unmodifiableList(panels);
    }

    public int panelCount() {
        return panels.size();
    }

    // ============================================================
    //  Активная панель
    // ============================================================

    public int activeIndex() {
        if (panels.isEmpty()) return -1;
        if (activeIndex < 0 || activeIndex >= panels.size()) {
            activeIndex = 0;
        }
        return activeIndex;
    }

    public DockPanel activePanel() {
        if (panels.isEmpty()) return null;
        int idx = activeIndex();
        return panels.get(idx);
    }

    public boolean activate(int index) {
        if (panels.isEmpty()) return false;
        if (index < 0 || index >= panels.size()) return false;
        if (index == activeIndex) return false;
        activeIndex = index;
        return true;
    }

    public boolean activate(DockPanel panel) {
        int idx = panels.indexOf(panel);
        if (idx < 0) return false;
        return activate(idx);
    }

    // ============================================================
    //  Геометрия
    // ============================================================

    public DockBounds bounds() {
        return bounds;
    }

    public void setBounds(DockBounds bounds) {
        if (bounds == null) return;
        this.bounds = bounds;
    }

    // ============================================================
    //  Collapse (0.3.2)
    // ============================================================

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean value) {
        this.collapsed = value;
    }

    /** @return новое состояние (true — свёрнут). */
    public boolean toggleCollapsed() {
        this.collapsed = !this.collapsed;
        return this.collapsed;
    }

    /** Может ли узел быть свёрнут. TOP/CENTER — нет. */
    public boolean isCollapsible() {
        return position == DockPosition.LEFT
            || position == DockPosition.RIGHT
            || position == DockPosition.BOTTOM;
    }
}