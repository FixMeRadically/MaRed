package com.fixmer.mared.gui2.docking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fixmer.mared.gui2.docking.layout.DockBounds;

/**
 * Узел Dock-дерева MaRed.
 *
 * 0.3.1 (audit #30/#31):
 *   Введён activeIndex. Раньше при нескольких panels в одном узле
 *   renderer рисовал все сразу в одних bounds (панели накладывались
 *   друг на друга), а input шёл всем.
 *
 *   Теперь: узел хранит активную панель, renderer рисует только её,
 *   input уходит только ей. Tab bar показывает остальные панели,
 *   клик по табу переключает activeIndex.
 *
 *   Семантика:
 *     - panels пустой  → activeIndex = 0 (невалидно, рендер пропустится);
 *     - panels.size==1 → tabs не показываются, активна единственная;
 *     - panels.size>1  → активна ровно одна, индекс в [0, size).
 */
public final class DockNode {

    private final DockPosition position;

    private final List<DockPanel> panels = new ArrayList<>();

    /** Индекс активной панели в panels. Валиден, если panels непустой. */
    private int activeIndex = 0;

    /**
     * Текущая геометрия области.
     */
    private DockBounds bounds = new DockBounds(0, 0, 0, 0);

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
        // Первый panel становится активным.
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
        // Активный удалён или вышел за границы — прижимаем к последнему.
        if (activeIndex >= panels.size()) {
            activeIndex = panels.size() - 1;
        }
    }

    public boolean hasPanels() {
        return !panels.isEmpty();
    }

    /**
     * Только для чтения. Мутации — через add()/remove().
     */
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
            // self-healing
            activeIndex = 0;
        }
        return activeIndex;
    }

    public DockPanel activePanel() {
        if (panels.isEmpty()) return null;
        int idx = activeIndex();
        return panels.get(idx);
    }

    /**
     * Активировать панель по индексу. Идемпотентно.
     * @return true, если панель была активирована (индекс валиден).
     */
    public boolean activate(int index) {
        if (panels.isEmpty()) return false;
        if (index < 0 || index >= panels.size()) return false;
        if (index == activeIndex) return false;
        activeIndex = index;
        return true;
    }

    /**
     * Активировать конкретную панель.
     * @return true, если панель была найдена и стала активной.
     */
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
}