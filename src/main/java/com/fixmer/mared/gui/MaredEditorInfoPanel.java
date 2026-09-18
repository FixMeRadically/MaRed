package com.fixmer.mared.gui;

import java.util.HashSet;
import java.util.Set;

import com.fixmer.mared.script.MaredLang;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class MaredEditorInfoPanel {

    private static final int INFO_BG      = 0xFF1A1A24;
    private static final int COLLAPSED_BG = 0xFF1A1A24;
    private static final int TEXT         = 0xFFFFFFFF;
    private static final int TEXT_DIM     = 0xFFAAAAAA;
    private static final int TEXT_WARN    = 0xFFFFAA00;
    private static final int DIVIDER      = 0xFF333344;
    private static final int FILTER_BG    = 0xFF0A0A10;
    private static final int BTN_BG       = 0xFF2D2D2D;
    private static final int BTN_HOVER    = 0xFF3E3E42;
    private static final int MRED_COLOR   = 0xFFFF3333;

    private boolean collapsed = false;
    private String argFilter = "";
    private final Set<Integer> expandedArgs = new HashSet<>();
    private final MaredUi.PixelScroll infoScroll = new MaredUi.PixelScroll();
    private EditBox argFilterBox;

    public MaredEditorInfoPanel() {}

    public boolean isCollapsed() { return collapsed; }
    public void setCollapsed(boolean v) { collapsed = v; }
    public void toggleCollapsed() { collapsed = !collapsed; infoScroll.offset = 0; }
    public String argFilter() { return argFilter; }
    public void setArgFilter(String s) { argFilter = s; }
    public Set<Integer> expandedArgs() { return expandedArgs; }
    public void clearExpanded() { expandedArgs.clear(); }
    public MaredUi.PixelScroll scroll() { return infoScroll; }
    public EditBox argFilterBox() { return argFilterBox; }
    public void setArgFilterBox(EditBox box) { this.argFilterBox = box; }

    public int panelX(MaredEditorLayout layout) {
        return MaredEditorLayout.infoPanelX(collapsed);
    }

    public int panelWidth(MaredEditorLayout layout) {
        return collapsed ? MaredEditorLayout.INFO_COLLAPSED_W()
                         : MaredEditorLayout.INFO_W();
    }

    public int argFilterBoxY(MaredEditorLayout layout, Font font,
                             MaredCommandRegistry.CommandInfo info) {
        if (info == null) return 0;
        int pad = MaredEditorLayout.PAD();
        int y = MaredEditorLayout.TOOLBAR_H() + pad * 2 + 22 + 14 + 12 + 16 + 12;
        int w = MaredEditorLayout.INFO_W() - 16;
        y += MaredUi.wrappedHeight(font, info.description, w) + 6 + 12;
        y += MaredUi.wrappedHeight(font, info.example, w) + 10 + 12;
        return y;
    }

    public int bodyTop(MaredEditorLayout layout, Font font,
                       MaredCommandRegistry.CommandInfo info) {
        return argFilterBoxY(layout, font, info) + MaredEditorLayout.FILTER_H() + 4;
    }

    public EditBox ensureFilterBox(Font font, MaredEditorLayout layout,
                                   MaredCommandRegistry.CommandInfo info) {
        if (collapsed || info == null || info.arguments.isEmpty()) {
            argFilterBox = null;
            return null;
        }

        int infoX = panelX(layout);
        int y = argFilterBoxY(layout, font, info);
        int pad = 3;

        if (argFilterBox == null) {
            argFilterBox = new EditBox(font,
                infoX + pad + 3, y + 2,
                MaredEditorLayout.INFO_W() - pad * 2 - 6,
                MaredEditorLayout.FILTER_H() - 4,
                Component.literal(MaredLang.get("mared.ui.filter")));
            argFilterBox.setBordered(false);
            argFilterBox.setMaxLength(64);
            argFilterBox.setValue(argFilter);
            argFilterBox.setResponder(s -> argFilter = s);
        } else {
            argFilterBox.setX(infoX + pad + 3);
            argFilterBox.setY(y + 2);
            argFilterBox.setWidth(MaredEditorLayout.INFO_W() - pad * 2 - 6);
        }
        return argFilterBox;
    }

    public MaredUi.Content buildContent(MaredCommandRegistry.CommandInfo info,
                                        int accentTop, boolean isMared) {
        MaredUi.Content c = new MaredUi.Content();
        if (info == null) return c;
        int argColor = isMared ? MRED_COLOR : accentTop;

        for (int i = 0; i < info.arguments.size(); i++) {
            MaredCommandRegistry.Argument arg = info.arguments.get(i);
            if (!argFilter.isEmpty()) {
                String q = argFilter.toLowerCase();
                if (!arg.value.toLowerCase().contains(q)
                    && !arg.description.toLowerCase().contains(q)) continue;
            }
            boolean expanded = expandedArgs.contains(i);
            c.text((expanded ? "▾ " : "▸ ") + arg.value, TEXT);
            c.wrapped(arg.description, TEXT_DIM);
            if (expanded) {
                c.gap(6);
                c.text(MaredLang.get("mared.ui.examples"), TEXT_WARN);
                c.gap(2);
                for (String ex : arg.examples) { c.wrapped("• " + ex, argColor); c.gap(2); }
                c.gap(4);
            }
            c.gap(4);
        }
        if (!info.arguments.isEmpty()) c.gap(4);

        if (!info.nbtHints.isEmpty()) {
            c.text(MaredLang.get("mared.ui.nbt_components"), TEXT_WARN);
            c.gap(4);
            for (MaredCommandRegistry.NbtHint hint : info.nbtHints) {
                c.text(hint.tag, argColor);
                c.text(MaredLang.get("mared.ui.what") + " " + hint.what, TEXT_DIM);
                c.wrapped(MaredLang.get("mared.ui.why") + " " + hint.why, TEXT_DIM);
                if (hint.example != null && !hint.example.isEmpty()) {
                    c.wrapped(MaredLang.get("mared.ui.example") + " " + hint.example, argColor);
                }
                c.gap(6);
            }
        }
        return c;
    }

    public int hitArg(MaredEditorLayout layout, Font font,
                      MaredCommandRegistry.CommandInfo info,
                      double mx, double my, int editorBottom) {
        if (info == null) return -1;
        int bodyTop = bodyTop(layout, font, info);
        if (my < bodyTop || my > editorBottom - MaredEditorLayout.PAD()) return -1;

        int infoX = panelX(layout);
        int infoW = MaredEditorLayout.INFO_W();
        int scrollbarW = MaredEditorLayout.SCROLLBAR_W();
        int rightEdge = infoX + infoW - scrollbarW - 4;
        if (mx < infoX || mx > rightEdge) return -1;

        int innerW = infoW - 16 - 10;
        int y = bodyTop - infoScroll.offset;

        for (int i = 0; i < info.arguments.size(); i++) {
            MaredCommandRegistry.Argument arg = info.arguments.get(i);
            if (!argFilter.isEmpty()) {
                String q = argFilter.toLowerCase();
                if (!arg.value.toLowerCase().contains(q)
                    && !arg.description.toLowerCase().contains(q)) continue;
            }
            if (my >= y && my < y + 10) return i;
            y += 10 + MaredUi.wrappedHeight(font, arg.description, innerW);
            if (expandedArgs.contains(i)) {
                y += 6 + 12;
                for (String ex : arg.examples)
                    y += MaredUi.wrappedHeight(font, "• " + ex, innerW) + 2;
                y += 4;
            }
            y += 4;
        }
        return -1;
    }

    public void render(GuiGraphics g, Font font, MaredEditorLayout layout,
                       MaredCommandRegistry.CommandInfo info, int accentTop, int accentBottom,
                       boolean isMared, int mouseX, int mouseY,
                       int editorBottom, MaredUi.DragState drag) {
        if (info == null) return;

        int infoX = panelX(layout);
        int infoY = MaredEditorLayout.TOOLBAR_H() + MaredEditorLayout.PAD() * 2;
        int infoW = panelWidth(layout);
        int infoH = Math.max(0, editorBottom - infoY - MaredEditorLayout.PAD());
        int bottom = isMared ? MRED_COLOR : accentBottom;

        // Фон
        MaredUi.rect(g, infoX, infoY, infoX + infoW, infoY + infoH,
            collapsed ? COLLAPSED_BG : INFO_BG);

        // Верхняя и нижняя линии
        if (!collapsed) {
            MaredUi.rect(g, infoX, infoY, infoX + infoW, infoY + 1, accentTop);
            MaredUi.rect(g, infoX, infoY + infoH - 1, infoX + infoW, infoY + infoH, bottom);
        }

        // Toggle-кнопка
        int toggleX = infoX + 4, toggleY = infoY + 4;
        boolean toggleHover = MaredUi.hovered(mouseX, mouseY, toggleX, toggleY, 14, 14);
        MaredUi.button3D(g, font, toggleX, toggleY, 14, 14,
            collapsed ? "◄" : "►",
            toggleHover ? BTN_HOVER : BTN_BG, accentTop, TEXT, toggleHover);

        if (collapsed) {
            int cx = infoX + infoW / 2;
            int ty = infoY + 24;
            MaredUi.centered(g, font, "INFO", cx, ty, accentTop);
            return;
        }

        int x = infoX + 8;
        int maxW = infoW - 16 - MaredEditorLayout.SCROLLBAR_W() - 2;
        int y = infoY + 22;

        MaredUi.text(g, font, info.name, x, y, accentTop); y += 14;
        MaredUi.text(g, font, MaredLang.get("mared.ui.category") + " " + info.category, x, y, TEXT_DIM); y += 12;
        MaredUi.text(g, font, MaredLang.get("mared.ui.op_level") + " " + info.opLevel, x, y, TEXT_DIM); y += 16;
        MaredUi.text(g, font, MaredLang.get("mared.ui.description"), x, y, TEXT); y += 12;
        y = MaredUi.wrapped(g, font, info.description, x, y, maxW, TEXT_DIM); y += 6;
        MaredUi.text(g, font, MaredLang.get("mared.ui.example"), x, y, TEXT); y += 12;
        y = MaredUi.wrapped(g, font, info.example, x, y, maxW, TEXT); y += 10;

        int filterTitleColor = isMared ? MRED_COLOR : TEXT_WARN;
        MaredUi.text(g, font, MaredLang.get("mared.ui.filter_arguments"), x, y, filterTitleColor); y += 12;

        int filterH = MaredEditorLayout.FILTER_H();
        MaredUi.rect(g, infoX + 3, y, infoX + infoW - 3, y + filterH, FILTER_BG);
        MaredUi.outlineGradient(g, infoX + 3, y, infoW - 6, filterH, accentTop, bottom);
        y += filterH + 4;

        MaredUi.rect(g, infoX + 2, y, infoX + infoW - 2, y + 1, DIVIDER);
        y += 5;  // FIX: +2px — текст не на полоске

        int bodyTop = y;
        int bodyH = (infoY + infoH - MaredEditorLayout.PAD()) - bodyTop;

        MaredUi.Content content = buildContent(info, accentTop, isMared);
        int contentH = content.height(font, maxW);
        infoScroll.set(infoX, bodyTop, infoW, bodyH);
        infoScroll.content(contentH);
        infoScroll.clamp();

        // FIX: scissor ТОЛЬКО для области контента — начинается с bodyTop
        MaredUi.scissorOn(g, infoX + 1, bodyTop, infoX + infoW - 1, infoY + infoH - 1);
        content.render(g, font, x, bodyTop - infoScroll.offset, maxW);
        MaredUi.scissorOff(g);

        infoScroll.drawScrollbarGradient(g, accentTop, bottom, MaredEditorLayout.SCROLLBAR_W());
    }

    public boolean mouseClicked(MaredEditorLayout layout, Font font,
                                MaredCommandRegistry.CommandInfo info,
                                double mx, double my,
                                int editorBottom, MaredUi.DragState drag) {
        if (info == null) return false;

        int infoX = panelX(layout);
        int toggleX = infoX + 4;
        int toggleY = MaredEditorLayout.TOOLBAR_H() + MaredEditorLayout.PAD() * 2 + 4;

        if (MaredUi.hovered(mx, my, toggleX, toggleY, 14, 14)) {
            toggleCollapsed();
            return true;
        }

        if (collapsed || mx < infoX) return false;

        if (argFilterBox != null && argFilterBox.isMouseOver(mx, my)) {
            return false;
        }

        int argBoxY = argFilterBoxY(layout, font, info);
        boolean overFilter = my >= argBoxY - 1
                          && my < argBoxY + MaredEditorLayout.FILTER_H() + 1;

        int bodyTop = bodyTop(layout, font, info);
        int bodyH = (editorBottom - MaredEditorLayout.PAD()) - bodyTop;
        infoScroll.set(infoX, bodyTop, MaredEditorLayout.INFO_W(), bodyH);
        infoScroll.content(buildContent(info, 0, false).height(font,
            MaredEditorLayout.INFO_W() - 16 - MaredEditorLayout.SCROLLBAR_W() - 2));

        if (!overFilter && infoScroll.clickScrollbar(mx, my,
            MaredEditorLayout.SCROLLBAR_W(), drag, MaredUi.DragKind.INFO_SCROLL)) {
            return true;
        }

        if (!overFilter) {
            int hit = hitArg(layout, font, info, mx, my, editorBottom);
            if (hit >= 0) {
                if (expandedArgs.contains(hit)) expandedArgs.remove(hit);
                else expandedArgs.add(hit);
                return true;
            }
        }

        return !overFilter;
    }
}