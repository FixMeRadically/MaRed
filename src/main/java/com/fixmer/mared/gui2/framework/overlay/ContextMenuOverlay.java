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
 * 0.3.2:
 *   - Up/Down/Home/End/Enter keyboard navigation.
 *   - Mouse hover переключает выделение.
 *   - Type-to-search: первая буква начинает накопление буфера,
 *     следующий Enter/стрелки сбрасывают буфер.
 *   - consumeCloseRequest() после успешного действия.
 */
public final class ContextMenuOverlay implements Overlay {

    private static final int ITEM_H = 14;
    private static final int SEP_H  = 5;
    private static final int PAD_X  = 8;
    private static final int PAD_Y  = 4;

    /** Как долго буфер type-to-search живёт без ввода. */
    private static final long SEARCH_BUFFER_TIMEOUT_MS = 1000L;

    private Integer categoryColor;
    public ContextMenuOverlay category(int color){categoryColor=color;return this;}

    private final int anchorX;
    private final int anchorY;
    private final List<ContextMenuEntry> entries;
    private final Runnable onClose;

    private int menuX, menuY, menuW, menuH;
    private boolean layoutDone = false;
    private int highlightedIndex = -1;
    private boolean closeRequested = false;

    // Type-to-search
    private final StringBuilder searchBuffer = new StringBuilder(16);
    private long lastSearchKeyMs = 0L;

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
        this.highlightedIndex = firstSelectableIndex();
    }

    @Override public OverlayLayer layer() { return OverlayLayer.POPUP; }
    @Override public boolean isInputBarrier() { return false; }
    @Override public boolean closeOnEscape() { return true; }

    @Override
    public void onClose() {
        if (onClose != null) {
            try { onClose.run(); }
            catch (Throwable ignored) { }
        }
    }

    @Override
    public boolean consumeCloseRequest() {
        if (closeRequested) { closeRequested = false; return true; }
        return false;
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
        Render.outline(g, menuX, menuY, menuW, menuH, categoryColor==null?t.accent:com.fixmer.mared.technology.editor.EditorDialogStyle.from(t,categoryColor).accent());

        int hoveredIdx = indexAt(mouseX, mouseY);
        if (hoveredIdx >= 0 && isSelectable(hoveredIdx)) {
            highlightedIndex = hoveredIdx;
        }

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

            boolean hov = (i == highlightedIndex);

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

        // 0.3.2: показать буфер поиска в углу панели.
        if (searchBuffer.length() > 0 && !isSearchExpired()) {
            String buf = searchBuffer.toString();
            int bw = font.width(buf);
            int bx = menuX + menuW - bw - PAD_X;
            int by = menuY - 12;
            Render.rect(g, bx - 4, by, bx + bw + 4, by + 11, 0xCC000000);
            Render.textNoShadow(g, font, buf, bx, by + 2, 0xFFFFDD55);
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
        if (by < 12) by = 12; // оставить место для search buffer
        menuX = bx;
        menuY = by;
    }

    // ============================================================
    //  Mouse
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!layoutDone) return true;

        if (mx < menuX || mx >= menuX + menuW
            || my < menuY || my >= menuY + menuH) {
            return false;
        }

        if (button != 0) return true;

        int idx = indexAt(mx, my);
        if (idx >= 0 && isSelectable(idx)) {
            ContextMenuEntry it = entries.get(idx);
            if (it.enabled() && it.action() != null) {
                Runnable action = it.action();
                try { action.run(); }
                catch (Throwable ignored) { }
            }
            closeRequested = true;
            return true;
        }
        return true;
    }

    private int indexAt(double mx, double my) {
        if (mx < menuX || mx >= menuX + menuW) return -1;
        if (my < menuY || my >= menuY + menuH) return -1;

        int curY = menuY + PAD_Y;
        int n = entries.size();
        for (int i = 0; i < n; i++) {
            ContextMenuEntry it = entries.get(i);
            int h = it.separator() ? SEP_H : ITEM_H;
            if (my >= curY && my < curY + h) {
                return it.separator() ? -1 : i;
            }
            curY += h;
        }
        return -1;
    }

    // ============================================================
    //  Keyboard
    // ============================================================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) return false; // ESC → OverlayManager

        // 0.3.2: любой не-char ключ сбрасывает буфер поиска.
        clearSearchBuffer();

        switch (keyCode) {
            case 264: moveSelection(+1); return true;
            case 265: moveSelection(-1); return true;
            case 268: moveSelectionToEdge(true); return true;
            case 269: moveSelectionToEdge(false); return true;
            case 257: case 335: activateHighlighted(); return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // 0.3.2: type-to-search.
        if (codePoint < 32) return true;

        long now = System.currentTimeMillis();
        if (isSearchExpired(now)) searchBuffer.setLength(0);
        searchBuffer.append(Character.toLowerCase(codePoint));
        lastSearchKeyMs = now;

        int found = findFirstMatch(searchBuffer.toString());
        if (found >= 0) highlightedIndex = found;
        return true;
    }

    private boolean isSearchExpired() {
        return isSearchExpired(System.currentTimeMillis());
    }

    private boolean isSearchExpired(long now) {
        return searchBuffer.length() > 0
            && (now - lastSearchKeyMs) > SEARCH_BUFFER_TIMEOUT_MS;
    }

    private void clearSearchBuffer() {
        searchBuffer.setLength(0);
        lastSearchKeyMs = 0L;
    }

    /**
     * Первый enabled-selectable entry, чей label начинается с prefix.
     * Поиск идёт от текущего highlighted — если совпадение на нём же,
     * берём его (стандартное поведение IDE).
     */
    private int findFirstMatch(String prefix) {
        if (prefix.isEmpty()) return -1;
        int n = entries.size();
        if (n == 0) return -1;

        int start = highlightedIndex >= 0 ? highlightedIndex : 0;
        for (int step = 0; step < n; step++) {
            int idx = (start + step) % n;
            if (!isSelectable(idx)) continue;
            ContextMenuEntry it = entries.get(idx);
            if (!it.enabled()) continue;
            String label = it.label();
            int tab = label.indexOf('\t');
            String text = tab >= 0 ? label.substring(0, tab) : label;
            if (text.toLowerCase().startsWith(prefix)) return idx;
        }
        return -1;
    }

    private void moveSelection(int delta) {
        int n = entries.size();
        if (n == 0) return;

        int start = highlightedIndex;
        if (start < 0) start = delta > 0 ? -1 : n;

        int i = start;
        for (int step = 0; step < n; step++) {
            i += delta;
            if (i < 0) i = n - 1;
            if (i >= n) i = 0;
            if (isSelectable(i)) {
                highlightedIndex = i;
                return;
            }
        }
    }

    private void moveSelectionToEdge(boolean home) {
        int n = entries.size();
        if (n == 0) return;

        if (home) {
            for (int i = 0; i < n; i++) {
                if (isSelectable(i)) { highlightedIndex = i; return; }
            }
        } else {
            for (int i = n - 1; i >= 0; i--) {
                if (isSelectable(i)) { highlightedIndex = i; return; }
            }
        }
    }

    private void activateHighlighted() {
        if (highlightedIndex < 0 || highlightedIndex >= entries.size()) return;
        if (!isSelectable(highlightedIndex)) return;
        ContextMenuEntry it = entries.get(highlightedIndex);
        if (!it.enabled() || it.action() == null) return;
        try { it.action().run(); }
        catch (Throwable ignored) { }
        closeRequested = true;
    }

    private int firstSelectableIndex() {
        int n = entries.size();
        for (int i = 0; i < n; i++) {
            if (isSelectable(i)) return i;
        }
        return -1;
    }

    private boolean isSelectable(int i) {
        if (i < 0 || i >= entries.size()) return false;
        return !entries.get(i).separator();
    }
}