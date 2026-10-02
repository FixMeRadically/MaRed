package com.fixmer.mared.gui2.framework.components;

import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;

public class MaredLabel extends MaredComponent {

    private String text;

    public MaredLabel(String text) { this.text = text; }

    public void setText(String text) { this.text = text; }
    public String text() { return text; }

    @Override
    protected void safeRender(MaredRenderContext context) {
        context.graphics().drawString(
            context.font(),
            text,
            bounds.x(),
            bounds.y() + 12,
            ThemeColors.text()
        );
    }
}