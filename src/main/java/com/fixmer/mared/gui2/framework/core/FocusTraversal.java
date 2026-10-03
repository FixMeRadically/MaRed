package com.fixmer.mared.gui2.framework.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Tab-traversal по focusable компонентам одного Screen.
 *
 * 0.3.2 (accessibility):
 *   Раньше фокус ставился только по клику мыши — Tab не работал.
 *   FocusTraversal хранит упорядоченный список целей, поддерживает
 *   Tab (вперёд) и Shift+Tab (назад), при переключении озвучивает
 *   новую цель через Minecraft narrator.
 *
 * Регистрация:
 *   - StudioSession создаёт traversal и передаёт в controller;
 *   - controller рекурсивно обходит дерево компонентов панели и
 *     регистрирует те, что focusable().
 *
 * Порядок: порядок регистрации. Для одинакового детерминизма —
 * панели регистрируются в порядке создания dock-узлов.
 *
 * Не потокобезопасен — всё с main thread.
 */
public final class FocusTraversal {

    private final List<MaredComponent> targets = new ArrayList<>(8);
    private FocusManager focusManager;

    public FocusTraversal() {}

    public void setFocusManager(FocusManager fm) {
        this.focusManager = fm;
    }

    public void register(MaredComponent c) {
        if (c == null) return;
        if (!c.focusable()) return;
        if (!targets.contains(c)) {
            targets.add(c);
        }
    }

    public void unregister(MaredComponent c) {
        targets.remove(c);
    }

    public void clear() {
        targets.clear();
    }

    public int size() { return targets.size(); }

    /**
     * Циклическая навигация.
     * @param direction +1 — вперёд (Tab), -1 — назад (Shift+Tab).
     * @return true, если фокус был передан (list непуст).
     */
    public boolean handleTab(int direction) {
        if (direction == 0) direction = 1;

        List<MaredComponent> visible = new ArrayList<>(targets.size());
        for (MaredComponent c : targets) {
            if (c.isVisible() && c.isEnabled()) visible.add(c);
        }
        if (visible.isEmpty()) return false;

        MaredComponent owner = focusManager != null ? focusManager.owner() : null;
        int currentIdx = (owner != null) ? visible.indexOf(owner) : -1;

        int nextIdx;
        if (currentIdx < 0) {
            nextIdx = (direction > 0) ? 0 : visible.size() - 1;
        } else {
            int n = visible.size();
            nextIdx = ((currentIdx + direction) % n + n) % n;
        }

        MaredComponent next = visible.get(nextIdx);
        next.requestFocus();
        narrate(next);
        return true;
    }

    /**
     * Перевести фокус на первый target. Используется при открытии
     * Screen'а — чтобы у пользователя был валидный начальный фокус.
     * Без narration.
     */
    public boolean focusInitial() {
        for (MaredComponent c : targets) {
            if (c.isVisible() && c.isEnabled()) {
                c.requestFocus();
                return true;
            }
        }
        return false;
    }

    // ============================================================
    //  Narration
    // ============================================================

    /**
     * Озвучить компонент. Безопасно вызывается даже если narrator
     * выключен — просто ничего не произойдёт.
     */
    public static void narrate(MaredComponent c) {
        if (!(c instanceof NarratableComponent n)) return;
        try {
            Component text = n.narrationText();
            if (text == null) return;
            if (text.getString().isEmpty()) return;
            Minecraft.getInstance().getNarrator().sayNow(text);
        } catch (Throwable ignored) {
            // narration не критичен для работы GUI.
        }
    }
}