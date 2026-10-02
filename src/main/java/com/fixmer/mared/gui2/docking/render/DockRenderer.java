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

/**
 * Главный renderer и input-dispatcher Dock системы.
 *
 * 0.3.0:
 *   - Разделители между панелями (5px hit, 1px линия).
 *   - Drag разделителей меняет ratios панелей.
 *   - Hover-подсветка разделителей.
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

    /**
     * Отрисовка разделителей. Вызывается ПОСЛЕ render() — поверх панелей,
     * но под popup-меню. mouseX/mouseY — для hover.
     */
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

        // LEFT|CENTER
        drawVerticalDivider(context, leftW, yTop, yBottom,
            activeDrag == DividerKind.LEFT_CENTER,
            isNearX(mouseX, leftW) && mouseY >= yTop && mouseY < yBottom,
            isDraggingDivider());

        // CENTER|RIGHT
        int rx = screenWidth - rightW;
        drawVerticalDivider(context, rx, yTop, yBottom,
            activeDrag == DividerKind.CENTER_RIGHT,
            isNearX(mouseX, rx) && mouseY >= yTop && mouseY < yBottom,
            isDraggingDivider());

        // WORKSPACE|BOTTOM
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
        else if (anyDrag) color = DIVIDER_IDLE;
        else color = DIVIDER_IDLE;

        // тонкая линия
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
    //  Панели
    // ============================================================

    private static void renderNode(DockNode node, MaredRenderContext context) {
        if (node == null) return;
        if (!node.hasPanels()) return;

        DockBounds b = node.bounds();
        int headerH = DockStyle.HEADER_HEIGHT;

        if (b.height() < MIN_HEIGHT_FOR_HEADER) {
            for (DockPanel panel : node.panels()) {
                panel.component().layout(new MaredBounds(
                    b.x(), b.y(), b.width(), b.height()));
                panel.component().render(context);
            }
            return;
        }

        int contentX = b.x();
        int contentY = b.y() + headerH;
        int contentW = b.width();
        int contentH = Math.max(0, b.height() - headerH);

        for (DockPanel panel : node.panels()) {
            panel.component().layout(new MaredBounds(
                contentX, contentY, contentW, contentH));

            DockPanelRenderer.render(
                panel, context,
                b.x(), b.y(), b.width(), b.height()
            );

            panel.component().render(context);
        }
    }

    // ============================================================
    //  Divider drag API
    // ============================================================

    /** Начать drag, если точка попадает в hitbox разделителя. */
    public static boolean beginDividerDrag(DockManager manager,
                                           double mx, double my,
                                           int screenWidth, int screenHeight,
                                           int button) {
        if (button != 0) return false;

        DividerKind kind = hitDivider(manager, mx, my, screenWidth, screenHeight);
        if (kind == DividerKind.NONE) return false;

        activeDrag = kind;
        dragStartMx = mx;
        dragStartMy = my;
        dragScreenW = Math.max(1, screenWidth);
        dragScreenH = Math.max(1, screenHeight);
        dragStartRatio = manager.layout().ratio(kind.position);
        return true;
    }

    /** Обновить ratio во время drag. */
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

    /** Завершить drag. Сохранение — в MaredStudioController.shutdown(). */
    public static void endDividerDrag(DockManager manager) {
        if (activeDrag == DividerKind.NONE) return;
        activeDrag = DividerKind.NONE;
    }

    // ============================================================
    //  Hit-test
    // ============================================================

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

        // LEFT|CENTER
        if (near(mx, leftW) && my >= yTop && my < yBottom) {
            return DividerKind.LEFT_CENTER;
        }

        // CENTER|RIGHT
        int rx = screenWidth - rightW;
        if (near(mx, rx) && my >= yTop && my < yBottom) {
            return DividerKind.CENTER_RIGHT;
        }

        // WORKSPACE|BOTTOM
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
    //  Dispatch — клики
    // ============================================================

    public static boolean dispatchClick(DockManager manager,
                                        double mx, double my, int button) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            if (!contains(node.bounds(), mx, my)) continue;
            for (DockPanel panel : node.panels()) {
                if (panel.component().mouseClicked(mx, my, button)) return true;
            }
        }
        return false;
    }

    public static boolean dispatchPressed(DockManager manager,
                                          double mx, double my, int button) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            if (!contains(node.bounds(), mx, my)) continue;
            for (DockPanel panel : node.panels()) {
                if (panel.component().mousePressed(mx, my, button)) return true;
            }
        }
        return false;
    }

    public static boolean dispatchReleased(DockManager manager,
                                           double mx, double my, int button) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            for (DockPanel panel : node.panels()) {
                if (panel.component().mouseReleased(mx, my, button)) return true;
            }
        }
        return false;
    }

    public static boolean dispatchDrag(DockManager manager,
                                       double mx, double my, int button,
                                       double dragX, double dragY) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            for (DockPanel panel : node.panels()) {
                if (panel.component().mouseDragged(mx, my, button, dragX, dragY))
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
            if (!contains(node.bounds(), mx, my)) continue;
            for (DockPanel panel : node.panels()) {
                if (panel.component().mouseScrolled(mx, my, scrollX, scrollY))
                    return true;
            }
        }
        return false;
    }

    public static boolean dispatchKey(DockManager manager,
                                      int keyCode, int scanCode, int modifiers) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            for (DockPanel panel : node.panels()) {
                if (panel.component().keyPressed(keyCode, scanCode, modifiers))
                    return true;
            }
        }
        return false;
    }

    public static boolean dispatchChar(DockManager manager,
                                       char codePoint, int modifiers) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            for (DockPanel panel : node.panels()) {
                if (panel.component().charTyped(codePoint, modifiers)) return true;
            }
        }
        return false;
    }

    public static void dispatchMove(DockManager manager, double mx, double my) {
        for (DockNode node : manager.nodes()) {
            if (!node.hasPanels()) continue;
            for (DockPanel panel : node.panels()) {
                panel.component().mouseMoved(mx, my);
            }
        }
    }

    private static boolean contains(DockBounds b, double mx, double my) {
        return mx >= b.x() && mx < b.x() + b.width()
            && my >= b.y() && my < b.y() + b.height();
    }
}