package com.fixmer.mared.gui.editor.sidebar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.registry.MaredCommandRegistry;
import com.fixmer.mared.gui.common.MaredUi;
import com.fixmer.mared.gui.editor.MaredEditorLayout;

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
    private final Set<Integer> expandedArgs = new HashSet<>(4);
    private final MaredUi.PixelScroll infoScroll = new MaredUi.PixelScroll();
    private EditBox argFilterBox;

    // ---- Кэш layout ----
    private LayoutEntry cachedLayout = null;
    private int cachedLayoutKey = 0;
    private int cachedContentHeight = 0;

    public MaredEditorInfoPanel() {}

    // ============================================================
    //  Public API
    // ============================================================

    public boolean isCollapsed() { return collapsed; }
    public void setCollapsed(boolean v) { collapsed = v; invalidateLayout(); }
    public void toggleCollapsed() { collapsed = !collapsed; infoScroll.offset = 0; invalidateLayout(); }
    public String argFilter() { return argFilter; }
    public void setArgFilter(String s) { argFilter = s; invalidateLayout(); }
    public Set<Integer> expandedArgs() { return expandedArgs; }
    public void clearExpanded() { expandedArgs.clear(); invalidateLayout(); }
    public MaredUi.PixelScroll scroll() { return infoScroll; }
    public EditBox argFilterBox() { return argFilterBox; }
    public void setArgFilterBox(EditBox box) { this.argFilterBox = box; }

    private void invalidateLayout() {
        cachedLayout = null;
        cachedLayoutKey = 0;
    }

    // ============================================================
    //  Layout
    // ============================================================

    private static final class Section {
        final int argIndex;
        final String text;
        final int color;
        final boolean wrapped;
        final int height;

        Section(int argIndex, String text, int color, boolean wrapped, int height) {
            this.argIndex = argIndex;
            this.text = text;
            this.color = color;
            this.wrapped = wrapped;
            this.height = height;
        }
    }

    private static final class LayoutEntry {
        final Section[] sections;
        final int totalHeight;
        LayoutEntry(Section[] sections, int totalHeight) {
            this.sections = sections;
            this.totalHeight = totalHeight;
        }
    }

    private int layoutKey(MaredCommandRegistry.CommandInfo info, int accentTop,
                          boolean isMared, int innerW) {
        int h = info != null ? info.name.hashCode() : 0;
        h = h * 31 + accentTop;
        h = h * 31 + (isMared ? 1 : 0);
        h = h * 31 + innerW;
        h = h * 31 + argFilter.hashCode();
        h = h * 31 + expandedArgs.hashCode();
        return h;
    }

    private LayoutEntry buildLayout(MaredCommandRegistry.CommandInfo info, int accentTop,
                                    boolean isMared, Font font, int innerW) {
        int argColor = isMared ? MRED_COLOR : accentTop;
        List<Section> sections = new ArrayList<>(16);

        String q = argFilter.toLowerCase();
        boolean hasFilter = !q.isEmpty();

        for (int i = 0; i < info.arguments.size(); i++) {
            MaredCommandRegistry.Argument arg = info.arguments.get(i);
            if (hasFilter
                && !arg.value.toLowerCase().contains(q)
                && !arg.description.toLowerCase().contains(q)) continue;

            boolean expanded = expandedArgs.contains(i);

            sections.add(new Section(i,
                (expanded ? "▾ " : "▸ ") + arg.value, TEXT, false, 10));
            int descH = MaredUi.wrappedHeight(font, arg.description, innerW);
            sections.add(new Section(i, arg.description, TEXT_DIM, true, descH));

            if (expanded) {
                sections.add(new Section(-1, "", 0, false, 6));
                sections.add(new Section(-1, MaredLang.get("mared.ui.examples"), TEXT_WARN, false, 10));
                sections.add(new Section(-1, "", 0, false, 2));
                for (String ex : arg.examples) {
                    sections.add(new Section(-1, "• " + ex, argColor, true,
                        MaredUi.wrappedHeight(font, "• " + ex, innerW)));
                    sections.add(new Section(-1, "", 0, false, 2));
                }
                sections.add(new Section(-1, "", 0, false, 4));
            }
            sections.add(new Section(-1, "", 0, false, 4));
        }

        if (!info.arguments.isEmpty()) sections.add(new Section(-1, "", 0, false, 4));

        if (!info.nbtHints.isEmpty()) {
            sections.add(new Section(-1, MaredLang.get("mared.ui.nbt_components"), TEXT_WARN, false, 10));
            sections.add(new Section(-1, "", 0, false, 4));
            for (MaredCommandRegistry.NbtHint hint : info.nbtHints) {
                sections.add(new Section(-1, hint.tag, argColor, false, 10));
                sections.add(new Section(-1,
                    MaredLang.get("mared.ui.what") + " " + hint.what, TEXT_DIM, false, 10));
                String why = MaredLang.get("mared.ui.why") + " " + hint.why;
                sections.add(new Section(-1, why, TEXT_DIM, true,
                    MaredUi.wrappedHeight(font, why, innerW)));
                if (hint.example != null && !hint.example.isEmpty()) {
                    String s = MaredLang.get("mared.ui.example") + " " + hint.example;
                    sections.add(new Section(-1, s, argColor, true,
                        MaredUi.wrappedHeight(font, s, innerW)));
                }
                sections.add(new Section(-1, "", 0, false, 6));
            }
        }

        int total = 0;
        for (Section s : sections) total += s.height;
        return new LayoutEntry(sections.toArray(new Section[0]), total);
    }

    private LayoutEntry getLayout(MaredCommandRegistry.CommandInfo info, int accentTop,
                                  boolean isMared, Font font, int innerW) {
        int key = layoutKey(info, accentTop, isMared, innerW);
        if (cachedLayout != null && cachedLayoutKey == key) return cachedLayout;
        cachedLayout = buildLayout(info, accentTop, isMared, font, innerW);
        cachedLayoutKey = key;
        cachedContentHeight = cachedLayout.totalHeight;
        return cachedLayout;
    }

    // ============================================================
    //  Геометрия
    // ============================================================

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
        int w = MaredEditorLayout.INFO_W() - pad * 2 - 6;

        if (argFilterBox == null) {
            argFilterBox = new EditBox(font,
                infoX + pad + 3, y + 2, w, MaredEditorLayout.FILTER_H() - 4,
                Component.literal(MaredLang.get("mared.ui.filter")));
            argFilterBox.setBordered(false);
            argFilterBox.setMaxLength(64);
            argFilterBox.setValue(argFilter);
            argFilterBox.setResponder(s -> {
                argFilter = s;
                invalidateLayout();
            });
        } else {
            argFilterBox.setX(infoX + pad + 3);
            argFilterBox.setY(y + 2);
            argFilterBox.setWidth(w);
        }
        return argFilterBox;
    }

    // ============================================================
    //  Render
    // ============================================================

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

        MaredUi.rect(g, infoX, infoY, infoX + infoW, infoY + infoH,
            collapsed ? COLLAPSED_BG : INFO_BG);

        if (!collapsed) {
            MaredUi.rect(g, infoX, infoY, infoX + infoW, infoY + 1, accentTop);
            MaredUi.rect(g, infoX, infoY + infoH - 1, infoX + infoW, infoY + infoH, bottom);
        }

        int toggleX = infoX + 4;
        int toggleY = infoY + 4;
        boolean toggleHover = MaredUi.hovered(mouseX, mouseY, toggleX, toggleY, 14, 14);
        MaredUi.button3D(g, font, toggleX, toggleY, 14, 14,
            collapsed ? "◄" : "►",
            toggleHover ? BTN_HOVER : BTN_BG, accentTop, TEXT, toggleHover);

        if (collapsed) {
            MaredUi.centered(g, font, "INFO", infoX + infoW / 2, infoY + 24, accentTop);
            return;
        }

        int x = infoX + 8;
        int maxW = infoW - 16 - MaredEditorLayout.SCROLLBAR_W() - 2;
        int y = infoY + 22;

        MaredUi.text(g, font, info.name, x, y, accentTop); y += 14;
        MaredUi.text(g, font, MaredLang.get("mared.ui.category") + " " + info.category,
            x, y, TEXT_DIM); y += 12;
        MaredUi.text(g, font, MaredLang.get("mared.ui.op_level") + " " + info.opLevel,
            x, y, TEXT_DIM); y += 16;
        MaredUi.text(g, font, MaredLang.get("mared.ui.description"), x, y, TEXT); y += 12;
        y = MaredUi.wrapped(g, font, info.description, x, y, maxW, TEXT_DIM); y += 6;
        MaredUi.text(g, font, MaredLang.get("mared.ui.example"), x, y, TEXT); y += 12;
        y = MaredUi.wrapped(g, font, info.example, x, y, maxW, TEXT); y += 10;

        int filterTitleColor = isMared ? MRED_COLOR : TEXT_WARN;
        MaredUi.text(g, font, MaredLang.get("mared.ui.filter_arguments"), x, y, filterTitleColor);
        y += 12;

        int filterH = MaredEditorLayout.FILTER_H();
        MaredUi.rect(g, infoX + 3, y, infoX + infoW - 3, y + filterH, FILTER_BG);
        MaredUi.outlineGradient(g, infoX + 3, y, infoW - 6, filterH, accentTop, bottom);
        y += filterH + 4;

        MaredUi.rect(g, infoX + 2, y, infoX + infoW - 2, y + 1, DIVIDER);
        y += 5;

        int bodyTop = y;
        int bodyH = (infoY + infoH - MaredEditorLayout.PAD()) - bodyTop;

        LayoutEntry layoutEntry = getLayout(info, accentTop, isMared, font, maxW);
        infoScroll.set(infoX, bodyTop, infoW, bodyH);
        infoScroll.content(layoutEntry.totalHeight);
        infoScroll.clamp();

        MaredUi.scissorOn(g, infoX + 1, bodyTop, infoX + infoW - 1, infoY + infoH - 1);

        int cy = bodyTop - infoScroll.offset;
        for (Section s : layoutEntry.sections) {
            if (!s.text.isEmpty()) {
                if (s.wrapped) MaredUi.wrapped(g, font, s.text, x, cy, maxW, s.color);
                else MaredUi.text(g, font, s.text, x, cy, s.color);
            }
            cy += s.height;
        }

        MaredUi.scissorOff(g);
        infoScroll.drawScrollbarGradient(g, accentTop, bottom, MaredEditorLayout.SCROLLBAR_W());
    }

    // ============================================================
    //  Mouse
    // ============================================================

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
        if (argFilterBox != null && argFilterBox.isMouseOver(mx, my)) return false;

        int argBoxY = argFilterBoxY(layout, font, info);
        boolean overFilter = my >= argBoxY - 1
                          && my < argBoxY + MaredEditorLayout.FILTER_H() + 1;

        int bodyTop = bodyTop(layout, font, info);
        int bodyH = (editorBottom - MaredEditorLayout.PAD()) - bodyTop;
        infoScroll.set(infoX, bodyTop, MaredEditorLayout.INFO_W(), bodyH);

        int innerW = MaredEditorLayout.INFO_W() - 16 - MaredEditorLayout.SCROLLBAR_W() - 2;
        LayoutEntry layoutEntry = getLayout(info, 0, false, font, innerW);
        infoScroll.content(layoutEntry.totalHeight);

        if (!overFilter && infoScroll.clickScrollbar(mx, my,
            MaredEditorLayout.SCROLLBAR_W(), drag, MaredUi.DragKind.INFO_SCROLL)) {
            return true;
        }

        if (!overFilter) {
            int hit = hitArgFromLayout(layoutEntry, bodyTop, infoScroll.offset,
                mx, my, editorBottom);
            if (hit >= 0) {
                if (expandedArgs.contains(hit)) expandedArgs.remove(hit);
                else expandedArgs.add(hit);
                invalidateLayout();
                return true;
            }
        }

        return !overFilter;
    }

    private int hitArgFromLayout(LayoutEntry layoutEntry, int bodyTop, int scrollOffset,
                                 double mx, double my, int editorBottom) {
        if (my < bodyTop || my > editorBottom - MaredEditorLayout.PAD()) return -1;

        int cy = bodyTop - scrollOffset;
        for (Section s : layoutEntry.sections) {
            if (s.argIndex >= 0 && !s.wrapped) {
                if (my >= cy && my < cy + s.height) return s.argIndex;
            }
            cy += s.height;
        }
        return -1;
    }
}