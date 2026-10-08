package com.fixmer.mared.gui2.welcome.layout;

import com.fixmer.mared.gui2.welcome.hero.WelcomeModuleButton;

/**
 * Связка «кнопка + её геометрия».
 *
 * Genesis: WelcomeLayout строит List&lt;ButtonLayout&gt;, а mouse / click /
 * render ходят только по этому списку и не знают про startX / buttonY.
 *
 * contains() делегирует к WelcomeBounds - единственному владельцу
 * прямоугольника кнопки. Так hit-test и рендер гарантированно
 * используют одни и те же координаты.
 */
public final class ButtonLayout {

    private final WelcomeModuleButton button;
    private final WelcomeBounds bounds;

    public ButtonLayout(WelcomeModuleButton button, WelcomeBounds bounds) {
        this.button = button;
        this.bounds = bounds;
    }

    public WelcomeModuleButton button() {
        return button;
    }

    public WelcomeBounds bounds() {
        return bounds;
    }

    public boolean contains(double mouseX, double mouseY) {
        return bounds.contains(mouseX, mouseY);
    }
}