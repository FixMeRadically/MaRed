package com.fixmer.mared.gui2.welcome.render;

import com.fixmer.mared.gui2.welcome.hero.WelcomeHeroController;
import com.fixmer.mared.gui2.welcome.layout.ButtonLayout;
import com.fixmer.mared.gui2.welcome.render.environment.WelcomeEnvironment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 1.5.37.1A: РєРѕРѕСЂРґРёРЅР°С‚РѕСЂ РІРёР·СѓР°Р»СЊРЅРѕРіРѕ pipeline.
 *
 * Р’Р»Р°РґРµРµС‚ WelcomeEnvironment (СЃСЂРµРґР°) Рё WelcomeButtonRenderer
 * (РєР°СЂС‚РѕС‡РєРё РјРѕРґСѓР»РµР№). РЎР»РѕРё СЃСЂРµРґС‹ Р¶РёРІСѓС‚ РІРЅСѓС‚СЂРё Environment -
 * Р·РґРµСЃСЊ РёС… РїРѕС€С‚СѓС‡РЅРѕ РЅРµ РїРµСЂРµС‡РёСЃР»СЏРµРј.
 */
public final class WelcomeHeroRenderer {

    private final WelcomeEnvironment environment = new WelcomeEnvironment();
    private final WelcomeButtonRenderer buttons  = new WelcomeButtonRenderer();

    public void tick() {
        environment.tick();
    }

    public void render(
            GuiGraphics graphics,
            WelcomeHeroController controller,
            int width,
            int height,
            int mouseX,
            int mouseY
    ) {
        environment.setAccent(controller.theme().color());
        environment.setMouse(mouseX, mouseY, width, height);
        environment.render(graphics, width, height);

        Font font = Minecraft.getInstance().font;
        for (ButtonLayout bl : controller.layout().buttons()) {
            buttons.render(graphics, font, bl.button(), bl.bounds());
        }
    }
}