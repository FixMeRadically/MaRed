package com.fixmer.mared.gui2.docking.render;

import com.fixmer.mared.gui2.docking.DockLayout;
import com.fixmer.mared.gui2.docking.DockManager;
import com.fixmer.mared.gui2.docking.DockNode;
import com.fixmer.mared.gui2.docking.DockPanel;
import com.fixmer.mared.gui2.docking.DockPosition;
import com.fixmer.mared.gui2.docking.layout.DockBounds;
import com.fixmer.mared.gui2.docking.layout.DockLayoutCalculator;
import com.fixmer.mared.gui2.docking.style.DockStyle;
import com.fixmer.mared.gui2.framework.core.MaredBounds;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.gui2.modules.theme.ModuleColorRegistry;
import com.fixmer.mared.gui2.modules.theme.ModuleTheme;
import com.fixmer.mared.gui2.modules.theme.ModuleType;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Главный renderer и input-dispatcher Dock системы.
 *
 * 0.3.0: разделители между панелями, drag меняет ratios.
 * 0.3.1 (audit #30/#31): renderNode рисует только активную панель,
 * tab bar для multi-panel узлов, dispatch — только активной.
 * 0.3.2: collapsed-узлы рендерятся как header-only, клик по стрелке
 * или по всей панели переключает collapse.
 */
public final class DockRenderer {

    private DockRenderer() {}

    private static final int MIN_HEIGHT_FOR_HEADER = 40;
    private static final int DIVIDER_HIT = 5;

    private static final int DIVIDER_IDLE  = 0x33FFFFFF;
    private static final int DIVIDER_HOVER = 0xAA55AAFF;
    private static final int DIVIDER_DRAG  = 0xFF55AAFF;

    // ============================================================
    //  Drag state
    // ============================================================

    private enum DividerKind {
        NONE(null),
        LEFT_CENTER(DockPosition.LEFT),
        CENTER_RIGHT(DockPosition.RIGHT),
        TOP_BOTTOM(DockPosition.BOTTOM);

        final DockPosition position;
        DividerKind(DockPosition p) { this.position = p; }
    }

    private static DividerKind activeDrag = DividerKind.NONE;
    private static double dragStartMx = 0;
    private static double dragStartMy = 0;
    private static float dragStartRatio = 0f;
    private static int dragScreenW = 1;
    private static int dragScreenH = 1;

    public static boolean isDraggingDivider() {
        return activeDrag != DividerKind.NONE;
    }

    // ============================================================
    //  Render
    // ============================================================

    public static void render(DockManager manager,
                              MaredRenderContext context,
                              int screenWidth,
                              int screenHeight) {

        DockLayoutCalculator.calculate(manager.layout(), screenWidth, screenHeight);

        context.graphics().fill(0, 0, screenWidth, screenHeight, 0xFF111111);

        renderNode(manager.layout().node(DockPosition.TOP), context);
        renderNode(manager.layout().node(DockPosition.LEFT), context);
        renderNode(manager.layout().node(DockPosition.CENTER), context);
        renderNode(manager.layout().node(DockPosition.RIGHT), context);
        renderNode(manager.layout().node(DockPosition.BOTTOM), context);
    }

    public static void renderDividers(DockManager manager,
                                      MaredRenderContext context,
                                      int screenWidth,
                                      int screenHeight,
                                      int mouseX, int mouseY) {
        DockLayout layout = manager.layout();

        int leftW   = layout.node(DockPosition.LEFT).bounds().width();
        int rightW  = layout.node(DockPosition.RIGHT).bounds().width();
        int bottomH = layout.node(DockPosition.BOTTOM).bounds().height();
        int topH    = layout.node(DockPosition.TOP).bounds().height();

        int yTop    = topH;
        int yBottom = screenHeight - bottomH;

        // 0.3.2: свёрнутые панели не тянутся — разделитель всё равно
        // рисуем (для консистентности), но drag его не двигает,
        // если панель collapsed (см. beginDividerDrag).
        drawVerticalDivider(context, leftW, yTop, yBottom,
            activeDrag == DividerKind.LEFT_CENTER,
            isNearX(mouseX, leftW) && mouseY >= yTop && mouseY < yBottom,
            isDraggingDivider());

        int rx = screenWidth - rightW;
        drawVerticalDivider(context, rx, yTop, yBottom,
            activeDrag == DividerKind.CENTER_RIGHT,
            isNearX(mouseX, rx) && mouseY >= yTop && mouseY < yBottom,
            isDraggingDivider());

        drawHorizontalDivider(context, 0, screenWidth, yBottom,
            activeDrag == DividerKind.TOP_BOTTOM,
            isNearY(mouseY, yBottom) && mouseX >= 0 && mouseX < screenWidth,
            isDraggingDivider());
    }

    private static void drawVerticalDivider(MaredRenderContext ctx,
                                            int x, int y0, int y1,
                                            boolean dragging, boolean hovered,
                                            boolean anyDrag) {
        int color;
        if (dragging) color = DIVIDER_DRAG;
        else if (hovered && !anyDrag) color = DIVIDER_HOVER;
        else color = DIVIDER_IDLE;

        ctx.graphics().fill(x, y0, x + 1, y1, color);
    }

    private static void drawHorizontalDivider(MaredRenderContext ctx,
                                              int x0, int x1, int y,
                                              boolean dragging, boolean hovered,
                                              boolean anyDrag) {
        int color;
        if (dragging) color = DIVIDER_DRAG;
        else if (hovered && !anyDrag) color = DIVIDER_HOVER;
        else color = DIVIDER_IDLE;

        ctx.graphics().fill(x0, y, x1, y + 1, color);
    }

    private static boolean isNearX(int mx, int target) {
        return mx >= target - 2 && mx <= target + 2;
    }

    private static boolean isNearY(int my, int target) {
        return my >= target - 2 && my <= target + 2;
    }

    // ============================================================
    //  Узел
    // ============================================================

    private static void renderNode(DockNode node, MaredRenderContext context) {
        if (node == null) return;
        if (!node.hasPanels()) return;

        DockBounds b = node.bounds();

        // 0.3.2: collapsed — рисуем только header.
        if (node.isCollapsed()) {
            renderCollapsedNode(node, context, b);
            return;
        }

        int headerH = DockStyle.HEADER_HEIGHT;
        DockPanel active = node.activePanel();
        if (active == null) return;

        if (b.height() < MIN_HEIGHT_FOR_HEADER) {
            active.component().layout(new MaredBounds(
                b.x(), b.y(), b.width(), b.height()));
            active.component().render(context);
            return;
        }

        int contentX = b.x();
        int contentY = b.y() + headerH;
        int contentW = b.width();
        int contentH = Math.max(0, b.height() - headerH);

        active.component().layout(new MaredBounds(
            contentX, contentY, contentW, contentH));

        if (node.panelCount() > 1) {
            DockPanelRenderer.renderTabs(node, context,
                b.x(), b.y(), b.width(), headerH);
        } else {
            DockPanelRenderer.render(active, context,
                b.x(), b.y(), b.width(), b.height());
        }

        active.component().render(context);
    }

    /**
     * 0.3.2: collapsed — рисуется только header. Стрелка и заголовок
     * в центре панели. Для LEFT/RIGHT панель узкая, поэтому заголовок
     * не помещается — только стрелка. Для BOTTOM — стрелка + title.
     */
    private static void renderCollapsedNode(DockNode node,
                                             MaredRenderContext ctx,
                                             DockBounds b) {
        MaredTheme t = MaredThemeRegistry.active();
        GuiGraphics g = ctx.graphics();
        Font font = ctx.font();

        DockPosition pos = node.position();

        // Accent-цвет — по модулю активной панели.
        int accent = t.accent;
        DockPanel active = node.activePanel();
        if (active != null) {
            ModuleTheme mt = ModuleColorRegistry.get(active.moduleType());
            if (mt != null) accent = mt.accent();
        }

        // Фон.
        g.fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(),
            t.bgPanelRaised);

        // Accent-полоска по стороне.
        if (pos == DockPosition.LEFT || pos == DockPosition.RIGHT) {
            g.fill(b.x(), b.y(),
                b.x() + DockStyle.ACCENT_HEIGHT, b.y() + b.height(),
                accent);
        } else {
            g.fill(b.x(), b.y(),
                b.x() + b.width(), b.y() + DockStyle.ACCENT_HEIGHT,
                accent);
        }

        // Стрелка разворота.
        String arrow;
        switch (pos) {
            case LEFT   -> arrow = "▶";
            case RIGHT  -> arrow = "◀";
            case BOTTOM -> arrow = "▲";
            default     -> arrow = "?";
        }

        int aw = font.width(arrow);

        if (pos == DockPosition.BOTTOM && active != null) {
            // Full-width — рисуем стрелку слева + заголовок.
            int ax = b.x() + DockStyle.PADDING;
            int ay = b.y() + (b.height() - 8) / 2;
            g.drawString(font, arrow, ax, ay, t.text, false);
            g.drawString(font, active.title(),
                ax + aw + 8, ay, t.text, false);
        } else {
            // Узкая панель — стрелка по центру.
            int ax = b.x() + (b.width() - aw) / 2;
            int ay = b.y() + (b.height() - 8) / 2;
            g.drawString(font, arrow, ax, ay, t.text, false);
        }
    }

    // ============================================================
    //  Divider drag API
    // ============================================================

    public static boolean beginDividerDrag(DockManager manager,
                                           double mx, double my,
                                           int screenWidth, int screenHeight,
                                           int button) {
        if (button != 0) return false;

        DividerKind kind = hitDivider(manager, mx, my, screenWidth, screenHeight);
        if (kind == DividerKind.NONE) return false;

        // 0.3.2: свёрнутая панель не даёт тянуть себя.
        DockNode node = manager.layout().node(kind.position);
        if (node != null && node.isCollapsed()) return false;

        activeDrag = kind;
        dragStartMx = mx;
        dragStartMy = my;
        dragScreenW = Math.max(1, screenWidth);
        dragScreenH = Math.max(1, screenHeight);
        dragStartRatio = manager.layout().ratio(kind.position);
        return true;
    }

    public static void updateDividerDrag(DockManager manager,
                                         double mx, double my,
                                         int screenWidth, int screenHeight) {
        if (activeDrag == DividerKind.NONE) return;

        float delta;
        switch (activeDrag) {
            case LEFT_CENTER -> delta = (float)((mx - dragStartMx) / dragScreenW);
            case CENTER_RIGHT -> delta = (float)(-(mx - dragStartMx) / dragScreenW);
            case TOP_BOTTOM -> delta = (float)(-(my - dragStartMy) / dragScreenH);
            default -> { return; }
        }

        manager.layout().setRatio(activeDrag.position, dragStartRatio + delta);
    }

    public static void endDividerDrag(DockManager manager) {
        if (activeDrag == DividerKind.NONE) return;
        activeDrag = DividerKind.NONE;
    }

    private static DividerKind hitDivider(DockManager manager,
                                           double mx, double my,
                                           int screenWidth, int screenHeight) {
        DockLayout layout = manager.layout();

        int leftW   = layout.node(DockPosition.LEFT).bounds().width();
        int rightW  = layout.node(DockPosition.RIGHT).bounds().width();
        int bottomH = layout.node(DockPosition.BOTTOM).bounds().height();
        int topH    = layout.node(DockPosition.TOP).bounds().height();

        int yTop    = topH;
        int yBottom = screenHeight - bottomH;

        if (near(mx, leftW) && my >= yTop && my < yBottom) {
            return DividerKind.LEFT_CENTER;
        }
        int rx = screenWidth - rightW;
        if (near(mx, rx) && my >= yTop && my < yBottom) {
            return DividerKind.CENTER_RIGHT;
        }
        if (near(my, yBottom) && mx >= 0 && mx < screenWidth) {
            return DividerKind.TOP_BOTTOM;
        }
        return DividerKind.NONE;
    }

    private static boolean near(double value, int target) {
        return value >= target - (DIVIDER_HIT / 2)
            && value <= target + (DIVIDER_HIT / 2) + 1;
    }

    // ============================================================
    //  Dispatch
    // ============================================================

    public static boolean dispatchClick(DockManager manager,
                                        double mx, double my, int button) {
        MaredRenderContext ctx = currentContext;

        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            if (!contains(node.bounds(), mx, my)) continue;

            // 0.3.2: collapse.
            if (node.isCollapsible()
                && DockPanelRenderer.hitCollapseArrow(
                    node, (int) mx, (int) my)) {
                node.toggleCollapsed();
                return true;
            }
            // collapsed: клик в любом месте — разворот.
            if (node.isCollapsed()) {
                node.toggleCollapsed();
                return true;
            }

            // Вкладки.
            if (node.panelCount() > 1 && ctx != null) {
                int idx = DockPanelRenderer.hitTab(node, ctx,
                    node.bounds().x(), node.bounds().y(),
                    node.bounds().width(), DockStyle.HEADER_HEIGHT,
                    mx, my);
                if (idx >= 0) {
                    node.activate(idx);
                    return true;
                }
            }

            DockPanel active = node.activePanel();
            if (active != null
                && active.component().mouseClicked(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    public static boolean dispatchPressed(DockManager manager,
                                          double mx, double my, int button) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            if (!contains(node.bounds(), mx, my)) continue;
            if (node.isCollapsed()) return true;

            DockPanel active = node.activePanel();
            if (active != null
                && active.component().mousePressed(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    public static boolean dispatchReleased(DockManager manager,
                                           double mx, double my, int button) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;

            DockPanel active = node.activePanel();
            if (active != null
                && active.component().mouseReleased(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    public static boolean dispatchDrag(DockManager manager,
                                       double mx, double my, int button,
                                       double dragX, double dragY) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            if (node.isCollapsed()) continue;

            DockPanel active = node.activePanel();
            if (active != null
                && active.component().mouseDragged(mx, my, button,
                    dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    public static boolean dispatchScroll(DockManager manager,
                                         double mx, double my,
                                         double scrollX, double scrollY) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            if (node.isCollapsed()) continue;
            if (!contains(node.bounds(), mx, my)) continue;

            DockPanel active = node.activePanel();
            if (active != null
                && active.component().mouseScrolled(mx, my,
                    scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    public static boolean dispatchKey(DockManager manager,
                                      int keyCode, int scanCode, int modifiers) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            if (node.isCollapsed()) continue;

            DockPanel active = node.activePanel();
            if (active != null
                && active.component().keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public static boolean dispatchChar(DockManager manager,
                                       char codePoint, int modifiers) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            if (node.isCollapsed()) continue;

            DockPanel active = node.activePanel();
            if (active != null
                && active.component().charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public static void dispatchMove(DockManager manager, double mx, double my) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            if (node.isCollapsed()) continue;

            DockPanel active = node.activePanel();
            if (active != null) {
                active.component().mouseMoved(mx, my);
            }
        }
    }

    private static boolean contains(DockBounds b, double mx, double my) {
        return mx >= b.x() && mx < b.x() + b.width()
            && my >= b.y() && my < b.y() + b.height();
    }

    // ============================================================
    //  Current render context (для tab hit-test)
    // ============================================================

    private static MaredRenderContext currentContext;

    public static void beginFrame(MaredRenderContext context) {
        currentContext = context;
    }

    public static void endFrame() {
        currentContext = null;
    }
}