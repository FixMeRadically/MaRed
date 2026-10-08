package com.fixmer.mared.gui2.framework.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.render.Render;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.gui2.studio.action.EditorAction;
import com.fixmer.mared.gui2.studio.action.EditorActionArgs;
import com.fixmer.mared.gui2.studio.action.EditorActionContext;
import com.fixmer.mared.gui2.studio.action.EditorActionRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Command Palette — единый доступ ко всем EditorActions.
 *
 * 0.3.2:
 *   - Fuzzy-ish filter по title и id.
 *   - Up/Down/Home/End/Enter/Escape.
 *   - Mouse click и hover.
 *   - Disabled actions отфильтровываются.
 *   - Сам себя (VIEW_COMMAND_PALETTE) не показывает.
 *
 * Формат:
 *   ┌─────────────────────────────────────────┐
 *   │ > save_                                 │  input
 *   ├─────────────────────────────────────────┤
 *   │ Save                              Ctrl+S│  highlighted
 *   │ Reload persistent                       │
 *   ├─────────────────────────────────────────┤
 *   │ 2 / 12                                  │  footer
 *   └─────────────────────────────────────────┘
 */
public final class CommandPaletteOverlay implements Overlay {

    private static final int PANEL_W_MAX  = 620;
    private static final int PANEL_W_MIN  = 360;
    private static final int INPUT_H      = 20;
    private static final int ITEM_H       = 16;
    private static final int FOOTER_H     = 14;
    private static final int MAX_VISIBLE  = 12;
    private static final int PAD          = 6;

    private static int BG_PANEL(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.panel();}
    private static int BG_INPUT(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.sunken();}
    private static int BG_ITEM(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised();}
    private static int BG_HIGHLIGHT(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.selected();}
    private static int BORDER(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge();}
    private static int TEXT(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.text();}
    private static int TEXT_DIM(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim();}
    private static int TEXT_FAINT(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim();}
    private static int ACCENT(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent();}
    private static int SHORTCUT_COL(){return com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim();}

    /** id, которые палитра не должна показывать (сам себя). */
    private static final List<String> EXCLUDED_IDS =
        List.of("mared:view.command_palette");

    // ============================================================
    //  Entry
    // ============================================================

    private static final class Entry {
        final EditorAction action;
        final String displayTitle;
        final String displayShortcut;
        final String searchTitleLower;
        final String searchIdLower;

        Entry(EditorAction action) {
            this.action = action;
            this.displayTitle = MaredLang.get(action.titleKey());
            this.displayShortcut = action.shortcutDisplay();
            this.searchTitleLower =
                displayTitle.toLowerCase(Locale.ROOT);
            this.searchIdLower =
                action.id().toLowerCase(Locale.ROOT);
        }
    }

    // ============================================================
    //  State
    // ============================================================

    private final EditorActionRegistry registry;
    private final EditorActionContext context;
    private final Runnable onDismiss;

    private final StringBuilder query = new StringBuilder();
    private int cursor = 0;

    /** Полный список (enabled actions, кроме excluded). */
    private final List<Entry> allEntries = new ArrayList<>(16);

    /** Отфильтрованные entries по текущему query. */
    private final List<Entry> filtered = new ArrayList<>(16);

    private int highlightedIndex = 0;
    private int scrollOffset = 0;

    private boolean closeRequested = false;

    // Layout, рассчитывается в render
    private int panelX, panelY, panelW, panelH;
    private int inputX, inputY, inputW;
    private int listY, listH;
    private int visibleItems;

    public CommandPaletteOverlay(EditorActionRegistry registry,
                                 EditorActionContext context,
                                 Runnable onDismiss) {
        this.registry = registry;
        this.context = context;
        this.onDismiss = onDismiss;
        rebuildAllEntries();
        refilter();
    }

    // ============================================================
    //  Overlay
    // ============================================================

    @Override public OverlayLayer layer() { return OverlayLayer.MODAL; }
    @Override public boolean isInputBarrier() { return true; }
    @Override public boolean closeOnEscape() { return true; }

