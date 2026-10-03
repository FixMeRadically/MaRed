package com.fixmer.mared.gui2.framework.components;

import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;

/**
 * Панель с фоном.
 *
 * 0.3.1: live theme switching.
 *
 * Раньше панель запоминала ThemeColors.panel() в момент создания.
 * После смены активной темы существующий MaredPanel продолжал
 * рисовать старый цвет до пересоздания.
 *
 * Теперь:
 *   - explicit-цвет (переданный в конструктор) хранится как есть;
 *   - без explicit-цвета панель резолвит bgPanel через context.theme()
 *     во время render — тема применяется мгновенно.
 */
public class MaredPanel extends MaredComponent {

    /** null = semantic: использовать активную тему. */
    private final Integer explicitBackground;

    public MaredPanel() {
        this.explicitBackground = null;
    }

    public MaredPanel(int background) {
        this.explicitBackground = background;
    }

    @Override
    protected void safeRender(MaredRenderContext context) {
        int bg = explicitBackground != null
            ? explicitBackground
            : context.theme().bgPanel;
        context.graphics().fill(
            bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), bg);
    }
}