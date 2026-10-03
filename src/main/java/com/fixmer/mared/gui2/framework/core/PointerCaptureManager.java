package com.fixmer.mared.gui2.framework.core;

/**
 * Единый владелец pointer-захвата на уровне Screen.
 *
 * 0.3.1 (audit #9):
 *   Раньше при drag (scrollbar, selection, splitter) mouseDragged
 *   рассылался всем панелям. Компонент, начавший drag, "терял"
 *   продолжение, если курсор уходил за его границы.
 *
 * Теперь:
 *   1. Компонент, начавший drag, вызывает capturePointer(button).
 *   2. До mouseReleased все mouseDragged идут ТОЛЬКО этому компоненту,
 *      независимо от позиции мыши.
 *   3. mouseReleased автоматически освобождает захват.
 *
 * Один Screen = один PointerCaptureManager.
 *
 * Нужен для:
 *   - scrollbar drag (editor, log, любые списки);
 *   - selection rectangle (editor selection, будущий marquee);
 *   - splitter drag (dock divider — уже есть в DockRenderer, но
 *     логика пойдёт через тот же manager в будущем);
 *   - slider drag;
 *   - gizmo / docking drag tabs.
 */
public final class PointerCaptureManager {

    private MaredComponent owner;
    private int button = -1;

    /** Захватить pointer для кнопки button. Перезаписывает предыдущий. */
    public void capture(MaredComponent c, int button) {
        if (c == null) return;
        this.owner = c;
        this.button = button;
    }

    /** Освободить pointer, если владелец — c. Идемпотентно. */
    public void release(MaredComponent c) {
        if (owner == null || owner != c) return;
        owner = null;
        button = -1;
    }

    /** Освободить pointer с любого владельца (после mouseReleased). */
    public void releaseAll() {
        owner = null;
        button = -1;
    }

    public boolean isCaptured() {
        return owner != null;
    }

    public boolean isCapturedBy(MaredComponent c) {
        return owner == c;
    }

    public boolean isCapturedForButton(int b) {
        return owner != null && button == b;
    }

    public MaredComponent owner() {
        return owner;
    }

    public int button() {
        return button;
    }

    /**
     * Привязать менеджер ко всему дереву компонентов.
     * Аналогично FocusManager.attachTo.
     */
    public void attachTo(MaredComponent root) {
        if (root == null) return;
        root.attachPointerManager(this);

        if (root instanceof MaredContainer container) {
            for (MaredComponent child : container.children()) {
                attachTo(child);
            }
        }
    }
}