package com.fixmer.mared.gui2.framework.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;

/**
 * Список строк.
 *
 * 0.3.1:
 *   - hit-test согласован с render: и там, и там текстовый ряд
 *     начинается с bounds.y() + PAD_TOP.
 *     Раньше render рисовал через +10, а hit-test считал от bounds.y(),
 *     клик по верхней пустой области выбирал первую строку.
 *   - items() возвращает immutable копию — caller не может обойти
 *     selection/hover, мутируя внутренний список.
 */
public class MaredList extends MaredPanel {

    protected final List<String> items = new ArrayList<>();
    private int selected = -1;
    private int hoveredIndex = -1;
    private Consumer<String> onSelected;

    private static final int ROW_H    = 14;
    private static final int PAD_TOP  = 10;

    public void add(String value) { if (value != null) items.add(value); }
    public void clear() { items.clear(); selected = -1; hoveredIndex = -1; }

    /** 0.3.1: наружу immutable snapshot. */
    public List<String> items() { return List.copyOf(items); }

    public String selected() {
        if (selected < 0 || selected >= items.size()) return null;
        return items.get(selected);
    }

    public int selectedIndex() { return selected; }
    public int hoveredIndex()  { return hoveredIndex; }

    public void setOnSelected(Consumer<String> callback) {
        this.onSelected = callback;
    }

    protected void select(int index) {
        if (index < 0 || index >= items.size()) return;
        selected = index;
        if (onSelected != null) onSelected.accept(items.get(index));
    }

    @Override
    protected void safeRender(MaredRenderContext context) {
        super.safeRender(context);

        int y = bounds.y() + PAD_TOP;
        int n = items.size();
        for (int i = 0; i < n; i++) {
            int color;
            if (i == selected) color = ThemeColors.accent();
            else color = ThemeColors.text();

            context.graphics().drawString(
                context.font(),
                items.get(i),
                bounds.x() + 8,
                y,
                color
            );

            y += ROW_H;
            if (y > bounds.bottom()) break;
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY)) { hoveredIndex = -1; return; }
        int idx = (int) ((mouseY - bounds.y() - PAD_TOP) / ROW_H);
        hoveredIndex = (idx < 0 || idx >= items.size()) ? -1 : idx;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!bounds.contains(mouseX, mouseY)) return false;
        int idx = (int) ((mouseY - bounds.y() - PAD_TOP) / ROW_H);
        if (idx < 0 || idx >= items.size()) return false;
        select(idx);
        return true;
    }
}