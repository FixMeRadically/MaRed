package com.fixmer.mared.gui2.docking.render;

import com.fixmer.mared.gui2.docking.DockNode;
import com.fixmer.mared.gui2.docking.DockPanel;
import com.fixmer.mared.gui2.docking.DockPosition;
import com.fixmer.mared.gui2.docking.layout.DockBounds;
import com.fixmer.mared.gui2.docking.style.DockStyle;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.gui2.modules.theme.ModuleColorRegistry;
import com.fixmer.mared.gui2.modules.theme.ModuleTheme;
import com.fixmer.mared.gui2.modules.theme.ModuleType;

import net.minecraft.client.gui.Font;

/**
 * Рендер Dock-панели MaRed Studio.
 *
 * 0.3.0: тема из MaredThemeRegistry.
 * 0.3.1: рендер tab bar для узлов с > 1 панелью.
 * 0.3.2: стрелка collapse в header'е + hit-test.
 */
public final class DockPanelRenderer {

    private DockPanelRenderer() {}

    // ---- Tab bar layout ----
    private static final int TAB_PAD_X = 10;
    private static final int TAB_MIN_W = 36;
    private static final int TAB_GAP   = 2;
    private static final int TAB_H_PAD = 6;
    private static final int TAB_UNDERLINE = 2;

    // ============================================================
    //  Обычный рендер панели (single-panel node)
    // ============================================================

    public static void render(DockPanel panel, MaredRenderContext context,
                              int x, int y, int width, int height) {

        MaredTheme theme = MaredThemeRegistry.active();
        int accent = theme.accent;

        ModuleTheme moduleTheme = ModuleColorRegistry.get(panel.moduleType());
        if (moduleTheme != null) accent = moduleTheme.accent();

        context.graphics().fill(x, y, x + width, y + height, theme.bgPanel);
        context.graphics().fill(x, y, x + width,
            y + DockStyle.HEADER_HEIGHT, theme.bgPanelRaised);
        context.graphics().fill(x, y, x + width,
            y + DockStyle.ACCENT_HEIGHT, accent);

        context.graphics().fill(
            x + DockStyle.ACCENT_INDICATOR_X,
            y + DockStyle.ACCENT_INDICATOR_Y,
            x + DockStyle.ACCENT_INDICATOR_X + DockStyle.ACCENT_INDICATOR_W,
            y + DockStyle.ACCENT_INDICATOR_Y + DockStyle.ACCENT_INDICATOR_H,
            accent);

        // 0.3.2: стрелка collapse — для single-panel узла тоже.
        drawCollapseArrow(context, null, x, y, DockStyle.HEADER_HEIGHT);

        context.graphics().drawString(context.font(), panel.title(),
            x + DockStyle.TITLE_OFFSET_X, y + DockStyle.TITLE_OFFSET_Y,
            0xFFFFFFFF);

        drawMenuButton(context, x + width - DockStyle.MENU_BUTTON_OFFSET_RIGHT,
            y + DockStyle.MENU_BUTTON_OFFSET_Y);

        if (panel.closable()) {
            drawCloseButton(context, x + width - DockStyle.CLOSE_BUTTON_OFFSET_RIGHT,
                y + DockStyle.CLOSE_BUTTON_OFFSET_Y);
        }

        drawBorder(context, x, y, width, height, theme.border);
    }

    // ============================================================
    //  Tab bar (multi-panel node)
    // ============================================================

    public static void renderTabs(DockNode node, MaredRenderContext context,
                                  int x, int y, int width, int headerH) {

        MaredTheme theme = MaredThemeRegistry.active();
        Font font = context.font();

        context.graphics().fill(x, y, x + width, y + headerH, theme.bgPanelRaised);
        context.graphics().fill(x, y, x + width, y + DockStyle.ACCENT_HEIGHT,
            theme.accent);

        int reservedRight = DockStyle.MENU_BUTTON_OFFSET_RIGHT + 16
            + (node.activePanel() != null && node.activePanel().closable()
                ? DockStyle.CLOSE_BUTTON_OFFSET_RIGHT + 16
                : 0);

        // 0.3.2: резервируем место под стрелку collapse.
        int reservedLeft = DockStyle.COLLAPSE_ARROW_OFFSET_X
            + DockStyle.COLLAPSE_HIT_W + 4;

        int availableW = Math.max(40, width - reservedRight - reservedLeft);
        int n = node.panelCount();

        int[] widths = computeTabWidths(font, node, availableW);
        int active = node.activeIndex();

        int cx = x + reservedLeft;
        int cy = y;
        int ch = headerH;

        for (int i = 0; i < n; i++) {
            DockPanel panel = node.panels().get(i);
            int tw = widths[i];
            boolean isActive = (i == active);

            int accent = theme.accent;
            ModuleTheme mt = ModuleColorRegistry.get(panel.moduleType());
            if (mt != null) accent = mt.accent();

            int textColor = isActive ? theme.text : theme.textDim;

            if (isActive) {
                context.graphics().fill(cx, cy, cx + tw, cy + ch,
                    theme.bgPanel);
            }

            String title = panel.title();
            int tx = cx + TAB_PAD_X;
            int ty = cy + (ch - 8) / 2;
            int maxTextW = tw - TAB_PAD_X * 2;
            String drawn = ellipsize(font, title, maxTextW);
            context.graphics().drawString(font, drawn, tx, ty, textColor);

            if (isActive) {
                context.graphics().fill(cx, cy + ch - TAB_UNDERLINE,
                    cx + tw, cy + ch, accent);
            }

            cx += tw + TAB_GAP;
        }

        // 0.3.2: стрелка collapse — перекрывает левую часть header'а.
        drawCollapseArrow(context, node, x, y, headerH);

        drawMenuButton(context, x + width - DockStyle.MENU_BUTTON_OFFSET_RIGHT,
            y + DockStyle.MENU_BUTTON_OFFSET_Y);

        DockPanel activePanel = node.activePanel();
        if (activePanel != null && activePanel.closable()) {
            drawCloseButton(context,
                x + width - DockStyle.CLOSE_BUTTON_OFFSET_RIGHT,
                y + DockStyle.CLOSE_BUTTON_OFFSET_Y);
        }
    }

