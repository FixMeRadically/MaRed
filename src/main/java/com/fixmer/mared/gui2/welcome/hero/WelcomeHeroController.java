package com.fixmer.mared.gui2.welcome.hero;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.modules.ModuleId;
import com.fixmer.mared.gui2.studio.MaredStudioScreen;
import com.fixmer.mared.gui2.visual.scene.VisualScene;
import com.fixmer.mared.gui2.welcome.input.WelcomeClickHandler;
import com.fixmer.mared.gui2.welcome.input.WelcomeInputState;
import com.fixmer.mared.gui2.welcome.input.WelcomeMouseHandler;
import com.fixmer.mared.gui2.welcome.layout.WelcomeLayout;

import net.minecraft.client.Minecraft;

public final class WelcomeHeroController {

    private final WelcomeModuleSelector selector;
    private final WelcomeThemeController theme;
    private final WelcomeInputState input;
    private final WelcomeMouseHandler mouse;
    private final WelcomeClickHandler click;
    private final WelcomeLayout layout;
    private VisualScene currentScene;

    public WelcomeHeroController() {
        selector = new WelcomeModuleSelector();
        theme    = new WelcomeThemeController();
        input    = new WelcomeInputState();
        mouse    = new WelcomeMouseHandler(input);
        click    = new WelcomeClickHandler();
        layout   = new WelcomeLayout();
        registerModules();
    }

    private void registerModules() {
        selector.register(new WelcomeModuleButton(ModuleId.CONTENT,   "Content",   0x55FFAA));
        selector.register(new WelcomeModuleButton(ModuleId.WORLD,     "World",     0x55AAFF));
        selector.register(new WelcomeModuleButton(ModuleId.LOGIC,     "Logic",     0xAA55FF));
        selector.register(new WelcomeModuleButton(ModuleId.RESOURCES, "Resources", 0xFFAA55));
        selector.register(new WelcomeModuleButton(ModuleId.TOOLS,     "Tools",     0xFFFF55));
        selector.register(new WelcomeModuleButton(ModuleId.SCENARIOS, "Scenarios", 0xFF5577));
    }

    /**
     * Genesis: РµРґРёРЅР°СЏ С‚РѕС‡РєР° РїРµСЂРµСЃР±РѕСЂРєРё РіРµРѕРјРµС‚СЂРёРё.
     * Р’С‹Р·С‹РІР°РµС‚СЃСЏ РёР· MaredWelcomeScreen.render() РєР°Р¶РґС‹Р№ РєР°РґСЂ - СЃР°Рј
     * WelcomeLayout.rebuild() РєСЌС€РёСЂСѓРµС‚ (width, height, count) Рё
     * РјРѕР»С‡Р° РІС‹С…РѕРґРёС‚, РµСЃР»Рё РЅРёС‡РµРіРѕ РЅРµ РїРѕРјРµРЅСЏР»РѕСЃСЊ.
     */
    public void resize(int width, int height) {
        layout.rebuild(width, height, selector.buttons());
    }

    public void open(ModuleId module) {
        if (module == null) return;

        selector.select(module);
        WelcomeModuleButton button = selector.active();
        if (button != null) {
            theme.setColor(button.color());
        }

        Mared.LOGGER.info("[welcome] opening module {}", module);

        com.fixmer.mared.gui2.launcher.MaredScreenManager.openStudio();
    }

    public void mouseMoved(double x, double y) {
        mouse.moved(x, y, layout);
    }

    public void mouseClicked(double x, double y) {
        click.clicked(x, y, layout, this);
    }

    public void tick() {
        theme.tick();
    }

    public WelcomeModuleSelector selector()   { return selector; }
    public WelcomeThemeController theme()     { return theme; }
    public WelcomeInputState input()          { return input; }
    public WelcomeLayout layout()             { return layout; }
    public VisualScene scene()                { return currentScene; }
}