package com.fixmer.mared.gui2.welcome.input;

import com.fixmer.mared.gui2.welcome.hero.WelcomeHeroController;
import com.fixmer.mared.gui2.welcome.layout.ButtonLayout;
import com.fixmer.mared.gui2.welcome.layout.WelcomeLayout;

public final class WelcomeClickHandler {

    public void clicked(
            double x,
            double y,
            WelcomeLayout layout,
            WelcomeHeroController controller
    ) {
        for (ButtonLayout bl : layout.buttons()) {
            if (bl.contains(x, y)) {
                controller.open(bl.button().module());
                return;
            }
        }
    }
}