    @Override
    public void onClose() {
        if (onDismiss != null) {
            try { onDismiss.run(); }
            catch (Throwable ignored) { }
        }
    }

    @Override
    public boolean consumeCloseRequest() {
        if (closeRequested) { closeRequested = false; return true; }
        return false;
    }

    // ============================================================
    //  Data
    // ============================================================

    private void rebuildAllEntries() {
        allEntries.clear();
        if (registry == null || context == null) return;

        for (EditorAction a : registry.all()) {
            if (EXCLUDED_IDS.contains(a.id())) continue;
            if (!a.isEnabled(context, EditorActionArgs.EMPTY)) continue;
            allEntries.add(new Entry(a));
        }
    }

    private void refilter() {
        filtered.clear();
        String q = query.toString().trim().toLowerCase(Locale.ROOT);

        if (q.isEmpty()) {
            filtered.addAll(allEntries);
        } else {
            // Оценка: 0..n — позиция совпадения.
            // Меньше = лучше. Titле важнее id.
            final List<ScoredEntry> scored = new ArrayList<>(16);
            for (Entry e : allEntries) {
                int t = e.searchTitleLower.indexOf(q);
                int i = e.searchIdLower.indexOf(q);
                if (t < 0 && i < 0) continue;
                int score;
                if (t >= 0 && i >= 0) score = Math.min(t, i + 100);
                else if (t >= 0)       score = t;
                else                    score = i + 100;
                scored.add(new ScoredEntry(e, score));
            }
            scored.sort((a, b) -> Integer.compare(a.score, b.score));
            for (ScoredEntry s : scored) filtered.add(s.entry);
        }

        // Сброс выделения
        highlightedIndex = filtered.isEmpty() ? -1 : 0;
        scrollOffset = 0;
    }

    private static final class ScoredEntry {
        final Entry entry;
        final int score;
        ScoredEntry(Entry e, int s) { entry = e; score = s; }
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    public void render(GuiGraphics g, Font font,
                       int screenW, int screenH,
                       int mouseX, int mouseY) {
        int desiredW = Math.min(PANEL_W_MAX, Math.max(PANEL_W_MIN,
            screenW - 80));
        int maxH = (int) (screenH * 0.7f);

        visibleItems = Math.min(MAX_VISIBLE,
            Math.max(1, (maxH - INPUT_H - FOOTER_H - PAD * 3) / ITEM_H));

        int listRows = Math.min(visibleItems, Math.max(1, filtered.size()));
        panelW = desiredW;
        panelH = PAD + INPUT_H + PAD + listRows * ITEM_H + PAD + FOOTER_H;

        panelX = (screenW - panelW) / 2;
        panelY = Math.max(20, screenH / 6);

        // Затемнение фона
        Render.rect(g, 0, 0, screenW, screenH, 0x40000000);

        // Панель
        Render.rect(g, panelX + 4, panelY + 4,
            panelX + panelW + 4, panelY + panelH + 4, 0x80000000);
        Render.rect(g, panelX, panelY, panelX + panelW, panelY + panelH,
            BG_PANEL());
        Render.outline(g, panelX, panelY, panelW, panelH, ACCENT());

        // Input
        inputX = panelX + PAD;
        inputY = panelY + PAD;
        inputW = panelW - PAD * 2;
        Render.rect(g, inputX, inputY, inputX + inputW, inputY + INPUT_H,
            BG_INPUT());
        Render.outline(g, inputX, inputY, inputW, INPUT_H, BORDER());

        String prompt = "> ";
        Render.textNoShadow(g, font, prompt, inputX + 6, inputY + 6,
            ACCENT());

        int promptW = font.width(prompt);
        String q = query.toString();
        Render.textNoShadow(g, font, q, inputX + 6 + promptW,
            inputY + 6, TEXT());

        // Курсор
        long now = System.currentTimeMillis();
        if ((now / 500) % 2 == 0) {
            int cx = inputX + 6 + promptW + font.width(q.substring(0, cursor));
            Render.rect(g, cx, inputY + 4, cx + 1,
                inputY + INPUT_H - 4, TEXT());
        }

        // List
        listY = inputY + INPUT_H + PAD;
        listH = listRows * ITEM_H;

        clampScroll();
        drawList(g, font, screenW, screenH, mouseX, mouseY);

        // Footer
        int footY = panelY + panelH - FOOTER_H - PAD;
        String status;
        if (filtered.isEmpty()) {
            status = MaredLang.get("mared.palette.no_match");
        } else {
            status = (highlightedIndex + 1) + " / " + filtered.size();
        }
        Render.textNoShadow(g, font, status, panelX + PAD, footY + 4,
            TEXT_FAINT());

        String hint = MaredLang.get("mared.palette.hint");
        int hw = font.width(hint);
        Render.textNoShadow(g, font, hint,
            panelX + panelW - PAD - hw, footY + 4, TEXT_FAINT());
    }

