package com.fixmer.mared.gui2.framework.overlay;

import java.util.ArrayList;
import java.util.List;

import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Overlay контекстного меню.
 *
 * 0.3.1: instance-based, не синглтон.
 *        Использует entry.separator() (accessor record) для проверки.
 *        Клик вне меню — закрывает (OverlayManager.remove).
 */
public final class ContextMenuOverlay implements Overlay {

    private static final int ITEM_H = 14;
    private static final int SEP_H  = 5;
    private static final int PAD_X  = 8;
    private static final int PAD_Y  = 4;

    private final int anchorX;
    private final int anchorY;
    private final List<ContextMenuEntry> entries;
    private final Runnable onClose;

    private int menuX, menuY, menuW, menuH;
    private boolean layoutDone = false;

    public ContextMenuOverlay(int x, int y, List<ContextMenuEntry> entries) {
        this(x, y, entries, null);
    }

    public ContextMenuOverlay(int x, int y, List<ContextMenuEntry> entries,
                              Runnable onClose) {
        this.anchorX = x;
        this.anchorY = y;
        this.entries = entries == null
            ? List.of()
            : new ArrayList<>(entries);
        this.onClose = onClose;
    }

    // ============================================================
    //  Overlay
    // ============================================================

    @Override
    public OverlayLayer layer() { return OverlayLayer.POPUP; }

    @Override
    public boolean isInputBarrier() { return false; }

    @Override
    public boolean closeOnEscape() { return true; }

    @Override
    public void onClose() {
        if (onClose != null) {
            try { onClose.run(); }
            catch (Throwable ignored) { }
        }
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    public void render(GuiGraphics g, Font font,
                       int screenW, int screenH,
                       int mouseX, int mouseY) {
        if (entries.isEmpty()) return;

        if (!layoutDone) {
            computeLayout(font, screenW, screenH);
            layoutDone = true;
        }

        MaredTheme t = MaredThemeRegistry.active();
        if (t == null) return;

        Render.rect(g, menuX + 3, menuY + 3,
            menuX + menuW + 3, menuY + menuH + 3, 0x80000000);
        Render.rect(g, menuX, menuY, menuX + menuW, menuY + menuH,
            t.bgPanelRaised);
        Render.outline(g, menuX, menuY, menuW, menuH, t.accent);

        int curY = menuY + PAD_Y;
        int n = entries.size();
        for (int i = 0; i < n; i++) {
            ContextMenuEntry it = entries.get(i);
            if (it.separator()) {
                Render.rect(g, menuX + PAD_X, curY + SEP_H / 2,
                    menuX + menuW - PAD_X, curY + SEP_H / 2 + 1, t.divider);
                curY += SEP_H;
                continue;
            }

            boolean hov = it.enabled()
                && mouseX >= menuX && mouseX < menuX + menuW
                && mouseY >= curY && mouseY < curY + ITEM_H;

            if (hov) {
                Render.rect(g, menuX + 2, curY,
                    menuX + menuW - 2, curY + ITEM_H, t.bgHover);
            }

            int color = it.enabled() ? t.text : t.textFaint;
            String label = it.label();
            int tab = label.indexOf('\t');
            if (tab >= 0) {
                String left = label.substring(0, tab);
                String right = label.substring(tab + 1);
                TextUtils.text(g, font, left, menuX + PAD_X, curY + 3, color);
                int rw = font.width(right);
                TextUtils.text(g, font, right,
                    menuX + menuW - PAD_X - rw, curY + 3, t.textDim);
            } else {
                TextUtils.text(g, font, label, menuX + PAD_X, curY + 3, color);
            }

            curY += ITEM_H;
        }
    }

    private void computeLayout(Font font, int screenW, int screenH) {
        int w = 0;
        for (ContextMenuEntry it : entries) {
            if (it.separator()) continue;
            String label = it.label();
            int tab = label.indexOf('\t');
            int textW;
            if (tab >= 0) {
                String left = label.substring(0, tab);
                String right = label.substring(tab + 1);
                textW = font.width(left) + 24 + font.width(right);
            } else {
                textW = font.width(label);
            }
            if (textW > w) w = textW;
        }
        menuW = w + PAD_X * 2;

        int h = PAD_Y * 2;
        for (ContextMenuEntry it : entries) {
            h += it.separator() ? SEP_H : ITEM_H;
        }
        menuH = h;

        int bx = anchorX;
        int by = anchorY;
        if (bx + menuW > screenW - 4) bx = screenW - menuW - 4;
        if (by + menuH > screenH - 4) by = screenH - menuH - 4;
        if (bx < 4) bx = 4;
        if (by < 4) by = 4;
        menuX = bx;
        menuY = by;
    }

    // ============================================================
    //  Input
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!layoutDone) return true;

        // Клик вне меню — не поглощён, OverlayManager закроет POPUP.
        if (mx < menuX || mx >= menuX + menuW
            || my < menuY || my >= menuY + menuH) {
            return false;
        }

        if (button != 0) return true;

        int curY = menuY + PAD_Y;
        for (ContextMenuEntry it : entries) {
            if (it.separator()) { curY += SEP_H; continue; }
            if (my >= curY && my < curY + ITEM_H) {
                if (it.enabled() && it.action() != null) {
                    Runnable action = it.action();
                    try { action.run(); }
                    catch (Throwable ignored) { }
                }
                // Поглощено — OverlayManager попросит закрыться (POPUP),
                // либо Screen уберёт overlay по своему решению.
                return false;
            }
            curY += ITEM_H;
        }
        return true;
    }
}