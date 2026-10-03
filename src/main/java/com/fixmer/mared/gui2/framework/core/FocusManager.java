package com.fixmer.mared.gui2.framework.core;

/**
 * Единый владелец фокуса на уровне Screen.
 *
 * 0.3.1: решает проблему аудита #8 — три компонента могли одновременно
 * иметь focused=true, потому что каждый хранил свой boolean.
 *
 * Один Screen = один FocusManager = максимум один owner.
 *
 * Компоненты не хранят focused как поле. Они спрашивают владельца:
 *   isFocused() → focusManager.isFocused(this).
 *
 * Привязка к дереву компонентов:
 *   focusManager.attachTo(rootComponent);
 * Рекурсивно раздаёт ссылку через MaredContainer.children().
 *
 * Хуки:
 *   onFocusGained() / onFocusLost() на MaredComponent —
 *   вызываются ровно один раз при смене владельца.
 */
public final class FocusManager {

    private MaredComponent owner;

    /** Запросить фокус для компонента. */
    public void request(MaredComponent c) {
        if (c == null) return;
        if (owner == c) return;

        MaredComponent prev = owner;
        owner = c;

        if (prev != null) {
            try { prev.onFocusLost(); }
            catch (Throwable ignored) { /* не роняем управление фокусом */ }
        }
        try { c.onFocusGained(); }
        catch (Throwable ignored) { }
    }

    /** Снять фокус, если владелец — c. Идемпотентно. */
    public void clear(MaredComponent c) {
        if (owner == null || owner != c) return;
        owner = null;
        try { c.onFocusLost(); }
        catch (Throwable ignored) { }
    }

    /** Снять фокус с любого владельца. */
    public void clear() {
        MaredComponent prev = owner;
        if (prev == null) return;
        owner = null;
        try { prev.onFocusLost(); }
        catch (Throwable ignored) { }
    }

    public boolean isFocused(MaredComponent c) {
        return c != null && owner == c;
    }

    public MaredComponent owner() { return owner; }

    public boolean hasOwner() { return owner != null; }

    /**
     * Привязать менеджер ко всему дереву компонентов.
     * Обходит MaredContainer рекурсивно; MaredComponent без детей
     * получает ссылку и может звать requestFocus().
     */
    public void attachTo(MaredComponent root) {
        if (root == null) return;
        root.attachFocusManager(this);

        if (root instanceof MaredContainer container) {
            for (MaredComponent child : container.children()) {
                attachTo(child);
            }
        }
    }
}