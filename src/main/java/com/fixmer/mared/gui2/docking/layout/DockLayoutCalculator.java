package com.fixmer.mared.gui2.docking.layout;

import com.fixmer.mared.gui2.docking.DockConstraints;
import com.fixmer.mared.gui2.docking.DockLayout;
import com.fixmer.mared.gui2.docking.DockNode;
import com.fixmer.mared.gui2.docking.DockPosition;
import com.fixmer.mared.gui2.docking.style.DockStyle;

/**
 * Рассчитывает расположение Dock интерфейса.
 *
 * 0.3.0:
 *   - Bounds из ratios.
 *   - Размеры клампятся через DockConstraints per-позицию.
 *   - TOP — fixed(constraints).
 * 0.3.0 (fix): если на позиции нет зарегистрированных панелей — не
 * резервировать место.
 * 0.3.2 (collapse):
 *   - Свёрнутый узел занимает фиксированный минимум:
 *       LEFT/RIGHT — COLLAPSED_SIZE_SIDE,
 *       BOTTOM     — COLLAPSED_SIZE_BOTTOM.
 *   - TOP/CENTER игнорируют collapsed.
 */
public final class DockLayoutCalculator {

    private DockLayoutCalculator() {}

    public static void calculate(DockLayout layout,
                                  int screenWidth,
                                  int screenHeight) {

        DockNode topNode    = layout.node(DockPosition.TOP);
        DockNode leftNode   = layout.node(DockPosition.LEFT);
        DockNode rightNode  = layout.node(DockPosition.RIGHT);
        DockNode bottomNode = layout.node(DockPosition.BOTTOM);
        DockNode centerNode = layout.node(DockPosition.CENTER);

        boolean hasTop    = hasPanels(topNode);
        boolean hasLeft   = hasPanels(leftNode);
        boolean hasRight  = hasPanels(rightNode);
        boolean hasBottom = hasPanels(bottomNode);

        // 0.3.2: collapse.
        boolean leftCollapsed   = leftNode   != null && leftNode.isCollapsed();
        boolean rightCollapsed  = rightNode  != null && rightNode.isCollapsed();
        boolean bottomCollapsed = bottomNode != null && bottomNode.isCollapsed();

        DockConstraints topC    = layout.constraints(DockPosition.TOP);
        DockConstraints leftC   = layout.constraints(DockPosition.LEFT);
        DockConstraints rightC  = layout.constraints(DockPosition.RIGHT);
        DockConstraints bottomC = layout.constraints(DockPosition.BOTTOM);
        DockConstraints centerC = layout.constraints(DockPosition.CENTER);

        int topH = 0;
        if (hasTop) {
            topH = topC.resizable()
                ? clamp((int)(screenHeight * 0.05f), topC.minPx(), topC.maxPx())
                : topC.minPx();
        }

        // 0.3.2: свёрнутая панель = фиксированный минимум.
        int leftW = hasLeft
            ? (leftCollapsed
                ? DockStyle.COLLAPSED_SIZE_SIDE
                : leftC.clamp((int)(screenWidth * layout.ratio(DockPosition.LEFT))))
            : 0;

        int rightW = hasRight
            ? (rightCollapsed
                ? DockStyle.COLLAPSED_SIZE_SIDE
                : rightC.clamp((int)(screenWidth * layout.ratio(DockPosition.RIGHT))))
            : 0;

        int bottomH = hasBottom
            ? (bottomCollapsed
                ? DockStyle.COLLAPSED_SIZE_BOTTOM
                : bottomC.clamp((int)(screenHeight * layout.ratio(DockPosition.BOTTOM))))
            : 0;

        // Защита: LEFT + RIGHT не должны съесть CENTER.
        int minCenter = centerC.minPx();
        int availableForSides = Math.max(0, screenWidth - minCenter);
        if (leftW + rightW > availableForSides) {
            int total = leftW + rightW;
            if (total > 0) {
                leftW  = (int) ((long) leftW  * availableForSides / total);
                rightW = (int) ((long) rightW * availableForSides / total);
            } else {
                leftW = rightW = availableForSides / 2;
            }
        }

        int workspaceY = topH;
        int workspaceH = Math.max(40, screenHeight - topH - bottomH);
        int workspaceW = Math.max(minCenter, screenWidth - leftW - rightW);

        if (hasTop) {
            topNode.setBounds(new DockBounds(0, 0, screenWidth, topH));
        }
        if (hasLeft) {
            leftNode.setBounds(new DockBounds(0, workspaceY, leftW, workspaceH));
        }
        if (centerNode != null) {
            centerNode.setBounds(new DockBounds(leftW, workspaceY, workspaceW, workspaceH));
        }
        if (hasRight) {
            rightNode.setBounds(new DockBounds(screenWidth - rightW, workspaceY, rightW, workspaceH));
        }
        if (hasBottom) {
            bottomNode.setBounds(new DockBounds(0, screenHeight - bottomH, screenWidth, bottomH));
        }
    }

    private static boolean hasPanels(DockNode node) {
        return node != null && !node.panels().isEmpty();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}