    private void drawList(GuiGraphics g, Font font,
                          int screenW, int screenH,
                          int mouseX, int mouseY) {
        if (filtered.isEmpty()) return;

        int itemW = panelW - PAD * 2;
        int itemX = panelX + PAD;

        g.enableScissor(itemX, listY, itemX + itemW, listY + listH);

        int visibleCount = Math.min(visibleItems, filtered.size());
        for (int i = 0; i < visibleCount; i++) {
            int idx = scrollOffset + i;
            if (idx >= filtered.size()) break;

            Entry e = filtered.get(idx);
            int y = listY + i * ITEM_H;

            boolean hover = mouseX >= itemX && mouseX < itemX + itemW
                && mouseY >= y && mouseY < y + ITEM_H;
            if (hover) highlightedIndex = idx;

            boolean isHighlighted = (idx == highlightedIndex);

            int bg = isHighlighted ? BG_HIGHLIGHT()
                : (hover ? BG_ITEM() : 0);
            if (bg != 0) {
                Render.rect(g, itemX, y, itemX + itemW, y + ITEM_H, bg);
            }

            // Title
            int textY = y + (ITEM_H - 8) / 2;
            int titleX = itemX + 8;
            int maxTitleW = itemW - 16;

            String title = e.displayTitle;
            int shortcutW = e.displayShortcut.isEmpty()
                ? 0
                : font.width(e.displayShortcut) + 12;

            int availTitleW = Math.max(40, maxTitleW - shortcutW);
            String drawnTitle = TextUtils.ellipsize(font, title, availTitleW);
            Render.textNoShadow(g, font, drawnTitle, titleX, textY,
                isHighlighted ? TEXT() : TEXT_DIM());

            // Shortcut
            if (!e.displayShortcut.isEmpty()) {
                int sw = font.width(e.displayShortcut);
                Render.textNoShadow(g, font, e.displayShortcut,
                    itemX + itemW - 8 - sw, textY,
                    isHighlighted ? SHORTCUT_COL() : TEXT_FAINT());
            }
        }

        g.disableScissor();

        // Scrollbar, если нужно
        if (filtered.size() > visibleItems) {
            int trackX = panelX + panelW - PAD - 3;
            int trackH = listH;
            Render.rect(g, trackX, listY, trackX + 2,
                listY + trackH, com.fixmer.mared.technology.editor.GenesisEditorVisuals.track());

            int thumbH = Math.max(8, trackH * visibleItems / filtered.size());
            int thumbY = listY
                + (trackH - thumbH) * scrollOffset
                  / Math.max(1, filtered.size() - visibleItems);
            Render.rect(g, trackX, thumbY, trackX + 2,
                thumbY + thumbH, ACCENT());
        }
    }

    private void clampScroll() {
        int maxOffset = Math.max(0, filtered.size() - visibleItems);
        if (scrollOffset > maxOffset) scrollOffset = maxOffset;
        if (scrollOffset < 0) scrollOffset = 0;

        // Прокрутка к выделенному
        if (highlightedIndex >= 0) {
            if (highlightedIndex < scrollOffset) {
                scrollOffset = highlightedIndex;
            } else if (highlightedIndex >= scrollOffset + visibleItems) {
                scrollOffset = highlightedIndex - visibleItems + 1;
            }
        }
    }