    public static int hitTab(DockNode node, MaredRenderContext context,
                             int x, int y, int width, int headerH,
                             double mx, double my) {
        if (node == null) return -1;
        if (node.panelCount() < 2) return -1;
        if (my < y || my >= y + headerH) return -1;

        int reservedRight = DockStyle.MENU_BUTTON_OFFSET_RIGHT + 16
            + (node.activePanel() != null && node.activePanel().closable()
                ? DockStyle.CLOSE_BUTTON_OFFSET_RIGHT + 16
                : 0);

        int reservedLeft = DockStyle.COLLAPSE_ARROW_OFFSET_X
            + DockStyle.COLLAPSE_HIT_W + 4;

        int availableW = Math.max(40, width - reservedRight - reservedLeft);

        int[] widths = computeTabWidths(context.font(), node, availableW);
        int cx = x + reservedLeft;

        int n = node.panelCount();
        for (int i = 0; i < n; i++) {
            int tw = widths[i];
            if (mx >= cx && mx < cx + tw) return i;
            cx += tw + TAB_GAP;
        }
        return -1;
    }

    // ============================================================
    //  Collapse arrow (0.3.2)
    // ============================================================

    /**
     * Рисует стрелку collapse в header'е узла.
     * node = null → стрелка не рисуется (single-panel узел без
     * collapse — это заглушка для случая, когда collapse не поддержан).
     */
    static void drawCollapseArrow(MaredRenderContext ctx, DockNode node,
                                  int x, int y, int headerH) {
        if (node == null) return;
        if (!node.isCollapsible()) return;

        MaredTheme theme = MaredThemeRegistry.active();
        boolean collapsed = node.isCollapsed();

        String arrow;
        switch (node.position()) {
            case LEFT   -> arrow = collapsed ? "▶" : "◀";
            case RIGHT  -> arrow = collapsed ? "◀" : "▶";
            case BOTTOM -> arrow = collapsed ? "▲" : "▼";
            default     -> { return; }
        }

        int ax = x + DockStyle.COLLAPSE_ARROW_OFFSET_X;
        int ay = y + (headerH - 8) / 2;
        ctx.graphics().drawString(ctx.font(), arrow, ax, ay,
            theme.textDim, false);
    }

    /**
     * Хит-тест стрелки collapse.
     * Для collapsed узла вся панель = хит-зона (клик в любом месте
     * разворачивает). Для развёрнутого — только узкая зона вокруг
     * стрелки в header'е.
     */
    public static boolean hitCollapseArrow(DockNode node,
                                           int mouseX, int mouseY) {
        if (node == null) return false;
        if (!node.isCollapsible()) return false;

        DockBounds b = node.bounds();

        // collapsed: вся панель — зона разворота.
        if (node.isCollapsed()) {
            return mouseX >= b.x() && mouseX < b.x() + b.width()
                && mouseY >= b.y() && mouseY < b.y() + b.height();
        }

        // развёрнут: только зона стрелки в header'е.
        if (mouseY < b.y() || mouseY >= b.y() + DockStyle.HEADER_HEIGHT) {
            return false;
        }

        int ax = b.x() + DockStyle.COLLAPSE_ARROW_OFFSET_X - 2;
        int aw = DockStyle.COLLAPSE_HIT_W;
        return mouseX >= ax && mouseX < ax + aw;
    }

    // ============================================================
    //  Layout helpers
    // ============================================================

    private static int[] computeTabWidths(Font font, DockNode node, int availableW) {
        int n = node.panelCount();
        int[] widths = new int[n];
        if (n == 0) return widths;

        int totalGaps = (n - 1) * TAB_GAP;
        int spaceForTabs = Math.max(1, availableW - totalGaps);

        int[] natural = new int[n];
        int naturalTotal = 0;
        for (int i = 0; i < n; i++) {
            String t = node.panels().get(i).title();
            natural[i] = Math.max(TAB_MIN_W, font.width(t) + TAB_PAD_X * 2);
            naturalTotal += natural[i];
        }

        if (naturalTotal <= spaceForTabs) {
            System.arraycopy(natural, 0, widths, 0, n);
            return widths;
        }

        int even = Math.max(TAB_MIN_W, spaceForTabs / n);
        for (int i = 0; i < n; i++) widths[i] = even;
        return widths;
    }

    private static String ellipsize(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        int ell = font.width("...");
        if (maxW <= ell) return "";
        return font.plainSubstrByWidth(s, maxW - ell) + "...";
    }

    // ============================================================
    //  Мелкие элементы
    // ============================================================

    private static void drawMenuButton(MaredRenderContext context, int x, int y) {
        context.graphics().drawString(context.font(), "⋮", x, y, 0xFFAAAAAA);
    }

    private static void drawCloseButton(MaredRenderContext context, int x, int y) {
        context.graphics().drawString(context.font(), "×", x, y, 0xFFAAAAAA);
    }

    private static void drawBorder(MaredRenderContext context,
                                   int x, int y, int width, int height, int color) {
        context.graphics().fill(x, y, x + width, y + 1, color);
        context.graphics().fill(x, y + height - 1, x + width, y + height, color);
        context.graphics().fill(x, y, x + 1, y + height, color);
        context.graphics().fill(x + width - 1, y, x + width, y + height, color);
    }
}