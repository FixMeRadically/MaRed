package com.fixmer.mared.gui2.framework.overlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Владелец overlay'ев на уровне Screen.
 *
 * 0.3.1: instance-based замена статическому MaredContextMenu.
 *
 * Render order: TOAST → MODAL → POPUP (по возрастанию z).
 * Dispatch order: POPUP → MODAL → TOAST (по убыванию z).
 *
 * POPUP-слой имеет особую семантику: клик вне верхнего POPUP'а
 * закрывает его.
 */
public final class OverlayManager {

    private static final Comparator<Overlay> BY_LAYER =
        Comparator.comparingInt(o -> o.layer().z());

    private final List<Overlay> overlays = new ArrayList<>(4);

    // ============================================================
    //  Управление
    // ============================================================

    public void push(Overlay overlay) {
        if (overlay == null) return;
        overlays.add(overlay);
        try { overlay.onOpen(); }
        catch (Throwable ignored) { }
    }

    public boolean remove(Overlay overlay) {
        if (overlay == null) return false;
        boolean removed = overlays.remove(overlay);
        if (removed) {
            try { overlay.onClose(); }
            catch (Throwable ignored) { }
        }
        return removed;
    }

    public void clearLayer(OverlayLayer layer) {
        List<Overlay> toRemove = new ArrayList<>(2);
        for (Overlay o : overlays) {
            if (o.layer() == layer) toRemove.add(o);
        }
        for (Overlay o : toRemove) remove(o);
    }

    public void clear() {
        List<Overlay> copy = new ArrayList<>(overlays);
        for (Overlay o : copy) {
            try { o.onClose(); }
            catch (Throwable ignored) { }
        }
        overlays.clear();
    }

    // ============================================================
    //  Диагностика
    // ============================================================

    public boolean isEmpty() { return overlays.isEmpty(); }

    public boolean hasLayer(OverlayLayer layer) {
        for (Overlay o : overlays) {
            if (o.layer() == layer) return true;
        }
        return false;
    }

    public int size() { return overlays.size(); }

    public boolean isInputCaptured() { return !overlays.isEmpty(); }

    public boolean hasBarrier() {
        for (Overlay o : overlays) {
            if (o.isInputBarrier()) return true;
        }
        return false;
    }

    /** Topmost overlay без учёта слоя. */
    public Overlay top() {
        if (overlays.isEmpty()) return null;
        Overlay best = null;
        int bestZ = Integer.MIN_VALUE;
        for (Overlay o : overlays) {
            int z = o.layer().z();
            if (z >= bestZ) { // >= чтобы взять последний добавленный
                best = o;
                bestZ = z;
            }
        }
        return best;
    }

    // ============================================================
    //  Render
    // ============================================================

    public void render(GuiGraphics g, Font font,
                       int screenW, int screenH,
                       int mouseX, int mouseY) {
        if (overlays.isEmpty()) return;

        List<Overlay> ordered = new ArrayList<>(overlays);
        ordered.sort(BY_LAYER);

        for (Overlay o : ordered) {
            try {
                o.render(g, font, screenW, screenH, mouseX, mouseY);
            } catch (Throwable ignored) { }
        }
    }

    // ============================================================
    //  Input dispatch
    // ============================================================

    /**
     * @return true, если событие поглощено (handled или barrier).
     * Клик вне верхнего POPUP'а (без барьера) закрывает его.
     */
    public boolean mouseClicked(double mx, double my, int button) {
        if (overlays.isEmpty()) return false;

        List<Overlay> ordered = sortedDescending();

        for (Overlay o : ordered) {
            boolean handled = o.mouseClicked(mx, my, button);
            if (handled) return true;

            // Если это POPUP и клик был вне — закрыть.
            if (o.layer() == OverlayLayer.POPUP) {
                remove(o);
                return true;
            }

            if (o.isInputBarrier()) return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (overlays.isEmpty()) return false;

        List<Overlay> ordered = sortedDescending();

        // Escape закрывает верхний overlay с closeOnEscape().
        if (keyCode == 256) {
            for (Overlay o : ordered) {
                if (o.closeOnEscape()) {
                    remove(o);
                    return true;
                }
            }
        }

        for (Overlay o : ordered) {
            if (o.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (o.isInputBarrier()) return true;
        }
        return false;
    }

    public boolean shouldBlockGenericInput() {
        return hasBarrier();
    }

    // ============================================================
    //  Internals
    // ============================================================

    private List<Overlay> sortedDescending() {
        List<Overlay> ordered = new ArrayList<>(overlays);
        ordered.sort(BY_LAYER.reversed());
        return ordered;
    }
}