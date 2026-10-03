package com.fixmer.mared.gui2.framework.overlay;

/**
 * Слой overlay'ев.
 *
 * Rendering порядок: от меньшего z к большему (bottom → top).
 * Dispatch: от большего z к меньшему (top → bottom).
 *
 * MODAL — модальные диалоги (input barrier, блокируют всё).
 * POPUP — контекстные меню (клик вне закрывает).
 * TOAST — уведомления (не блокируют).
 */
public enum OverlayLayer {
    TOAST(0),
    MODAL(1),
    POPUP(2);

    private final int z;

    OverlayLayer(int z) { this.z = z; }

    public int z() { return z; }
}