    // ============================================================
    //  Keyboard
    // ============================================================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl  = (modifiers & 2) != 0;
        boolean shift = (modifiers & 1) != 0;

        if (keyCode == 256) {          // ESC
            closeRequested = true;
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // ENTER
            executeHighlighted();
            return true;
        }

        if (ctrl) {
            switch (keyCode) {
                case 65: // Ctrl+A
                    cursor = query.length();
                    return true;
                case 87: // Ctrl+W — удалить слово
                    deleteWordBeforeCursor();
                    return true;
                case 85: // Ctrl+U — очистить до начала
                    query.delete(0, cursor);
                    cursor = 0;
                    refilter();
                    return true;
            }
            return true;
        }

        switch (keyCode) {
            case 264: // DOWN
                moveHighlighted(+1);
                return true;
            case 265: // UP
                moveHighlighted(-1);
                return true;
            case 268: // HOME
                moveHighlightedToEdge(true);
                return true;
            case 269: // END
                moveHighlightedToEdge(false);
                return true;
            case 259: // BACKSPACE
                if (cursor > 0) {
                    query.deleteCharAt(cursor - 1);
                    cursor--;
                    refilter();
                }
                return true;
            case 261: // DELETE
                if (cursor < query.length()) {
                    query.deleteCharAt(cursor);
                    refilter();
                }
                return true;
            case 263: // LEFT
                if (cursor > 0) cursor--;
                return true;
            case 262: // RIGHT
                if (cursor < query.length()) cursor++;
                return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint < 32) return true;
        query.insert(cursor, codePoint);
        cursor++;
        refilter();
        return true;
    }

    private void deleteWordBeforeCursor() {
        if (cursor == 0) return;
        int i = cursor;
        while (i > 0 && query.charAt(i - 1) == ' ') i--;
        while (i > 0 && query.charAt(i - 1) != ' ') i--;
        query.delete(i, cursor);
        cursor = i;
        refilter();
    }

    private void moveHighlighted(int delta) {
        if (filtered.isEmpty()) return;
        int n = filtered.size();
        int next = highlightedIndex + delta;
        if (next < 0) next = n - 1;
        if (next >= n) next = 0;
        highlightedIndex = next;
    }

    private void moveHighlightedToEdge(boolean home) {
        if (filtered.isEmpty()) return;
        highlightedIndex = home ? 0 : filtered.size() - 1;
    }

    private void executeHighlighted() {
        if (highlightedIndex < 0 || highlightedIndex >= filtered.size()) return;
        Entry e = filtered.get(highlightedIndex);
        if (registry == null || context == null) return;

        // Закрываемся ДО execute — action может открыть новый overlay.
        closeRequested = true;

        try {
            registry.execute(e.action.id(), context, EditorActionArgs.EMPTY);
        } catch (Throwable t) {
            // Registry.execute ловит сам — сюда не попадёт.
        }
    }

    // ============================================================
    //  Mouse
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;

        // Клик по input
        if (mx >= inputX && mx < inputX + inputW
            && my >= inputY && my < inputY + INPUT_H) {
            // Позиционируем курсор по x
            Font font = Minecraft.getInstance().font;
            int relX = (int) mx - (inputX + 6 + font.width("> "));
            String q = query.toString();
            int best = q.length();
            for (int i = 0; i <= q.length(); i++) {
                if (font.width(q.substring(0, i)) >= relX) {
                    best = i;
                    break;
                }
            }
            cursor = Math.max(0, best);
            return true;
        }

        // Клик по item
        if (my >= listY && my < listY + listH) {
            int row = (int) ((my - listY) / ITEM_H);
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < filtered.size()) {
                highlightedIndex = idx;
                executeHighlighted();
                return true;
            }
        }
        // Клик по остальной области панели поглощается (barrier)
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my,
                                 double scrollX, double scrollY) {
        if (filtered.size() <= visibleItems) return true;
        if (scrollY < 0) scrollOffset++;
        else if (scrollY > 0) scrollOffset--;
        clampScroll();
        return true;
    }
}