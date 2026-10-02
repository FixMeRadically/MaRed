package com.fixmer.mared.gui2.framework.components;

import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;

public class MaredPanel extends MaredComponent {

    private final int background;

    public MaredPanel() { this(ThemeColors.panel()); }

    public MaredPanel(int background) { this.background = background; }

    @Override
    protected void safeRender(MaredRenderContext context) {
        context.graphics().fill(
            bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
            background);
    }
}